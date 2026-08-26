package com.data.datafusion.service.jobevent;

import com.data.datafusion.domain.Job;

public interface IEventService {
    void pushStartJobEvent(JobStatusEvent startJobEvent);

    JobStatusEvent takeStartJobEvent();

    void pushJobStatusEvent(JobStatusEvent jobStatusEvent);

    JobStatusEvent takeJobStatusEvent();

    void addJob(Job job);

    void updateJob(Job job);

    void deleteJob(Job job);

    Job takeAddJobEvent();

    Job takeDeleteJobEvent();
}
