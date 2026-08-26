package com.data.datafusion.service.jobevent;

import com.data.datafusion.domain.JobInstance;
import java.io.Serializable;

public class JobStatusEvent implements Serializable {

    private JobInstance jobInstance;

    private String status;

    private Long startTime;

    private Long endTime;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public JobInstance getJobInstance() {
        return jobInstance;
    }

    public void setJobInstance(JobInstance jobInstance) {
        this.jobInstance = jobInstance;
    }
}
