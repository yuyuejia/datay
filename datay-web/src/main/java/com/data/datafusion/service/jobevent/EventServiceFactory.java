package com.data.datafusion.service.jobevent;

import com.data.datafusion.config.SpringUtil;
import java.util.HashMap;
import java.util.Map;

public class EventServiceFactory {

    public static Map<String, IEventService> commonServiceMap = new HashMap<>();

    public static void register(EventTypeEnum eventTypeEnum, IEventService commonService) {
        commonServiceMap.put(eventTypeEnum.toString(), commonService);
    }

    public static IEventService getEventService() {
        String property = SpringUtil.getEnvironment().getProperty("development.mode");
        if ("cluster".equals(property)) {
            return RedisEventService.getInstance();
        } else {
            return StandaloneEventService.getInstance();
        }
    }

    public enum EventTypeEnum {
        Standalone,
        Redis,
    }
}
