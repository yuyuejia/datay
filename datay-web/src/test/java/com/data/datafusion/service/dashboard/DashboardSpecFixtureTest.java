package com.data.datafusion.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * v1 看板 spec 回归样例：作为 AI、前端、后端共用契约的基线。
 */
class DashboardSpecFixtureTest {

    @Test
    void shouldValidateVersion1Fixture() throws Exception {
        String json;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("dashboard/spec-v1-example.json")) {
            assertThat(in).isNotNull();
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        DashboardSpecValidator validator = new DashboardSpecValidator(
            new ObjectMapper(),
            new AnalysisDatasetResolver(mock(AnalysisDatasetRepository.class), new ObjectMapper())
        );

        Map<String, Object> spec = validator.validateAndNormalize(json);

        assertThat(spec).containsEntry("version", 1);
        assertThat(spec.get("widgets")).asList().hasSize(3);
        assertThat(spec.get("filters")).asList().hasSize(1);
    }
}
