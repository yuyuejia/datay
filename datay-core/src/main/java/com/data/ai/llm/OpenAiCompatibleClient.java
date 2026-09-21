package com.data.ai.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI Chat Completions 兼容客户端。
 *
 * <p>只依赖 OkHttp 与 fastjson2，不引入额外 SDK，可无缝对接 OpenAI / DeepSeek / 通义千问 / Ollama 等
 * 遵循该协议的推理服务。支持 function calling（tools），为上层工具注册预留能力。
 *
 * <p>该类被 DataY Core 的大模型组件与 DataY Web 的 AI 助手共用，配置通过 {@link LlmClientConfig} 传入。
 */
public class OpenAiCompatibleClient {

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final LlmClientConfig config;
    private final OkHttpClient httpClient;

    public OpenAiCompatibleClient(LlmClientConfig config) {
        this.config = config;
        int timeout = config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 120;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 发起一次对话补全请求。
     *
     * @param messages        历史消息（含 system / user / assistant / tool）
     * @param tools           可下发的工具定义，为空表示不启用 function calling
     * @param forceToolChoice 是否强制模型必须调用工具，用于收敛 loop
     */
    public ChatResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools, boolean forceToolChoice) throws IOException {
        JSONObject payload = new JSONObject();
        payload.put("model", config.getModel());
        payload.put("temperature", config.getTemperature());
        payload.put("stream", false);

        JSONArray messageArray = new JSONArray();
        for (ChatMessage message : messages) {
            messageArray.add(message.toOpenAiFormat());
        }
        payload.put("messages", messageArray);

        if (tools != null && !tools.isEmpty()) {
            JSONArray toolArray = new JSONArray();
            for (ToolDefinition tool : tools) {
                toolArray.add(tool.toOpenAiFormat());
            }
            payload.put("tools", toolArray);
            if (forceToolChoice) {
                payload.put("tool_choice", "required");
            }
        }

        Request request = new Request.Builder()
            .url(resolveEndpoint())
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + config.getApiKey())
            .post(RequestBody.create(payload.toJSONString(), JSON_TYPE))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("大模型请求失败，状态码 " + response.code() + "，响应内容：" + truncate(body));
            }
            return parseResponse(body);
        }
    }

    private ChatResponse parseResponse(String body) {
        JSONObject root = JSON.parseObject(body);
        if (root == null) {
            throw new IllegalStateException("大模型返回内容无法解析：" + truncate(body));
        }

        JSONObject usage = root.getJSONObject("usage");
        int promptTokens = usage == null ? 0 : usage.getIntValue("prompt_tokens");
        int completionTokens = usage == null ? 0 : usage.getIntValue("completion_tokens");

        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("大模型未返回任何结果：" + truncate(body));
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        String content = message == null ? null : message.getString("content");

        List<ToolCall> toolCalls = new ArrayList<>();
        if (message != null) {
            JSONArray calls = message.getJSONArray("tool_calls");
            if (calls != null) {
                for (int i = 0; i < calls.size(); i++) {
                    JSONObject call = calls.getJSONObject(i);
                    JSONObject function = call.getJSONObject("function");
                    toolCalls.add(
                        new ToolCall(
                            call.getString("id"),
                            function == null ? "" : function.getString("name"),
                            function == null ? "{}" : function.getString("arguments")
                        )
                    );
                }
            }
        }

        return new ChatResponse(content, toolCalls, promptTokens, completionTokens);
    }

    private String resolveEndpoint() {
        String base = config.getBaseUrl();
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
            return JSON.toJSONString(value);
        } catch (Exception e) {
            return "{\"error\":\"序列化结果失败\"}";
        }
    }

    /**
     * 将模型给出的工具入参 JSON 字符串解析为 Map，解析失败时返回空 Map。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return (Map<String, Object>) JSON.parseObject(arguments, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
