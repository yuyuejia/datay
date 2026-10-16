package com.data.datafusion.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ETL 任务执行配置。
 *
 * <p>ETL 任务默认在当前 worker 进程内执行（{@code mode=local}）；配置为 {@code mode=docker} 时，
 * 任务改为在独立的 docker 容器中执行：容器提供 Java 运行环境，任务 jar 与日志目录、驱动/lib 目录
 * 通过挂载共享给容器，任务 JSON 由 worker 写入挂载目录后交给容器执行。</p>
 *
 * <p>对应配置项位于 {@code development.etl} 下，示例：</p>
 * <pre>
 * development:
 *   etl:
 *     mode: docker
 *     docker:
 *       image: eclipse-temurin:17-jre
 *       jar-path: /opt/datay/datay-core-1.0.1-SNAPSHOT-jar-with-dependencies.jar
 *       network: host
 *       memory: 2g
 *       lib-dirs:
 *         - /opt/datay/lib
 *       extra-mounts:
 *         - /data/driver-jars:/data/driver-jars:ro
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "development.etl")
public class EtlExecutionProperties {

    /** 进程内执行（默认）。 */
    public static final String MODE_LOCAL = "local";

    /** docker 容器执行。 */
    public static final String MODE_DOCKER = "docker";

    /** 执行模式：{@code local} 或 {@code docker}。 */
    private String mode = MODE_LOCAL;

    private Docker docker = new Docker();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Docker getDocker() {
        return docker;
    }

    public void setDocker(Docker docker) {
        this.docker = docker;
    }

    /**
     * 是否使用 docker 模式执行 ETL 任务。
     */
    public boolean isDockerMode() {
        return MODE_DOCKER.equalsIgnoreCase(mode == null ? null : mode.trim());
    }

    /**
     * docker 执行相关配置。
     */
    public static class Docker {

        /** docker 镜像，需包含 Java 运行环境。 */
        private String image = "eclipse-temurin:17-jre";

        /** datay-core 可执行 jar（jar-with-dependencies）在宿主机上的路径。 */
        private String jarPath;

        /**
         * worker 可执行 jar（Spring Boot fat jar）在宿主机上的路径。
         *
         * <p>未配置 {@link #jarPath} 时，docker 模式将直接引用该 jar 内嵌的 core 运行时
         * （通过 Spring Boot {@code PropertiesLauncher} 加载 {@code BOOT-INF/lib}），
         * 无需单独构建/挂载 core jar。留空时自动探测当前运行的 worker jar。</p>
         */
        private String workerJarPath;

        /** docker 可执行文件路径。 */
        private String binary = "docker";

        /** docker 网络模式，如 {@code host}、{@code bridge}，为空则使用 docker 默认网络。 */
        private String network;

        /**
         * 是否允许容器访问宿主机服务（默认为 true，通常无需配置）。
         *
         * <p>打开后：为容器注入 {@code --add-host <alias>:host-gateway}，并把任务 JSON 中指向
         * {@code localhost} / {@code 127.0.0.1} 的数据源/服务地址改写为 {@link #hostAccessAlias}，
         * 使容器内的 ETL 能直接访问跑在宿主机上的数据库或模型服务。</p>
         */
        private boolean hostAccess = true;

        /** 宿主机访问别名，默认 {@code host.docker.internal}（Docker Desktop 内置，Linux 由 host-gateway 解析）。 */
        private String hostAccessAlias = "host.docker.internal";

        /** 是否随 {@code --add-host} 注入 host-gateway 映射（Linux 需要；Docker Desktop 可关闭）。 */
        private boolean addHostGateway = true;

        /** 容器内存限制，如 {@code 2g}。 */
        private String memory;

        /** 容器 CPU 限制，如 {@code 2}。 */
        private String cpus;

        /** 额外的 java 参数，如 {@code -Xmx1g}（单个字符串，按空白拆分）。 */
        private String javaArgs;

        /** 容器名前缀，实际容器名为 {@code <prefix><instanceCode>}。 */
        private String containerNamePrefix = "datay-etl-";

        /** 容器内工作目录，日志与状态文件均落在此目录下的 log 子目录。 */
        private String containerWorkDir = "/app";

        /** 宿主机日志目录，为空时默认 {@code <工作目录>/log}。 */
        private String hostLogDir;

        /** 宿主机驱动目录，为空时默认 {@code <工作目录>/drivers}；目录存在时挂载到容器。 */
        private String driverDir;

        /** 挂载驱动目录到容器内的路径，须与容器内相对路径 {@code drivers} 对应。 */
        private String containerDriverDir = "/app/drivers";

        /**
         * 宿主机数据目录，为空时默认 {@code <工作目录>/data}；目录存在时挂载到容器
         * {@code <workDir>/data}（读写）。
         *
         * <p>用于分享任务使用的相对路径数据文件，如内嵌 DuckDB 文件
         * {@code ./data/xxx.duckdb}、本地上传文件 {@code ./data/files} 等。</p>
         */
        private String dataDir;

        /** Maven 驱动仓库，透传给容器的 {@code DATAY_DRIVER_REPOS}。 */
        private String driverRepos;

        /** 附加 lib 目录（宿主路径），逐个挂载到容器 {@code <workDir>/lib/<name>} 并加入 classpath。 */
        private List<String> libDirs = new ArrayList<>();

        /** 附加挂载，格式 {@code hostPath:containerPath[:ro|rw]}。 */
        private List<String> extraMounts = new ArrayList<>();

        /** 附加环境变量，透传给容器。 */
        private Map<String, String> extraEnv = new LinkedHashMap<>();

        /** 是否在启动前尝试拉取镜像。 */
        private boolean pullImage = false;

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public String getJarPath() {
            return jarPath;
        }

        public void setJarPath(String jarPath) {
            this.jarPath = jarPath;
        }

        public String getWorkerJarPath() {
            return workerJarPath;
        }

        public void setWorkerJarPath(String workerJarPath) {
            this.workerJarPath = workerJarPath;
        }

        public String getBinary() {
            return binary;
        }

        public void setBinary(String binary) {
            this.binary = binary;
        }

        public String getNetwork() {
            return network;
        }

        public void setNetwork(String network) {
            this.network = network;
        }

        public boolean isHostAccess() {
            return hostAccess;
        }

        public void setHostAccess(boolean hostAccess) {
            this.hostAccess = hostAccess;
        }

        public String getHostAccessAlias() {
            return hostAccessAlias;
        }

        public void setHostAccessAlias(String hostAccessAlias) {
            this.hostAccessAlias = hostAccessAlias;
        }

        public boolean isAddHostGateway() {
            return addHostGateway;
        }

        public void setAddHostGateway(boolean addHostGateway) {
            this.addHostGateway = addHostGateway;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }

        public String getCpus() {
            return cpus;
        }

        public void setCpus(String cpus) {
            this.cpus = cpus;
        }

        public String getJavaArgs() {
            return javaArgs;
        }

        public void setJavaArgs(String javaArgs) {
            this.javaArgs = javaArgs;
        }

        public String getContainerNamePrefix() {
            return containerNamePrefix;
        }

        public void setContainerNamePrefix(String containerNamePrefix) {
            this.containerNamePrefix = containerNamePrefix;
        }

        public String getContainerWorkDir() {
            return containerWorkDir;
        }

        public void setContainerWorkDir(String containerWorkDir) {
            this.containerWorkDir = containerWorkDir;
        }

        public String getHostLogDir() {
            return hostLogDir;
        }

        public void setHostLogDir(String hostLogDir) {
            this.hostLogDir = hostLogDir;
        }

        public String getDriverDir() {
            return driverDir;
        }

        public void setDriverDir(String driverDir) {
            this.driverDir = driverDir;
        }

        public String getContainerDriverDir() {
            return containerDriverDir;
        }

        public void setContainerDriverDir(String containerDriverDir) {
            this.containerDriverDir = containerDriverDir;
        }

        public String getDataDir() {
            return dataDir;
        }

        public void setDataDir(String dataDir) {
            this.dataDir = dataDir;
        }

        public String getDriverRepos() {
            return driverRepos;
        }

        public void setDriverRepos(String driverRepos) {
            this.driverRepos = driverRepos;
        }

        public List<String> getLibDirs() {
            return libDirs;
        }

        public void setLibDirs(List<String> libDirs) {
            this.libDirs = libDirs == null ? new ArrayList<>() : libDirs;
        }

        public List<String> getExtraMounts() {
            return extraMounts;
        }

        public void setExtraMounts(List<String> extraMounts) {
            this.extraMounts = extraMounts == null ? new ArrayList<>() : extraMounts;
        }

        public Map<String, String> getExtraEnv() {
            return extraEnv;
        }

        public void setExtraEnv(Map<String, String> extraEnv) {
            this.extraEnv = extraEnv == null ? new LinkedHashMap<>() : extraEnv;
        }

        public boolean isPullImage() {
            return pullImage;
        }

        public void setPullImage(boolean pullImage) {
            this.pullImage = pullImage;
        }
    }
}
