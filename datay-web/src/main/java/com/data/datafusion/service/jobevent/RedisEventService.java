package com.data.datafusion.service.jobevent;

import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.domain.Job;
import com.data.datafusion.service.cluster.LeaderElection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.redisson.api.RBoundedBlockingQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RedisEventService implements IEventService {

    private final Logger log = LoggerFactory.getLogger(RedisEventService.class);

    public static RBoundedBlockingQueue<JobStatusEvent> startJobQueue;

    ExecutorService startJobEventExecutor = Executors.newSingleThreadExecutor();

    public static RBoundedBlockingQueue<JobStatusEvent> jobStatusQueue;
    ExecutorService jobStatusEventExecutor = Executors.newSingleThreadExecutor();

    public static RBoundedBlockingQueue<Job> addJobQueue;
    ExecutorService addJobEventExecutor = Executors.newSingleThreadExecutor();
    public static RBoundedBlockingQueue<Job> deleteJobQueue;
    ExecutorService deleteJobEventExecutor = Executors.newSingleThreadExecutor();

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Autowired(required = false)
    LeaderElection leaderElection;

    private static RedisEventService instance;

    public static synchronized RedisEventService getInstance() {
        if (instance == null) {
            instance = SpringUtil.getBean(RedisEventService.class);
            instance.init();
        }
        return instance;
    }

    //    @PostConstruct
    public void init() {
        startJobQueue = redissonClient.getBoundedBlockingQueue("startJobQueue");
        startJobQueue.trySetCapacity(10000);
        jobStatusQueue = redissonClient.getBoundedBlockingQueue("endJobQueue");
        jobStatusQueue.trySetCapacity(10000);
        addJobQueue = redissonClient.getBoundedBlockingQueue("addJobQueue");
        addJobQueue.trySetCapacity(10000);
        deleteJobQueue = redissonClient.getBoundedBlockingQueue("deleteJobQueue");
        deleteJobQueue.trySetCapacity(10000);
        //work节点需要启动接受StartJobEvent，执行任务
        startJobEventExecutor.execute(new StartJobEventHandler());

        //master节点需要启动接受EndJobEvent，AddJobEvent，DeleteJobEvent
        if (leaderElection.isMaster()) {
            jobStatusEventExecutor.execute(new JobStatusEventHandler());
            addJobEventExecutor.execute(new AddJobEventHandler());
            deleteJobEventExecutor.execute(new DeleteJobEventHandler());
        }
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
            log.info("删除任务事件发送id {}", job.getId());
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
