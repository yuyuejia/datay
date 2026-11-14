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

    /**
     * 强制刷新缓冲区（用于事务提交、程序退出或异常情况）。
     * <p>默认无缓冲实现，具体处理器可覆盖。
     */
    default void flush() {}

    /**
     * 推进并持久化采集位点。
     *
     * <p>SQL Server 等以「变更窗口」为单位采集的实现，需要在一个窗口内所有表都处理完成后
     * 才推进位点，避免同一事务修改多张表时某张表尚未发出但位点已前移导致丢数据。
     * 默认实现为空，具体处理器可覆盖。
     *
     * @param position 位点值（如 SQL Server 的十六进制 LSN）
     */
    default void checkpoint(String position) {}
}
