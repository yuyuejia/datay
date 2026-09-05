package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.List;

public class MaterializeRequestDTO implements Serializable {

    private Long dataSourceId;
    private String schemaName;
    private String tableName;
    private Boolean overwrite;
    private List<MaterializeFieldDTO> fields;

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
}