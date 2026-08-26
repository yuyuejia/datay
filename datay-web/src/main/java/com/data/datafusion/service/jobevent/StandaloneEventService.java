package com.data.datafusion.service.jobevent;

import com.data.datafusion.domain.Job;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StandaloneEventService implements IEventService {

    private final Logger log = LoggerFactory.getLogger(StandaloneEventService.class);

    private static LinkedBlockingQueue<JobStatusEvent> startJobQueue;

    private ExecutorService startJobEventExecutor = Executors.newSingleThreadExecutor();

    private static LinkedBlockingQueue<JobStatusEvent> jobStatusQueue;
    private ExecutorService jobStatusEventExecutor = Executors.newSingleThreadExecutor();

    private static LinkedBlockingQueue<Job> addJobQueue;
    private ExecutorService addJobEventExecutor = Executors.newSingleThreadExecutor();

    private static LinkedBlockingQueue<Job> deleteJobQueue;
    private ExecutorService deleteJobEventExecutor = Executors.newSingleThreadExecutor();

    private static StandaloneEventService instance;

    public static synchronized StandaloneEventService getInstance() {
        if (instance == null) {
            instance = new StandaloneEventService();
            instance.init();
        }
        return instance;
    }

    //    @PostConstruct
    public void init() {
        startJobQueue = new LinkedBlockingQueue<>(10000);
        jobStatusQueue = new LinkedBlockingQueue<>(10000);
        addJobQueue = new LinkedBlockingQueue<>(10000);
        deleteJobQueue = new LinkedBlockingQueue<>(10000);
        startJobEventExecutor.execute(new StartJobEventHandler());
        jobStatusEventExecutor.execute(new JobStatusEventHandler());
        addJobEventExecutor.execute(new AddJobEventHandler());
        deleteJobEventExecutor.execute(new DeleteJobEventHandler());
    }

    public void pushStartJobEvent(JobStatusEvent startJobEvent) {
        try {
            startJobQueue.put(startJobEvent);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JobStatusEvent takeStartJobEvent() {
        try {
            return startJobQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void pushJobStatusEvent(JobStatusEvent jobStatusEvent) {
        try {
            jobStatusQueue.put(jobStatusEvent);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JobStatusEvent takeJobStatusEvent() {
        try {
            return jobStatusQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addJob(Job job) {
        try {
            addJobQueue.put(job);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateJob(Job job) {}

    @Override
    public void deleteJob(Job job) {
        try {
            deleteJobQueue.put(job);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Job takeAddJobEvent() {
        try {
            return addJobQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Job takeDeleteJobEvent() {
        try {
            return deleteJobQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
