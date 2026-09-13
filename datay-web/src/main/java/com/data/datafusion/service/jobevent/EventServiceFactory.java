package com.data.datafusion.service.jobevent;

import com.data.datafusion.config.DeploymentProperties;
import java.util.HashMap;
import java.util.Map;

/**
 * 事件服务工厂：根据部署角色({@code development.mode})返回对应的事件服务实现。
 *
 * <ul>
 *     <li>{@code standalone}：{@link StandaloneEventService}，单机内存队列</li>
 *     <li>{@code master} / {@code worker} / {@code master,worker}：{@link RedisEventService}，基于 Redis 队列分发</li>
 * </ul>
 */
public class EventServiceFactory {

    public static Map<String, IEventService> commonServiceMap = new HashMap<>();

    public static void register(EventTypeEnum eventTypeEnum, IEventService commonService) {
        commonServiceMap.put(eventTypeEnum.toString(), commonService);
    }

    public static IEventService getEventService() {
        DeploymentProperties deployment = DeploymentProperties.get();
        if (deployment.isClusterMode()) {
            return RedisEventService.getInstance();
        }
        return StandaloneEventService.getInstance();
    }

    public enum EventTypeEnum {
        Standalone,
        Redis,
    }
}
