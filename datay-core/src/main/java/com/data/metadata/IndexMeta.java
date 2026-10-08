package com.data.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * 表索引元数据。
 */
public class IndexMeta {

    private String name;
    private List<String> columns = new ArrayList<>();
    private boolean unique;
    private String type;

    public IndexMeta() {}

    public IndexMeta(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public void addColumn(String column) {
        if (column != null) {
            this.columns.add(column);
        }
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
