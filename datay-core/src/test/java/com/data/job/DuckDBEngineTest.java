package com.data.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuckDBEngineTest {

    @Test
    void getDBFileKeepsAlreadyAbsolutePath() {
        Path absolute = Paths.get(System.getProperty("user.dir"), "log", "duckdb-engine-absolute.db")
            .toAbsolutePath()
            .normalize();

        assertEquals(absolute.toString(), DuckDBEngine.getDBFile(absolute.toString()));
    }

    @Test
    void deleteDBFileClosesPoolCreatedWithRelativeName() throws Exception {
        String instanceCode = "duckdb-engine-close-" + System.nanoTime();
        String dbFile = DuckDBEngine.getDBFile(instanceCode);
        try {
            try (Connection conn = DuckDBEngine.getInstance().getConnection(instanceCode);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE t (id INTEGER)");
            }

            assertTrue(
                DuckDBEngine.getInstance().getAllPoolStatus().containsKey(dbFile),
                "连接池 key 应为规范化后的绝对路径"
            );

            DuckDBEngine.deleteDBFile(instanceCode);

            assertFalse(
                DuckDBEngine.getInstance().getAllPoolStatus().containsKey(dbFile),
                "deleteDBFile 应关闭并移除对应的实例连接池"
            );
            assertFalse(Files.exists(Paths.get(dbFile)), "实例数据库文件应被删除");
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    @Test
    @Timeout(60000)
    void simpleJobClosesInstancePoolAndHousekeeperThread() throws Exception {
        String instanceCode = "duckdb-simple-job-" + System.nanoTime();
        String dbFile = DuckDBEngine.getDBFile(instanceCode);
        ETLFlowTask runner = new ETLFlowTask();
        runner.getContext().setJobInstanceCode(instanceCode);
        runner.getContext().setJobCode("test-job");
        String job = """
            {
              "units": [
                {".id": "gen", ".name": "GenerateFlowFile", "dataFormat": "JSON_ARRAY",
                 "inputData": "[{\\"id\\":1,\\"name\\":\\"alice\\"}]"},
                {".id": "sink", ".name": "DuckDBWrite", "table": "t", "model": "append"}
              ],
              "connections": [
                {"sourceId": "gen", "targetId": "sink", "sourcePort": 0}
              ]
            }
            """;
        try {
            runner.runJob(job);

            assertInstanceClosed(dbFile);
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    @Test
    @Timeout(60000)
    void debugJobClosesInstancePool() throws Exception {
        String instanceCode = "duckdb-debug-job-" + System.nanoTime();
        String dbFile = DuckDBEngine.getDBFile(instanceCode);
        ETLFlowTask runner = new ETLFlowTask();
        runner.getContext().setJobInstanceCode(instanceCode);
        runner.getContext().setJobCode("test-job");
        runner.getContext().setDebugMode(true);
        runner.getContext().setDebugRowLimit(10);
        String job = """
            {
              "units": [
                {".id": "gen", ".name": "GenerateFlowFile", "dataFormat": "JSON_ARRAY",
                 "inputData": "[{\\"id\\":1}]"},
                {".id": "sql", ".name": "DuckDBSql",
                 "sql": "CREATE TABLE debug_t (id INTEGER); INSERT INTO debug_t VALUES (1);"}
              ],
              "connections": [
                {"sourceId": "gen", "targetId": "sql", "sourcePort": 0}
              ]
            }
            """;
        try {
            runner.runJob(job);

            assertInstanceClosed(dbFile);
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    @Test
    @Timeout(60000)
    void failedJobClosesInstancePool() throws Exception {
        String instanceCode = "duckdb-failed-job-" + System.nanoTime();
        String dbFile = DuckDBEngine.getDBFile(instanceCode);
        ETLFlowTask runner = new ETLFlowTask();
        runner.getContext().setJobInstanceCode(instanceCode);
        runner.getContext().setJobCode("test-job");
        String job = """
            {
              "units": [
                {".id": "gen", ".name": "GenerateFlowFile", "dataFormat": "JSON_ARRAY",
                 "inputData": "[{\\"id\\":1}]"},
                {".id": "sql", ".name": "DuckDBSql", "sql": "SELECT * FROM missing_table;"}
              ],
              "connections": [
                {"sourceId": "gen", "targetId": "sql", "sourcePort": 0}
              ]
            }
            """;
        try {
            assertThrows(Exception.class, () -> runner.runJob(job));

            assertInstanceClosed(dbFile);
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    private void assertInstanceClosed(String dbFile) throws InterruptedException {
        assertFalse(Files.exists(Paths.get(dbFile)), "任务结束后实例数据库文件应被删除");
        assertFalse(
            DuckDBEngine.getInstance().getAllPoolStatus().containsKey(dbFile),
            "任务结束后实例连接池应被关闭并移除"
        );

        // HikariCP 的 housekeeper 线程名包含连接池名称，确认没有残留
        assertFalse(isHousekeeperStillAlive(dbFile, 3, TimeUnit.SECONDS), "任务结束后不应残留实例连接池的 housekeeper 线程");
    }

    private boolean isHousekeeperStillAlive(String dbFile, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        do {
            String expectedThreadName = "DuckDB-Pool-" + dbFile + " housekeeper";
            boolean alive = Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.isAlive() && expectedThreadName.equals(thread.getName()));
            if (!alive) {
                return false;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        return true;
    }
}
