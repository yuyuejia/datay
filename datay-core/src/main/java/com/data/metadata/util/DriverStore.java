package com.data.metadata.util;

import com.data.metadata.DBType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JDBC 驱动本地存储。
 *
 * <p>平台不再自动下载驱动。未随平台预置的驱动（如达梦、SAP HANA、TDengine、SQL Server 等）
 * 由用户在数据源页面统一上传，保存到 {@code drivers/<type>/default/}。连接池始终按该目录加载
 * 驱动，因此上传后重新建立连接即可生效。
 *
 * <p>通用 JDBC 数据源的驱动按数据源隔离，存放在
 * {@code drivers/jdbc/<owner>/default/}，支持一次上传驱动及其依赖 jar。不同通用 JDBC 数据源
 * 之间不共享驱动。
 */
public final class DriverStore {

    /** 驱动包根目录（相对于进程工作目录）。 */
    public static final String DRIVER_BASE_DIR = "drivers";

    /** 通用 JDBC 数据源的驱动目录标识。 */
    public static final String GENERIC_JDBC_TYPE = "jdbc";

    private static final String DEFAULT_DIR = "default";

    private DriverStore() {}

    /**
     * 驱动基础目录，例如 {@code drivers/dm}。
     */
    public static File getDriverBaseDir(String type) {
        return new File(DRIVER_BASE_DIR, normalizeType(type));
    }

    /**
     * 驱动目录，统一为 {@code drivers/<type>/default}。
     */
    public static File getDriverDir(String type) {
        return new File(getDriverBaseDir(type), DEFAULT_DIR);
    }

    /**
     * 按归属（如数据源 ID 或随机标识）隔离的驱动目录：
     * {@code drivers/<type>/<owner>/default}。{@code owner} 为空时回退到
     * {@code drivers/<type>/default}。
     */
    public static File getDriverDir(String type, String owner) {
        String normalizedOwner = normalizeOwner(owner);
        if (normalizedOwner == null) {
            return getDriverDir(type);
        }
        return new File(new File(getDriverBaseDir(type), normalizedOwner), DEFAULT_DIR);
    }

    /**
     * 清洗驱动归属标识，只允许字母、数字、下划线和中划线。
     *
     * @return 清洗后的标识；空白输入返回 {@code null}
     */
    public static String normalizeOwner(String owner) {
        String value = owner == null ? "" : owner.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("非法的驱动归属标识：" + owner);
        }
        return value;
    }

    /**
     * 驱动目录，统一为 {@code drivers/<type>/default}。
     */
    public static File getDriverDir(DBType dbType) {
        return getDriverDir(dbType == null ? null : dbType.name());
    }

    /**
     * 列出指定类型已安装的驱动 jar，按文件名排序。
     */
    public static List<File> listJars(String type) {
        return listJars(getDriverDir(type));
    }

    /**
     * 列出指定类型、指定归属下已安装的驱动 jar，按文件名排序。
     */
    public static List<File> listJars(String type, String owner) {
        return listJars(getDriverDir(type, owner));
    }

    private static List<File> listJars(File dir) {
        File[] jars = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            return List.of();
        }
        List<File> result = new ArrayList<>(List.of(jars));
        result.sort(Comparator.comparing(File::getName));
        return result;
    }

    /**
     * 列出指定类型已安装的驱动 jar。
     */
    public static List<File> listJars(DBType dbType) {
        return dbType == null ? List.of() : listJars(dbType.name());
    }

    /**
     * 判断指定类型是否已上传驱动。
     */
    public static boolean isDriverInstalled(String type) {
        return !listJars(type).isEmpty();
    }

    /**
     * 判断指定类型、指定归属下是否已上传驱动。
     */
    public static boolean isDriverInstalled(String type, String owner) {
        return !listJars(type, owner).isEmpty();
    }

    /**
     * 判断指定类型是否已上传驱动。
     */
    public static boolean isDriverInstalled(DBType dbType) {
        return dbType != null && isDriverInstalled(dbType.name());
    }

    /**
     * 清洗上传的驱动文件名：只保留文件名本身，并且必须以 {@code .jar} 结尾。
     */
    public static String sanitizeJarName(String fileName) throws IOException {
        String value = fileName == null ? "" : fileName.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isEmpty()) {
            throw new IOException("驱动文件名不能为空");
        }
        if (!value.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("仅支持 .jar 驱动文件：" + value);
        }
        if (value.length() > 200) {
            throw new IOException("驱动文件名过长：" + value);
        }
        return value;
    }

    /**
     * 校验文件确实是 jar（zip 以 {@code PK} 魔数开头），避免把错误文件当成驱动。
     */
    public static void validateJar(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("驱动文件不存在");
        }
        if (file.length() == 0) {
            throw new IOException("驱动文件为空：" + file.getName());
        }
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] magic = new byte[2];
            int read = in.read(magic);
            if (read < 2 || magic[0] != 'P' || magic[1] != 'K') {
                throw new IOException("上传文件不是有效的 JAR：" + file.getName());
            }
        }
    }

    /**
     * 安装驱动 jar。
     *
     * @param type     数据源类型，如 {@code DM}、{@code TDENGINE}、{@code JDBC}
     * @param sources  已暂存到本地的待安装 jar
     * @param replace  {@code true} 表示替换该类型已有的全部 jar；否则只覆盖同名文件
     * @return 安装后的驱动 jar 列表
     */
    public static List<File> installDrivers(String type, List<Path> sources, boolean replace) throws IOException {
        return installDrivers(type, null, sources, replace);
    }

    /**
     * 安装驱动 jar 到指定归属目录。
     *
     * @param type     数据源类型，如 {@code DM}、{@code TDENGINE}、{@code JDBC}
     * @param owner    归属标识；为空时安装到 {@code drivers/<type>/default}
     * @param sources  已暂存到本地的待安装 jar
     * @param replace  {@code true} 表示替换该归属已有的全部 jar；否则只覆盖同名文件
     * @return 安装后的驱动 jar 列表
     */
    public static List<File> installDrivers(String type, String owner, List<Path> sources, boolean replace) throws IOException {
        if (sources == null || sources.isEmpty()) {
            throw new IOException("请选择要上传的驱动文件");
        }

        Map<String, Path> uploads = new LinkedHashMap<>();
        for (Path source : sources) {
            if (source == null || !Files.isRegularFile(source)) {
                throw new IOException("驱动文件不存在");
            }
            String name = sanitizeJarName(source.getFileName().toString());
            validateJar(source.toFile());
            if (uploads.putIfAbsent(name, source) != null) {
                throw new IOException("存在同名驱动文件：" + name);
            }
        }

        File dir = getDriverDir(type, owner);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("无法创建驱动目录：" + dir.getAbsolutePath());
        }

        Map<String, File> staged = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, Path> entry : uploads.entrySet()) {
                File part = new File(dir, entry.getKey() + ".part");
                Files.copy(entry.getValue(), part.toPath(), StandardCopyOption.REPLACE_EXISTING);
                staged.put(entry.getKey(), part);
            }
            if (replace) {
                deleteJarFiles(dir);
            }
            for (Map.Entry<String, File> entry : staged.entrySet()) {
                moveIntoPlace(entry.getValue(), new File(dir, entry.getKey()));
            }
            staged.clear();
            return listJars(type, owner);
        } finally {
            staged.values().forEach(DriverStore::deleteQuietly);
        }
    }

    /**
     * 删除指定类型已上传的全部驱动 jar。
     */
    public static void deleteDrivers(String type) {
        deleteJarFiles(getDriverDir(type));
    }

    /**
     * 删除指定类型、指定归属下已上传的全部驱动 jar。
     */
    public static void deleteDrivers(String type, String owner) {
        deleteJarFiles(getDriverDir(type, owner));
    }

    /**
     * 删除指定类型已上传的全部驱动 jar。
     */
    public static void deleteDrivers(DBType dbType) {
        if (dbType != null) {
            deleteDrivers(dbType.name());
        }
    }

    private static String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("非法的数据源类型：" + type);
        }
        return value;
    }

    private static void deleteJarFiles(File dir) {
        File[] jars = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) {
            return;
        }
        for (File jar : jars) {
            deleteQuietly(jar);
        }
    }

    private static void moveIntoPlace(File source, File target) throws IOException {
        Path sourcePath = source.toPath();
        Path targetPath = target.toPath();
        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
