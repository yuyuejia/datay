package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.job.DatasourceInfo;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.TypeConverter;
import com.data.metadata.util.ConnectionPoolManager;
import com.data.metadata.util.DriverDownloader;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 通用 JDBC 数据源 Provider（非内置具体类型）。
 *
 * <p>用于接入平台未内置的任意 JDBC 数据库（如 Hive、Phoenix、Trino、StarRocks 等）：
 * 用户提供 JDBC URL、驱动类名，并可指定驱动 Jar（本地路径或 Maven 坐标）。驱动 Jar 通过
 * 隔离类加载器加载，避免类冲突。
 *
 * <p>库表元数据同时兼容两类 JDBC 驱动：
 * <ul>
 *   <li><b>schema 型</b>（PostgreSQL/Hive 等）：{@code getSchemas()} 返回 schema，表以 {@code (null, schema)} 读取；</li>
 *   <li><b>catalog 型</b>（MySQL/MariaDB/StarRocks/OceanBase 等）：无 schema，逻辑库由 catalog 承载，
 *       表以 {@code (catalog, null)} 读取。</li>
 * </ul>
 *
 * <p>连接参数（{@code extraParams}）：
 * <ul>
 *   <li>{@code jdbc.driverClass}：JDBC 驱动类名（可选；缺省时从驱动 Jar 的
 *       {@code META-INF/services/java.sql.Driver} 解析）</li>
 *   <li>{@code jdbc.driverJar}：驱动 Jar 文件或目录路径（可选；不填则使用平台类路径）</li>
 *   <li>{@code jdbc.maven.groupId} / {@code jdbc.maven.artifactId} / {@code jdbc.maven.version}：
 *       从 Maven 仓库下载驱动 Jar（可选，作为 {@code jdbc.driverJar} 的补充）</li>
 *   <li>{@code jdbc.testQuery}：连通性测试语句（可选，默认 {@code SELECT 1}）</li>
 * </ul>
 */
@DataSourceRegister("jdbc")
public class GenericJdbcDataSourceProvider extends JdbcDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return new DataSourceTypeDescriptor().setType("JDBC").setDisplayName("通用 JDBC").setJdbc(true);
    }

    @Override
    public TypeConverter typeConverter(String type) {
        return DatabaseConverter.getCommonConverter();
    }

    // ------------------------------------------------------------------ 元数据（兼容 schema 型 / catalog 型驱动）

    @Override
    public List<String> getSchemas(Connection conn) throws SQLException {
        List<String> schemas = JdbcMetadata.readSchemas(conn);
        if (!schemas.isEmpty()) {
            return schemas;
        }
        // 部分数据库（MySQL/MariaDB/StarRocks/OceanBase 等）以 catalog 承载「数据库/schema」概念
        List<String> catalogs = JdbcMetadata.getCatalogs(conn);
        if (!catalogs.isEmpty()) {
            return catalogs;
        }
        try {
            String catalog = conn.getCatalog();
            if (catalog != null && !catalog.isBlank()) {
                return List.of(catalog);
            }
        } catch (SQLException ignored) {
            // ignore
        }
        return List.of();
    }

    @Override
    public List<TableMeta> getTables(Connection conn, String schema, Integer limit, String search) throws SQLException {
        String[] types = new String[] { "TABLE", "VIEW" };
        List<TableMeta> tables = isSchemaBased(conn)
            ? JdbcMetadata.readTableList(conn, null, schema, types, limit, search, false)
            : JdbcMetadata.readTableList(conn, schema, null, types, limit, search, false);
        if (tables.isEmpty() && schema != null && !schema.isBlank()) {
            // 兜底：换另一种组合再试一次
            List<TableMeta> fallback = isSchemaBased(conn)
                ? JdbcMetadata.readTableList(conn, schema, null, types, limit, search, false)
                : JdbcMetadata.readTableList(conn, null, schema, types, limit, search, false);
            tables = fallback;
        }
        return tables;
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = readTableMetaBothWays(conn, schema, table);
        tableMeta.setDbType(typeName());
        return tableMeta;
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String schema, String table) throws SQLException {
        boolean catalogBased = !isSchemaBased(conn);
        String catalog = catalogBased ? schema : null;
        String schemaName = catalogBased ? null : schema;
        TableMeta tableMeta = readTableMetaBothWays(conn, schema, table);
        tableMeta.setDbType(typeName());
        tableMeta.setComment(JdbcMetadata.getTableComment(conn, catalog, schemaName, table));
        tableMeta.setIndexes(JdbcMetadata.getTableIndexes(conn, catalog, schemaName, table));
        return tableMeta;
    }

    @Override
    public boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        if (tableExists(conn, schema, null, table)) {
            return true;
        }
        return tableExists(conn, null, schema, table);
    }

    /**
     * 优先按 catalog 型读取表结构，失败（表不存在）时回退 schema 型。
     */
    private static TableMeta readTableMetaBothWays(Connection conn, String schema, String table) throws SQLException {
        try {
            return JdbcMetadata.readTableMeta(conn, schema, null, table);
        } catch (SQLException e) {
            return JdbcMetadata.readTableMeta(conn, null, schema, table);
        }
    }

    /**
     * 判断驱动是否为 schema 型：标准 {@code getSchemas()} 有返回即为 schema 型，否则视为 catalog 型。
     */
    private static boolean isSchemaBased(Connection conn) {
        try {
            return !JdbcMetadata.readSchemas(conn).isEmpty();
        } catch (SQLException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 连接

    @Override
    public Connection openConnection(DatasourceInfo info) throws SQLException {
        Map<String, String> extra = info.getExtraParams() == null ? Map.of() : info.getExtraParams();

        String driverClass = firstNonBlank(extra.get("jdbc.driverClass"), info.getDriver());
        String driverJar = trimToNull(extra.get("jdbc.driverJar"));
        String testQuery = trimToNull(extra.get("jdbc.testQuery"));

        if (driverJar == null) {
            String groupId = trimToNull(extra.get("jdbc.maven.groupId"));
            String artifactId = trimToNull(extra.get("jdbc.maven.artifactId"));
            String version = trimToNull(extra.get("jdbc.maven.version"));
            if (groupId != null && artifactId != null && version != null) {
                try {
                    File jar = DriverDownloader.downloadJar(
                        groupId,
                        artifactId,
                        version,
                        new File(DriverDownloader.DRIVER_BASE_DIR, "jdbc")
                    );
                    driverJar = jar.getAbsolutePath();
                } catch (Exception e) {
                    System.err.println("通用 JDBC 驱动下载失败：" + e.getMessage());
                }
            }
        }

        if (driverClass == null && driverJar != null) {
            driverClass = resolveDriverClassFromJar(driverJar);
        }

        return ConnectionPoolManager.getConnection(info, driverClass, driverJar, testQuery);
    }

    /**
     * 从驱动 Jar 的 {@code META-INF/services/java.sql.Driver} 读取驱动类名。
     */
    private static String resolveDriverClassFromJar(String jarPath) {
        File jar = new File(jarPath);
        if (!jar.isFile()) {
            return null;
        }
        try (JarFile jarFile = new JarFile(jar)) {
            JarEntry entry = jarFile.getJarEntry("META-INF/services/java.sql.Driver");
            if (entry == null) {
                return null;
            }
            try (
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(jarFile.getInputStream(entry), StandardCharsets.UTF_8)
                )
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        return line;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("读取驱动 Jar 的 Driver 服务声明失败：" + e.getMessage());
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
