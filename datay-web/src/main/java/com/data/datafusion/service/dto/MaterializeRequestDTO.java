package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.List;

public class MaterializeRequestDTO implements Serializable {

    private Long dataSourceId;
    private String schemaName;
    private String tableName;
    private Boolean overwrite;
    private List<MaterializeFieldDTO> fields;

    /** 时间维度：物化成功后是否生成预置数据。 */
    private Boolean generateData;

    /** 时间维度预置数据起始日期（yyyy-MM-dd）。 */
    private String dataStart;

    /** 时间维度预置数据结束日期（yyyy-MM-dd）。 */
    private String dataEnd;

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Boolean getOverwrite() {
        return overwrite;
    }

    public void setOverwrite(Boolean overwrite) {
        this.overwrite = overwrite;
    }

    public List<MaterializeFieldDTO> getFields() {
        return fields;
    }

    public void setFields(List<MaterializeFieldDTO> fields) {
        this.fields = fields;
    }

    public Boolean getGenerateData() {
        return generateData;
    }

    public void setGenerateData(Boolean generateData) {
        this.generateData = generateData;
    }

    public String getDataStart() {
        return dataStart;
    }

    public void setDataStart(String dataStart) {
        this.dataStart = dataStart;
    }

    public String getDataEnd() {
        return dataEnd;
    }

    public void setDataEnd(String dataEnd) {
        this.dataEnd = dataEnd;
    }
}