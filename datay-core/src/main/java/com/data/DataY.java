package com.data;

import cn.hutool.core.io.FileUtil;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import com.data.status.LocalFileStatusStorageStrategy;
import com.data.status.StatusStorageStrategyFactory;

/**
 * DataY 任务执行入口。
 *
 * <p>既可作为命令行工具直接执行任务 JSON（{@code java -jar datay-core.jar job.json}），
 * 也可作为 docker 执行模式的容器入口：通过环境变量注入任务元数据与状态存储配置，
 * 使容器内写出的任务日志、增量状态与 worker 宿主共享（依赖挂载目录）。</p>
 *
 * <p>支持的环境变量：</p>
 * <ul>
 *     <li>{@code DATAY_JOB_CODE} / {@code DATAY_INSTANCE_CODE} / {@code DATAY_PARENT_INSTANCE_CODE}
 *         / {@code DATAY_TENANT_ID}：任务元数据，决定日志文件命名 {@code log/<jobCode>/<instanceCode>.log}</li>
 *     <li>{@code DATAY_STATUS_TYPE}：{@code local}（默认）或 {@code minio}</li>
 *     <li>{@code DATAY_STATUS_LOCAL_BASE_PATH}：local 策略的状态文件根目录</li>
 *     <li>{@code DATAY_STATUS_MINIO_ENDPOINT} / {@code DATAY_STATUS_MINIO_ACCESS_KEY}
 *         / {@code DATAY_STATUS_MINIO_SECRET_KEY} / {@code DATAY_STATUS_MINIO_BUCKET_NAME}：minio 策略配置</li>
 * </ul>
 *
 * <p>进程退出码约定：成功返回 0，失败返回非 0，供上层（docker 模式）判定任务状态。</p>
 */
public class DataY {

    /** 未传入任务文件时使用的默认路径（兼容旧命令行用法）。 */
    private static final String DEFAULT_FILE_PATH = "/Users/datay/src/test/test.json";

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * 执行任务并返回进程退出码：成功 0，失败非 0。
     *
     * @param args 第一个参数为任务 JSON 文件路径；为空时使用默认路径
     * @return 退出码
     */
    static int run(String[] args) {
        String filePath = args.length > 0 ? args[0] : DEFAULT_FILE_PATH;
        if (args.length > 0) {
            System.out.println("使用传入的文件路径: " + filePath);
        } else {
            System.out.println("未传入文件路径，使用默认路径: " + DEFAULT_FILE_PATH);
        }

        try {
            String jobContext = FileUtil.readUtf8String(filePath);
            configureStatusStorage();

            TaskInstance taskInstance = buildTaskInstance(jobContext);
            if (taskInstance == null) {
                // 兼容旧用法：无任务元数据时使用默认执行器（日志写到 test/<时间戳>.log）
                ETLFlowTask runner = new ETLFlowTask();
                runner.runJob(jobContext);
            } else {
                ETLFlowTask runner = new ETLFlowTask(taskInstance, null);
                runner.runJob(taskInstance.getJobContext());
            }
            return 0;
        } catch (Throwable e) {
            System.err.println("作业执行失败: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * 根据环境变量配置状态存储策略，须在构造 {@link ETLFlowTask} 之前调用。
     */
    private static void configureStatusStorage() {
        String type = env("DATAY_STATUS_TYPE", "local");
        if ("minio".equalsIgnoreCase(type)) {
            String endpoint = env("DATAY_STATUS_MINIO_ENDPOINT", "");
            String accessKey = env("DATAY_STATUS_MINIO_ACCESS_KEY", "");
            String secretKey = env("DATAY_STATUS_MINIO_SECRET_KEY", "");
            String bucket = env("DATAY_STATUS_MINIO_BUCKET_NAME", "datay-status");
            if (endpoint.isBlank()) {
                throw new IllegalStateException("状态存储类型为 minio，但未配置 DATAY_STATUS_MINIO_ENDPOINT");
            }
            StatusStorageStrategyFactory.registerMinioStrategy(endpoint, accessKey, secretKey, bucket);
            StatusStorageStrategyFactory.setActiveStrategy(StatusStorageStrategyFactory.getStrategy("minio"));
        } else {
            String basePath = env("DATAY_STATUS_LOCAL_BASE_PATH", null);
            if (basePath == null || basePath.isBlank()) {
                StatusStorageStrategyFactory.setActiveStrategy(new LocalFileStatusStorageStrategy());
            } else {
                StatusStorageStrategyFactory.setActiveStrategy(new LocalFileStatusStorageStrategy(basePath));
            }
        }
    }

    /**
     * 从环境变量构建任务实例；未提供任务编码与实例编码时返回 {@code null}，走默认执行器。
     */
    private static TaskInstance buildTaskInstance(String jobContext) {
        String jobCode = env("DATAY_JOB_CODE", null);
        String instanceCode = env("DATAY_INSTANCE_CODE", null);
        if (isBlank(jobCode) && isBlank(instanceCode)) {
            return null;
        }
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setJobCode(jobCode);
        taskInstance.setInstanceCode(instanceCode);
        taskInstance.setParentInstanceCode(env("DATAY_PARENT_INSTANCE_CODE", null));
        taskInstance.setTenantId(env("DATAY_TENANT_ID", null));
        taskInstance.setType("ETL");
        taskInstance.setJobContext(jobContext);
        return taskInstance;
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return isBlank(value) ? defaultValue : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
