package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据应用初始化请求。
 *
 * <p>两种用法：按市场中已有资产包初始化（传 {@link #packageId}），
 * 或直接上传一份资产包 JSON 初始化（传 {@link #content}）。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AppPackageInitRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 市场中资产包 ID，与 {@link #content} 二选一。 */
    public String packageId;

    /** 资产包 JSON 内容，与 {@link #packageId} 二选一。 */
    public String content;

    /**
     * 冲突策略：OVERWRITE 覆盖更新已存在的同编码 / 同名资产（默认，用于资产包升级后的覆盖安装） /
     * RENAME 自动重命名 / SKIP 跳过已存在的同编码资产。
     */
    public String conflictStrategy;

    /**
     * 数据源绑定覆盖：键为资产包内数据源逻辑键（{@code name|type|url}）或包内逻辑 ID 字符串，
     * 值为目标租户已有数据源 ID。命中的条目直接复用指定数据源，不再按名称自动匹配或新建。
     */
    public Map<String, String> dataSourceMapping = new LinkedHashMap<>();

    /** 初始化后是否把导入的任务置为上线（默认 false，仅导入为离线，避免误调度）。 */
    public Boolean onlineJobs = Boolean.FALSE;
}
