package com.data.datafusion.ai.rag.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本向量化客户端。
 *
 * <p>为指标知识库提供统一的 embedding 能力，屏蔽本地哈希向量与外部
 * OpenAI 兼容接口的差异。索引与查询必须使用同一实现，否则向量维度不一致。
 */
public interface EmbeddingClient {

    /** 提供方标识，用于索引元信息与一致性校验。 */
    String provider();

    /** 向量维度。 */
    int dimensions();

    /** 当前客户端是否可用。 */
    boolean available();

    /** 将单段文本向量化为 L2 归一化向量。 */
    float[] embed(String text);

    /** 批量向量化，默认逐个调用。 */
    default List<float[]> embedAll(List<String> texts) {
        List<float[]> vectors = new ArrayList<>();
        if (texts == null) {
            return vectors;
        }
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }
}
