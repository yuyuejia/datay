package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据服务调用统计：汇总 KPI 与按天趋势。
 */
public class DataApiCallStatsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String apiId;

    private String apiCode;

    private String apiName;

    private LocalDate startDate;

    private LocalDate endDate;

    private long totalCount;

    private long successCount;

    private long failureCount;

    private double successRate;

    private double avgDurationMs;

    private long maxDurationMs;

    private List<TrendPoint> trend = new ArrayList<>();

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(long successCount) {
        this.successCount = successCount;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(long failureCount) {
        this.failureCount = failureCount;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public double getAvgDurationMs() {
        return avgDurationMs;
    }

    public void setAvgDurationMs(double avgDurationMs) {
        this.avgDurationMs = avgDurationMs;
    }

    public long getMaxDurationMs() {
        return maxDurationMs;
    }

    public void setMaxDurationMs(long maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
    }

    public List<TrendPoint> getTrend() {
        return trend;
    }

    public void setTrend(List<TrendPoint> trend) {
        this.trend = trend == null ? new ArrayList<>() : trend;
    }

    /** 单日调用趋势。 */
    public static class TrendPoint implements Serializable {

        private static final long serialVersionUID = 1L;

        private LocalDate date;

        private long totalCount;

        private long successCount;

        private long failureCount;

        private double avgDurationMs;

        private long maxDurationMs;

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public long getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }

        public long getSuccessCount() {
            return successCount;
        }

        public void setSuccessCount(long successCount) {
            this.successCount = successCount;
        }

        public long getFailureCount() {
            return failureCount;
        }

        public void setFailureCount(long failureCount) {
            this.failureCount = failureCount;
        }

        public double getAvgDurationMs() {
            return avgDurationMs;
        }

        public void setAvgDurationMs(double avgDurationMs) {
            this.avgDurationMs = avgDurationMs;
        }

        public long getMaxDurationMs() {
            return maxDurationMs;
        }

        public void setMaxDurationMs(long maxDurationMs) {
            this.maxDurationMs = maxDurationMs;
        }
    }
}
