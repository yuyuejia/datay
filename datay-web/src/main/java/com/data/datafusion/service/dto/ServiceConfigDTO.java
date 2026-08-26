package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.ServiceConfig} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ServiceConfigDTO implements Serializable {

    private Long id;

    private String dfGroup;

    private String dfKey;

    private String dfValue;

    private ZonedDateTime createTime;

    private String ytenantId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDfGroup() {
        return dfGroup;
    }

    public void setDfGroup(String dfGroup) {
        this.dfGroup = dfGroup;
    }

    public String getDfKey() {
        return dfKey;
    }

    public void setDfKey(String dfKey) {
        this.dfKey = dfKey;
    }

    public String getDfValue() {
        return dfValue;
    }

    public void setDfValue(String dfValue) {
        this.dfValue = dfValue;
    }

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getYtenantId() {
        return ytenantId;
    }

    public void setYtenantId(String ytenantId) {
        this.ytenantId = ytenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceConfigDTO)) {
            return false;
        }

        ServiceConfigDTO serviceConfigDTO = (ServiceConfigDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, serviceConfigDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ServiceConfigDTO{" +
            "id=" + getId() +
            ", dfGroup='" + getDfGroup() + "'" +
            ", dfKey='" + getDfKey() + "'" +
            ", dfValue='" + getDfValue() + "'" +
            ", createTime='" + getCreateTime() + "'" +
            ", ytenantId='" + getYtenantId() + "'" +
            "}";
    }
}
