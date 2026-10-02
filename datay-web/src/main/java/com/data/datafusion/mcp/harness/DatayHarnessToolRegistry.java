package com.data.datafusion.mcp.harness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DataY 管理 Harness 的工具注册中心。
 *
 * <p>Spring 构造注入 {@code List<DatayHarnessTool>} 会带上容器内全部实现，
 * 因此接入新工具是纯粹的「加一个 Bean」，不涉及任何中心化改动。
 *
 * <p>{@code /mcp/datay} 端点直接遍历 {@link #all()} 注册工具能力，
 * 工具目录接口也复用同一份元数据，保证「能看到的」与「能调用的」始终一致。
 */
@Component
public class DatayHarnessToolRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(DatayHarnessToolRegistry.class);

    private final Map<String, DatayHarnessTool> tools = new LinkedHashMap<>();

    public DatayHarnessToolRegistry(List<DatayHarnessTool> harnessTools) {
        if (harnessTools != null) {
            for (DatayHarnessTool tool : harnessTools) {
                register(tool);
            }
        }
        LOG.info("DataY harness tool registry initialized, {} tool(s) registered: {}", tools.size(), tools.keySet());
    }

    /**
     * 注册单个工具，同名工具会被拒绝，避免模型端出现歧义。
     */
    public void register(DatayHarnessTool tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            LOG.warn("Skip registering harness tool with blank name: {}", tool);
            return;
        }
        DatayHarnessTool existing = tools.putIfAbsent(tool.name(), tool);
        if (existing != null) {
            throw new IllegalStateException("Duplicate harness tool name detected: " + tool.name());
        }
    }

    public Optional<DatayHarnessTool> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<DatayHarnessTool> all() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public int size() {
        return tools.size();
    }

    /**
     * 生成工具目录，按能力分组，供 Agent 一次性了解 Harness 能力边界。
     *
     * @return 分组后的目录视图，仅包含工具名、说明、是否写操作与所需权限
     */
    public Map<String, List<Map<String, Object>>> catalog() {
        Map<String, List<Map<String, Object>>> grouped = new TreeMap<>();
        for (DatayHarnessTool tool : tools.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.name());
            item.put("description", tool.description());
            item.put("mutating", tool.mutating());
            Set<String> authorities = tool.requiredAuthorities();
            if (!authorities.isEmpty()) {
                item.put("requiredAuthorities", new ArrayList<>(authorities));
            }
            grouped.computeIfAbsent(tool.group(), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }
}
