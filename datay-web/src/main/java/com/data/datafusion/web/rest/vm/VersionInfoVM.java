package com.data.datafusion.web.rest.vm;

/**
 * 版本信息视图对象。
 *
 * @param application 应用名称
 * @param version 应用版本号
 * @param artifact 构建产物名称
 * @param group 构建产物分组
 * @param buildTime 构建时间（ISO-8601）
 * @param gitCommit Git 提交短哈希
 * @param gitBranch Git 分支
 * @param gitTag Git 描述（通常为最近的标签）
 * @param javaVersion 运行时 Java 版本
 */
public record VersionInfoVM(
    String application,
    String version,
    String artifact,
    String group,
    String buildTime,
    String gitCommit,
    String gitBranch,
    String gitTag,
    String javaVersion
) {}
