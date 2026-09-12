package com.data.datafusion.config;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 部署角色配置。
 *
 * <p>data-web 中的调度系统由 master（调度）与 worker（执行）两类角色组成，通过
 * {@code development.mode} 配置项决定当前进程承担的角色：</p>
 *
 * <ul>
 *     <li>{@code standalone}：单机部署，调度与执行合一（默认值）</li>
 *     <li>{@code master}：仅部署 master，负责调度、依赖检查与任务状态落库，不执行任务</li>
 *     <li>{@code worker}：仅部署 worker，负责接收并执行任务，不参与调度与调度器初始化</li>
 *     <li>{@code cluster}：master 与 worker 合并部署（同时具备调度与执行能力）</li>
 * </ul>
 *
 * <p>master 与 worker 分离部署时依赖 Redis 进行事件分发与 Leader 选举，
 * 即 {@code master} / {@code worker} / {@code cluster} 均使用集群事件通道。</p>
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
    public static final String MODE_CLUSTER = "cluster";

    /**
     * 部署模式，默认 standalone
     */
    private String mode = MODE_STANDALONE;

    /**
     * Leader 选举锁名称，多 master 场景下需保持一致（默认 datafusion-leader）
     */
    private String leaderLockName = "datafusion-leader";

    private static volatile DeploymentProperties instance;

    public DeploymentProperties() {}

    @PostConstruct
    public void init() {
        instance = this;
        log.info("######################## 调度部署模式 development.mode={} ({})", mode, describe());
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
            fallback.setMode(environment.getProperty(MODE_KEY, MODE_STANDALONE));
        }
        return fallback;
    }

    public static String mode() {
        return get().getMode();
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
    }

    /**
     * 是否具备调度能力（master 职责）：初始化 Quartz 调度、处理任务状态/上线/下线事件。
     */
    public boolean isSchedulerEnabled() {
        return MODE_STANDALONE.equals(mode) || MODE_MASTER.equals(mode) || MODE_CLUSTER.equals(mode);
    }

    /**
     * 是否具备执行能力（worker 职责）：消费待执行任务并真正执行。
     */
    public boolean isWorkerEnabled() {
        return MODE_STANDALONE.equals(mode) || MODE_WORKER.equals(mode) || MODE_CLUSTER.equals(mode);
    }

    /**
     * 是否使用集群事件通道（Redis）。standalone 为单机内存队列，其余模式均需要 Redis。
     */
    public boolean isClusterMode() {
        return !MODE_STANDALONE.equals(mode);
    }

    public String describe() {
        switch (mode) {
            case MODE_MASTER:
                return "master 独立部署：仅调度";
            case MODE_WORKER:
                return "worker 独立部署：仅执行";
            case MODE_CLUSTER:
                return "master/worker 合并部署";
            default:
                return "standalone 单机部署";
        }
    }
}
