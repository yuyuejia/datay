package com.data.datafusion.ai;

import com.data.datafusion.ai.assistant.AiAssistant;
import com.data.datafusion.ai.assistant.AiAssistantContext;
import com.data.ai.llm.ChatMessage;
import com.data.ai.llm.ChatResponse;
import com.data.ai.llm.OpenAiCompatibleClient;
import com.data.ai.llm.ToolCall;
import com.data.ai.llm.ToolDefinition;
import com.data.datafusion.ai.llm.LlmConfig;
import com.data.datafusion.ai.llm.LlmConfigService;
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
 * 通用 AI 助手 Agent。
 *
 * <p>核心是一个「思考 → 调用工具 → 观察结果 → 再思考」的循环：
 * <ol>
 *     <li>用助手自定义的系统提示词，把用户需求连同该助手可用的工具下发给模型；</li>
 *     <li>若模型请求调用工具，则在本进程执行工具并把结果回填为 tool 消息；</li>
 *     <li>重复上述过程，直到模型不再请求工具、直接给出最终答案；</li>
 *     <li>若达到最大轮次仍未收敛，则按助手的收敛提示词强制关闭工具、输出结果。</li>
 * </ol>
 * 整个循环是一问一答的串行推进，工具执行结果真实来自数据库，避免模型凭空编造。
 *
 * <p>Agent 本身不感知具体场景，提示词、工具集合与输出抽取均由 {@link AiAssistant} 决定。
 */
@Service
public class AiAgent {

    private static final Logger LOG = LoggerFactory.getLogger(AiAgent.class);

    private final AiToolRegistry toolRegistry;
    private final LlmConfigService llmConfigService;

    public AiAgent(AiToolRegistry toolRegistry, LlmConfigService llmConfigService) {
        this.toolRegistry = toolRegistry;
        this.llmConfigService = llmConfigService;
    }

    /**
     * 以指定助手执行一次生成会话，内部完成完整的 tool-calling loop。
     *
     * @param assistant 目标助手，决定提示词、可用工具与安全边界
     * @param userMessage 用户的自然语言需求
     * @param dataSource 当前绑定的数据源，可能为空（此时模型只能做理论推理）
     * @param history 历史对话（不含本轮 user 消息），可为空
     * @param contextData 会话附加上下文，如上游组件调试采样数据，可为空
     * @return 包含最终 SQL、说明与 loop 轨迹的结果
     */
    public AiResult generate(
        AiAssistant assistant,
        String userMessage,
        DataSourceDTO dataSource,
        List<ChatMessage> history,
        Map<String, Object> contextData
    ) {
        LlmConfig config = llmConfigService.getConfig();
        if (!config.isConfigured()) {
            throw new IllegalStateException("AI 助手未配置，请先在服务配置中设置大模型 API Key");
        }
        OpenAiCompatibleClient client = llmConfigService.getActiveClient();

        String dataSourceId = dataSource == null ? null : dataSource.getId();
        AiToolContext context = new AiToolContext(dataSourceId, dataSource, userMessage, assistant);
        boolean allowMutating = config.isAllowMutatingTools() && assistant.allowMutatingTools();

        List<AiTool> allowedTools = toolRegistry.allowed(allowMutating, assistant);
        List<ToolDefinition> tools = allowedTools
            .stream()
            .map(AiAgent::definitionOf)
            .toList();
        Map<String, AiTool> toolIndex = new LinkedHashMap<>();
        for (AiTool tool : allowedTools) {
            toolIndex.put(tool.name(), tool);
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(assistant.buildSystemPrompt(new AiAssistantContext(dataSource, userMessage, contextData))));
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(userMessage));

        List<AiResult.ToolTrace> traces = new ArrayList<>();
        int round = 0;
        boolean toolsAvailable = !tools.isEmpty();
        ChatResponse lastResponse = null;
        int maxRounds = Math.max(1, config.getMaxToolRounds());

        while (round < maxRounds) {
            round++;
            boolean finalizeRound = round >= maxRounds;
            boolean forceFinal = finalizeRound && lastResponse != null && lastResponse.isToolCallRequested();

            if (forceFinal) {
                // 轮次耗尽且模型还在要工具：关掉工具，追加收敛指令，强制产出最终答案
                messages.add(ChatMessage.user(assistant.finalizeInstruction()));
                toolsAvailable = false;
            }

            ChatResponse response;
            try {
                response = client.chat(messages, toolsAvailable ? tools : List.of(), false);
            } catch (Exception e) {
                LOG.error("LLM call failed at round {} for assistant {}", round, assistant.id(), e);
                throw new IllegalStateException("调用 AI 服务失败: " + e.getMessage(), e);
            }
            lastResponse = response;
            LOG.debug(
                "AI agent round {} assistant {}: content={}, toolCalls={}",
                round,
                assistant.id(),
                response.getContent(),
                response.getToolCalls().size()
            );

            if (!response.isToolCallRequested()) {
                // 模型给出最终答复，loop 结束
                return buildResult(assistant, response, traces, round, true, context.getArtifacts());
            }

            // 记录 assistant 的 tool_calls 消息，保证协议完整性
            messages.add(ChatMessage.assistant(response.getContent(), response.getToolCalls()));

            List<ToolCall> requested = response.getToolCalls();
            int executed = 0;
            for (ToolCall call : requested) {
                if (executed >= Math.max(1, config.getMaxToolCallsPerRound())) {
                    messages.add(
                        ChatMessage.tool(call.getId(), call.getName(), client.toJson(Map.of("error", "本轮工具调用次数超限，已忽略")))
                    );
                    continue;
                }
                executed++;

                Optional<AiTool> toolOpt = Optional.ofNullable(toolIndex.get(call.getName()));

                Map<String, Object> arguments = client.parseArguments(call.getArguments());
                Object result;
                boolean success = true;
                long start = System.currentTimeMillis();

                if (toolOpt.isEmpty()) {
                    success = false;
                    result = Map.of("error", "未知或当前助手不可用的工具: " + call.getName());
                } else {
                    AiTool tool = toolOpt.get();
                    if (tool.mutating() && !allowMutating) {
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

                traces.add(new AiResult.ToolTrace(call.getName(), arguments, success, System.currentTimeMillis() - start));
                messages.add(ChatMessage.tool(call.getId(), call.getName(), client.toJson(result)));
            }
        }

        // 理论上不会走到这里：最后一轮已关闭工具，模型必须给出内容
        if (lastResponse != null) {
            return buildResult(assistant, lastResponse, traces, round, false, context.getArtifacts());
        }
        throw new IllegalStateException("AI 助手未能生成结果");
    }

    private static ToolDefinition definitionOf(AiTool tool) {
        return new ToolDefinition(tool.name(), tool.description(), tool.parametersSchema());
    }

    private AiResult buildResult(
        AiAssistant assistant,
        ChatResponse response,
        List<AiResult.ToolTrace> traces,
        int rounds,
        boolean converged,
        Map<String, Object> artifacts
    ) {
        String content = response.getContent() == null ? "" : response.getContent();
        AiResult result = new AiResult();
        result.setSql(assistant.extractSql(content));
        result.setExplanation(content);
        result.setDashboardSpec(assistant.extractSpec(content));
        result.setRounds(rounds);
        result.setConverged(converged);
        result.setToolCalls(traces);
        result.setArtifacts(artifacts);
        result.setPromptTokens(response.getPromptTokens());
        result.setCompletionTokens(response.getCompletionTokens());
        return result;
    }
}
