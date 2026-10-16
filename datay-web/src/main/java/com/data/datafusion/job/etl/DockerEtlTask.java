package com.data.datafusion.job.etl;

import cn.hutool.core.util.StrUtil;
import com.data.datafusion.config.EtlExecutionProperties;
import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import com.data.datafusion.service.StatusStorageConfigService;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在 docker 容器中执行 ETL 任务。
 *
 * <p>与 {@link ETLTask}（进程内执行）的相对应实现：把任务 JSON 写入挂载的日志目录，再以
 * {@code docker run} 启动容器执行 {@code com.data.DataY}。任务 jar、驱动/lib 目录通过挂载共享，
 * 容器直接写宿主日志目录，因此 {@code WorkerResource} 的日志读取逻辑无需改动。</p>
 *
 * <p>任务成败以容器退出码判定：0 为成功，非 0 抛异常由 {@link AbstractTask} 映射为 FAILED。</p>
 */
public class DockerEtlTask extends AbstractTask {

    private static final Logger LOG = LoggerFactory.getLogger(DockerEtlTask.class);

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile Process process;

    private volatile String containerName;

    private volatile String dockerBinary;

    public DockerEtlTask(JobInstance jobInstance) {
        super(jobInstance);
    }

    @Override
    public String doExecute() throws Exception {
        EtlExecutionProperties properties = getExecutionProperties();
        EtlExecutionProperties.Docker cfg = properties.getDocker();
        DockerCommandBuilder.DockerRuntime runtime = resolveRuntime(cfg);

        String jobCode = getJobCode();
        String instanceCode = getInstanceCode();
        String hostLogDir = DockerCommandBuilder.resolveHostLogDir(cfg.getHostLogDir());
        String workDir = StrUtil.isBlank(cfg.getContainerWorkDir()) ? "/app" : cfg.getContainerWorkDir().trim();

        // 任务 JSON 写入挂载目录，容器内通过相同挂载可见
        Path hostJobFile = Path.of(hostLogDir, jobCode, instanceCode + ".job.json");
        String containerJobFile = workDir + "/log/" + jobCode + "/" + instanceCode + ".job.json";
        Files.createDirectories(hostJobFile.getParent());
        // 宿主机访问：把回环地址改写为容器可访问的主机别名，使容器内 ETL 能连接本地数据源
        String jobContext = getJobInstance().getJobContext();
        if (cfg.isHostAccess()) {
            jobContext = JobContextHostRewriter.rewriteLoopback(jobContext, cfg.getHostAccessAlias());
        }
        Files.writeString(hostJobFile, jobContext, StandardCharsets.UTF_8);

        try {
            containerName = cfg.getContainerNamePrefix() + instanceCode;
            dockerBinary = StrUtil.isBlank(cfg.getBinary()) ? "docker" : cfg.getBinary();

            Map<String, String> env = buildContainerEnv(cfg, workDir);
            List<String> command = DockerCommandBuilder.build(
                cfg,
                containerName,
                hostLogDir,
                containerJobFile,
                new ArrayList<>(env.keySet()),
                runtime
            );

            if (cfg.isPullImage()) {
                pullImage(cfg.getImage());
            }

            LOG.info(
                "开始执行 ETL docker 容器任务: container={}, image={}, runtime={}",
                containerName,
                cfg.getImage(),
                runtime.isEmbedded() ? "embedded-worker-jar" : "core-jar"
            );
            int code = runDocker(command, env);
            if (cancelled.get()) {
                throw new InterruptedException("Task was cancelled manually");
            }
            if (code != 0) {
                throw new Exception("ETL docker 容器执行失败，退出码：" + code);
            }
            LOG.info("ETL docker 容器任务执行完成: container={}, exitCode={}", containerName, code);
            return String.valueOf(code);
        } finally {
            try {
                Files.deleteIfExists(hostJobFile);
            } catch (IOException e) {
                LOG.warn("删除任务 JSON 文件失败: {}", hostJobFile, e);
            }
        }
    }

    /**
     * 解析容器运行时来源，按以下优先级：
     * <ol>
     *     <li>配置了 {@code jar-path}：使用独立的 datay-core 可执行 jar；</li>
     *     <li>{@code worker-jar-path} 或自动探测到当前运行的 worker fat jar：使用其内嵌 core 运行时；</li>
     *     <li>在工程目录下自动探测已构建的 datay-core 可执行 jar（开发态）；</li>
     *     <li>开发态展开目录：直接挂载当前进程 classpath 运行（IDE 启动也能用，无需构建 jar）。</li>
     * </ol>
     */
    private DockerCommandBuilder.DockerRuntime resolveRuntime(EtlExecutionProperties.Docker cfg) {
        if (StrUtil.isNotBlank(cfg.getJarPath())) {
            File coreJar = new File(cfg.getJarPath().trim());
            if (!coreJar.isFile()) {
                throw new IllegalStateException("datay-core 可执行 jar 不存在: " + coreJar.getAbsolutePath());
            }
            return DockerCommandBuilder.coreJarRuntime(cfg, coreJar.getAbsolutePath());
        }

        Path workerJar = resolveWorkerJarPath(cfg);
        if (workerJar != null) {
            return DockerCommandBuilder.workerJarRuntime(cfg, workerJar.toString());
        }

        Path coreJar = detectCoreJarFromProject();
        if (coreJar != null) {
            LOG.info("未配置运行时来源，自动探测到 datay-core 可执行 jar: {}", coreJar);
            return DockerCommandBuilder.coreJarRuntime(cfg, coreJar.toString());
        }

        DockerCommandBuilder.DockerRuntime devRuntime = buildDevClasspathRuntime();
        if (devRuntime != null) {
            LOG.info("未找到可执行 jar，docker 模式使用开发态 classpath 运行时（{} 个条目）", devRuntime.getClasspath().size());
            return devRuntime;
        }

        throw new IllegalStateException(
            "docker 执行模式未找到可用的 ETL 运行时：请配置 development.etl.docker.jar-path（datay-core 可执行 jar）" +
            "或 development.etl.docker.worker-jar-path（worker fat jar）；或先执行 " +
            "`mvn -pl datay-core -am package -DskipTests` 生成 core jar"
        );
    }

    /**
     * 解析 worker fat jar 路径：优先配置项，其次自动探测当前运行的 jar。
     */
    private Path resolveWorkerJarPath(EtlExecutionProperties.Docker cfg) {
        if (StrUtil.isNotBlank(cfg.getWorkerJarPath())) {
            File jar = new File(cfg.getWorkerJarPath().trim());
            return jar.isFile() ? jar.toPath() : null;
        }
        return detectRunningWorkerJar();
    }

    /**
     * 探测当前进程运行所在的 jar（Spring Boot fat jar 场景）。
     *
     * <p>兼容两种 code source：可执行 jar（{@code file:/x.jar}）与 Spring Boot 嵌套布局
     * （{@code jar:file:/x.jar!/BOOT-INF/classes!/}）。以展开目录（IDE）启动时返回 {@code null}。</p>
     */
    static Path detectRunningWorkerJar() {
        Path path = codeSourcePath();
        if (path != null && Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
            return path;
        }
        return null;
    }

    /**
     * 探测当前类所在目录（IDE / 展开目录启动时的 classes 目录）。
     */
    private static Path detectRunningClassesDir() {
        Path path = codeSourcePath();
        if (path != null && Files.isDirectory(path)) {
            return path;
        }
        return null;
    }

    /**
     * 解析本类 code source 为本地路径，兼容多种 URL 形式：
     * <ul>
     *     <li>可执行 jar：{@code file:/x.jar}</li>
     *     <li>旧版 Spring Boot 嵌套：{@code jar:file:/x.jar!/BOOT-INF/classes!/}</li>
     *     <li>Spring Boot 3.2+ 嵌套：{@code jar:nested:/x.jar/!BOOT-INF/classes/!/} 或 {@code nested:/x.jar/!...}</li>
     * </ul>
     */
    private static Path codeSourcePath() {
        try {
            java.security.CodeSource codeSource = DockerEtlTask.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            return parseCodeSourceUrl(codeSource.getLocation().toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 把 code source location 字符串解析为 jar 或目录的本地路径。
     */
    static Path parseCodeSourceUrl(String location) {
        String path = location;
        if (path.startsWith("jar:")) {
            path = path.substring("jar:".length());
        }
        if (path.startsWith("nested:")) {
            path = path.substring("nested:".length());
            int marker = path.indexOf("/!");
            if (marker >= 0) {
                path = path.substring(0, marker);
            }
        } else {
            int marker = path.indexOf("!/");
            if (marker >= 0) {
                path = path.substring(0, marker);
            }
        }
        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        }
        return Path.of(URLDecoder.decode(path, StandardCharsets.UTF_8));
    }

    /**
     * 开发态自动探测工程内已构建的 datay-core 可执行 jar。
     *
     * <p>从当前工作目录、类所在目录逐级向上查找 {@code datay-core/target/*-jar-with-dependencies.jar}。</p>
     */
    private Path detectCoreJarFromProject() {
        Set<Path> starts = new LinkedHashSet<>();
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        starts.add(userDir);
        Path classesDir = detectRunningClassesDir();
        if (classesDir != null) {
            starts.add(classesDir.toAbsolutePath().normalize());
        }
        for (Path start : starts) {
            for (Path dir = start; dir != null; dir = dir.getParent()) {
                Path found = newestCoreJar(dir.resolve("datay-core").resolve("target"));
                if (found != null) {
                    return found;
                }
                if (dir.getFileName() != null && "datay-core".equals(dir.getFileName().toString())) {
                    found = newestCoreJar(dir.resolve("target"));
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private static Path newestCoreJar(Path dir) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                .filter(p -> p.getFileName().toString().matches("datay-core-.*-jar-with-dependencies\\.jar"))
                .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 开发态运行时：直接使用当前进程 classpath（展开目录 + 依赖 jar），无需构建可执行 jar。
     */
    private DockerCommandBuilder.DockerRuntime buildDevClasspathRuntime() {
        try {
            Class.forName(DockerCommandBuilder.MAIN_CLASS);
        } catch (Throwable e) {
            return null;
        }
        String classpath = System.getProperty("java.class.path", "");
        if (StrUtil.isBlank(classpath)) {
            return null;
        }
        List<String> entries = new ArrayList<>();
        boolean hasClassesDir = false;
        for (String entry : classpath.split(File.pathSeparator)) {
            if (StrUtil.isBlank(entry)) {
                continue;
            }
            entries.add(entry);
            if (Files.isDirectory(Path.of(entry))) {
                hasClassesDir = true;
            }
        }
        // 仅开发态展开目录（存在 classes 目录）时启用，避免把单个 fat jar 当作普通 classpath
        if (!hasClassesDir) {
            return null;
        }
        return DockerCommandBuilder.explodedRuntime(entries);
    }

    /**
     * 构建透传给容器的环境变量：任务元数据、状态存储配置、驱动仓库与附加环境变量。
     */
    private Map<String, String> buildContainerEnv(EtlExecutionProperties.Docker cfg, String workDir) {
        JobInstance instance = getJobInstance();
        Map<String, String> env = new LinkedHashMap<>();
        putIfPresent(env, "DATAY_JOB_CODE", instance.getJobCode());
        putIfPresent(env, "DATAY_INSTANCE_CODE", instance.getInstanceCode());
        putIfPresent(env, "DATAY_PARENT_INSTANCE_CODE", instance.getParentInstanceCode());
        putIfPresent(env, "DATAY_TENANT_ID", instance.getTenantId());

        // 状态存储：local 写入挂载的 log 目录，minio 透传连接配置
        StatusStorageConfigService.StatusStorageConfig status = loadStatusConfig();
        if (status != null && "minio".equalsIgnoreCase(status.type)) {
            env.put("DATAY_STATUS_TYPE", "minio");
            putIfPresent(env, "DATAY_STATUS_MINIO_ENDPOINT", status.minioEndpoint);
            putIfPresent(env, "DATAY_STATUS_MINIO_ACCESS_KEY", status.minioAccessKey);
            putIfPresent(env, "DATAY_STATUS_MINIO_SECRET_KEY", status.minioSecretKey);
            putIfPresent(env, "DATAY_STATUS_MINIO_BUCKET_NAME", status.minioBucketName);
        } else {
            env.put("DATAY_STATUS_TYPE", "local");
            env.put("DATAY_STATUS_LOCAL_BASE_PATH", workDir + "/log");
        }

        putIfPresent(env, "DATAY_DRIVER_REPOS", cfg.getDriverRepos());
        if (cfg.getExtraEnv() != null) {
            cfg.getExtraEnv().forEach((k, v) -> putIfPresent(env, k, v));
        }
        return env;
    }

    private StatusStorageConfigService.StatusStorageConfig loadStatusConfig() {
        try {
            StatusStorageConfigService service = SpringUtil.getBean(StatusStorageConfigService.class);
            return service == null ? null : service.getConfig();
        } catch (Exception e) {
            LOG.warn("读取状态存储配置失败，docker 任务按 local 处理: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取执行配置，供子类/单元测试覆写。
     */
    protected EtlExecutionProperties getExecutionProperties() {
        return SpringUtil.getBean(EtlExecutionProperties.class);
    }

    /**
     * 执行 docker 命令并流式采集容器输出到任务日志。
     */
    private int runDocker(List<String> command, Map<String, String> env) throws IOException, InterruptedException {
        CommandLine commandLine = new CommandLine(command.get(0));
        for (int i = 1; i < command.size(); i++) {
            commandLine.addArgument(command.get(i), false);
        }

        DefaultExecutor exec = new DefaultExecutor() {
            @Override
            protected Process launch(CommandLine cmd, Map<String, String> environment, File dir) throws IOException {
                Process p = super.launch(cmd, environment, dir);
                process = p;
                if (cancelled.get()) {
                    p.destroyForcibly();
                }
                return p;
            }
        };
        // 容器内的 DataY 会把任务日志直接写入挂载的日志文件，这里不再把容器 stdout 追加到同一文件，
        // 否则会出现重复内容与编码不一致（容器 stdout 用默认 PumpStreamHandler 输出到应用控制台）
        try {
            return exec.execute(commandLine, env);
        } catch (ExecuteException e) {
            return e.getExitValue();
        } catch (IOException e) {
            // 容器被 kill 时读取进程输出流会抛 IOException，交由上层按取消处理
            if (cancelled.get()) {
                return -1;
            }
            throw e;
        }
    }

    private void pullImage(String image) {
        try {
            Process p = new ProcessBuilder(dockerBinary, "pull", image).redirectErrorStream(true).inheritIO().start();
            p.waitFor();
        } catch (Exception e) {
            LOG.warn("拉取镜像失败（继续尝试执行）: image={}, error={}", image, e.getMessage());
        }
    }

    private void killContainer() {
        if (containerName == null || dockerBinary == null) {
            return;
        }
        try {
            Process cleanup = new ProcessBuilder(dockerBinary, "rm", "-f", containerName).redirectErrorStream(true).start();
            cleanup.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("清理 docker 容器被中断: container={}", containerName);
        } catch (Exception e) {
            LOG.warn("清理 docker 容器失败: container={}, error={}", containerName, e.getMessage());
        }
    }

    @Override
    public boolean cancel() {
        cancelled.set(true);
        killContainer();
        if (process != null) {
            process.destroyForcibly();
        }
        return true;
    }

    private static void putIfPresent(Map<String, String> env, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            env.put(key, value);
        }
    }
}
