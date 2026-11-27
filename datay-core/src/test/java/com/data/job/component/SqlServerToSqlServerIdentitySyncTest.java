package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * SQL Server → SQL Server 同步验证：源表主键为 IDENTITY 列，目标表自动创建。
 *
 * <p>采集阶段应去掉驱动的 {@code identity} 类型标记，因此自动建表不生成标识列，
 * 后续写入源端显式主键值时不会再报“不能为标识列插入显式值”。</p>
 */
public class SqlServerToSqlServerIdentitySyncTest {

    private static final Db SQLSERVER = new Db(
        "sqlserver",
        "jdbc:sqlserver://127.0.0.1:1433;databaseName=datay_verify;encrypt=false;trustServerCertificate=true",
        "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "sa",
        "Datay@12345",
        "dbo"
    );

    private static final String SOURCE = "s2s_identity_src";
    private static final String TARGET = "s2s_identity_auto_tgt";

    private static final Path LOCAL_DRIVER_DIR = Path.of("drivers", "sqlserver", "default");
    private static boolean localDriverDirCreated;

    @BeforeAll
    public static void ensureSqlServerDriver() throws Exception {
        if (hasJar(LOCAL_DRIVER_DIR)) {
            return;
        }
        Path repoDriverDir = Path.of("..", "drivers", "sqlserver", "default").toAbsolutePath().normalize();
        if (!hasJar(repoDriverDir)) {
            return;
        }
        Files.createDirectories(LOCAL_DRIVER_DIR);
        localDriverDirCreated = true;
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(repoDriverDir, "*.jar")) {
            for (Path jar : jars) {
                Files.copy(jar, LOCAL_DRIVER_DIR.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @AfterAll
    public static void cleanSqlServerDriver() throws Exception {
        if (!localDriverDirCreated) {
            return;
        }
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(LOCAL_DRIVER_DIR, "*.jar")) {
            for (Path jar : jars) {
                Files.deleteIfExists(jar);
            }
        }
        Files.deleteIfExists(LOCAL_DRIVER_DIR);
    }

    private static boolean hasJar(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(dir, "*.jar")) {
            return jars.iterator().hasNext();
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    public void checkDatabase() {
        assumeTrue(canConnect(SQLSERVER), "本地 SQL Server 不可用，跳过 SQL Server -> SQL Server 验证");
    }

    @AfterEach
    public void tearDown() {
        dropQuietly("dbo." + SOURCE);
        dropQuietly("dbo." + TARGET);
        DBUtils.closePool(SQLSERVER.url(), SQLSERVER.user());
    }

    @Test
    @Timeout(240000)
    public void autoCreateTargetWithoutIdentityAndCopyExplicitKeys() throws Exception {
        dropQuietly("dbo." + SOURCE);
        dropQuietly("dbo." + TARGET);
        execute("CREATE TABLE dbo." + SOURCE
            + " ([Id] BIGINT IDENTITY(1,1) PRIMARY KEY, [Name] NVARCHAR(50), [Amount] DECIMAL(10,2))");
        // 普通自增值
        execute("INSERT INTO dbo." + SOURCE + " ([Name], [Amount]) VALUES ('alice', 12.34), ('bob', 56.78)");
        // 显式指定主键值，验证同步后目标端保持源端原值（IDENTITY_INSERT 是会话级设置）
        try (Connection conn = connect(); Statement statement = conn.createStatement()) {
            statement.execute("SET IDENTITY_INSERT dbo." + SOURCE + " ON");
            statement.execute("INSERT INTO dbo." + SOURCE + " ([Id], [Name], [Amount]) VALUES (100, 'carol', 100.00)");
            statement.execute("SET IDENTITY_INSERT dbo." + SOURCE + " OFF");
        }

        // 采集阶段应把 BIGINT IDENTITY 清理为 BIGINT
        try (Connection conn = connect()) {
            TableMeta meta = DBUtils.getTableMetaData(datasourceInfo(), conn, "dbo", SOURCE);
            ColumnMeta id = findColumn(meta, "Id");
            assertNotNull(id);
            assertEquals("BIGINT", id.getType().toUpperCase());
        }

        new ETLFlowTask().runJob(job());

        try (Connection conn = connect(); Statement statement = conn.createStatement()) {
            // 自动建表的目标表不应包含标识列
            try (ResultSet rs = statement.executeQuery(
                "SELECT COUNT(*) FROM sys.identity_columns WHERE object_id = OBJECT_ID('dbo." + TARGET + "')"
            )) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "自动建表不应生成 IDENTITY 列");
            }
            try (ResultSet rs = statement.executeQuery(
                "SELECT COUNT(*) FROM sys.indexes i "
                    + "JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id "
                    + "JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id "
                    + "WHERE i.object_id = OBJECT_ID('dbo." + TARGET + "') AND i.is_primary_key = 1 AND c.name = 'Id'"
            )) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "目标表主键应包含 Id");
            }
            try (ResultSet rs = statement.executeQuery(
                "SELECT [Id], [Name], [Amount] FROM dbo." + TARGET + " ORDER BY [Id]"
            )) {
                Map<Long, String> rows = new LinkedHashMap<>();
                while (rs.next()) {
                    rows.put(rs.getLong(1), rs.getString(2) + "|" + rs.getBigDecimal(3).toPlainString());
                }
                assertEquals(3, rows.size());
                assertEquals("alice|12.34", rows.get(1L));
                assertEquals("bob|56.78", rows.get(2L));
                assertEquals("carol|100.00", rows.get(100L));
            }
        }
    }

    private ColumnMeta findColumn(TableMeta table, String name) {
        for (ColumnMeta column : table.columns()) {
            if (column.getName().equalsIgnoreCase(name)) {
                return column;
            }
        }
        return null;
    }

    private String job() {
        return """
            {
              "units": [
                {
                  ".id": "StreamJdbcInput",
                  ".name": "StreamJdbcInput",
                  "sourceId": {
                    "type": "%1$s",
                    "url": "%2$s",
                    "driver": "%3$s",
                    "username": "%4$s",
                    "password": "%5$s",
                    "dbschema": "%6$s"
                  },
                  "schema": "%6$s",
                  "table": "%7$s"
                },
                {
                  ".id": "StreamJdbcOutput",
                  ".name": "StreamJdbcOutput",
                  "sourceId": {
                    "type": "%1$s",
                    "url": "%2$s",
                    "driver": "%3$s",
                    "username": "%4$s",
                    "password": "%5$s",
                    "dbschema": "%6$s"
                  },
                  "schema": "%6$s",
                  "table": "%8$s",
                  "model": "overwrite"
                }
              ],
              "connections": [
                { "sourceId": "StreamJdbcInput", "targetId": "StreamJdbcOutput", "sourcePort": 0 }
              ],
              "version": "1.0.0"
            }
            """.formatted(
            SQLSERVER.type(),
            SQLSERVER.url(),
            SQLSERVER.driver(),
            SQLSERVER.user(),
            SQLSERVER.password(),
            SQLSERVER.schema(),
            SOURCE,
            TARGET
        );
    }

    private DatasourceInfo datasourceInfo() {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(SQLSERVER.type());
        info.setUrl(SQLSERVER.url());
        info.setDriver(SQLSERVER.driver());
        info.setUsername(SQLSERVER.user());
        info.setPassword(SQLSERVER.password());
        info.setDbschema(SQLSERVER.schema());
        return info;
    }

    private boolean canConnect(Db db) {
        try (Connection ignored = connect()) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Connection connect() throws SQLException {
        return DBUtils.getConnection(datasourceInfo());
    }

    private void execute(String sql) throws SQLException {
        try (Connection conn = connect(); Statement statement = conn.createStatement()) {
            statement.execute(sql);
        }
    }

    private void dropQuietly(String qualifiedTable) {
        try {
            execute("DROP TABLE " + qualifiedTable);
        } catch (Exception ignored) {
            // 表可能不存在
        }
    }

    private record Db(String type, String url, String driver, String user, String password, String schema) {}
}
