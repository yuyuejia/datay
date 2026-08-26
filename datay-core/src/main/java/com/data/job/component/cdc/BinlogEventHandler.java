package com.data.job.component.cdc;

/**
 * Binlog事件处理器接口
 * 用于处理从MySQL Binlog中解析出的事件
 */
public interface BinlogEventHandler {
    /**
     * 处理Binlog事件
     * @param event Binlog事件
     */
    void handleEvent(BinlogEvent event);

    /**
     * 处理异常
     * @param event 发生异常的事件
     * @param exception 异常信息
     */
    void handleError(BinlogEvent event, Exception exception);
}
