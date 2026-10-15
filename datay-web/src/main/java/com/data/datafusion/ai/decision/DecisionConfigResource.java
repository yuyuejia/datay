package com.data.datafusion.ai.decision;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 决策模型服务配置接口。
 *
 * <p>提供「服务配置 · 决策模型」分组的读取、保存与连通性测试，
 * 配置落库于 {@code dp_service_config} 的 {@code decision} 分组。
 */
@RestController
@RequestMapping("/api/ai/decision/config")
public class DecisionConfigResource {

    private static final Logger LOG = LoggerFactory.getLogger(DecisionConfigResource.class);

    private final DecisionConfigService decisionConfigService;

    public DecisionConfigResource(DecisionConfigService decisionConfigService) {
        this.decisionConfigService = decisionConfigService;
    }

    /**
     * {@code GET /ai/decision/config} : 获取决策模型配置（沿用系统级 API Key 时不返回该密钥）。
     */
    @GetMapping("")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TENANT_ADMIN')")
    public ResponseEntity<DecisionConfig> getConfig() {
        return ResponseEntity.ok(decisionConfigService.getConfigForDisplay());
    }

    /**
     * {@code POST /ai/decision/config} : 保存决策模型配置并使其立即生效。
     */
    @PostMapping("")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody DecisionConfig config) {
        LOG.info("REST request to update decision config: baseUrl={}, model={}", config.getBaseUrl(), config.getModel());
        try {
            decisionConfigService.updateAndReload(config);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOG.error("Failed to update decision config", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * {@code GET /ai/decision/config/test} : 使用当前配置发起一次最小决策请求验证连通性。
     */
    @GetMapping("/test")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> testConfig() {
        Map<String, Object> result = new HashMap<>();
        try {
            String model = decisionConfigService.testConnection();
            result.put("success", true);
            result.put("strategy", model);
        } catch (Exception e) {
            LOG.error("Failed to test decision config", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
