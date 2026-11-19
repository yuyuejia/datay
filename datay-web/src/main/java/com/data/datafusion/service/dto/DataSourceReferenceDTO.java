package com.data.datafusion.service.dto;

import java.io.Serializable;

/**
 * 数据源引用信息。删除数据源前用于提示哪些任务、模型或服务仍在使用该数据源。
 */
public class DataSourceReferenceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 引用类型编码，例如 JOB、ETL_TASK、DATA_MODEL。 */
    private String type;

    /** 引用类型名称，用于界面展示。 */
    private String typeLabel;

    /** 引用对象 ID。 */
    private String id;

    /** 引用对象名称。 */
    private String name;

    /** 删除数据源时是否会一并删除该引用对象。 */
    private boolean cascadeDelete;

    public DataSourceReferenceDTO() {}

    public DataSourceReferenceDTO(String type, String typeLabel, String id, String name) {
        this(type, typeLabel, id, name, false);
    }

    public DataSourceReferenceDTO(String type, String typeLabel, String id, String name, boolean cascadeDelete) {
        this.type = type;
        this.typeLabel = typeLabel;
        this.id = id;
        this.name = name;
        this.cascadeDelete = cascadeDelete;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isCascadeDelete() {
        return cascadeDelete;
    }

    public void setCascadeDelete(boolean cascadeDelete) {
        this.cascadeDelete = cascadeDelete;
    }
}
