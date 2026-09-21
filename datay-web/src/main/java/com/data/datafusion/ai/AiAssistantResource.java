package com.data.datafusion.ai;

import com.data.datafusion.ai.assistant.AiAssistant;
import com.data.datafusion.ai.assistant.AiAssistantRegistry;
import com.data.datafusion.ai.assistant.QueryAssistant;
import com.data.datafusion.ai.assistant.SqlTaskAssistant;
import com.data.datafusion.ai.dto.AiAssistantInfo;
import com.data.datafusion.ai.dto.AiGenerateRequest;
import com.data.datafusion.ai.dto.AiGenerateResponse;
import com.data.datafusion.ai.dto.AiToolInfo;
import com.data.datafusion.ai.llm.AiProperties;
import com.data.datafusion.ai.llm.ChatMessage;
import com.data.datafusion.ai.tool.AiTool;
import com.data.datafusion.ai.tool.AiToolRegistry;
import com.data.datafusion.service.DataSourceService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI 助手接口。
 *
 * <p>提供运行状态、助手清单（含各自能力与工具），以及按助手发起自然语言生成的入口。
 * 生成过程内部走完整的 tool-calling loop，由 {@link AiAgent} 负责编排。
 */
@RestController
@RequestMapping("/api/ai")
public class AiAssistantResource {

    private static final Logger LOG = LoggerFactory.getLogger(AiAssistantResource.class);

    private final AiAgent aiAgent;
    private final AiAssistantRegistry assistantRegistry;
    private final AiToolRegistry toolRegistry;
    private final AiProperties properties;
    private final DataSourceService dataSourceService;

    public AiAssistantResource(
        AiAgent aiAgent,
        AiAssistantRegistry assistantRegistry,
        AiToolRegistry toolRegistry,
        AiProperties properties,
        DataSourceService dataSourceService
    ) {
        this.aiAgent = aiAgent;
        this.assistantRegistry = assistantRegistry;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.dataSourceService = dataSourceService;
    }

    /**
     * 查询 AI 服务可用状态。
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("available", properties.isConfigured());
        body.put("model", properties.getModel());
        body.put("maxToolRounds", properties.getMaxToolRounds());
        body.put("toolCount", toolRegistry.all().size());
        body.put("assistantCount", assistantRegistry.all().size());
        if (!properties.isConfigured()) {
            body.put("message", "AI 助手未配置，请在配置文件中设置 datay.ai.api-key");
        }
        return ResponseEntity.ok(body);
    }

    /**
     * 列出已注册的助手及其能力（示例提问、可用工具）。
     */
    @GetMapping("/assistants")
    public ResponseEntity<List<AiAssistantInfo>> assistants() {
        List<AiAssistantInfo> assistants = assistantRegistry
            .all()
            .stream()
            .map(this::toAssistantInfo)
            .toList();
        return ResponseEntity.ok(assistants);
    }

    /**
     * 自然语言转 SQL。内部自动完成多轮工具调用，直到模型给出最终 SQL。
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody AiGenerateRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请输入您的数据需求"));
        }
        if (!properties.isConfigured()) {
            return ResponseEntity.ok(AiGenerateResponse.unavailable("AI 助手未配置，请先设置 datay.ai.api-key"));
        }

        Optional<AiAssistant> assistant = resolveAssistant(request);
        if (assistant.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "未知的 AI 助手: " + request.getAssistantId()));
        }

        var dataSource = Optional.ofNullable(request.getDataSourceId())
            .flatMap(dataSourceService::findOne)
            .orElse(null);

        try {
            AiResult result = aiAgent.generate(assistant.get(), request.getMessage(), dataSource, toHistory(request));
            return ResponseEntity.ok(AiGenerateResponse.from(result));
        } catch (IllegalStateException e) {
            LOG.warn("AI generation failed for assistant {}: {}", assistant.get().id(), e.getMessage());
            return ResponseEntity.status(502).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            LOG.error("AI generation error for assistant {}", assistant.get().id(), e);
            return ResponseEntity.status(500).body(Map.of("message", "生成 SQL 失败: " + e.getMessage()));
        }
    }

    /**
     * 解析目标助手：优先按 assistantId，其次兼容旧的 mode 字段，最后回退到缺省助手。
     * 显式传入的 assistantId 无法识别时返回空，由调用方给出 400。
     */
    private Optional<AiAssistant> resolveAssistant(AiGenerateRequest request) {
        String assistantId = request.getAssistantId();
        if (assistantId != null && !assistantId.isBlank()) {
            return assistantRegistry.find(assistantId);
        }
        String mode = request.getMode();
        if (mode != null && !mode.isBlank()) {
            String mapped = "task".equalsIgnoreCase(mode.trim()) ? SqlTaskAssistant.ID : QueryAssistant.ID;
            return assistantRegistry.find(mapped);
        }
        return Optional.of(assistantRegistry.defaultAssistant());
    }

    private AiAssistantInfo toAssistantInfo(AiAssistant assistant) {
        boolean allowMutating = properties.isAllowMutatingTools() && assistant.allowMutatingTools();
        List<AiToolInfo> tools = toolRegistry
            .allowed(allowMutating, assistant)
            .stream()
            .map(this::toToolInfo)
            .toList();
        return new AiAssistantInfo(
            assistant.id(),
            assistant.displayName(),
            assistant.description(),
            assistant.allowWrites(),
            assistant.samplePrompts(),
            tools
        );
    }

    private AiToolInfo toToolInfo(AiTool tool) {
        return new AiToolInfo(tool.name(), tool.description(), tool.mutating(), tool.parametersSchema());
    }

    private List<ChatMessage> toHistory(AiGenerateRequest request) {
        List<ChatMessage> history = new ArrayList<>();
        if (request.getHistory() == null) {
            return history;
        }
        for (AiGenerateRequest.HistoryMessage item : request.getHistory()) {
            if (item.getContent() == null || item.getContent().isBlank()) {
                continue;
            }
            String role = item.getRole() == null ? "user" : item.getRole().toLowerCase();
            if ("assistant".equals(role)) {
                history.add(ChatMessage.assistant(item.getContent(), List.of()));
            } else if ("user".equals(role)) {
                history.add(ChatMessage.user(item.getContent()));
            }
        }
        return history;
    }
}
