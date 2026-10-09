package com.data.datafusion.service.apppackage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 资产包中的普通任务条目，用于 SQL 任务与编排任务（DAG）。
 *
 * <p>{@code jobContext} 以结构化 JSON 保存：SQL 任务形如
 * {@code {"dataSourceId":1,"schema":"main","sql":"..."}}；编排任务形如
 * {@code {"jobs":[{"id":1}],"jobDepends":[...],"nodeLayout":[...]}}。
 * 导入时会重写其中的数据源 ID / 子任务 ID。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppPackageJob {

    /** 包内逻辑 ID（Job ID）。 */
    public String oldId;

    public String jobName;

    public String jobGroup;

    public String type;

    public String cron;

    public JsonNode jobContext;

    public String status;

    public String project;
}
