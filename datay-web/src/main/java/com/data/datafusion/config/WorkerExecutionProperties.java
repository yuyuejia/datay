package com.data.datafusion.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Worker 任务执行并发配置。
 *
 * <p>对应配置项位于 {@code development.worker} 下，示例：</p>
 * <pre>
 * development:
 *   worker:
 *     thread-count: 50       # 同时执行的任务数上限
 *     queue-capacity: 50     # 待执行队列容量，满后暂停从事件队列取任务（背压）
 * </pre>
 *
 * <p>也可以使用环境变量 {@code DEVELOPMENT_WORKER_THREAD_COUNT} /
 * {@code DEVELOPMENT_WORKER_QUEUE_CAPACITY} 覆盖。</p>
 *
 * <p>注意：{@code StartJobEventHandler} 是通过 {@code new} 创建的（不经过 Spring 容器），
 * 因此这里提供静态 {@link #get()} 入口，容器启动后返回绑定了配置的实例，未启动时回退到
 * Environment 或默认值。</p>
 */
@Component
@ConfigurationProperties(prefix = "development.worker")
public class WorkerExecutionProperties {

    private static final Logger log = LoggerFactory.getLogger(WorkerExecutionProperties.class);

    /** 默认并发执行线程数。 */
    public static final int DEFAULT_THREAD_COUNT = 50;

    /** 默认待执行队列容量。 */
    public static final int DEFAULT_QUEUE_CAPACITY = 50;

    /** 并发执行线程数，即单节点同一时刻真正并行执行的任务数上限。 */
    private int threadCount = DEFAULT_THREAD_COUNT;

    /** 待执行队列容量，队列满后 worker 暂停从事件队列取任务，避免进程内无限堆积。 */
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

    private static volatile WorkerExecutionProperties instance;

    public WorkerExecutionProperties() {}

    @PostConstruct
    public void init() {
        instance = this;
        normalize();
        log.info("######################## Worker 并发配置 threadCount={}, queueCapacity={}", threadCount, queueCapacity);
    }

    /**
     * 兜底获取实例：配置类尚未被 Spring 托管时（如单元测试）回退到 Environment / 默认值。
     */
    public static WorkerExecutionProperties get() {
        if (instance != null) {
            return instance;
        }
        WorkerExecutionProperties fallback = new WorkerExecutionProperties();
        Environment environment = SpringUtil.getEnvironment();
        if (environment != null) {
            fallback.setThreadCount(
                environment.getProperty("development.worker.thread-count", Integer.class, DEFAULT_THREAD_COUNT)
            );
            fallback.setQueueCapacity(
                environment.getProperty("development.worker.queue-capacity", Integer.class, DEFAULT_QUEUE_CAPACITY)
            );
        }
        fallback.normalize();
        return fallback;
    }

    /**
     * 非法值回退默认值，避免线程池/队列参数导致启动失败。
     */
    void normalize() {
        if (threadCount < 1) {
            log.warn("development.worker.thread-count={} 非法，回退默认值 {}", threadCount, DEFAULT_THREAD_COUNT);
            threadCount = DEFAULT_THREAD_COUNT;
        }
        if (queueCapacity < 1) {
            log.warn("development.worker.queue-capacity={} 非法，回退默认值 {}", queueCapacity, DEFAULT_QUEUE_CAPACITY);
            queueCapacity = DEFAULT_QUEUE_CAPACITY;
        }
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
