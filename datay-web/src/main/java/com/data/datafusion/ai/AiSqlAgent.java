package com.data.datafusion.ai;

import com.data.datafusion.ai.llm.AiProperties;
import com.data.datafusion.ai.llm.ChatMessage;
import com.data.datafusion.ai.llm.ChatResponse;
import com.data.datafusion.ai.llm.OpenAiCompatibleClient;
import com.data.datafusion.ai.llm.ToolCall;
import com.data.datafusion.ai.llm.ToolDefinition;
import com.data.datafusion.ai.tool.AiTool;
import com.data.datafusion.ai.tool.AiToolContext;
import com.data.datafusion.ai.tool.AiToolRegistry;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SQL 生成 Agent。
 *
 * <p>核心是一个「思考 → 调用工具 → 观察结果 → 再思考」的循环：
 * <ol>
 *     <li>把用户需求连同可用工具下发给模型；</li>
 *     <li>若模型请求调用工具，则在本进程执行工具并把结果回填为 tool 消息；</li>
 *     <li>重复上述过程，直到模型不再请求工具、直接给出最终 SQL；</li>
 *     <li>若达到最大轮次仍未收敛，则强制关闭工具，要求模型立即输出 SQL。</li>
 * </ol>
 * 整个循环是一问一答的串行推进，工具执行结果真实来自数据库，避免模型凭空编造表名与字段。
 */
@Service
public class AiSqlAgent {

    private static final Logger LOG = LoggerFactory.getLogger(AiSqlAgent.class);

    /** 收敛阶段提示词：工具已关闭，必须给出最终答案。 */
    private static final String FINALIZE_INSTRUCTION = """
        你已获得足够信息，请立即给出最终 SQL 答案。不要再请求任何工具调用。
        输出格式要求：
        1. 先用 ```sql 代码块给出**完整可执行**的 SQL（仅限单条 SELECT，禁止 DDL/DML）；
        2. 代码块之后用简体中文简要说明查询逻辑与涉及的表。
        """;

    private final AiToolRegistry toolRegistry;
    private final OpenAiCompatibleClient client;
    private final AiProperties properties;

    public AiSqlAgent(AiToolRegistry toolRegistry, OpenAiCompatibleClient client, AiProperties properties) {
        this.toolRegistry = toolRegistry;
        this.client = client;
        this.properties = properties;
    }

    /**
     * 执行一次 SQL 生成会话，内部完成完整的 tool-calling loop。
     *
     * @param userMessage 用户的自然语言需求
     * @param dataSource 当前绑定的数据源，可能为空（此时模型只能做理论推理）
     * @param history 历史对话（不含本轮 user 消息），可为空
     * @return 包含最终 SQL、说明与 loop 轨迹的结果
     */
    public AiSqlResult generate(String userMessage, DataSourceDTO dataSource, List<ChatMessage> history) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("AI 助手未配置，请先设置 datay.ai.api-key");
        }

        Long dataSourceId = dataSource == null ? null : dataSource.getId();
        AiToolContext context = new AiToolContext(dataSourceId, dataSource, userMessage);

        List<ToolDefinition> tools = toolRegistry.definitions(properties.isAllowMutatingTools());
        Map<String, AiTool> toolIndex = new LinkedHashMap<>();
        for (AiTool tool : toolRegistry.all()) {
            toolIndex.put(tool.name(), tool);
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(dataSource)));
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(userMessage));

        List<AiSqlResult.ToolTrace> traces = new ArrayList<>();
        int round = 0;
        boolean toolsAvailable = !tools.isEmpty();
        ChatResponse lastResponse = null;

        while (round < Math.max(1, properties.getMaxToolRounds())) {
            round++;
            boolean finalizeRound = round >= Math.max(1, properties.getMaxToolRounds());
            boolean forceFinal = finalizeRound && lastResponse != null && lastResponse.isToolCallRequested();

            if (forceFinal) {
                // 轮次耗尽且模型还在要工具：关掉工具，追加收敛指令，强制产出最终 SQL
                messages.add(ChatMessage.user(FINALIZE_INSTRUCTION));
                toolsAvailable = false;
            }

            ChatResponse response;
            try {
                response = client.chat(messages, toolsAvailable ? tools : List.of(), false);
            } catch (Exception e) {
                LOG.error("LLM call failed at round {}", round, e);
                throw new IllegalStateException("调用 AI 服务失败: " + e.getMessage(), e);
            }
            lastResponse = response;
            LOG.debug("AI agent round {}: content={}, toolCalls={}", round, response.getContent(), response.getToolCalls().size());

            if (!response.isToolCallRequested()) {
                // 模型给出最终答复，loop 结束
                return buildResult(response, traces, round, true);
            }

            // 记录 assistant 的 tool_calls 消息，保证协议完整性
            messages.add(ChatMessage.assistant(response.getContent(), response.getToolCalls()));

            List<ToolCall> requested = response.getToolCalls();
            int executed = 0;
            for (ToolCall call : requested) {
                if (executed >= Math.max(1, properties.getMaxToolCallsPerRound())) {
                    messages.add(
                        ChatMessage.tool(call.getId(), call.getName(), client.toJson(Map.of("error", "本轮工具调用次数超限，已忽略")))
                    );
                    continue;
                }
                executed++;

                Optional<AiTool> toolOpt = toolIndex.containsKey(call.getName())
                    ? Optional.of(toolIndex.get(call.getName()))
                    : toolRegistry.find(call.getName());

                Map<String, Object> arguments = client.parseArguments(call.getArguments());
                Object result;
                boolean success = true;
                long start = System.currentTimeMillis();

                if (toolOpt.isEmpty()) {
                    success = false;
                    result = Map.of("error", "未知工具: " + call.getName());
                } else {
                    AiTool tool = toolOpt.get();
                    if (tool.mutating() && !properties.isAllowMutatingTools()) {
                        success = false;
                        result = Map.of("error", "该工具具备写操作，当前已被禁用: " + call.getName());
                    } else {
                        try {
                            result = tool.execute(arguments, context);
                        } catch (Exception e) {
                            LOG.warn("AI tool {} execution failed", call.getName(), e);
                            success = false;
                            result = Map.of("error", "工具执行失败: " + e.getMessage());
                        }
                    }
                }

                traces.add(
                    new AiSqlResult.ToolTrace(
                        call.getName(),
                        arguments,
                        success,
                        System.currentTimeMillis() - start
                    )
                );
                messages.add(ChatMessage.tool(call.getId(), call.getName(), client.toJson(result)));
            }
        }

        // 理论上不会走到这里：最后一轮已关闭工具，模型必须给出内容
        if (lastResponse != null) {
            return buildResult(lastResponse, traces, round, false);
        }
        throw new IllegalStateException("AI 助手未能生成结果");
    }

    private AiSqlResult buildResult(ChatResponse response, List<AiSqlResult.ToolTrace> traces, int rounds, boolean converged) {
        String content = response.getContent() == null ? "" : response.getContent();
        String sql = SqlExtractor.extract(content);
        AiSqlResult result = new AiSqlResult();
        result.setSql(sql);
        result.setExplanation(content);
        result.setRounds(rounds);
        result.setConverged(converged);
        result.setToolCalls(traces);
        result.setPromptTokens(response.getPromptTokens());
        result.setCompletionTokens(response.getCompletionTokens());
        return result;
    }

    private String buildSystemPrompt(DataSourceDTO dataSource) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 DataY 数据平台内置的 SQL 助手，职责是把用户的自然语言数据需求翻译成正确的 SQL。\n\n");
        sb.append("工作方式：\n");
        sb.append("1. 先判断信息是否充分。若不清楚有哪些表、哪些字段，主动调用工具去查，不要凭经验猜测表名与字段名。\n");
        sb.append("2. 典型的探索路径：list_database_objects(schemas) → list_database_objects(tables) → list_database_objects(columns)。\n");
        sb.append("3. 写出 SQL 后，调用 validate_sql 自检；若有语法或字段错误，依据报错修正后重新校验。\n");
        sb.append("4. 仅在确有必要时调用 preview_sql_result 试跑，用于确认口径，不要滥用。\n");
        sb.append("5. 确认无误后直接给出最终答案，不必再有冗长铺垫。\n\n");
        sb.append("硬性约束：\n");
        sb.append("- 只生成单条只读 SELECT 查询，禁止 INSERT/UPDATE/DELETE/DDL 等任何写操作。\n");
        sb.append("- 严禁编造不存在的表名或字段名，所有标识符必须来自工具查询的真实元数据。\n");
        sb.append("- SQL 必须适配当前数据源的方言。\n");
        sb.append("- 输出用简体中文说明，SQL 放在 ```sql 代码块中。\n\n");

        if (dataSource != null) {
            sb.append("当前数据源信息：\n");
            sb.append("- 名称：").append(nullSafe(dataSource.getName())).append('\n');
            sb.append("- 类型：").append(nullSafe(dataSource.getType())).append('\n');
            if (dataSource.getSchemaName() != null && !dataSource.getSchemaName().isBlank()) {
                sb.append("- 默认 schema：").append(dataSource.getSchemaName()).append('\n');
            }
            sb.append("- 数据源已在会话上下文中绑定，调用工具时无需传入数据源标识。\n");
        } else {
            sb.append("本次会话未绑定具体数据源，若用户需要真实表结构，请提示其在数据查询页选择数据源后再试。\n");
        }
        return sb.toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }
}
