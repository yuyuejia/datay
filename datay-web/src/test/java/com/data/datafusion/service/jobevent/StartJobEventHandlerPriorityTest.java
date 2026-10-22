package com.data.datafusion.service.jobevent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StartJobEventHandlerPriorityTest {

    @Test
    void executesHigherPriorityFirstAndKeepsFifoForSamePriority() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new PriorityBlockingQueue<>(10, PrioritizedFutureTask::compare)
        );
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        executor.execute(
            new PrioritizedFutureTask(
                () -> {
                    running.countDown();
                    release.await();
                    return "running";
                },
                2,
                0
            )
        );
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

        submit(executor, executionOrder, "low", 1, 1);
        submit(executor, executionOrder, "high", 3, 2);
        submit(executor, executionOrder, "normal-1", 2, 3);
        submit(executor, executionOrder, "normal-2", 2, 4);

        release.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executionOrder).containsExactly("high", "normal-1", "normal-2", "low");
    }

    private void submit(ThreadPoolExecutor executor, List<String> executionOrder, String name, int priority, long sequence) {
        executor.execute(
            new PrioritizedFutureTask(
                () -> {
                    executionOrder.add(name);
                    return name;
                },
                priority,
                sequence
            )
        );
    }
}
