package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.job.DatasourceInfo;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TypeConverter;
import com.data.metadata.util.ConnectionPoolManager;
import com.data.metadata.util.DriverDownloader;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
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
