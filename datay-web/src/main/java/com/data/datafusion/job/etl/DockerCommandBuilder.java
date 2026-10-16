package com.data.datafusion.job.etl;

import cn.hutool.core.util.StrUtil;
import com.data.datafusion.config.EtlExecutionProperties;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Docker 执行命令拼装器。
 *
 * <p>把 {@link EtlExecutionProperties.Docker} 配置转换成 {@code docker run} 命令，供
 * {@link DockerEtlTask} 执行。抽离为纯函数便于单元测试。</p>
 *
 * <p>支持三种运行时来源（见 {@link DockerRuntime}）：独立的 core 可执行 jar、worker fat jar
 * 内嵌 core、以及开发态展开目录 classpath。</p>
 *
 * <p>关键约定：</p>
 * <ul>
 *     <li>容器工作目录为 {@code containerWorkDir}（默认 {@code /app}），宿主机日志目录挂载到其
 *         {@code log} 子目录；</li>
 *     <li>宿主驱动目录挂载到 {@code containerDriverDir}（默认 {@code /app/drivers}），
 *         使容器内相对路径 {@code drivers/...} 命中；</li>
 *     <li>附加 lib 目录挂载到 {@code <workDir>/lib/<name>} 并加入 classpath；</li>
 *     <li>环境变量以 {@code -e KEY} 形式传递（值由 docker 进程自身环境提供），避免密钥出现在
 *         宿主机进程参数中。</li>
 * </ul>
 */
public final class DockerCommandBuilder {

    static final String JAR_CONTAINER_NAME = "datay-core.jar";

    /** worker fat jar 在容器内的挂载名（内嵌 core 运行时模式）。 */
    static final String WORKER_JAR_CONTAINER_NAME = "datay-web.jar";

    static final String MAIN_CLASS = "com.data.DataY";

    /** Spring Boot 3.2+ 的启动器，用于以 fat jar 内嵌类路径执行指定主类。 */
    static final String SPRING_BOOT_LAUNCHER = "org.springframework.boot.loader.launch.PropertiesLauncher";

    private DockerCommandBuilder() {}

    /**
     * 运行时来源描述：挂载项 + 容器内 classpath。
     *
     * <p>三种构造方式对应三种来源：</p>
     * <ul>
     *     <li>{@link #coreJarRuntime}：独立 core 可执行 jar，直接用 {@code -cp} 运行；</li>
     *     <li>{@link #workerJarRuntime}：worker fat jar 内嵌 core，用 Spring Boot 启动器运行；</li>
     *     <li>{@link #explodedRuntime}：开发态展开目录 + 依赖 jar，按原始绝对路径挂载，用 {@code -cp} 运行。</li>
     * </ul>
     */
    public static final class DockerRuntime {

        /** 每个挂载项为 {hostPath, containerPath, mode}，mode 可为 null。 */
        private final List<String[]> mounts = new ArrayList<>();

        /** 容器内 classpath 条目（非内嵌模式使用）。 */
        private final List<String> classpath = new ArrayList<>();

        /** 是否用 Spring Boot {@code PropertiesLauncher} 运行内嵌类路径。 */
        private boolean embedded;

        /** 内嵌模式下作为 {@code -cp} 的 worker jar 容器内路径。 */
        private String embeddedJar;

        void mount(String hostPath, String containerPath, String mode) {
            mounts.add(new String[] { hostPath, containerPath, mode });
        }

        public List<String[]> getMounts() {
            return mounts;
        }

        public List<String> getClasspath() {
            return classpath;
        }

        public boolean isEmbedded() {
            return embedded;
        }

        public String getEmbeddedJar() {
            return embeddedJar;
        }
    }

    /**
     * 使用独立的 datay-core 可执行 jar（jar-with-dependencies）。
     *
     * @param cfg         docker 配置
     * @param hostJarPath 宿主机上的 core jar 绝对路径
     */
    public static DockerRuntime coreJarRuntime(EtlExecutionProperties.Docker cfg, String hostJarPath) {
        String workDir = trimTrailingSlash(cfg.getContainerWorkDir());
        String containerJar = workDir + "/" + JAR_CONTAINER_NAME;
        DockerRuntime runtime = new DockerRuntime();
        runtime.mount(hostJarPath, containerJar, "ro");
        runtime.classpath.add(containerJar);
        return runtime;
    }

    /**
     * 使用 worker fat jar 内嵌的 core 运行时。
     */
    public static DockerRuntime workerJarRuntime(EtlExecutionProperties.Docker cfg, String workerJarPath) {
        String workDir = trimTrailingSlash(cfg.getContainerWorkDir());
        String containerJar = workDir + "/" + WORKER_JAR_CONTAINER_NAME;
        DockerRuntime runtime = new DockerRuntime();
        runtime.mount(workerJarPath, containerJar, "ro");
        runtime.embedded = true;
        runtime.embeddedJar = containerJar;
        return runtime;
    }

    /**
     * 开发态运行时：直接使用当前进程的 classpath（展开目录 + 依赖 jar），逐个按原始绝对路径只读挂载，
     * 使容器内的 {@code -cp} 与宿主机一致，无需预先构建可执行 jar。
     *
     * <p>Maven 本地仓库下的 jar 统一挂载仓库根目录，避免产生大量挂载项。</p>
     *
     * @param hostClasspathEntries 宿主机 classpath 条目（绝对路径）
     * @return 运行时；无有效条目时返回 {@code null}
     */
    public static DockerRuntime explodedRuntime(List<String> hostClasspathEntries) {
        DockerRuntime runtime = new DockerRuntime();
        Path mavenRepo = Path.of(System.getProperty("user.home"), ".m2", "repository").toAbsolutePath().normalize();
        boolean mavenRepoMounted = false;
        Set<String> mounted = new LinkedHashSet<>();
        for (String entry : hostClasspathEntries) {
            if (StrUtil.isBlank(entry) || entry.trim().endsWith("*")) {
                continue;
            }
            Path path;
            try {
                path = Path.of(entry.trim()).toAbsolutePath().normalize();
            } catch (Exception e) {
                continue;
            }
            if (!Files.exists(path)) {
                continue;
            }
            String absolute = path.toString();
            if (path.startsWith(mavenRepo)) {
                if (!mavenRepoMounted) {
                    runtime.mount(mavenRepo.toString(), mavenRepo.toString(), "ro");
                    mavenRepoMounted = true;
                }
            } else if (mounted.add(absolute)) {
                runtime.mount(absolute, absolute, "ro");
            }
            runtime.classpath.add(absolute);
        }
        return runtime.classpath.isEmpty() ? null : runtime;
    }

    /**
     * 解析宿主机日志目录，未配置时默认 {@code <工作目录>/log}。
     */
    public static String resolveHostLogDir(String configured) {
        if (StrUtil.isNotBlank(configured)) {
            return new File(configured.trim()).getAbsolutePath();
        }
        return new File(System.getProperty("user.dir"), "log").getAbsolutePath();
    }

    /**
     * 解析宿主机驱动目录，未配置时默认 {@code <工作目录>/drivers}。
     */
    public static String resolveDriverDir(String configured) {
        if (StrUtil.isNotBlank(configured)) {
            return new File(configured.trim()).getAbsolutePath();
        }
        return new File(System.getProperty("user.dir"), "drivers").getAbsolutePath();
    }

    /**
     * 解析宿主机数据目录，未配置时默认 {@code <工作目录>/data}。
     */
    public static String resolveDataDir(String configured) {
        if (StrUtil.isNotBlank(configured)) {
            return new File(configured.trim()).getAbsolutePath();
        }
        return new File(System.getProperty("user.dir"), "data").getAbsolutePath();
    }

    /**
     * 拼装 {@code docker run} 命令。
     *
     * @param cfg            docker 配置
     * @param containerName  容器名
     * @param hostLogDir     宿主机日志目录（绝对路径）
     * @param containerJobFile 容器内任务 JSON 文件路径
     * @param envKeys        需要透传给容器的环境变量名（值由 docker 进程环境提供）
     * @param runtime        运行时来源
     * @return 命令列表
     */
    public static List<String> build(
        EtlExecutionProperties.Docker cfg,
        String containerName,
        String hostLogDir,
        String containerJobFile,
        List<String> envKeys,
        DockerRuntime runtime
    ) {
        String workDir = trimTrailingSlash(cfg.getContainerWorkDir());
        String containerLogDir = workDir + "/log";

        List<String> cmd = new ArrayList<>();
        cmd.add(cfg.getBinary());
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("-w");
        cmd.add(workDir);

        // 日志目录（rw，供容器写日志与增量状态）
        addMount(cmd, hostLogDir, containerLogDir, null);
        // 运行时挂载项（只读）
        for (String[] mount : runtime.getMounts()) {
            addMount(cmd, mount[0], mount[1], mount[2]);
        }
        // 驱动目录（rw，支持容器内按需下载驱动）
        String driverDir = resolveDriverDir(cfg.getDriverDir());
        if (new File(driverDir).isDirectory()) {
            addMount(cmd, driverDir, cfg.getContainerDriverDir(), null);
        }
        // 数据目录（rw，供容器读写相对路径数据文件，如 ./data/xxx.duckdb、./data/files）
        String dataDir = resolveDataDir(cfg.getDataDir());
        if (new File(dataDir).isDirectory()) {
            addMount(cmd, dataDir, workDir + "/data", null);
        }
        // 附加 lib 目录（只读，加入 classpath）
        List<String> validLibDirs = new ArrayList<>();
        for (String libDir : cfg.getLibDirs()) {
            if (StrUtil.isBlank(libDir)) {
                continue;
            }
            File dir = new File(libDir.trim());
            if (!dir.isDirectory()) {
                continue;
            }
            String containerLib = workDir + "/lib/" + dir.getName();
            addMount(cmd, dir.getAbsolutePath(), containerLib, "ro");
            validLibDirs.add(containerLib);
        }
        // 附加挂载
        for (String mount : cfg.getExtraMounts()) {
            if (StrUtil.isNotBlank(mount)) {
                cmd.add("-v");
                cmd.add(mount.trim());
            }
        }

        if (StrUtil.isNotBlank(cfg.getNetwork())) {
            cmd.add("--network");
            cmd.add(cfg.getNetwork().trim());
        }
        // 宿主机访问：注入 host-gateway 映射，使容器内能解析宿主机别名（Linux 需要，Docker Desktop 兼容）
        if (cfg.isHostAccess() && cfg.isAddHostGateway() && StrUtil.isNotBlank(cfg.getHostAccessAlias())) {
            cmd.add("--add-host");
            cmd.add(cfg.getHostAccessAlias().trim() + ":host-gateway");
        }
        if (StrUtil.isNotBlank(cfg.getMemory())) {
            cmd.add("-m");
            cmd.add(cfg.getMemory().trim());
        }
        if (StrUtil.isNotBlank(cfg.getCpus())) {
            cmd.add("--cpus");
            cmd.add(cfg.getCpus().trim());
        }

        for (String key : envKeys) {
            if (StrUtil.isNotBlank(key)) {
                cmd.add("-e");
                cmd.add(key);
            }
        }

        cmd.add(cfg.getImage());

        cmd.add("java");
        for (String arg : splitArgs(cfg.getJavaArgs())) {
            cmd.add(arg);
        }
        if (runtime.isEmbedded()) {
            // 通过 Spring Boot 启动器加载 worker fat jar 内嵌的 BOOT-INF/lib + classes
            String loaderPath = joinLibClasspathEntries(validLibDirs);
            if (!loaderPath.isEmpty()) {
                cmd.add("-Dloader.path=" + loaderPath);
            }
            cmd.add("-cp");
            cmd.add(runtime.getEmbeddedJar());
            cmd.add("-Dloader.main=" + MAIN_CLASS);
            cmd.add(SPRING_BOOT_LAUNCHER);
        } else {
            // java -cp <classpath>[;<lib>/*] com.data.DataY <jobFile>
            cmd.add("-cp");
            cmd.add(buildClasspath(runtime.getClasspath(), validLibDirs));
            cmd.add(MAIN_CLASS);
        }
        cmd.add(containerJobFile);
        return cmd;
    }

    /**
     * classpath 由运行时条目与所有 lib 目录下的 jar 组成。
     */
    static String buildClasspath(List<String> runtimeEntries, List<String> containerLibDirs) {
        StringBuilder sb = new StringBuilder();
        for (String entry : runtimeEntries) {
            if (sb.length() > 0) {
                sb.append(File.pathSeparator);
            }
            sb.append(entry);
        }
        for (String libDir : containerLibDirs) {
            if (sb.length() > 0) {
                sb.append(File.pathSeparator);
            }
            sb.append(libDir).append("/*");
        }
        return sb.toString();
    }

    /**
     * lib 目录列表拼成 Spring Boot {@code loader.path}（逗号分隔）。
     */
    static String joinLibClasspathEntries(List<String> containerLibDirs) {
        return String.join(",", containerLibDirs);
    }

    private static void addMount(List<String> cmd, String hostPath, String containerPath, String mode) {
        String mount = hostPath + ":" + containerPath + (mode == null ? "" : ":" + mode);
        cmd.add("-v");
        cmd.add(mount);
    }

    private static String trimTrailingSlash(String path) {
        if (StrUtil.isBlank(path)) {
            return "/app";
        }
        String trimmed = path.trim();
        while (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static List<String> splitArgs(String raw) {
        List<String> args = new ArrayList<>();
        if (StrUtil.isBlank(raw)) {
            return args;
        }
        for (String part : raw.trim().split("\\s+")) {
            if (!part.isBlank()) {
                args.add(part);
            }
        }
        return args;
    }
}
