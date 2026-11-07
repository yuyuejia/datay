package com.data.datafusion.service.apppackage.model;

import com.data.datafusion.service.dto.DataQualityRuleBindingDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * 资产包中的数据质量检核任务。
 *
 * <p>{@code dataSourceId} 为包内数据源逻辑 ID，导入时按数据源映射表重写为新租户的真实 ID。
 * 规则使用完整快照，不依赖目标租户的规则库。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppPackageDataQualityTask {

    /** 包内逻辑 ID（任务 ID）。 */
    public String oldId;

    public String taskName;

    public String taskDesc;

    /** 包内数据源逻辑 ID。 */
    public String dataSourceId;

    public String schemaName;

    public String tableName;

    public String whereClause;

    public Boolean failOnWarn;

    public Integer sampleLimit;

    public String outputFormat;

    public String outputDir;

    public List<DataQualityRuleBindingDTO> rules = new ArrayList<>();
}
