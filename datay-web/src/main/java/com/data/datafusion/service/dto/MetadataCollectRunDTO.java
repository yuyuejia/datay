package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * 元数据采集任务的一次执行结果。
 */
public class MetadataCollectRunDTO implements Serializable {

    private String id;

    private String instanceCode;

    private String status;

    private ZonedDateTime startTime;

    private ZonedDateTime endTime;

    private ZonedDateTime createTime;

    private String jobMessage;

    private String outputDir;

    private String tablesPath;

    private String columnsPath;

    private String tablesDownloadUrl;

    private String columnsDownloadUrl;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInstanceCode() {
        return instanceCode;
    }

    public void setInstanceCode(String instanceCode) {
        this.instanceCode = instanceCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(ZonedDateTime startTime) {
        this.startTime = startTime;
    }

    public ZonedDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(ZonedDateTime endTime) {
        this.endTime = endTime;
    }

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public String getJobMessage() {
        return jobMessage;
    }

    public void setJobMessage(String jobMessage) {
        this.jobMessage = jobMessage;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getTablesPath() {
        return tablesPath;
    }

    public void setTablesPath(String tablesPath) {
        this.tablesPath = tablesPath;
    }

    public String getColumnsPath() {
        return columnsPath;
    }

    public void setColumnsPath(String columnsPath) {
        this.columnsPath = columnsPath;
    }

    public String getTablesDownloadUrl() {
        return tablesDownloadUrl;
    }

    public void setTablesDownloadUrl(String tablesDownloadUrl) {
        this.tablesDownloadUrl = tablesDownloadUrl;
    }

    public String getColumnsDownloadUrl() {
        return columnsDownloadUrl;
    }

    public void setColumnsDownloadUrl(String columnsDownloadUrl) {
        this.columnsDownloadUrl = columnsDownloadUrl;
    }
}
