package com.data.datafusion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 数据服务：将数据源的数据（整表或自定义 SQL）以 API 形式暴露给第三方系统。
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_data_api")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataApi implements Serializable, TenantAware {

    /** 数据来源类型：选择数据源的数据表。 */
    public static final String SOURCE_TYPE_TABLE = "TABLE";

    /** 数据来源类型：自定义 SQL。 */
    public static final String SOURCE_TYPE_SQL = "SQL";

    /** 数据来源类型：注册已存在的 HTTP API（并以数据服务形式对外代理）。 */
    public static final String SOURCE_TYPE_API = "API";

    /** 服务状态：启用（可对外访问）。 */
    public static final String STATUS_ENABLED = "ENABLED";

    /** 服务状态：停用（对外访问将被拒绝）。 */
    public static final String STATUS_DISABLED = "DISABLED";

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "data_source_id")
    private Long dataSourceId;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "sql_text")
    private String sqlText;

    @Column(name = "api_config")
    private String apiConfig;

    @Column(name = "status")
    private String status;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    public Long getId() {
        return this.id;
    }

    public DataApi id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public DataApi name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return this.code;
    }

    public DataApi code(String code) {
        this.setCode(code);
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return this.description;
    }

    public DataApi description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getDataSourceId() {
        return this.dataSourceId;
    }

    public DataApi dataSourceId(Long dataSourceId) {
        this.setDataSourceId(dataSourceId);
        return this;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getSourceType() {
        return this.sourceType;
    }

    public DataApi sourceType(String sourceType) {
        this.setSourceType(sourceType);
        return this;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSchemaName() {
        return this.schemaName;
    }

    public DataApi schemaName(String schemaName) {
        this.setSchemaName(schemaName);
        return this;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return this.tableName;
    }

    public DataApi tableName(String tableName) {
        this.setTableName(tableName);
        return this;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSqlText() {
        return this.sqlText;
    }

    public DataApi sqlText(String sqlText) {
        this.setSqlText(sqlText);
        return this;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public String getApiConfig() {
        return this.apiConfig;
    }

    public DataApi apiConfig(String apiConfig) {
        this.setApiConfig(apiConfig);
        return this;
    }

    public void setApiConfig(String apiConfig) {
        this.apiConfig = apiConfig;
    }

    public String getStatus() {
        return this.status;
    }

    public DataApi status(String status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public DataApi tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public DataApi createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public DataApi updateTime(ZonedDateTime updateTime) {
        this.setUpdateTime(updateTime);
        return this;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataApi)) {
            return false;
        }
        return getId() != null && getId().equals(((DataApi) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "DataApi{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", code='" + getCode() + "'" +
            ", description='" + getDescription() + "'" +
            ", dataSourceId=" + getDataSourceId() +
            ", sourceType='" + getSourceType() + "'" +
            ", schemaName='" + getSchemaName() + "'" +
            ", tableName='" + getTableName() + "'" +
            ", status='" + getStatus() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            "}";
    }
}
