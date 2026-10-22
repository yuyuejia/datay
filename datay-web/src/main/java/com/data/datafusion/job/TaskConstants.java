package com.data.datafusion.job;

/**
 * Application constants.
 */
public final class TaskConstants {

    public static final String TASK_PROPERTIES = "TASK_PROPERTIES";

    public static final String TASK_TYPE_DEMO = "DEMO";

    public static final String TASK_TYPE_SHELL = "SHELL";

    public static final String TASK_TYPE_SQL = "SQL";

    public static final String TASK_TYPE_SPARK = "SPARK";

    public static final String TASK_TYPE_FLINK = "FLINK";

    public static final String TASK_TYPE_DAG = "DAG";

    public static final String TASK_TYPE_ETL = "ETL";

    public static final String TASK_STATUS_APPENDING = "APPENDING";
    public static final String TASK_STATUS_RUNNING = "RUNNING";
    public static final String TASK_STATUS_WAITING = "WAITING";
    public static final String TASK_STATUS_SUCCESSFUL = "SUCCESSFUL";
    public static final String TASK_STATUS_FAILED = "FAILED";
    public static final String TASK_STATUS_TIMEOUT = "TIMEOUT";

    public static final String TASK_STATUS_ONLINE = "ONLINE";
    public static final String TASK_STATUS_OFFLINE = "OFFLINE";
    public static final String TASK_STATUS_INTERRUPTED = "INTERRUPTED";

    /** 任务最大自动重试次数（首次执行之外）。 */
    public static final int MAX_RETRY_COUNT = 10;

    /** 任务超时时间上限：7 天（秒）。 */
    public static final int MAX_TIMEOUT_SECONDS = 7 * 24 * 60 * 60;

    /** 任务优先级：低。 */
    public static final int PRIORITY_LOW = 1;

    /** 任务优先级：中（默认）。 */
    public static final int PRIORITY_NORMAL = 2;

    /** 任务优先级：高。 */
    public static final int PRIORITY_HIGH = 3;

    /** 默认任务优先级。 */
    public static final int DEFAULT_PRIORITY = PRIORITY_NORMAL;

    /** 任务优先级最小值。 */
    public static final int MIN_PRIORITY = PRIORITY_LOW;

    /** 任务优先级最大值。 */
    public static final int MAX_PRIORITY = PRIORITY_HIGH;

    /** 判断优先级是否在合法范围内。 */
    public static boolean isValidPriority(Integer priority) {
        return priority != null && priority >= MIN_PRIORITY && priority <= MAX_PRIORITY;
    }

    private TaskConstants() {}
}
