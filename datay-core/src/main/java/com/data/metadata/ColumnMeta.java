package com.data.metadata;

public class ColumnMeta {

    private String name;
    private String comment;
    private String type;
    private int length;

    private String defaultValue;
    private boolean nullable = true;

    private int precision;
    private int scale;

    /**
     * 源库原始类型定义，例如 MySQL 的 {@code enum('a','b')} / {@code set('x','y')}。
     * 用于 CDC 解析 Binlog 中的枚举下标、集合位图，其它场景可为空。
     */
    private String typeDefinition;

    private boolean isPrimaryKey;

    public void setPrimaryKey(boolean isPrimaryKey) {
        this.isPrimaryKey = isPrimaryKey;
    }

    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    public String getTypeDefinition() {
        return typeDefinition;
    }

    public void setTypeDefinition(String typeDefinition) {
        this.typeDefinition = typeDefinition;
    }

    public ColumnMeta(String name, String type, int length, int precision, int scale) {
        this.name = name;
        this.type = type;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
    }

    public ColumnMeta(String id, String type) {
        this.name = id;
        this.type = type;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDefaultValue() {
        return defaultValue;
    }
}
