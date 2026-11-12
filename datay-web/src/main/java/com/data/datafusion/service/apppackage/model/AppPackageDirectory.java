package com.data.datafusion.service.apppackage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 资产包中的目录条目，数据模型目录、指标目录与 ETL 任务目录共用（结构一致）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppPackageDirectory {

    /** 包内逻辑 ID。 */
    public String oldId;

    public String name;

    /** 父目录的包内逻辑 ID，根目录为 null。 */
    public String parentOldId;

    public Integer sortOrder;

    /** ETL 任务目录专用：该目录归属的任务类型 BATCH / STREAM。 */
    public String taskType;
}
