package com.data.datafusion.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.service.dataset.DatasetFilterSqlBuilder;
import com.data.datafusion.service.dataset.DatasetScopeCondition;
import com.data.datafusion.service.dataset.DatasetScopeSqlBuilder;
import com.data.datafusion.service.dataset.DatasetSortLimitSqlBuilder;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 真实数据库方言验证：行级权限 + 字段筛选 + 排序 + 分页 SQL。
 *
 * <p>默认跳过。启动 MySQL / PostgreSQL / SQL Server 容器后，用
 * {@code DATAY_MULTI_DB_IT=true} 运行。
 */
@EnabledIfEnvironmentVariable(named = "DATAY_MULTI_DB_IT", matches = "true")
class DashboardDatabaseDialectTest {

    private static final List<DatabaseCase> DATABASES = List.of(
        new DatabaseCase(
            "mysql",
            "MYSQL",
            "jdbc:mysql://127.0.0.1:13306/datay_test?useSSL=false&allowPublicKeyRetrieval=true",
            "root",
            "Datay@12345"
        ),
        new DatabaseCase(
            "postgres",
            "POSTGRESQL",
            "jdbc:postgresql://127.0.0.1:15432/datay_test",
            "postgres",
            "Datay@12345"
        ),
        new DatabaseCase(
            "sqlserver",
            "SQLSERVER",
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=master;encrypt=false;trustServerCertificate=true",
            "sa",
            "Datay@12345"
        )
    );

    @Test
    void shouldApplyScopeFilterSortAndPaginationOnEachDatabase() throws Exception {
        for (DatabaseCase database : DATABASES) {
            Class.forName(database.driver());
            try (Connection connection = DriverManager.getConnection(database.url(), database.user(), database.password())) {
                String table = "dash_verify_orders";
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TABLE IF EXISTS " + table);
                    statement.execute("CREATE TABLE " + table + " (region VARCHAR(20), order_amount INT)");
                    statement.execute("INSERT INTO " + table + " (region, order_amount) VALUES ('华东', 100), ('华东', 200), ('华南', 300)");
                }
                try {
                    String firstPage = buildSql(database.type(), table, 1, 0);
                    String secondPage = buildSql(database.type(), table, 1, 1);

                    assertThat(amounts(connection, firstPage))
                        .as(database.name() + " first page")
                        .containsExactly(200);
                    assertThat(amounts(connection, secondPage))
                        .as(database.name() + " second page")
                        .containsExactly(100);
                } finally {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("DROP TABLE IF EXISTS " + table);
                    }
                }
            }
        }
    }

    private String buildSql(String databaseType, String table, int limit, int offset) {
        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setType(databaseType);
        String baseSql = "SELECT region, order_amount FROM " + table;
        Set<String> fields = Set.of("region", "order_amount");
        String scopedSql = DatasetScopeSqlBuilder.wrap(baseSql, List.of(condition("region", "EQ", "华东")), fields);
        DatasetScopeCondition amountCondition = condition("order_amount", "BETWEEN", "100");
        amountCondition.setValueEnd("200");
        String filteredSql = DatasetFilterSqlBuilder.wrap(scopedSql, List.of(amountCondition), fields);
        return DatasetSortLimitSqlBuilder.apply(
            dataSource,
            filteredSql,
            "order_amount",
            "desc",
            limit,
            offset,
            fields
        );
    }

    private List<Integer> amounts(Connection connection, String sql) throws Exception {
        List<Integer> result = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.add(rows.getInt("order_amount"));
            }
        }
        return result;
    }

    private DatasetScopeCondition condition(String field, String operator, String value) {
        DatasetScopeCondition condition = new DatasetScopeCondition();
        condition.setFieldName(field);
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }

    private record DatabaseCase(String name, String type, String url, String user, String password) {
        String driver() {
            return switch (type) {
                case "MYSQL" -> "com.mysql.cj.jdbc.Driver";
                case "POSTGRESQL" -> "org.postgresql.Driver";
                case "SQLSERVER" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                default -> throw new IllegalArgumentException("Unsupported database type: " + type);
            };
        }
    }
}
