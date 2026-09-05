package com.data.datafusion.service.dto;

import java.io.Serializable;

public class MaterializeResponseDTO implements Serializable {

    private boolean success;
    private String message;
    private String ddl;
    private boolean tableExists;

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
}