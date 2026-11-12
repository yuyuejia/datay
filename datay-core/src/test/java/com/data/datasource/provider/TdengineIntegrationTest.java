package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DriverDownloader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * TDengine 端到端集成测试，默认跳过。
 *
 * <p>启动方式见 {@code datay-core/src/test/docker/tdengine/README.md}，通过环境变量
 * {@code TDENGINE_IT_URL} 开启，例如：
 * <pre>
 * TDENGINE_IT_URL=jdbc:TAOS-WS://127.0.0.1:6041/ mvn -pl datay-core test -Dtest=TdengineIntegrationTest
 * </pre>
 */
class TdengineIntegrationTest {

    private static final String DATABASE = "datay_it";

    @Test
    void connectsBrowsesMetadataAndWritesData() throws Exception {
        String url = System.getenv("TDENGINE_IT_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "未设置 TDENGINE_IT_URL，跳过 TDengine 集成测试");

        try {
            DriverDownloader.downloadDriver(DBType.TDENGINE, "default");
        } catch (Exception e) {
            Assumptions.abort("TDengine JDBC 驱动不可用：" + e.getMessage());
        }
        Assumptions.assumeTrue(
            DriverDownloader.isDriverInstalled(DBType.TDENGINE, "default"),
            "TDengine JDBC 驱动未安装"
        );

        DatasourceInfo info = new DatasourceInfo();
        info.setType(DBType.TDENGINE.name());
        info.setUrl(url);
        info.setUsername(envOrDefault("TDENGINE_IT_USER", "root"));
        info.setPassword(envOrDefault("TDENGINE_IT_PASSWORD", "taosdata"));

        TdengineDataSourceProvider provider = new TdengineDataSourceProvider();
        Connection connection;
        try {
            connection = provider.openConnection(info);
        } catch (Exception e) {
            Assumptions.abort("无法连接 TDengine（" + url + "）：" + e.getMessage());
            return;
        }

        try (connection) {
            prepareSchema(connection);
            try {
                verifyMetadata(provider, connection);
                verifyNormalTableWrite(connection);
                verifyStableWriteIsRejected(provider, info, connection);
            } finally {
                cleanup(connection);
            }
        }
    }

    private void prepareSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + DATABASE);
        }
    }

    private void verifyMetadata(TdengineDataSourceProvider provider, Connection connection) throws SQLException {
        List<String> schemas = provider.getSchemas(connection);
        assertTrue(schemas.contains(DATABASE), "元数据应包含数据库 " + DATABASE + "，实际：" + schemas);

        // 先建一张普通表，保证表清单非空
        TableMeta table = normalTable();
        try (Statement statement = connection.createStatement()) {
            statement.execute(DatabaseConverter.generateTableDDL(DBType.TDENGINE.name(), table));
        }

        List<TableMeta> tables = provider.getTables(connection, DATABASE, 200, null);
        List<String> names = new ArrayList<>();
        for (TableMeta item : tables) {
            names.add(item.getTable());
        }
        assertTrue(names.contains(table.getTable()), "表清单应包含 " + table.getTable() + "，实际：" + names);

        TableMeta metadata = provider.getTableMeta(connection, DATABASE, table.getTable());
        assertEquals(DATABASE, metadata.getSchema());
        assertTrue(
            metadata.columns().stream().anyMatch(column -> "ts".equals(column.getName())),
            "表元数据应包含时间列 ts"
        );
    }

    private void verifyNormalTableWrite(Connection connection) throws SQLException {
        TableMeta table = normalTable();
        String insertSql = DatabaseConverter.generateWriteSQL(DBType.TDENGINE.name(), table, "append");
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            DatabaseConverter.bindParameter(
                DBType.TDENGINE.name(),
                statement,
                1,
                table.columns().get(0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0)
            );
            DatabaseConverter.bindParameter(DBType.TDENGINE.name(), statement, 2, table.columns().get(1), 23.5d);
            DatabaseConverter.bindParameter(DBType.TDENGINE.name(), statement, 3, table.columns().get(2), "beijing");
            // TDengine 不同版本 executeUpdate 返回值可能为 0，写入结果以最终 COUNT 为准
            statement.executeUpdate();
        }

        try (
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + DATABASE + "." + table.getTable())
        ) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    private void verifyStableWriteIsRejected(
        TdengineDataSourceProvider provider,
        DatasourceInfo info,
        Connection connection
    ) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "CREATE STABLE IF NOT EXISTS " +
                DATABASE +
                ".datay_meters_stable (ts TIMESTAMP, temperature DOUBLE) TAGS (location NCHAR(64))"
            );
            statement.execute(
                "CREATE TABLE IF NOT EXISTS " +
                DATABASE +
                ".datay_meters_001 USING " +
                DATABASE +
                ".datay_meters_stable TAGS ('beijing')"
            );
        }

        TableMeta stable = new TableMeta("datay_meters_stable");
        stable.setSchema(DATABASE);
        assertThrows(
            SQLException.class,
            () -> provider.validateWriteTarget(info, connection, stable, "append"),
            "向超级表普通写入应被拒绝"
        );
    }

    private TableMeta normalTable() {
        TableMeta table = new TableMeta("datay_meters", new ArrayList<>());
        table.setSchema(DATABASE);
        ColumnMeta ts = new ColumnMeta("ts", "TIMESTAMP");
        ColumnMeta temperature = new ColumnMeta("temperature", "DOUBLE");
        ColumnMeta location = new ColumnMeta("location", "NCHAR");
        location.setLength(64);
        table.columns().add(ts);
        table.columns().add(temperature);
        table.columns().add(location);
        return table;
    }

    private void cleanup(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
        } catch (SQLException ignored) {
            // 忽略清理失败
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
