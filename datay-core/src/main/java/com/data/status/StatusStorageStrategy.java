package com.data.status;

import java.util.List;
import java.util.Map;

/**
 * 状态保存策略接口。
 *
 * <p>ETL 任务中的有状态组件（如 StreamJdbcInput、CDC 组件）会把增量水位、binlog 位点等
 * 写入状态存储，任务下次启动时再读取恢复。状态以任务编码（jobCode）为维度保存。
 */
public interface StatusStorageStrategy {
    /**
     * 保存状态（整体覆盖）。
     *
     * @param status  状态数据
     * @param jobCode 任务编码
     * @return 是否保存成功
     */
    boolean saveStatus(Map<String, Object> status, String jobCode);

    /**
     * 加载状态。
     *
     * @param jobCode 任务编码
     * @return 状态数据，不存在时返回 null
     */
    Map<String, Object> loadStatus(String jobCode);

    /**
     * 删除某个任务的全部状态。
     *
     * @param jobCode 任务编码
     * @return 是否删除成功（状态本就不存在时视为成功）
     */
    boolean deleteStatus(String jobCode);

    /**
     * 列出所有已保存状态的任务编码。
     *
     * @return 任务编码列表，无数据时返回空列表
     */
    List<String> listJobCodes();

    /**
     * 获取策略名称。
     *
     * @return 策略名称
     */
    String getStrategyName();
}
