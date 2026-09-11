package com.data.job;

/**
 * 异常信息处理工具，用于把异常链上的关键信息拼接成便于阅读的提示。
 */
public final class ExceptionUtils {

    private ExceptionUtils() {}

    /**
     * 沿异常链收集每个异常的消息（去重），例如：
     * "数据迁移失败 -> sql query parameter cannot be null"。
     */
    public static String describe(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 20) {
            String message = current.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = current.getClass().getSimpleName();
            }
            if (builder.indexOf(message) < 0) {
                if (builder.length() > 0) {
                    builder.append(" -> ");
                }
                builder.append(message);
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    /**
     * 取异常链中最底层的原因消息。
     */
    public static String rootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        Throwable current = throwable;
        Throwable last = throwable;
        int depth = 0;
        while (current != null && depth++ < 20) {
            last = current;
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        String message = last.getMessage();
        return message == null || message.trim().isEmpty() ? last.getClass().getSimpleName() : message;
    }
}
