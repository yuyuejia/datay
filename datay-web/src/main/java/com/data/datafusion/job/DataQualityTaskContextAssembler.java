package com.data.datafusion.job;

import com.alibaba.fastjson2.JSONObject;

/**
 * 数据质量任务下发器。
 *
 * <p>质量任务的结果路径包含执行实例编码（{@code {outputDir}/{instanceCode}/quality_summary.csv}）。
 * 定时调度或 DAG 触发时会基于任务保存的 jobContext 复制出新实例，若直接复用上一次手工执行的
 * jobContext，实例元数据中的 {@code summaryPath}/{@code detailDir} 仍指向上一个实例，导致页面、
 * 下载和统计读取旧结果。因此在实例下发前按当前实例编码重写结果路径。</p>
 */
public final class DataQualityTaskContextAssembler {

    private static final String META_KEY = "dataQuality";

    private static final String SUMMARY_FILE = "quality_summary.csv";

    private DataQualityTaskContextAssembler() {}

    /**
     * 将 jobContext 中数据质量结果路径绑定到当前执行实例。
     *
     * @param jobContext   任务上下文 JSON
     * @param instanceCode 当前执行实例编码
     * @return 重写结果路径后的任务上下文；非质量任务或缺少输出目录时原样返回
     */
    public static String assemble(String jobContext, String instanceCode) {
        if (jobContext == null || jobContext.isBlank() || instanceCode == null || instanceCode.isBlank()) {
            return jobContext;
        }
        JSONObject context;
        try {
            context = JSONObject.parseObject(jobContext);
        } catch (Exception e) {
            return jobContext;
        }
        JSONObject metadata = context == null ? null : context.getJSONObject(META_KEY);
        if (metadata == null) {
            return jobContext;
        }
        String outputDir = metadata.getString("outputDir");
        if (outputDir == null || outputDir.isBlank()) {
            return jobContext;
        }
        String instanceDir = joinPath(outputDir, instanceCode);
        String summaryPath = joinPath(instanceDir, SUMMARY_FILE);
        metadata.put("summaryPath", summaryPath);
        metadata.put("resultPath", summaryPath);
        metadata.put("detailDir", instanceDir);
        return context.toJSONString();
    }

    static String joinPath(String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String normalized = part.replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (normalized.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(normalized);
        }
        return result.toString();
    }
}
