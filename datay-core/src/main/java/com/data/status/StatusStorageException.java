package com.data.status;

/**
 * 状态存储读取/写入异常。
 *
 * <p>调用方可通过 {@link #getReason()} 区分“没有状态”之外的真实故障类型，
 * 例如读取失败、内容为空或内容非法，从而决定是否让任务失败。</p>
 */
public class StatusStorageException extends RuntimeException {

    public enum Reason {
        /** 状态存储读取失败，例如 IO 错误、权限不足、网络异常等。 */
        READ_FAILED,
        /** 状态文件/对象存在，但内容为 0 字节或纯空白。 */
        EMPTY_CONTENT,
        /** 状态内容不是合法的 JSON，或解析结果不是有效的状态对象。 */
        INVALID_CONTENT,
        /** 状态存储服务不可用，例如认证失败、bucket 不存在等。 */
        STORAGE_UNAVAILABLE
    }

    private final Reason reason;

    public StatusStorageException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public StatusStorageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
