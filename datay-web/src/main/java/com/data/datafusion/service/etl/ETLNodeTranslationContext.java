package com.data.datafusion.service.etl;

import cn.hutool.json.JSONObject;
import com.data.datafusion.domain.ETLNode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ETL 节点翻译上下文。
 * <p>承载从 {@link ETLNode} 转换为 DataY Core 任务单元定义（unit）过程中所需的全部信息，
 * 翻译器（{@link ETLNodeTranslator}）只依赖该上下文，便于扩展与测试。
 */
public class ETLNodeTranslationContext {

    /** 设计器中的 ETL 节点。 */
    private final ETLNode node;

    /** 节点配置（已解析为 JSON 对象）。 */
    private final JSONObject config;

    /** 待填充的任务单元定义。 */
    private final Map<String, Object> unit;

    public ETLNodeTranslationContext(ETLNode node, JSONObject config, Map<String, Object> unit) {
        this.node = node;
        this.config = config;
        this.unit = unit == null ? new LinkedHashMap<>() : unit;
    }

    public ETLNode getNode() {
        return node;
    }

    public JSONObject getConfig() {
        return config;
    }

    public Map<String, Object> getUnit() {
        return unit;
    }

    /**
     * 读取配置项（字符串）。
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值，未配置时返回默认值
     */
    public String getConfigString(String key, String defaultValue) {
        Object value = config == null ? null : config.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString();
        return text.isEmpty() ? defaultValue : text;
    }
}
