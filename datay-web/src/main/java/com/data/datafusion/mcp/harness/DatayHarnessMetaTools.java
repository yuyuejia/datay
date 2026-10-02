package com.data.datafusion.mcp.harness;

import static com.data.datafusion.mcp.harness.DatayHarnessSchema.object;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.properties;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Harness 自身的元工具。
 *
 * <p>让 Agent 能一次性看到平台当前暴露了哪些管理能力、哪些是写操作、哪些需要管理员权限，
 * 避免反复试探。工具清单来自 {@link DatayHarnessToolRegistry}，
 * 这里用 {@link ObjectProvider} 延迟获取注册中心，避免「注册中心收集工具、工具又依赖注册中心」的循环依赖。
 */
@Configuration
public class DatayHarnessMetaTools {

    @Bean
    DatayHarnessTool datayHarnessCatalogTool(ObjectProvider<DatayHarnessToolRegistry> registryProvider) {
        return DatayHarnessTool.read(
            "datay_harness_catalog",
            "列出 DataY Harness 当前暴露的全部工具，按对象分组并标注是否写操作、所需权限，用于确认平台支持的操作范围。",
            object(properties()),
            (args, context) -> {
                DatayHarnessToolRegistry registry = registryProvider.getObject();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("totalTools", registry.size());
                result.put("groups", registry.catalog());
                return result;
            }
        );
    }
}
