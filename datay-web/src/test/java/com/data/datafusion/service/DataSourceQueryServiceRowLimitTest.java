package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.service.dto.DataSourceDTO;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link DataSourceQueryService} 行数限制行为验证。
 *
 * <p>使用内存 DuckDB 执行真实查询，覆盖默认行数、调用方指定行数、硬上限收敛，
 * 以及 SQL 自带更小 LIMIT 时不重复截断等场景。
 */
class DataSourceQueryServiceRowLimitTest {

    @TempDir
    Path tempDir;

    private DataSourceQueryService service;
    private DataSourceDTO dataSource;

    @BeforeEach
    void setUp() {
        service = new DataSourceQueryService();
        ReflectionTestUtils.setField(service, "defaultRows", 1000);
        ReflectionTestUtils.setField(service, "maxRows", 10000);

        dataSource = new DataSourceDTO();
        dataSource.setType("DUCKDB");
        dataSource.setUrl("jdbc:duckdb:");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldApplyDefaultLimitWhenCallerDoesNotSpecifyRows() throws Exception {
        Map<String, Object> result = service.executeQuery(dataSource, "SELECT * FROM range(5000)", null);

        assertThat((List<Map<String, Object>>) result.get("rows")).hasSize(1000);
        assertThat(result.get("rowLimit")).isEqualTo(1000);
        assertThat(result.get("truncated")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRespectCallerSpecifiedRows() throws Exception {
        Map<String, Object> result = service.executeQuery(dataSource, "SELECT * FROM range(5000)", 100);

        assertThat((List<Map<String, Object>>) result.get("rows")).hasSize(100);
        assertThat(result.get("rowLimit")).isEqualTo(100);
        assertThat(result.get("truncated")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldClampSpecifiedRowsToConfiguredMaxRows() throws Exception {
        ReflectionTestUtils.setField(service, "maxRows", 200);

        Map<String, Object> result = service.executeQuery(dataSource, "SELECT * FROM range(5000)", 5000);

        assertThat((List<Map<String, Object>>) result.get("rows")).hasSize(200);
        assertThat(result.get("rowLimit")).isEqualTo(200);
        assertThat(result.get("truncated")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldClampDefaultRowsToMaxRowsWhenDefaultIsLarger() throws Exception {
        ReflectionTestUtils.setField(service, "maxRows", 50);
        ReflectionTestUtils.setField(service, "defaultRows", 1000);

        Map<String, Object> result = service.executeQuery(dataSource, "SELECT * FROM range(5000)", null);

        assertThat((List<Map<String, Object>>) result.get("rows")).hasSize(50);
        assertThat(result.get("rowLimit")).isEqualTo(50);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldClampNonPositiveRowsToOne() throws Exception {
        Map<String, Object> result = service.executeQuery(dataSource, "SELECT * FROM range(5000)", -10);

        assertThat((List<Map<String, Object>>) result.get("rows")).hasSize(1);
        assertThat(result.get("rowLimit")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotMarkTruncatedWhenResultIsBelowLimit() throws Exception {
        Map<String, Object> result = service.executeQuery(dataSource, "SELECT * FROM range(10)", 100);

        assertThat((List<Map<String, Object>>) result.get("rows")).hasSize(10);
        assertThat(result.get("rowLimit")).isEqualTo(100);
        assertThat(result).doesNotContainKey("truncated");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHonorSmallerLimitAlreadyPresentInSql() throws Exception {
        Map<String, Object> result = service.executeQuery(dataSource, "SELECT * FROM range(5000) LIMIT 5", 100);

        assertThat((List<Map<String, Object>>) result.get("rows")).hasSize(5);
        assertThat(result).doesNotContainKey("truncated");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldApplyHardLimitToQueriesWithoutExplicitLimit() throws Exception {
        // 不传 limit 的通用入口仍然受 max-rows 保护
        Map<String, Object> result = service.executeQuery(dataSource, "SELECT * FROM range(20000)");

        assertThat((List<Map<String, Object>>) result.get("rows")).hasSize(10000);
        assertThat(result.get("rowLimit")).isEqualTo(10000);
        assertThat(result.get("truncated")).isEqualTo(true);
    }

    @Test
    void shouldNotAffectNonSelectStatements() throws Exception {
        Path database = tempDir.resolve("row-limit-dml.duckdb");
        try (
            Connection connection = DriverManager.getConnection("jdbc:duckdb:" + database);
            Statement statement = connection.createStatement()
        ) {
            statement.execute("CREATE TABLE t (id INTEGER)");
        }

        DataSourceDTO fileDataSource = new DataSourceDTO();
        fileDataSource.setType("DUCKDB");
        fileDataSource.setUrl("jdbc:duckdb:" + database);

        Map<String, Object> result = service.executeQuery(fileDataSource, "INSERT INTO t VALUES (1), (2), (3)", 1);

        assertThat(result.get("affectedRows")).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnResultForValuesStatement() throws Exception {
        // VALUES 不是 SELECT 开头，旧实现会把它当成更新语句而报错。
        Map<String, Object> result = service.executeQuery(dataSource, "VALUES (1, 2)", null);

        assertThat((List<Map<String, Object>>) result.get("rows")).hasSize(1);
        assertThat((List<String>) result.get("columns")).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnResultForSelectPrefixedWithComment() throws Exception {
        // 带注释的 SELECT 首关键字不是 SELECT，旧实现同样会误判。
        Map<String, Object> result = service.executeQuery(dataSource, "-- 调试注释\nSELECT 1 AS v", null);

        assertThat((List<Map<String, Object>>) result.get("rows")).hasSize(1);
        assertThat(result.get("rows").toString()).contains("v=1");
    }

    @Test
    void shouldExecuteMultiStatementScriptAndReturnLastResultSet() throws Exception {
        Map<String, Object> result = service.executeQuery(
            dataSource,
            "CREATE TABLE t (id INTEGER); INSERT INTO t VALUES (1), (2); SELECT * FROM t ORDER BY id",
            null
        );

        assertThat((List<Map<String, Object>>) result.get("rows")).hasSize(2);
        assertThat(result.get("affectedRows")).isEqualTo(2);
    }
}
