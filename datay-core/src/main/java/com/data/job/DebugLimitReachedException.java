package com.data.job;

/**
 * 调试模式下源组件达到采样数据量上限时抛出的特殊异常。
 * 该异常用于中断源组件的读取循环，不作为任务失败处理。
 */
public class DebugLimitReachedException extends RuntimeException {

    public DebugLimitReachedException(String message) {
        super(message);
    }
}
