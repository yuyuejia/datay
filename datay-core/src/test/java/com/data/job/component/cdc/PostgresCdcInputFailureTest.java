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
 * 验证 PostgreSQL 连接持续失败时异常会向上传播，使 ETL 任务以失败结束。
 */
class PostgresCdcInputFailureTest {

    @Test
    void unreachablePostgresShouldFailEtlJobEvenIfIgnoreError() throws Exception {
        runAndAssertFails(
            "pg-cdc-connect-fail-test",
            job("jdbc:postgresql://127.0.0.1:1/test?connectTimeout=1&socketTimeout=1", "postgres")
        );
    }

    @Test
    void postgresAuthenticationFailureShouldFailEtlJobEvenIfIgnoreError() throws Exception {
        runAndAssertFails(
            "pg-cdc-auth-fail-test",
            job(
                "jdbc:postgresql://127.0.0.1:5432/testdb?connectTimeout=1&socketTimeout=1",
                "datay_no_such_role_" + System.currentTimeMillis()
            )
        );
    }

    private void runAndAssertFails(String jobCode, String job) throws Exception {
        String instanceCode = jobCode + "-" + System.currentTimeMillis();
        TaskInstance instance = new TaskInstance();
        instance.setId(instanceCode);
        instance.setInstanceCode(instanceCode);
        instance.setJobCode(jobCode);
        instance.setJobName(jobCode);

        ETLFlowTask task = new ETLFlowTask(instance, null);
        try {
            RuntimeException error = assertThrows(RuntimeException.class, () -> task.runJob(job));

            assertTrue(error.getMessage().contains("最大重连次数 1"), error.getMessage());
        } finally {
            Files.deleteIfExists(Path.of("./log", "status_" + jobCode + ".json"));
            deleteLogDir(jobCode);
        }
    }

    private String job(String url, String username) {
        return """
            {
              "units": [
                {
                  ".id": "PostgresCDCInput",
                  ".name": "PostgresCDCInput",
                  "sourceId": {
                    "url": "%s",
                    "username": "%s",
                    "password": "",
                    "dbschema": "public"
                  },
                  "schema": "public",
                  "tableNamePattern": "not_exists",
                  "reconnectMaxAttempts": 1,
                  "reconnectIntervalMs": 10,
                  "ignoreError": true
                }
              ],
              "connections": [],
              "version": "1.0.0"
            }
            """.formatted(url, username);
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
