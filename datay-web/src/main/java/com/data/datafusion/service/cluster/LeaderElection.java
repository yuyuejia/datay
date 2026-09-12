package com.data.datafusion.service.cluster;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Redisson 的 Leader 选举。
 *
 * <p>多 master 部署时保证仅有一个 master 承担调度职责（初始化 Quartz 调度、消费调度事件）。</p>
 */
public class LeaderElection {

    private static final Logger logger = LoggerFactory.getLogger(LeaderElection.class);

    private static final int WAIT_SECONDS = 1;

    private RedissonClient redissonClient;

    private RLock leaderLock;

    private volatile boolean stop = false;

    private boolean isInit = false;

    /** 是否已调用 tryHold 启动选举线程 */
    private volatile boolean started = false;

    private Object masterLock = new Object();

    private Object initLock = new Object();

    private ElectionThread electionThread = new ElectionThread();

    private List<ElectionListener> listeners = new CopyOnWriteArrayList<>();

    public void tryHold(String leaderName) {
        synchronized (this) {
            if (started) {
                return;
            }
            started = true;
        }
        leaderLock = redissonClient.getLock(leaderName);
        electionThread.setDaemon(true);
        electionThread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown()));
    }

    /**
     * 是否已持有 Leader。若选举尚未启动或未完成，会等待选举结果（最多等待选举线程首次尝试完成）。
     */
    public boolean isMaster() {
        if (!started) {
            // 选举未启动（如 worker 独立部署或使用 standalone 模式），直接返回 false
            return false;
        }
        synchronized (initLock) {
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(WAIT_SECONDS * 10).toMillis();
            while (!isInit && System.currentTimeMillis() < deadline) {
                try {
                    initLock.wait(WAIT_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return electionThread.isMaster();
    }

    public void addElectionListener(ElectionListener electionListener) {
        if (listeners.contains(electionListener)) {
            return;
        }
        listeners.add(electionListener);
        // 若订阅时已是 Leader，立即回调，避免监听器错过当选事件
        if (isInit && electionThread.isMaster()) {
            electionListener.onElected();
        }
    }

    public void shutdown() {
        if (!started) {
            return;
        }
        stop = true;
        try {
            synchronized (masterLock) {
                masterLock.notifyAll();
            }
            if (electionThread.isAlive()) {
                electionThread.join(Duration.ofSeconds(WAIT_SECONDS * 5).toMillis());
            }
            listeners.clear();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("shutdown and give up leadership");
    }

    class ElectionThread extends Thread {

        private volatile boolean isMaster = false;

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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    synchronized (initLock) {
                        if (!isInit) {
                            isInit = true;
                            initLock.notifyAll();
                        }
                    }
                }
            }

            if (leaderLock != null && leaderLock.isLocked() && leaderLock.isHeldByCurrentThread()) {
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
