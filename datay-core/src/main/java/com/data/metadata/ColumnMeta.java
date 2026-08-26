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

    private boolean isPrimaryKey;

    public void setPrimaryKey(boolean isPrimaryKey) {
        this.isPrimaryKey = isPrimaryKey;
    }

    public boolean isPrimaryKey() {
        return isPrimaryKey;
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
