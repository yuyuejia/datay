package com.data.datafusion.config;

import com.data.datafusion.service.cluster.LeaderElection;
import com.data.datafusion.service.scheduler.QuartzService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 调度启动器：按部署角色初始化调度。
 *
 * <ul>
 *     <li>standalone：单机部署，直接初始化 Quartz 调度（调度 + 执行同进程）</li>
 *     <li>master：master 独立部署，参与 Leader 选举，当选后初始化 Quartz 调度</li>
 *     <li>worker：worker 独立部署，不初始化 Quartz 调度，仅等待执行任务</li>
 *     <li>master + worker：同一进程同时承担两类角色，参与 Leader 选举并在当选后初始化调度，
 *         同时消费待执行任务；无论是单角色还是多角色配置，只要包含 master 即走选举流程</li>
 * </ul>
 */
@Component
public class QuartzStartupRunner implements CommandLineRunner {

    private final Logger log = LoggerFactory.getLogger(QuartzStartupRunner.class);

    @Autowired
    QuartzService quartzService;

    /**
     * Leader 选举组件，仅在集群角色(master/worker)下存在。
     */
    @Autowired(required = false)
    ObjectProvider<LeaderElection> leaderElectionProvider;

    @Override
    public void run(String... args) throws Exception {
        DeploymentProperties deployment = DeploymentProperties.get();
        log.info("########################使用 {} 模式：{}", deployment.getMode(), deployment.describe());

        if (!deployment.isSchedulerEnabled()) {
            log.info("当前节点为 worker，不参与调度，等待接收 master 分发的任务");
            return;
        }

        if (deployment.isClusterEventChannel()) {
            // 集群角色下由 Leader(master) 负责初始化调度，选举成功后经 ElectionListener 回调初始化
            LeaderElection leaderElection = leaderElectionProvider == null ? null : leaderElectionProvider.getIfAvailable();
            if (leaderElection != null) {
                leaderElection.addElectionListener(quartzService);
                leaderElection.tryHold(deployment.getLeaderLockName());
                log.info("已加入 Leader 选举，当选后将初始化调度");
            } else {
                log.warn("集群角色未获取到 LeaderElection 组件，退化为直接初始化调度");
                quartzService.initJobScheduler();
            }
        } else {
            quartzService.initJobScheduler();
        }
    }
}
