package com.data.job.component.cdc;

/**
 * CDC 采集器在连续重连耗尽后抛出的连接异常。
 *
 * <p>该异常用于区分「数据源连接失败」和普通配置/数据错误：连接失败不允许被
 * {@code ignoreError} 吞掉，必须向上传播，使 ETL 任务状态变为 {@code FAILED}。
 */
public class CdcConnectionException extends RuntimeException {

    public CdcConnectionException(String message) {
        super(message);
    }

    public CdcConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
