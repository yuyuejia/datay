package com.data.datafusion.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.ai.AiResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link AiGenerateResponse} 结构化产物映射测试。
 */
class AiGenerateResponseTest {

    @Test
    void shouldMapEtlTaskSpecAndWarnings() {
        AiResult result = new AiResult();
        result.setEtlTaskSpec(Map.of("nodes", List.of()));
        result.getArtifacts().put(AiResult.ARTIFACT_WARNINGS, List.of("缺少必填配置：sql"));

        AiGenerateResponse response = AiGenerateResponse.from(result);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getEtlTaskSpec()).containsKey("nodes");
        assertThat(response.getWarnings()).containsExactly("缺少必填配置：sql");
    }
}
