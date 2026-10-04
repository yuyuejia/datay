package com.data.datafusion.service.apppackage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 资产包中的任务依赖条目，用于「任务定义」里的父子依赖调度。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppPackageJobDepend {

    public Long oldId;

    /** 父任务的包内逻辑 ID。 */
    public Long parentJobOldId;

    /** 子任务的包内逻辑 ID。 */
    public Long childJobOldId;

    public Long lastInterval;
}
