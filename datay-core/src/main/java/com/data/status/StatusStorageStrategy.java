package com.data.status;

import java.util.Map;

/**
 * Status保存策略接口
 */
public interface StatusStorageStrategy {
    /**
     * 保存状态
     * @param status 状态数据
     * @param jobCode 任务实例ID
     * @return 是否保存成功
     */
    boolean saveStatus(Map<String, Object> status, String jobCode);

    /**
     * 加载状态
     * @param jobCode 任务代码
     * @return 状态数据
     */
    Map<String, Object> loadStatus(String jobCode);

    /**
     * 获取策略名称
     * @return 策略名称
     */
    String getStrategyName();
}
