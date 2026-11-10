package com.data.job;

import java.io.Serializable;
import java.util.Objects;

/**
 * 组件配置字段的选项元数据。
 */
public class ComponentFieldOptionDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 选项值。 */
    private String value;

    /** 选项显示名称。 */
    private String label;

    /** 选项说明。 */
    private String description;

    public ComponentFieldOptionDescriptor() {}

    public ComponentFieldOptionDescriptor(String value, String label, String description) {
        this.value = value;
        this.label = label;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComponentFieldOptionDescriptor)) {
            return false;
        }
        return Objects.equals(value, ((ComponentFieldOptionDescriptor) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "ComponentFieldOptionDescriptor{" + "value='" + value + '\'' + ", label='" + label + '\'' + '}';
    }
}
