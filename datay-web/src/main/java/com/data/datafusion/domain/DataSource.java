package com.data.datafusion.domain;

import com.data.datafusion.config.StringMapConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * A DataSource.
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_datasource")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataSource implements Serializable, TenantAware {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "type")
    private String type;

    @Column(name = "url")
    private String url;

    @Column(name = "hostname")
    private String hostname;

    @Column(name = "port")
    private String port;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "tenant_id")
    private String tenantId;

    @Convert(converter = StringMapConverter.class)
    @Column(name = "extra_params", columnDefinition = "TEXT")
    private Map<String, String> extraParams;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public DataSource id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public DataSource name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public DataSource description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return this.type;
    }

    public DataSource type(String type) {
        this.setType(type);
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return this.url;
    }

    public DataSource url(String url) {
        this.setUrl(url);
        return this;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHostname() {
        return this.hostname;
    }

    public DataSource hostname(String hostname) {
        this.setHostname(hostname);
        return this;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPort() {
        return this.port;
    }

    public DataSource port(String port) {
        this.setPort(port);
        return this;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getSchemaName() {
        return this.schemaName;
    }

    public DataSource schemaName(String schemaName) {
        this.setSchemaName(schemaName);
        return this;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getUsername() {
        return this.username;
    }

    public DataSource username(String username) {
        this.setUsername(username);
        return this;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public DataSource password(String password) {
        this.setPassword(password);
        return this;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public ZonedDateTime getUpdateTime() {
        return this.updateTime;
    }

    public DataSource updateTime(ZonedDateTime updateTime) {
        this.setUpdateTime(updateTime);
        return this;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public ZonedDateTime getCreateTime() {
        return this.createTime;
    }

    public DataSource createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public DataSource tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Map<String, String> getExtraParams() {
        return this.extraParams;
    }

    public DataSource extraParams(Map<String, String> extraParams) {
        this.setExtraParams(extraParams);
        return this;
    }

    public void setExtraParams(Map<String, String> extraParams) {
        this.extraParams = extraParams;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataSource)) {
            return false;
        }
        return getId() != null && getId().equals(((DataSource) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DataSource{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", type='" + getType() + "'" +
            ", url='" + getUrl() + "'" +
            ", hostname='" + getHostname() + "'" +
            ", port='" + getPort() + "'" +
            ", schemaName='" + getSchemaName() + "'" +
            ", username='" + getUsername() + "'" +
            ", password='" + getPassword() + "'" +
            ", updateTime='" + getUpdateTime() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", tenantId='" + getTenantId() + "'" +
            ", extraParams='" + getExtraParams() + "'" +
            "}";
    }
}