package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.Objects;

/**
 * 单个表对的比对配置。
 */
public class DataCompareTableDTO implements Serializable {

    private String leftTable;

    private String rightTable;

    private String leftWhere;

    private String rightWhere;

    private String keyColumns;

    private String compareColumns;

    public String getLeftTable() {
        return leftTable;
    }

    public void setLeftTable(String leftTable) {
        this.leftTable = leftTable;
    }

    public String getRightTable() {
        return rightTable;
    }

    public void setRightTable(String rightTable) {
        this.rightTable = rightTable;
    }

    public String getLeftWhere() {
        return leftWhere;
    }

    public void setLeftWhere(String leftWhere) {
        this.leftWhere = leftWhere;
    }

    public String getRightWhere() {
        return rightWhere;
    }

    public void setRightWhere(String rightWhere) {
        this.rightWhere = rightWhere;
    }

    public String getKeyColumns() {
        return keyColumns;
    }

    public void setKeyColumns(String keyColumns) {
        this.keyColumns = keyColumns;
    }

    public String getCompareColumns() {
        return compareColumns;
    }

    public void setCompareColumns(String compareColumns) {
        this.compareColumns = compareColumns;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataCompareTableDTO)) {
            return false;
        }
        DataCompareTableDTO that = (DataCompareTableDTO) o;
        return Objects.equals(leftTable, that.leftTable) && Objects.equals(rightTable, that.rightTable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leftTable, rightTable);
    }
}
