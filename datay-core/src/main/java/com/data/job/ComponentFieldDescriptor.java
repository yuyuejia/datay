package com.data.job;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ETL 组件配置字段元数据。
 * <p>
 * 由 {@link ComponentMetadataExtractor} 根据组件字段上的 {@link ComponentField} 注解生成，
 * 随 {@link ComponentDescriptor} 一起对外暴露，供前端动态渲染配置表单。
 */
public class ComponentFieldDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 config 中的配置 key。 */
    private String key;

    /** 字段显示名称。 */
    private String label;

    /** 前端控件类型，取值为 {@link ComponentFieldType} 的名称。 */
    private String type;

    /** 表单分组。 */
    private String group;

    /** 同组内排序值。 */
    private int order;

    /** 是否必填。 */
    private boolean required;

    /** 输入框占位提示。 */
    private String placeholder;

    /** 字段帮助说明。 */
    private String help;

    /** 正则校验表达式。 */
    private String pattern;

    /** 正则校验失败提示。 */
    private String patternMessage;

    /** 默认值。 */
    private String defaultValue;

    /** 选项列表。 */
    private List<ComponentFieldOptionDescriptor> options = new ArrayList<>();

    /** 是否多选。 */
    private boolean multiple;

    /** 是否为敏感字段。 */
    private boolean secret;

    /** 值格式，取值为 {@link ComponentFieldValueFormat} 的名称。 */
    private String valueFormat;

    /** 显隐依赖的配置 key。 */
    private String dependsOn;

    /** 依赖值命中时显示。 */
    private List<String> visibleWhen = new ArrayList<>();

    /** 数据源类型过滤。 */
    private String dataSourceType;

    /** 关联的数据源配置 key。 */
    private String dataSourceField;

    /** 关联的 Schema 配置 key。 */
    private String schemaField;

    /** 关联的表配置 key。 */
    private String tableField;

    /** 数字最小值。 */
    private Double min;

    /** 数字最大值。 */
    private Double max;

    /** 数字步长。 */
    private Double step;

    /** 自定义字段组件名。 */
    private String customComponent;

    public ComponentFieldDescriptor() {}

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getHelp() {
        return help;
    }

    public void setHelp(String help) {
        this.help = help;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getPatternMessage() {
        return patternMessage;
    }

    public void setPatternMessage(String patternMessage) {
        this.patternMessage = patternMessage;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public List<ComponentFieldOptionDescriptor> getOptions() {
        return options;
    }

    public void setOptions(List<ComponentFieldOptionDescriptor> options) {
        this.options = options == null ? new ArrayList<>() : options;
    }

    public boolean isMultiple() {
        return multiple;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }

    public boolean isSecret() {
        return secret;
    }

    public void setSecret(boolean secret) {
        this.secret = secret;
    }

    public String getValueFormat() {
        return valueFormat;
    }

    public void setValueFormat(String valueFormat) {
        this.valueFormat = valueFormat;
    }

    public String getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(String dependsOn) {
        this.dependsOn = dependsOn;
    }

    public List<String> getVisibleWhen() {
        return visibleWhen;
    }

    public void setVisibleWhen(List<String> visibleWhen) {
        this.visibleWhen = visibleWhen == null ? new ArrayList<>() : visibleWhen;
    }

    public String getDataSourceType() {
        return dataSourceType;
    }

    public void setDataSourceType(String dataSourceType) {
        this.dataSourceType = dataSourceType;
    }

    public String getDataSourceField() {
        return dataSourceField;
    }

    public void setDataSourceField(String dataSourceField) {
        this.dataSourceField = dataSourceField;
    }

    public String getSchemaField() {
        return schemaField;
    }

    public void setSchemaField(String schemaField) {
        this.schemaField = schemaField;
    }

    public String getTableField() {
        return tableField;
    }

    public void setTableField(String tableField) {
        this.tableField = tableField;
    }

    public Double getMin() {
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    public Double getStep() {
        return step;
    }

    public void setStep(Double step) {
        this.step = step;
    }

    public String getCustomComponent() {
        return customComponent;
    }

    public void setCustomComponent(String customComponent) {
        this.customComponent = customComponent;
    }

    @Override
    public String toString() {
        return "ComponentFieldDescriptor{" + "key='" + key + '\'' + ", label='" + label + '\'' + ", type='" + type + '\'' + '}';
    }
}
