package com.data.datafusion.service.jobevent;

import com.data.datafusion.config.DeploymentProperties;
import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.domain.Job;
import com.data.datafusion.service.cluster.ElectionListener;
import com.data.datafusion.service.cluster.LeaderElection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.redisson.api.RBoundedBlockingQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 集群事件服务。
 *
 * <p>通过 Redis 队列在 master 与 worker 之间分发事件：</p>
 * <ul>
 *     <li>{@code startJobQueue}：master -&gt; worker，待执行任务</li>
 *     <li>{@code endJobQueue}：worker -&gt; master，任务状态变更</li>
 *     <li>{@code addJobQueue} / {@code deleteJobQueue}：调度变更事件，master 内部消费</li>
 * </ul>
 *
 * <p>handlers 按部署角色启动：</p>
 * <ul>
 *     <li>worker 职责：消费 {@code startJobQueue} 并执行任务，与调度能力解耦，worker 可独立部署</li>
 *     <li>master 职责：消费任务状态与调度变更事件，只在 Leader 节点启动，master 可独立部署</li>
 * </ul>
 */
@Service
public class RedisEventService implements IEventService, ElectionListener {

    private final Logger log = LoggerFactory.getLogger(RedisEventService.class);

    public static RBoundedBlockingQueue<JobStatusEvent> startJobQueue;

    ExecutorService startJobEventExecutor = Executors.newSingleThreadExecutor();

    public static RBoundedBlockingQueue<JobStatusEvent> jobStatusQueue;
    ExecutorService jobStatusEventExecutor = Executors.newSingleThreadExecutor();

    public static RBoundedBlockingQueue<Job> addJobQueue;
    ExecutorService addJobEventExecutor = Executors.newSingleThreadExecutor();
    public static RBoundedBlockingQueue<Job> deleteJobQueue;
    ExecutorService deleteJobEventExecutor = Executors.newSingleThreadExecutor();

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Autowired(required = false)
    LeaderElection leaderElection;

    /**
     * 显式依赖部署配置，保证在 {@link #init()} 前已完成 development.mode 绑定。
     */
    @Autowired
    private DeploymentProperties deploymentProperties;

    private static RedisEventService instance;

    /** worker 事件处理器是否已启动 */
    private final AtomicBoolean workerHandlersStarted = new AtomicBoolean(false);

    /** master 事件处理器是否已启动 */
    private final AtomicBoolean masterHandlersStarted = new AtomicBoolean(false);

    public static synchronized RedisEventService getInstance() {
        if (instance == null) {
            instance = SpringUtil.getBean(RedisEventService.class);
            instance.init();
        }
        return instance;
    }

    //    @PostConstruct
    public void init() {
        if (redissonClient == null) {
            throw new IllegalStateException(
                "集群角色(master/worker)需要 Redis(RedissonClient)，请检查 redisson 配置或改用 development.mode=standalone"
            );
        }
        startJobQueue = redissonClient.getBoundedBlockingQueue("startJobQueue");
        startJobQueue.trySetCapacity(10000);
        jobStatusQueue = redissonClient.getBoundedBlockingQueue("endJobQueue");
        jobStatusQueue.trySetCapacity(10000);
        addJobQueue = redissonClient.getBoundedBlockingQueue("addJobQueue");
        addJobQueue.trySetCapacity(10000);
        deleteJobQueue = redissonClient.getBoundedBlockingQueue("deleteJobQueue");
        deleteJobQueue.trySetCapacity(10000);

        DeploymentProperties deployment = deploymentProperties == null ? DeploymentProperties.get() : deploymentProperties;

        // worker 职责：接收待执行任务并执行，与是否 master 无关
        if (deployment.isWorkerEnabled()) {
            startWorkerHandlers();
        }

        // master 职责：只由 Leader 消费调度相关事件
        if (deployment.isSchedulerEnabled()) {
            if (leaderElection != null) {
                // 注册选举回调，当选时启动；若已是 Leader 则立即启动
                leaderElection.addElectionListener(this);
                if (leaderElection.isMaster()) {
                    startMasterHandlers();
                }
            } else {
                log.warn("未获取到 LeaderElection 组件，master 事件处理器直接启动");
                startMasterHandlers();
            }
        }
    }

    /**
     * 启动 worker 事件处理器（消费待执行任务），幂等。
     */
    public void startWorkerHandlers() {
        if (!workerHandlersStarted.compareAndSet(false, true)) {
            return;
        }
        log.info("启动 worker 事件处理器：消费 startJobQueue");
        startJobEventExecutor.execute(new StartJobEventHandler());
    }

    /**
     * 启动 master 事件处理器（消费任务状态与调度变更事件），幂等。
     */
    public void startMasterHandlers() {
        if (!masterHandlersStarted.compareAndSet(false, true)) {
            return;
        }
        log.info("启动 master 事件处理器：消费 endJobQueue/addJobQueue/deleteJobQueue");
        jobStatusEventExecutor.execute(new JobStatusEventHandler());
        addJobEventExecutor.execute(new AddJobEventHandler());
        deleteJobEventExecutor.execute(new DeleteJobEventHandler());
    }

    /**
     * 当选 Leader 后启动 master 事件处理器（支持 leader 漂移后接管）。
     */
    @Override
    public void onElected() {
        DeploymentProperties deployment = deploymentProperties == null ? DeploymentProperties.get() : deploymentProperties;
        if (deployment.isSchedulerEnabled()) {
            startMasterHandlers();
        }
    }

    public void pushStartJobEvent(JobStatusEvent startJobEvent) {
        try {
            startJobQueue.put(startJobEvent);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JobStatusEvent takeStartJobEvent() {
        try {
            return startJobQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void pushJobStatusEvent(JobStatusEvent jobStatusEvent) {
        try {
            jobStatusQueue.put(jobStatusEvent);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JobStatusEvent takeJobStatusEvent() {
        try {
            return jobStatusQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addJob(Job job) {
        try {
            addJobQueue.put(job);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateJob(Job job) {}

    @Override
    public void deleteJob(Job job) {
        try {
            log.info("删除任务事件发送id {}", job.getId());
            deleteJobQueue.put(job);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Job takeAddJobEvent() {
        try {
            return addJobQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Job takeDeleteJobEvent() {
        try {
            return deleteJobQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
