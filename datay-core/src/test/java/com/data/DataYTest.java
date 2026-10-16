package com.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * {@link DataY} 入口的单元测试：验证退出码约定（成功 0 / 失败非 0）。
 */
class DataYTest {

    private static final String SIMPLE_JOB = """
        {
          "units": [
            { ".id": "gen", ".name": "GenerateFlowFile", "inputData": "hello", "dataFormat": "TEXT", "loopCount": "1" },
            { ".id": "log", ".name": "LogFlowFile", "logDataContent": "true", "maxDataLength": "100" }
          ],
          "connections": [ { "sourceId": "gen", "targetId": "log" } ],
          "version": "1.0.0"
        }
        """;

    @Test
    @Timeout(30000)
    void returnsZeroOnSuccess() throws Exception {
        Path jobFile = Files.createTempFile("datay-job", ".json");
        Files.writeString(jobFile, SIMPLE_JOB, StandardCharsets.UTF_8);
        assertEquals(0, DataY.run(new String[] { jobFile.toString() }));
    }

    @Test
    void returnsNonZeroWhenFileMissing() {
        assertNotEquals(0, DataY.run(new String[] { "/not/exist/datay-job.json" }));
    }

    @Test
    @Timeout(30000)
    void returnsNonZeroOnInvalidJob() throws Exception {
        Path jobFile = Files.createTempFile("datay-invalid", ".json");
        Files.writeString(jobFile, "{ not-a-valid-job }", StandardCharsets.UTF_8);
        assertNotEquals(0, DataY.run(new String[] { jobFile.toString() }));
    }
}
