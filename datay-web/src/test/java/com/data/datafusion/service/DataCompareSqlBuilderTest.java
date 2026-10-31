package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataCompareSqlBuilderTest {

    private final DataCompareSqlBuilder builder = new DataCompareSqlBuilder();

    @Test
    void shouldBuildAndRunCompareSql() throws Exception {
        String sql = builder.buildCompareSql(List.of("id"), List.of("name", "amount"), false, true);

        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            executeStatements(
                connection,
                "CREATE TABLE dc_left (id BIGINT, name VARCHAR, amount VARCHAR);" +
                "INSERT INTO dc_left VALUES (1, 'Alice', '10'), (2, ' Bob ', '20'), (3, 'Carol', '30');" +
                "CREATE TABLE dc_right (id BIGINT, name VARCHAR, amount VARCHAR);" +
                "INSERT INTO dc_right VALUES (1, 'Alice', '10'), (2, 'Bob', '21'), (4, 'Dave', '40');"
            );
            executeStatements(connection, sql);

            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT * FROM dc_summary")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("left_rows")).isEqualTo(3);
                assertThat(rs.getLong("right_rows")).isEqualTo(3);
                assertThat(rs.getLong("same_rows")).isEqualTo(1);
                assertThat(rs.getLong("value_diff_rows")).isEqualTo(1);
                assertThat(rs.getLong("only_left_rows")).isEqualTo(1);
                assertThat(rs.getLong("only_right_rows")).isEqualTo(1);
                assertThat(rs.getLong("diff_rows")).isEqualTo(3);
            }

            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT diff_type, key__id FROM dc_result ORDER BY key__id")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("diff_type")).isEqualTo("VALUE_DIFF");
                assertThat(rs.getLong("key__id")).isEqualTo(2);
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("diff_type")).isEqualTo("ONLY_LEFT");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("diff_type")).isEqualTo("ONLY_RIGHT");
            }
        }
    }

    @Test
    void shouldWriteLocalExportFiles(@TempDir Path tempDir) throws Exception {
        FileStorageConfig config = new FileStorageConfig();
        config.type = "local";
        config.localBasePath = tempDir.toString();

        String resultPath = "data-compare/task1/instance1/diff_result_instance1.csv";
        String summaryPath = "data-compare/task1/instance1/summary_instance1.csv";
        Path outputDir = tempDir.resolve("tenants/t1/data-compare/task1/instance1");
        Files.createDirectories(outputDir);

        String compareSql = builder.buildCompareSql(List.of("id"), List.of("name"), false, false);
        String exportSql = builder.buildExportSql(config, "tenants/t1", resultPath, summaryPath, "CSV");

        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            executeStatements(
                connection,
                "CREATE TABLE dc_left (id BIGINT, name VARCHAR);" +
                "INSERT INTO dc_left VALUES (1, 'Alice');" +
                "CREATE TABLE dc_right (id BIGINT, name VARCHAR);" +
                "INSERT INTO dc_right VALUES (1, 'Bob');"
            );
            executeStatements(connection, compareSql);
            executeStatements(connection, exportSql);
        }

        assertThat(outputDir.resolve("diff_result_instance1.csv")).exists();
        assertThat(outputDir.resolve("summary_instance1.csv")).exists();
        assertThat(Files.readString(outputDir.resolve("summary_instance1.csv"))).contains("left_rows");
    }

    @Test
    void shouldBuildSuffixedCompareSqlForMultipleTables() {
        String sql = builder.buildCompareSql("1", List.of("id"), List.of("name"), false, false);
        assertThat(sql).contains("CREATE OR REPLACE TABLE dc_compare_1");
        assertThat(sql).contains("FROM dc_left_1");
        assertThat(sql).contains("FROM dc_right_1");
        assertThat(sql).contains("CREATE OR REPLACE TABLE dc_result_1");
        assertThat(sql).contains("CREATE OR REPLACE TABLE dc_summary_1");
    }

    @Test
    void shouldBuildCountOnlySummarySql() throws Exception {
        String sql = builder.buildCountSummarySql("0");
        assertThat(sql).contains("CREATE OR REPLACE TABLE dc_summary_0");
        assertThat(sql).contains("FROM dc_left_0");
        assertThat(sql).contains("FROM dc_right_0");
        assertThat(sql).contains("diff_rows");

        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            executeStatements(
                connection,
                "CREATE TABLE dc_left_0 (row_count BIGINT); INSERT INTO dc_left_0 VALUES (3);" +
                "CREATE TABLE dc_right_0 (row_count BIGINT); INSERT INTO dc_right_0 VALUES (2);"
            );
            executeStatements(connection, sql);
            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT * FROM dc_summary_0")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("left_rows")).isEqualTo(3);
                assertThat(rs.getLong("right_rows")).isEqualTo(2);
                assertThat(rs.getLong("diff_rows")).isEqualTo(1);
            }
        }
    }

    @Test
    void shouldBuildLocalExportSql() {
        FileStorageConfig config = new FileStorageConfig();
        config.type = "local";
        config.localBasePath = "./data/files";

        String sql = builder.buildExportSql(
            config,
            "tenants/t1",
            "data-compare/task1/instance1/diff_result_instance1.csv",
            "data-compare/task1/instance1/summary_instance1.csv",
            "CSV"
        );

        assertThat(sql).contains("COPY (SELECT * FROM dc_result)");
        assertThat(sql).contains("diff_result_instance1.csv");
        assertThat(sql).contains("summary_instance1.csv");
        assertThat(sql).contains("FORMAT CSV, HEADER");
        assertThat(sql).doesNotContain("CREATE SECRET");
    }

    @Test
    void shouldBuildMinioExportSqlWithSecret() {
        FileStorageConfig config = new FileStorageConfig();
        config.type = "minio";
        config.minioEndpoint = "http://minio:9000";
        config.minioAccessKey = "ak";
        config.minioSecretKey = "sk";
        config.minioBucketName = "datay-files";

        String sql = builder.buildExportSql(
            config,
            "tenants/t1",
            "data-compare/task1/instance1/diff_result_instance1.parquet",
            "data-compare/task1/instance1/summary_instance1.csv",
            "PARQUET"
        );

        assertThat(sql).contains("CREATE OR REPLACE SECRET dc_export_s3_secret");
        assertThat(sql).contains("TYPE s3");
        assertThat(sql).contains("ENDPOINT 'minio:9000'");
        assertThat(sql).contains("s3://datay-files/tenants/t1/data-compare/task1/instance1/diff_result_instance1.parquet");
        assertThat(sql).contains("FORMAT PARQUET");
        assertThat(sql).contains("FORMAT CSV, HEADER");
    }

    @Test
    void shouldRejectEmptyKeys() {
        assertThatThrownBy(() -> builder.buildCompareSql(List.of(), List.of("name"), false, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("主键字段");
    }

    private void executeStatements(Connection connection, String sql) throws Exception {
        for (String statementSql : sql.split(";")) {
            if (statementSql.isBlank()) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(statementSql);
            }
        }
    }
}
