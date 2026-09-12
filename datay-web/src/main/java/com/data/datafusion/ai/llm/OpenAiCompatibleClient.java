package com.data.datafusion.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenAI Chat Completions 兼容客户端。
 *
 * <p>只依赖 JDK 自带的 {@link HttpClient}，不引入额外 SDK，
 * 从而可以无缝对接任何遵循该协议的推理服务。
 */
@Component
public class OpenAiCompatibleClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 发起一次对话补全请求。
     *
     * @param messages 历史消息（含 system / user / assistant / tool）
     * @param tools 可下发的工具定义，为空表示不启用 function calling
     * @param forceToolChoice 是否强制模型必须调用工具，用于收敛 loop
     */
    public ChatResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools, boolean forceToolChoice)
        throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", properties.getModel());
        payload.put("temperature", properties.getTemperature());
        payload.put("stream", false);

        ArrayNode messageArray = payload.putArray("messages");
        for (ChatMessage message : messages) {
            messageArray.add(objectMapper.valueToTree(message.toOpenAiFormat()));
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolArray = payload.putArray("tools");
            for (ToolDefinition tool : tools) {
                toolArray.add(objectMapper.valueToTree(tool.toOpenAiFormat()));
            }
            if (forceToolChoice) {
                payload.put("tool_choice", "required");
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(resolveEndpoint()))
            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + properties.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOG.warn("LLM request failed with status {}: {}", response.statusCode(), response.body());
            throw new IllegalStateException("AI 服务返回异常状态 " + response.statusCode() + ": " + truncate(response.body()));
        }

        return parseResponse(response.body());
    }

    private ChatResponse parseResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);

        JsonNode usage = root.path("usage");
        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("AI 服务未返回任何结果");
        }
        JsonNode message = choices.get(0).path("message");

        String content = message.path("content").isMissingNode() || message.path("content").isNull()
            ? null
            : message.path("content").asText();

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode calls = message.path("tool_calls");
        if (calls.isArray()) {
            for (JsonNode call : calls) {
                String id = call.path("id").asText("");
                JsonNode function = call.path("function");
                String name = function.path("name").asText("");
                String arguments = function.path("arguments").asText("{}");
                toolCalls.add(new ToolCall(id, name, arguments));
            }
        }

        return new ChatResponse(content, toolCalls, promptTokens, completionTokens);
    }

    private String resolveEndpoint() {
        String base = properties.getBaseUrl();
        if (base == null || base.isBlank()) {
            base = "https://api.openai.com/v1";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) + "..." : value;
    }

    /**
     * 将任意对象序列化为紧凑 JSON 字符串，用于 tool 消息回填。
     */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return Map.of("error", "序列化结果失败: " + e.getMessage()).toString();
        }
    }

    /**
     * 将模型给出的工具入参 JSON 字符串解析为 Map，解析失败时返回空 Map。
     */
    public Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            LOG.debug("Failed to parse tool arguments: {}", arguments);
            return Map.of();
        }
    }
}
