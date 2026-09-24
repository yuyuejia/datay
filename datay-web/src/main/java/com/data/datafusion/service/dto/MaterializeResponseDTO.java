package com.data.datafusion.service.dto;

import java.io.Serializable;

public class MaterializeResponseDTO implements Serializable {

    private boolean success;
    private String message;
    private String ddl;
    private boolean tableExists;

    /** 时间维度生成预置数据的行数，未生成时为 null。 */
    private Integer dataRows;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDdl() {
        return ddl;
    }

    public void setDdl(String ddl) {
        this.ddl = ddl;
    }

    public boolean isTableExists() {
        return tableExists;
    }

    public void setTableExists(boolean tableExists) {
        this.tableExists = tableExists;
    }

    public Integer getDataRows() {
        return dataRows;
    }

    public void setDataRows(Integer dataRows) {
        this.dataRows = dataRows;
    }
}