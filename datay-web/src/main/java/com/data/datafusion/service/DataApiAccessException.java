package com.data.datafusion.service;

/**
 * 数据服务对外调用错误。{@code status} 为期望返回给第三方的 HTTP 状态码。
 */
public class DataApiAccessException extends RuntimeException {

    private final int status;

    public DataApiAccessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
