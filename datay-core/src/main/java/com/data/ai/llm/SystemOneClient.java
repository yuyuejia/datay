package com.data.ai.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Ollama Jev-style 决策模型（{@code /v1/systemone}）客户端。
 *
 * <p>只依赖 OkHttp 与 fastjson2，不引入额外 SDK。请求体为
 * {@code {"model": "...", "state": {...}, "questions": {...}}}，一次请求对一组命名问题给出答案。
 * 典型对接 Ollama 本地服务（默认 {@code http://localhost:11434/v1}，模型 {@code nimble} / {@code tev1}）。
 *
 * <p>客户端本身不感知配置来源，连接参数通过 {@link LlmClientConfig} 传入；其中 {@code temperature}
 * 对决策接口不生效，仅复用其字段以保持配置结构一致。
 */
public class SystemOneClient {

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static final String DEFAULT_BASE_URL = "http://localhost:11434/v1";

    private final LlmClientConfig config;
    private final OkHttpClient httpClient;

    public SystemOneClient(LlmClientConfig config) {
        this.config = config;
        int timeout = config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 120;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 发起一次决策请求。
     *
     * @param state     作为模型输入的命名状态（通常为一行数据）
     * @param questions 问题定义，键为问题名，值为 {@code {type, instructions, criteria}}
     * @return 各问题的回答与 token 用量
     */
    public SystemOneResponse decide(JSONObject state, JSONObject questions) throws IOException {
        JSONObject payload = new JSONObject();
        payload.put("model", config.getModel());
        payload.put("state", state == null ? new JSONObject() : state);
        payload.put("questions", questions == null ? new JSONObject() : questions);

        Request request = new Request.Builder()
            .url(resolveEndpoint())
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + (config.getApiKey() == null ? "" : config.getApiKey()))
            .post(RequestBody.create(payload.toJSONString(), JSON_TYPE))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException(describeFailure(response.code(), body));
            }
            return parseResponse(body);
        } catch (java.net.ConnectException e) {
            throw new IOException("无法连接决策模型服务：" + resolveEndpoint() + "，请确认 Ollama 已启动（ollama serve）。", e);
        } catch (java.net.UnknownHostException e) {
            throw new IOException("决策模型服务地址无法解析：" + resolveEndpoint() + "，请检查 baseUrl 配置。", e);
        }
    }

    /**
     * 把 Ollama 的失败响应翻译成可操作的中文提示。
     */
    private String describeFailure(int code, String body) {
        String model = config.getModel();
        if (body != null && (body.contains("not found") || body.contains("try pulling"))) {
            return "决策模型「" + model + "」未安装，请先在 Ollama 中执行：ollama pull " + model;
        }
        if (body != null && body.contains("does not support decision")) {
            return "模型「" + model + "」不是决策模型，请改用 nimble / tev1 等决策模型。";
        }
        return "决策模型请求失败，状态码 " + code + "，响应内容：" + truncate(body);
    }

    private SystemOneResponse parseResponse(String body) {
        JSONObject root = JSON.parseObject(body);
        if (root == null) {
            throw new IllegalStateException("决策模型返回内容无法解析：" + truncate(body));
        }

        JSONObject usage = root.getJSONObject("usage");
        int inputTokens = usage == null ? 0 : usage.getIntValue("input_tokens");
        int outputTokens = usage == null ? 0 : usage.getIntValue("output_tokens");
        JSONObject answers = root.getJSONObject("answers");
        if (answers == null) {
            throw new IllegalStateException("决策模型未返回 answers：" + truncate(body));
        }
        return new SystemOneResponse(answers, inputTokens, outputTokens);
    }

    private String resolveEndpoint() {
        String base = config.getBaseUrl();
        if (base == null || base.isBlank()) {
            base = DEFAULT_BASE_URL;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/systemone")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/systemone";
        }
        return base + "/v1/systemone";
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) + "..." : value;
    }

    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
