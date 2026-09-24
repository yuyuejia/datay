package com.data.datafusion.ai.rag.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link LocalHashEmbeddingClient} 的单元测试。
 */
class LocalHashEmbeddingClientTest {

    private final LocalHashEmbeddingClient client = new LocalHashEmbeddingClient(256);

    @Test
    void similarTextShouldHaveHigherCosineThanUnrelated() {
        double similar = cosine(client.embed("销售额"), client.embed("销售金额"));
        double unrelated = cosine(client.embed("销售额"), client.embed("客户年龄"));
        assertThat(similar).isGreaterThan(unrelated);
        assertThat(similar).isGreaterThan(0.2);
    }

    @Test
    void vectorShouldBeL2Normalized() {
        float[] vector = client.embed("北京");
        double norm = 0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void shouldExposeProviderAndDimensions() {
        assertThat(client.provider()).isEqualTo("local");
        assertThat(client.dimensions()).isEqualTo(256);
        assertThat(client.available()).isTrue();
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }
}
