package com.data.datafusion.service.cluster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeaderElection {

    private static final Logger logger = LoggerFactory.getLogger(LeaderElection.class);

    private static final int WAIT_SECONDS = 1;

    private RedissonClient redissonClient;

    private RLock leaderLock;

    private boolean stop = false;

    private boolean isInit = false;

    private Object masterLock = new Object();

    private Object initLock = new Object();

    private ElectionThread electionThread = new ElectionThread();

    private List<ElectionListener> listeners = new ArrayList<>();

    public void tryHold(String leaderName) {
        leaderLock = redissonClient.getLock(leaderName);
        electionThread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown()));
    }

    public boolean isMaster() {
        synchronized (initLock) {
            if (!isInit) {
                try {
                    initLock.wait();
                } catch (InterruptedException e) {}
            }
        }
        return electionThread.isMaster();
    }

    public void addElectionListener(ElectionListener electionListener) {
        if (listeners.contains(electionListener)) {
            return;
        }
        listeners.add(electionListener);
    }

    public void shutdown() {
        stop = true;
        try {
            synchronized (masterLock) {
                masterLock.notifyAll();
            }
            electionThread.join();
            listeners.clear();
        } catch (InterruptedException e) {}
        logger.info("shutdown and give up leadership");
    }

    class ElectionThread extends Thread {

        private boolean isMaster = false;

        public ElectionThread() {
            setName("leader-election");
        }

        @Override
        public void run() {
            while (!stop) {
                try {
                    if (isMaster) {
                        synchronized (masterLock) {
                            if (isInit) {
                                masterLock.wait();
                            } else {
                                masterLock.wait(Duration.ofSeconds(WAIT_SECONDS).toMillis());
                            }
                        }
                    } else {
                        isMaster = leaderLock.tryLock(WAIT_SECONDS, TimeUnit.SECONDS);
                        if (isMaster) {
                            logger.info("got leadership");
                            notifyElected();
                        }
                    }
                } catch (InterruptedException e) {} finally {
                    synchronized (initLock) {
                        if (!isInit) {
                            initLock.notifyAll();
                            isInit = true;
                        }
                    }
                }
            }

            if (leaderLock.isLocked() && leaderLock.isHeldByCurrentThread()) {
                leaderLock.unlock();
            }

            if (isMaster) {
                isMaster = false;
            }
        }

        public boolean isMaster() {
            return isMaster;
        }
    }

    private void notifyElected() {
        for (ElectionListener listener : listeners) {
            listener.onElected();
        }
    }

    public void setRedissonClient(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }
}
