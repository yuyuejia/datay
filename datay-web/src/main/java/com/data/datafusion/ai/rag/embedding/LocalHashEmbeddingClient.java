package com.data.datafusion.ai.rag.embedding;

import java.nio.charset.StandardCharsets;

/**
 * 本地字符 n-gram 哈希向量客户端。
 *
 * <p>将文本归一化后，按字符 1~3 gram 做哈希分桶累加，再做 L2 归一化。
 * 完全离线、确定性，可覆盖「销售额 / 销售金额」「北京 / 北京市」等字形相近的模糊匹配。
 */
public class LocalHashEmbeddingClient implements EmbeddingClient {

    private static final int MIN_DIMENSIONS = 16;

    private final int dimensions;

    public LocalHashEmbeddingClient(int dimensions) {
        this.dimensions = Math.max(MIN_DIMENSIONS, dimensions);
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimensions];
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return vector;
        }
        for (int n = 1; n <= 3; n++) {
            for (int i = 0; i + n <= normalized.length(); i++) {
                String gram = normalized.substring(i, i + n);
                int index = (int) (fnv1a(gram) % dimensions);
                vector[index] += 1.0f;
            }
        }
        l2Normalize(vector);
        return vector;
    }

    private static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static long fnv1a(String value) {
        long hash = 0xcbf29ce484222325L;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return hash & 0x7fffffffffffffffL;
    }

    private static void l2Normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += (double) v * v;
        }
        if (sum <= 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
