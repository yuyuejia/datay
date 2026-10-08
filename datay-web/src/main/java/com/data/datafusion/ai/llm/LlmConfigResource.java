package com.data.datafusion.ai.llm;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 大模型服务配置接口。
 *
 * <p>提供「服务配置 · 大模型」分组的读取、保存与连通性测试，
 * 配置落库于 {@code dp_service_config} 的 {@code llm} 分组。
 */
@RestController
@RequestMapping("/api/ai/config")
public class LlmConfigResource {

    private static final Logger LOG = LoggerFactory.getLogger(LlmConfigResource.class);

    private final LlmConfigService llmConfigService;

    public LlmConfigResource(LlmConfigService llmConfigService) {
        this.llmConfigService = llmConfigService;
    }

    /**
     * {@code GET /ai/config} : 获取大模型配置（沿用系统级 API Key 时不返回该密钥）。
     */
    @GetMapping("")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TENANT_ADMIN')")
    public ResponseEntity<LlmConfig> getConfig() {
        return ResponseEntity.ok(llmConfigService.getConfigForDisplay());
    }

    /**
     * {@code POST /ai/config} : 保存大模型配置并使其立即生效。
     */
    @PostMapping("")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody LlmConfig config) {
        LOG.info("REST request to update LLM config: baseUrl={}, model={}", config.getBaseUrl(), config.getModel());
        try {
            llmConfigService.updateAndReload(config);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOG.error("Failed to update LLM config", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * {@code GET /ai/config/test} : 使用当前配置发起一次最小请求验证连通性。
     */
    @GetMapping("/test")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> testConfig() {
        Map<String, Object> result = new HashMap<>();
        try {
            String model = llmConfigService.testConnection();
            result.put("success", true);
            result.put("strategy", model);
        } catch (Exception e) {
            LOG.error("Failed to test LLM config", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
