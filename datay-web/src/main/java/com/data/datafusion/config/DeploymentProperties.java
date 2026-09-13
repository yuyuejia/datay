package com.data.datafusion.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 部署角色配置。
 *
 * <p>data-web 中的调度系统由 master（调度）与 worker（执行）两类角色组成，通过
 * {@code development.mode} 配置项声明当前进程承担的角色：</p>
 *
 * <ul>
 *     <li>{@code standalone}：单机部署，调度与执行合一（默认值）</li>
 *     <li>{@code master}：master 独立部署，负责调度、依赖检查与任务状态落库，不执行任务</li>
 *     <li>{@code worker}：worker 独立部署，负责接收并执行任务，不参与调度</li>
 * </ul>
 *
 * <p>同一个服务同时具备 master 与 worker 职能时，把角色用逗号（或空格）一起写上即可，
 * 例如 {@code development.mode=master,worker}，等价于按 master、worker 两个角色同时启动。</p>
 *
 * <p>{@code standalone} 表达的是「单机部署」这一形态，可与 master / worker 角色组合：
 * 只写 {@code standalone} 表示单机部署（调度 + 执行，内存队列，不依赖 Redis）；
 * 写 {@code standalone,worker} 表示本机既按单机配置运行、又额外承担 worker 职能；
 * 写 {@code master} / {@code worker} / {@code master,worker} 表示多机部署，
 * 需要 Redis 进行事件分发，多 master 时还需要 Leader 选举。</p>
 *
 * <p>角色相关配置（Redis 连接、Leader 锁名称等）直接作为普通配置项声明，
 * 不再依赖 {@code application-master.yml} / {@code application-worker.yml} 之类的角色 profile。</p>
 */
@Component
@ConfigurationProperties(prefix = "development")
public class DeploymentProperties {

    private static final Logger log = LoggerFactory.getLogger(DeploymentProperties.class);

    /** 配置项 {@code development.mode} */
    public static final String MODE_KEY = "development.mode";

    public static final String MODE_STANDALONE = "standalone";
    public static final String MODE_MASTER = "master";
    public static final String MODE_WORKER = "worker";

    /** 逗号分隔的角色配置项 {@code development.modes}，与 {@code development.mode} 等价 */
    public static final String MODES_KEY = "development.modes";

    /** 多角色分隔符：逗号、分号、空白 */
    private static final String ROLE_SEPARATOR = "[,;\\s]+";

    /**
     * 部署角色，默认 standalone；多角色用逗号或空格分隔，如 {@code master,worker}
     * 或 {@code standalone,worker}。
     */
    private String mode = MODE_STANDALONE;

    /**
     * Leader 选举锁名称，多 master 场景下需保持一致（默认 datafusion-leader）
     */
    private String leaderLockName = "datafusion-leader";

    /**
     * 解析后的角色集合，保持配置书写顺序，如 {master, worker}
     */
    private Set<String> roles = defaultRoles();

    private static volatile DeploymentProperties instance;

    public DeploymentProperties() {}

    @PostConstruct
    public void init() {
        instance = this;
        normalize();
        log.info("######################## 调度部署角色 development.mode={} ({})", mode, describe());
    }

    /**
     * 兜底获取实例：配置类尚未被 Spring 托管时（如单元测试）退化为读取 Environment。
     */
    public static DeploymentProperties get() {
        if (instance != null) {
            return instance;
        }
        DeploymentProperties fallback = new DeploymentProperties();
        Environment environment = SpringUtil.getEnvironment();
        if (environment != null) {
            String value = environment.getProperty(MODE_KEY);
            if (value == null || value.isBlank()) {
                value = environment.getProperty(MODES_KEY);
            }
            fallback.setMode(value);
        }
        return fallback;
    }

    public static String mode() {
        return get().getMode();
    }

    /**
     * 角色配置归一化：解析角色集合并回写 mode。
     *
     * <p>standalone 为单机专用角色，与 master / worker 混用时忽略 standalone，按声明的角色处理。</p>
     */
    private void normalize() {
        Set<String> declared = split(this.mode);
        Set<String> parsed = declared.isEmpty()
            ? defaultRoles()
            : declared.stream()
                .filter(role -> MODE_STANDALONE.equals(role) || MODE_MASTER.equals(role) || MODE_WORKER.equals(role))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (parsed.isEmpty()) {
            log.warn("######################## development.mode={} 未识别到有效角色，按 standalone 处理", mode);
            parsed = defaultRoles();
        }
        this.roles = parsed;
        this.mode = String.join(",", parsed);
    }

    /**
     * 按逗号、分号或空白拆分角色配置，去重并转小写。
     */
    private static Set<String> split(String mode) {
        if (mode == null || mode.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(mode.trim().toLowerCase(Locale.ROOT).split(ROLE_SEPARATOR))
            .filter(part -> !part.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> defaultRoles() {
        return new LinkedHashSet<>(Collections.singletonList(MODE_STANDALONE));
    }

    public String getMode() {
        return mode;
    }

    public String getLeaderLockName() {
        return leaderLockName;
    }

    public void setLeaderLockName(String leaderLockName) {
        if (leaderLockName != null && !leaderLockName.isBlank()) {
            this.leaderLockName = leaderLockName.trim();
        }
    }

    public void setMode(String mode) {
        this.mode = mode == null || mode.isBlank() ? MODE_STANDALONE : mode.trim().toLowerCase(Locale.ROOT);
        normalize();
    }

    /**
     * 当前进程承担的角色集合，默认 standalone。
     */
    public Set<String> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    /**
     * 是否承担指定角色。
     */
    public boolean hasRole(String role) {
        return role != null && roles.contains(role);
    }

    /**
     * 是否具备调度能力（master 职责）：初始化 Quartz 调度、处理任务状态/上线/下线事件。
     */
    public boolean isSchedulerEnabled() {
        return hasRole(MODE_MASTER) || hasRole(MODE_STANDALONE);
    }

    /**
     * 是否具备执行能力（worker 职责）：消费待执行任务并真正执行。
     */
    public boolean isWorkerEnabled() {
        return hasRole(MODE_WORKER) || hasRole(MODE_STANDALONE);
    }

    /**
     * 是否使用集群事件通道（Redis）：进程内只要承担了 master 或 worker 角色就需要 Redis 分发事件，
     * 即使同时写上了 {@code standalone}（例如 {@code development.mode=standalone,worker}
     * 表示「本机既是单机节点、又额外承担 worker 职能」）依然需要 Redis。
     * 只有纯 {@code standalone} 才走单机内存队列。
     */
    public boolean isClusterEventChannel() {
        return hasRole(MODE_MASTER) || hasRole(MODE_WORKER);
    }

    public String describe() {
        Set<String> capabilities = new TreeSet<>();
        if (isSchedulerEnabled()) {
            capabilities.add("调度");
        }
        if (isWorkerEnabled()) {
            capabilities.add("执行");
        }
        String capability = String.join(" + ", capabilities) + " 部署";
        return hasRole(MODE_STANDALONE) ? "standalone 单机部署（" + capability + "）" : capability;
    }
}
