package com.data.datafusion.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

class DataQualityTaskContextAssemblerTest {

    @Test
    void rebindsResultPathsToCurrentInstance() {
        String jobContext =
            "{\"units\":[],\"dataQuality\":{" +
            "\"taskId\":\"task-1\",\"outputDir\":\"data-quality/task-1\"," +
            "\"summaryPath\":\"data-quality/task-1/old-instance/quality_summary.csv\"," +
            "\"resultPath\":\"data-quality/task-1/old-instance/quality_summary.csv\"," +
            "\"detailDir\":\"data-quality/task-1/old-instance\",\"rules\":[]}}";

        JSONObject metadata = JSONObject.parseObject(
            DataQualityTaskContextAssembler.assemble(jobContext, "new-instance")
        ).getJSONObject("dataQuality");

        assertThat(metadata.getString("summaryPath")).isEqualTo("data-quality/task-1/new-instance/quality_summary.csv");
        assertThat(metadata.getString("resultPath")).isEqualTo("data-quality/task-1/new-instance/quality_summary.csv");
        assertThat(metadata.getString("detailDir")).isEqualTo("data-quality/task-1/new-instance");
        assertThat(metadata.getString("taskId")).isEqualTo("task-1");
    }

    @Test
    void keepsContextWithoutDataQualityMetadata() {
        String jobContext = "{\"units\":[]}";

        assertThat(DataQualityTaskContextAssembler.assemble(jobContext, "new-instance")).isEqualTo(jobContext);
    }

    @Test
    void keepsContextWhenInstanceCodeOrOutputDirMissing() {
        String jobContext = "{\"dataQuality\":{\"outputDir\":\"data-quality/task-1\"}}";

        assertThat(DataQualityTaskContextAssembler.assemble(jobContext, null)).isEqualTo(jobContext);
        assertThat(DataQualityTaskContextAssembler.assemble(jobContext, " ")).isEqualTo(jobContext);
        assertThat(
            DataQualityTaskContextAssembler.assemble("{\"dataQuality\":{}}", "new-instance")
        ).isEqualTo("{\"dataQuality\":{}}");
    }
}
