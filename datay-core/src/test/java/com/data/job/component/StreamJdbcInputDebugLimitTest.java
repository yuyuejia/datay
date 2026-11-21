package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.data.job.DatasourceInfo;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * StreamJdbcInput 调试模式下行数限制下推到 SQL 的测试。
 */
class StreamJdbcInputDebugLimitTest extends ComponentTestBase {

    private StreamJdbcInput component;
    private TableMeta table;

    @BeforeEach
    void setUp() {
        component = new StreamJdbcInput();
        initComponent(component, "stream-jdbc-debug-limit-test", "StreamJdbcInput");

        table = new TableMeta();
        table.setSchema("demo");
        table.setTable("orders");

        useDatasource("mysql", "jdbc:mysql://localhost:3306/demo");
    }

    private void useDatasource(String type, String url) {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType(type);
        datasource.setUrl(url);
        component.setDatasource(datasource);
    }

    @Test
    void appendsDebugLimitByDefault() {
        context.setDebugMode(true);

        assertEquals(
            "SELECT * FROM demo.orders LIMIT 100",
            component.buildQuery(table, null, null)
        );
    }

    @Test
    void respectsConfiguredDebugRowLimit() {
        context.setDebugMode(true);
        context.setDebugRowLimit(10);

        assertEquals(
            "SELECT * FROM demo.orders LIMIT 10",
            component.buildQuery(table, null, null)
        );
    }

    @Test
    void doesNotAppendLimitWhenNotDebug() {
        assertFalse(component.buildQuery(table, null, null).contains("LIMIT"));
    }

    @Test
    void omitsNullSchema() {
        context.setDebugMode(true);
        table.setSchema(null);

        assertEquals(
            "SELECT * FROM orders LIMIT 100",
            component.buildQuery(table, null, null)
        );
    }

    @Test
    void appendsLimitAfterWhereAndIncrementCondition() {
        context.setDebugMode(true);
        component.setWhere("status = 'PAID'");
        component.setLastIncrValue("orders", 100);

        assertEquals(
            "SELECT * FROM demo.orders WHERE status = 'PAID' AND id > 100 LIMIT 100",
            component.buildQuery(table, "id", null)
        );
    }

    @Test
    void sqlServerUsesTopSyntax() {
        context.setDebugMode(true);
        useDatasource("sqlserver", "jdbc:sqlserver://localhost;databaseName=demo");

        assertEquals(
            "SELECT TOP 100 * FROM (SELECT * FROM demo.orders) t",
            component.buildQuery(table, null, null)
        );
    }

    @Test
    void hanaUsesTopSyntax() {
        context.setDebugMode(true);
        useDatasource("hana", "jdbc:sap://localhost:30015");

        assertEquals(
            "SELECT TOP 100 * FROM (SELECT * FROM demo.orders) t",
            component.buildQuery(table, null, null)
        );
    }

    @Test
    void oracleUsesRownumWrapper() {
        context.setDebugMode(true);
        useDatasource("oracle", "jdbc:oracle:thin:@//localhost:1521/demo");

        assertEquals(
            "SELECT * FROM (SELECT * FROM demo.orders) t WHERE ROWNUM <= 100",
            component.buildQuery(table, null, null)
        );
    }

    @Test
    void oracleRownumWrapperKeepsWhereCondition() {
        context.setDebugMode(true);
        useDatasource("oracle", "jdbc:oracle:thin:@//localhost:1521/demo");
        component.setWhere("status = 'PAID'");

        assertEquals(
            "SELECT * FROM (SELECT * FROM demo.orders WHERE status = 'PAID') t WHERE ROWNUM <= 100",
            component.buildQuery(table, null, null)
        );
    }
}
