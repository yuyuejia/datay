package com.data.metadata;

import com.data.metadata.ColumnMeta;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TableMeta {

    private String dbType;
    private String catalog;
    private String schema;
    private String table;
    private String comment;
    private List<ColumnMeta> columns;
    private List<IndexMeta> indexes = new ArrayList<>();

    public TableMeta() {
        columns = new ArrayList<>();
    }

    public TableMeta(String table, List<ColumnMeta> columns) {
        this.table = table;
        this.columns = columns;
    }

    public TableMeta(String schema, String table) {
        this.schema = schema;
        this.table = table;
    }

    public TableMeta(String table) {
        this.table = table;
        columns = new LinkedList<>();
    }

    public List<ColumnMeta> columns() {
        return columns;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void addColumn(ColumnMeta columnMeta) {
        columns.add(columnMeta);
    }

    public void setColumns(List<ColumnMeta> columns) {
        this.columns = columns;
    }

    public List<IndexMeta> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<IndexMeta> indexes) {
        this.indexes = indexes;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TableMeta{\n");
        sb.append("  table='").append(getTable()).append("'\n");
        sb.append("  columns=[\n");
        for (ColumnMeta col : columns()) {
            sb
                .append("    {name='")
                .append(col.getName())
                .append("', type='")
                .append(col.getType())
                .append("', length=")
                .append(col.getLength())
                .append(", precision=")
                .append(col.getPrecision())
                .append(", scale=")
                .append(col.getScale())
                .append(", defaultValue=")
                .append(col.getDefaultValue())
                .append("},\n");
        }
        sb.append("  ]\n}");
        return sb.toString();
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }
}
