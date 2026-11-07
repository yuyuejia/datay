package com.data.job.component.cdc;

import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 MongoDB 连接持续失败时异常会向上传播，使 ETL 任务以失败结束。
 */
class MongoCdcInputFailureTest {

    @Test
    void unreachableMongoShouldFailEtlJobEvenIfIgnoreError() throws Exception {
        String jobCode = "mongo-cdc-connect-fail-test";
        String instanceCode = jobCode + "-" + System.currentTimeMillis();
        TaskInstance instance = new TaskInstance();
        instance.setId(instanceCode);
        instance.setInstanceCode(instanceCode);
        instance.setJobCode(jobCode);
        instance.setJobName(jobCode);

        ETLFlowTask task = new ETLFlowTask(instance, null);
        try {
            RuntimeException error = assertThrows(RuntimeException.class, () -> task.runJob(job()));

            assertTrue(error.getMessage().contains("最大重连次数 1"), error.getMessage());
        } finally {
            Files.deleteIfExists(Path.of("./log", "status_" + jobCode + ".json"));
            deleteLogDir(jobCode);
        }
    }

    private String job() {
        return """
            {
              "units": [
                {
                  ".id": "MongoCDCInput",
                  ".name": "MongoCDCInput",
                  "sourceId": {
                    "url": "mongodb://127.0.0.1:1/test?serverSelectionTimeoutMS=200&connectTimeoutMS=200&socketTimeoutMS=200",
                    "dbschema": "test"
                  },
                  "database": "test",
                  "collection": "not_exists",
                  "reconnectMaxAttempts": 1,
                  "reconnectIntervalMs": 10,
                  "ignoreError": true
                }
              ],
              "connections": [],
              "version": "1.0.0"
            }
            """;
    }

    private void deleteLogDir(String jobCode) throws IOException {
        Path dir = Path.of("./log", jobCode);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        }
    }
}
