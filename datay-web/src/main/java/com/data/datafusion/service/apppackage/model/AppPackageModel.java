package com.data.datafusion.service.apppackage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * 资产包中的数据模型条目（含字段定义）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppPackageModel {

    /** 包内逻辑 ID。 */
    public String oldId;

    /** 关联数据源的包内逻辑 ID。 */
    public String dataSourceOldId;

    /** 所属模型目录的包内逻辑 ID。 */
    public String directoryOldId;

    public String name;

    public String code;

    public String description;

    public String modelType;

    public String dimensionKind;

    public Integer levelCount;

    public String timeFieldName;

    public String timeLevels;

    public String timeStart;

    public String timeEnd;

    public Boolean isRegistered;

    public String project;

    public String schemaName;

    public String tableName;

    public String displayFieldName;

    public List<AppPackageModelField> fields = new ArrayList<>();

    /** 资产包中的数据模型字段条目。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppPackageModelField {

        /** 包内逻辑 ID。 */
        public String oldId;

        public String fieldName;

        public String fieldType;

        public Integer fieldLength;

        public Integer fieldPrecision;

        public Integer fieldScale;

        public String description;

        public Integer sortOrder;

        public Boolean isPartitionKey;

        public Boolean isPrimaryKey;

        /** 关联维度模型的包内逻辑 ID。 */
        public String dimensionModelOldId;

        /** 关联维度字段的包内逻辑 ID。 */
        public String dimensionFieldOldId;

        public String fieldRole;

        public Integer levelIndex;
    }
}
