package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.datafusion.domain.DataCompareTask;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import com.data.job.TaskLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * 真实跨库数据比对验证：MySQL / PostgreSQL / DuckDB 两两比对。
 *
 * <p>依赖本机 MySQL(3306) 和 PostgreSQL(5432)；不可用时自动跳过。</p>
 */
class DataCompareCrossDatabaseTest {

    private static final String MYSQL_URL =
        "jdbc:mysql://127.0.0.1:3306/source?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";
    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/testdb";
    private static final String PG_USER = "admin";
    private static final String PG_PASSWORD = "123456";
    private static final String VERIFY_TABLE = "dc_cross_verify";

    @Test
    @Timeout(180)
    void shouldCompareMysqlPostgresAndDuckdbPairwise(@TempDir Path tempDir) throws Exception {
        assumeTrue(canConnect(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD), "MySQL source 库不可用，跳过");
        assumeTrue(canConnect(PG_URL, PG_USER, PG_PASSWORD), "PostgreSQL testdb 库不可用，跳过");

        Path duckdbFile = tempDir.resolve("compare_source.duckdb");
        prepareMysql();
        preparePostgres();
        prepareDuckdb(duckdbFile);

        DataSourceDTO mysql = dataSource("MYSQL", MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD, "source");
        DataSourceDTO postgres = dataSource("POSTGRESQL", PG_URL, PG_USER, PG_PASSWORD, "public");
        DataSourceDTO duckdb = dataSource("DUCKDB", "jdbc:duckdb:" + duckdbFile, null, null, "main");

        // MySQL 数据：1/10、2/21、3/30、4/40
        // PostgreSQL 数据：1/10、2/20、4/40、5/50
        // DuckDB 数据：1/10、2/20、3/30、4/40
        assertPair(tempDir, "mysql-pg", mysql, "source", postgres, "public", new Expected(2, 3, 1, 1, 1));
        assertPair(tempDir, "mysql-duckdb", mysql, "source", duckdb, "main", new Expected(3, 1, 0, 0, 1));
        assertPair(tempDir, "pg-duckdb", postgres, "public", duckdb, "main", new Expected(3, 2, 1, 1, 0));
    }

    private void assertPair(
        Path tempDir,
        String pairName,
        DataSourceDTO left,
        String leftSchema,
        DataSourceDTO right,
        String rightSchema,
        Expected expected
    ) throws Exception {
        String instanceCode = "dc-verify-" + pairName + "-" + System.currentTimeMillis();
        String runDir = "data-compare/" + pairName;
        String resultPath = runDir + "/diff_result_" + instanceCode + ".csv";
        String summaryPath = runDir + "/summary_" + instanceCode + ".csv";
        Files.createDirectories(tempDir.resolve("tenants/test/" + runDir));

        DataCompareTask task = new DataCompareTask();
        task.setTaskName("verify-" + pairName);
        task.setLeftTable(VERIFY_TABLE);
        task.setRightTable(VERIFY_TABLE);
        task.setLeftWhere("");
        task.setRightWhere("");

        DataCompareSqlBuilder builder = new DataCompareSqlBuilder();
        String compareSql = builder.buildCompareSql("0", List.of("id"), List.of("name", "amount"), false, false);
        FileStorageConfig storage = new FileStorageConfig();
        storage.type = "local";
        storage.localBasePath = tempDir.toString();
        String exportSql = builder.buildExportSql(
            storage,
            "tenants/test",
            resultPath,
            summaryPath,
            "CSV",
            DataCompareSqlBuilder.tableName(DataCompareSqlBuilder.RESULT_TABLE, "0"),
            DataCompareSqlBuilder.tableName(DataCompareSqlBuilder.SUMMARY_TABLE, "0")
        );

        DataCompareService service = new DataCompareService(null, null, null, null, null, null, null, null, null);
        String jobJson = service.buildJobJson(task, left, right, leftSchema, rightSchema, compareSql, exportSql);
        runEtl(jobJson, instanceCode);

        Path summaryFile = tempDir.resolve("tenants/test/" + summaryPath);
        Path resultFile = tempDir.resolve("tenants/test/" + resultPath);
        assertThat(summaryFile).exists();
        assertThat(resultFile).exists();

        List<String> summaryValues = DataCompareResultService.parseCsvLine(Files.readAllLines(summaryFile).get(1));
        long leftRows = Long.parseLong(summaryValues.get(0));
        long rightRows = Long.parseLong(summaryValues.get(1));
        long sameRows = Long.parseLong(summaryValues.get(2));
        long valueDiffRows = Long.parseLong(summaryValues.get(3));
        long onlyLeftRows = Long.parseLong(summaryValues.get(4));
        long onlyRightRows = Long.parseLong(summaryValues.get(5));
        long diffRows = Long.parseLong(summaryValues.get(6));

        assertThat(leftRows).as(pairName + " left rows").isEqualTo(4);
        assertThat(rightRows).as(pairName + " right rows").isEqualTo(4);
        assertThat(sameRows).as(pairName + " same rows").isEqualTo(expected.same);
        assertThat(valueDiffRows).as(pairName + " value diff rows").isEqualTo(expected.valueDiff);
        assertThat(onlyLeftRows).as(pairName + " only left rows").isEqualTo(expected.onlyLeft);
        assertThat(onlyRightRows).as(pairName + " only right rows").isEqualTo(expected.onlyRight);
        assertThat(diffRows).as(pairName + " diff rows").isEqualTo(expected.diff);

        List<String> resultLines = Files.readAllLines(resultFile);
        assertThat(resultLines).as(pairName + " result file").hasSize((int) expected.diff + 1);
    }

    private void runEtl(String jobJson, String instanceCode) throws Exception {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setJobCode("dc-verify");
        taskInstance.setInstanceCode(instanceCode);
        taskInstance.setJobContext(jobJson);
        taskInstance.setType(TaskConstants.TASK_TYPE_ETL);

        TaskLogger logger = new TaskLogger("dc-verify", instanceCode);
        try {
            ETLFlowTask flowTask = new ETLFlowTask(taskInstance, logger);
            flowTask.runJob(jobJson);
        } finally {
            logger.closeLogFile();
        }
    }

    private void prepareMysql() throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
            Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + VERIFY_TABLE);
            statement.execute("CREATE TABLE " + VERIFY_TABLE + " (id BIGINT PRIMARY KEY, name VARCHAR(64), amount DECIMAL(10,2))");
            statement.execute(
                "INSERT INTO " + VERIFY_TABLE + " VALUES (1,'Alice',10.00),(2,'Bob',21.00),(3,'Carol',30.00),(4,'Dave',40.00)"
            );
        }
    }

    private void preparePostgres() throws Exception {
        try (Connection connection = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
            Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + VERIFY_TABLE);
            statement.execute("CREATE TABLE " + VERIFY_TABLE + " (id BIGINT PRIMARY KEY, name VARCHAR(64), amount NUMERIC(10,2))");
            statement.execute(
                "INSERT INTO " + VERIFY_TABLE + " VALUES (1,'Alice',10.00),(2,'Bob',20.00),(4,'Dave',40.00),(5,'Eve',50.00)"
            );
        }
    }

    private void prepareDuckdb(Path duckdbFile) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + duckdbFile);
            Statement statement = connection.createStatement()) {
            statement.execute("CREATE OR REPLACE TABLE " + VERIFY_TABLE + " (id BIGINT, name VARCHAR, amount DECIMAL(10,2))");
            statement.execute(
                "INSERT INTO " + VERIFY_TABLE + " VALUES (1,'Alice',10.00),(2,'Bob',20.00),(3,'Carol',30.00),(4,'Dave',40.00)"
            );
        }
    }

    private static DataSourceDTO dataSource(String type, String url, String username, String password, String schema) {
        DataSourceDTO dto = new DataSourceDTO();
        dto.setType(type);
        dto.setUrl(url);
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setSchemaName(schema);
        dto.setHostname("127.0.0.1");
        return dto;
    }

    private static boolean canConnect(String url, String username, String password) {
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private record Expected(long same, long diff, long onlyLeft, long onlyRight, long valueDiff) {}
}
