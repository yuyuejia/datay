package com.data.datafusion.ai;

import com.data.datafusion.ai.dto.AiSqlRequest;
import com.data.datafusion.ai.dto.AiSqlResponse;
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
 * AI SQL 助手接口。
 *
 * <p>提供助手状态查询、已注册工具清单，以及自然语言转 SQL 的生成入口。
 * 生成过程内部走完整的 tool-calling loop，由 {@link AiSqlAgent} 负责编排。
 */
@RestController
@RequestMapping("/api/ai/sql")
public class AiSqlResource {

    private static final Logger LOG = LoggerFactory.getLogger(AiSqlResource.class);

    private final AiSqlAgent aiSqlAgent;
    private final AiToolRegistry toolRegistry;
    private final AiProperties properties;
    private final DataSourceService dataSourceService;

    public AiSqlResource(
        AiSqlAgent aiSqlAgent,
        AiToolRegistry toolRegistry,
        AiProperties properties,
        DataSourceService dataSourceService
    ) {
        this.aiSqlAgent = aiSqlAgent;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.dataSourceService = dataSourceService;
    }

    /**
     * 查询助手可用状态。
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("available", properties.isConfigured());
        body.put("model", properties.getModel());
        body.put("maxToolRounds", properties.getMaxToolRounds());
        body.put("toolCount", toolRegistry.all().size());
        if (!properties.isConfigured()) {
            body.put("message", "AI 助手未配置，请在配置文件中设置 datay.ai.api-key");
        }
        return ResponseEntity.ok(body);
    }

    /**
     * 查询已注册的工具清单，便于前端展示助手能力。
     *
     * @param mode 生成场景模式，用于过滤仅适用于特定模式的工具
     */
    @GetMapping("/tools")
    public ResponseEntity<List<AiToolInfo>> tools(@RequestParam(name = "mode", required = false) String mode) {
        AiSqlMode effectiveMode = AiSqlMode.from(mode);
        List<AiToolInfo> tools = new ArrayList<>();
        for (AiTool tool : toolRegistry.all()) {
            if (!tool.supports(effectiveMode)) {
                continue;
            }
            tools.add(new AiToolInfo(tool.name(), tool.description(), tool.mutating(), tool.parametersSchema()));
        }
        return ResponseEntity.ok(tools);
    }

    /**
     * 自然语言转 SQL。内部自动完成多轮工具调用，直到模型给出最终 SQL。
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody AiSqlRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请输入您的数据需求"));
        }
        if (!properties.isConfigured()) {
            return ResponseEntity.ok(AiSqlResponse.unavailable("AI 助手未配置，请先设置 datay.ai.api-key"));
        }

        var dataSource = Optional.ofNullable(request.getDataSourceId())
            .flatMap(dataSourceService::findOne)
            .orElse(null);
        AiSqlMode mode = AiSqlMode.from(request.getMode());

        try {
            AiSqlResult result = aiSqlAgent.generate(request.getMessage(), dataSource, toHistory(request), mode);
            return ResponseEntity.ok(AiSqlResponse.from(result));
        } catch (IllegalStateException e) {
            LOG.warn("AI SQL generation failed: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            LOG.error("AI SQL generation error", e);
            return ResponseEntity.status(500).body(Map.of("message", "生成 SQL 失败: " + e.getMessage()));
        }
    }

    private List<ChatMessage> toHistory(AiSqlRequest request) {
        List<ChatMessage> history = new ArrayList<>();
        if (request.getHistory() == null) {
            return history;
        }
        for (AiSqlRequest.HistoryMessage item : request.getHistory()) {
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
