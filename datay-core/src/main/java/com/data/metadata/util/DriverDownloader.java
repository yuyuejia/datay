package com.data.metadata.util;

import com.data.metadata.DBType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 通用 JDBC 驱动下载器。
 *
 * <p>平台只内置了部分数据库驱动，其余（如达梦）需要按需从 Maven 仓库下载到本地驱动目录
 * {@code drivers/<dbType>/<version>/}。下载目标目录与 {@link ConnectionPoolManager} 的加载目录保持一致。
 *
 * <p>Maven 仓库可通过环境变量 {@code DATAY_DRIVER_REPOS} 配置（逗号或分号分隔，按顺序回退），
 * 默认使用 Maven Central，失败后回退阿里云公共仓库。
 */
public final class DriverDownloader {

    /** 驱动包根目录（相对于进程工作目录）。 */
    public static final String DRIVER_BASE_DIR = "drivers";

    /** Maven 仓库列表环境变量。 */
    public static final String REPO_ENV = "DATAY_DRIVER_REPOS";

    private static final List<String> DEFAULT_REPOS = Arrays.asList(
        "https://repo1.maven.org/maven2",
        "https://maven.aliyun.com/repository/public"
    );

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 60000;

    /** 允许通过代码覆盖仓库列表（主要用于测试或外部配置注入）。 */
    private static volatile List<String> repoUrls;

    private DriverDownloader() {}

    /**
     * 获取当前生效的 Maven 仓库地址列表。
     */
    public static List<String> getRepoUrls() {
        if (repoUrls != null && !repoUrls.isEmpty()) {
            return repoUrls;
        }
        String env = System.getenv(REPO_ENV);
        if (env == null || env.isBlank()) {
            env = System.getProperty(REPO_ENV.toLowerCase(Locale.ROOT).replace('_', '.'));
        }
        if (env == null || env.isBlank()) {
            return DEFAULT_REPOS;
        }
        List<String> result = new ArrayList<>();
        for (String part : env.split("[,;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimTrailingSlash(trimmed));
            }
        }
        return result.isEmpty() ? DEFAULT_REPOS : result;
    }

    public static void setRepoUrls(List<String> urls) {
        repoUrls = urls;
    }

    /**
     * 驱动基础目录，例如 {@code drivers/dm}。
     */
    public static String getDriverBaseDir(DBType dbType) {
        if (dbType == null) {
            return DRIVER_BASE_DIR;
        }
        return DRIVER_BASE_DIR + "/" + dbType.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 驱动目录，统一为 {@code drivers/<type>/default}。
     *
     * <p>连接池始终按 {@code default} 目录加载驱动，因此手动下载与自动下载都落到同一目录，
     * 避免因版本目录不同导致「下载了却加载不到」。{@code version} 仅决定下载哪个版本的 jar，
     * 不再影响存放目录。
     */
    public static File getDriverDir(DBType dbType, String version) {
        return new File(getDriverBaseDir(dbType), "default");
    }

    /**
     * 判断指定版本驱动是否已下载到本地驱动目录。
     */
    public static boolean isDriverInstalled(DBType dbType, String version) {
        if (dbType == null || !dbType.isDownloadable()) {
            return false;
        }
        File dir = getDriverDir(dbType, version);
        File[] jars = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        return jars != null && jars.length > 0;
    }

    /**
     * 确保指定驱动已下载到本地驱动目录；已存在则直接返回。
     *
     * @return 下载后的驱动 jar 文件
     * @throws IOException 当类型不支持下载或所有仓库均下载失败时
     */
    public static File downloadDriver(DBType dbType, String version) throws IOException {
        if (dbType == null) {
            throw new IOException("数据库类型不能为空");
        }
        if (!dbType.isDownloadable()) {
            throw new IOException("该数据库类型未配置驱动下载源：" + dbType.name());
        }

        String relativePath = dbType.getMavenDriverRelativePath(version);
        String jarName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        File dir = getDriverDir(dbType, version);
        File target = new File(dir, jarName);

        if (target.exists() && target.length() > 0) {
            return target;
        }

        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("无法创建驱动目录：" + dir.getAbsolutePath());
        }

        List<String> repos = getRepoUrls();
        List<String> failures = new ArrayList<>();
        for (String repo : repos) {
            String url = trimTrailingSlash(repo) + "/" + relativePath;
            File tmp = new File(dir, jarName + ".part");
            try {
                downloadToFile(url, tmp);
                validateJar(tmp, url);
                moveIntoPlace(tmp, target);
                System.out.println("Downloaded driver JAR from " + url + " to " + target.getAbsolutePath());
                return target;
            } catch (Exception e) {
                deleteQuietly(tmp);
                failures.add(repo + " -> " + e.getMessage());
                System.err.println("Failed to download driver from " + url + ": " + e.getMessage());
            }
        }

        throw new IOException("驱动下载失败（" + dbType.name() + " " + dbType.resolveVersion(version) + "），已尝试仓库：" + failures);
    }

    private static void downloadToFile(String url, File dest) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("User-Agent", "DataY-DriverDownloader");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(dest.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 校验下载内容确实是 jar（zip 以 {@code PK} 魔数开头），避免把 HTML 错误页当成驱动。
     */
    private static void validateJar(File file, String url) throws IOException {
        if (!file.exists() || file.length() == 0) {
            throw new IOException("下载内容为空");
        }
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] magic = new byte[2];
            int read = in.read(magic);
            if (read < 2 || magic[0] != 'P' || magic[1] != 'K') {
                throw new IOException("下载内容不是有效的 JAR 文件：" + url);
            }
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

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
