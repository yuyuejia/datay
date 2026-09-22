package com.data.job;

import java.io.Serializable;
import java.util.Objects;

/**
 * ETL 组件元数据描述。
 * <p>
 * 由 {@link ComponentFactory} 扫描组件实现上的 {@link ComponentRegister} 注解自动生成，
 * 用于向前端组件面板提供组件的编码、名称、分类、描述等信息。
 */
public class ComponentDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 组件编码（对应 ComponentFactory 注册名）。 */
    private String code;

    /** 组件显示名称。 */
    private String name;

    /** 组件分类。 */
    private String group;

    /** 组件描述。 */
    private String desc;

    /** 组件实现类全限定名。 */
    private String className;

    /** 同分类内排序值。 */
    private int order;

    public ComponentDescriptor() {}

    public ComponentDescriptor(String code, String name, String group, String desc, String className, int order) {
        this.code = code;
        this.name = name;
        this.group = group;
        this.desc = desc;
        this.className = className;
        this.order = order;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComponentDescriptor)) {
            return false;
        }
        return Objects.equals(code, ((ComponentDescriptor) o).code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public String toString() {
        return "ComponentDescriptor{" +
            "code='" + code + '\'' +
            ", name='" + name + '\'' +
            ", group='" + group + '\'' +
            ", desc='" + desc + '\'' +
            ", className='" + className + '\'' +
            ", order=" + order +
            '}';
    }
}
