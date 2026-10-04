package com.data.datafusion.service.apppackage;

import com.data.datafusion.service.apppackage.model.AppPackageContent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 资产包 JSON 编解码工具。
 *
 * <p>资产包是与前端、与其它租户交换的文件格式，必须独立于 Web 层的 Jackson 配置，
 * 因此这里持有一个专用 {@link ObjectMapper}：忽略未知字段（向后兼容新增字段）、
 * 序列化时跳过 null（清单更紧凑）。
 */
public final class AppPackageJson {

    private static final ObjectMapper MAPPER = JsonMapper
        .builder()
        .addModule(new JavaTimeModule())
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    private AppPackageJson() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** 对象转 JSON 字符串。 */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("资产包序列化失败: " + e.getMessage(), e);
        }
    }

    /** 解析资产包 JSON。 */
    public static AppPackageContent readContent(String json) {
        try {
            return MAPPER.readValue(json, AppPackageContent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("资产包内容解析失败，不是合法的资产包 JSON: " + e.getOriginalMessage(), e);
        }
    }

    /** 安全解析任意 JSON 文本，解析失败返回 null。 */
    public static JsonNode readTreeQuietly(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** 把 JSON 节点序列化为字符串，null 原样返回。 */
    public static String writeQuietly(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.toString();
    }
}
