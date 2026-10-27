package com.data.datafusion.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Quartz 调度线程配置。
 *
 * <p>对应配置项位于 {@code development.scheduler} 下：</p>
 * <pre>
 * development:
 *   scheduler:
 *     thread-count: 10   # Quartz 触发线程数
 * </pre>
 *
 * <p>该值决定同一时间点有大量 Cron 任务到期时，调度侧能并发拉起多少个任务实例。
 * 每个触发都会读写数据库，因此该值不宜超过数据库连接池（Hikari）容量。</p>
 */
@Component
@ConfigurationProperties(prefix = "development.scheduler")
public class SchedulerExecutionProperties {

    private static final Logger log = LoggerFactory.getLogger(SchedulerExecutionProperties.class);

    /** Quartz 默认调度线程数（与 Spring SchedulerFactoryBean 默认值保持一致）。 */
    public static final int DEFAULT_THREAD_COUNT = 10;

    /** Quartz 触发线程数。 */
    private int threadCount = DEFAULT_THREAD_COUNT;

    public SchedulerExecutionProperties() {}

    @PostConstruct
    public void init() {
        normalize();
        log.info("######################## Quartz 调度线程数 threadCount={}", threadCount);
    }

    void normalize() {
        if (threadCount < 1) {
            log.warn("development.scheduler.thread-count={} 非法，回退默认值 {}", threadCount, DEFAULT_THREAD_COUNT);
            threadCount = DEFAULT_THREAD_COUNT;
        }
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }
}
