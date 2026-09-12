package com.data.datafusion.config;

import com.data.datafusion.service.cluster.LeaderElection;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 调度集群配置：为 master / worker / cluster 部署模式提供 Leader 选举与 Redis 客户端。
 *
 * <p>standalone 模式不注册这些 Bean（通过 {@code development.mode} 条件控制），避免对 Redis 的强依赖。</p>
 *
 * <p>Leader 选举用于保证多 master 场景下只有一个 master 初始化 Quartz 调度并处理调度事件；
 * worker 节点同样参与选举（仅 master 角色会使用选举结果）。</p>
 */
@Configuration
public class SchedulerClusterConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SchedulerClusterConfiguration.class);

    /**
     * 集群模式下的 Redisson 客户端。
     * 若应用已通过 redisson-spring-boot-starter 提供 Redis 连接（如 redisson.yml / spring.data.redis 配置），
     * 则以已有连接为准，此处不会覆盖。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    @ConditionalOnClusterDeployment
    public RedissonClient redissonClient(
        @Value("${spring.data.redis.host:127.0.0.1}") String host,
        @Value("${spring.data.redis.port:6379}") int port,
        @Value("${spring.data.redis.password:}") String password,
        @Value("${spring.data.redis.database:0}") int database
    ) {
        String address = "redis://" + host + ":" + port;
        Config config = new Config();
        SingleServerConfig serverConfig = config.useSingleServer().setAddress(address).setDatabase(database);
        if (StringUtils.hasText(password)) {
            serverConfig.setPassword(password);
        }
        log.info("初始化调度 Redis 客户端: {}", address);
        return Redisson.create(config);
    }

    /**
     * Leader 选举组件，集群模式下 master 采用。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(LeaderElection.class)
    @ConditionalOnClusterDeployment
    public LeaderElection leaderElection(RedissonClient redissonClient) {
        LeaderElection leaderElection = new LeaderElection();
        leaderElection.setRedissonClient(redissonClient);
        return leaderElection;
    }
}
