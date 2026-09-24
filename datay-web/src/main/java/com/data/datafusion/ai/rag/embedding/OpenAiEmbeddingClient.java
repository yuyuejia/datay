package com.data.datafusion.ai.rag.embedding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OpenAI 兼容的 {@code /embeddings} 客户端。
 *
 * <p>适用于 OpenAI / 通义千问 / 智谱 / Ollama 等提供 embeddings 接口的服务。
 * 批量请求会按固定大小分片，避免单次输入过多。注意 DeepSeek 默认不提供该接口。
 */
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final int CHUNK_SIZE = 64;

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;

    private volatile int dimensions = 0;

    public OpenAiEmbeddingClient(String baseUrl, String apiKey, String model, int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : 60;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public boolean available() {
        return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public float[] embed(String text) {
        List<float[]> vectors = embedAll(List.of(text == null ? "" : text));
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        if (texts == null || texts.isEmpty()) {
            return result;
        }
        for (int start = 0; start < texts.size(); start += CHUNK_SIZE) {
            List<String> chunk = texts.subList(start, Math.min(start + CHUNK_SIZE, texts.size()));
            result.addAll(requestChunk(chunk));
        }
        return result;
    }

    private List<float[]> requestChunk(List<String> chunk) {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("input", chunk);

        Request request = new Request.Builder()
            .url(resolveEndpoint())
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(payload.toJSONString(), JSON_TYPE))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Embedding 请求失败，状态码 " + response.code() + "，响应：" + truncate(body));
            }
            return parse(body, chunk.size());
        } catch (IOException e) {
            throw new IllegalStateException("调用 Embedding 服务失败: " + e.getMessage(), e);
        }
    }

    private List<float[]> parse(String body, int expected) {
        JSONObject root = JSON.parseObject(body);
        JSONArray data = root == null ? null : root.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new IllegalStateException("Embedding 返回内容为空：" + truncate(body));
        }
        List<float[]> ordered = new ArrayList<>();
        for (int i = 0; i < expected; i++) {
            ordered.add(null);
        }
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            JSONArray values = item.getJSONArray("embedding");
            float[] vector = new float[values.size()];
            for (int j = 0; j < values.size(); j++) {
                vector[j] = values.getFloatValue(j);
            }
            dimensions = vector.length;
            int index = item.getIntValue("index", i);
            if (index >= 0 && index < ordered.size()) {
                ordered.set(index, vector);
            } else {
                ordered.add(vector);
            }
        }
        List<float[]> result = new ArrayList<>();
        for (float[] vector : ordered) {
            if (vector != null) {
                result.add(vector);
            }
        }
        return result;
    }

    private String resolveEndpoint() {
        String base = baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/embeddings")) {
            return base;
        }
        return base + "/embeddings";
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) + "..." : value;
    }
}
