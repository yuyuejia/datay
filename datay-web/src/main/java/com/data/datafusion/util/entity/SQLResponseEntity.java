package com.data.datafusion.util.entity;

import java.io.Serializable;

public class SQLResponseEntity<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int resultCode;
    private String message;
    private T data;

    public SQLResponseEntity(int resultCode, String message, T data) {
        this.resultCode = resultCode;
        this.message = message;
        this.data = data;
    }

    public SQLResponseEntity(int resultCode, String message) {
        this.resultCode = resultCode;
        this.message = message;
    }

    public SQLResponseEntity() {}

    public int getResultCode() {
        return resultCode;
    }

    public void setResultCode(int resultCode) {
        this.resultCode = resultCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
