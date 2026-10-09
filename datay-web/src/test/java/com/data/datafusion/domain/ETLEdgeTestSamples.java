package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ETLEdgeTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static ETLEdge getETLEdgeSample1() {
        return new ETLEdge()
            .id("1")
            .taskId("taskId1")
            .name("name1")
            .code("code1")
            .source("source1")
            .target("target1")
            .config("config1")
            .status("status1")
            .tenantId("tenantId1")
            .dr(1);
    }

    public static ETLEdge getETLEdgeSample2() {
        return new ETLEdge()
            .id("2")
            .taskId("taskId2")
            .name("name2")
            .code("code2")
            .source("source2")
            .target("target2")
            .config("config2")
            .status("status2")
            .tenantId("tenantId2")
            .dr(2);
    }

    public static ETLEdge getETLEdgeRandomSampleGenerator() {
        return new ETLEdge()
            .id(UUID.randomUUID().toString())
            .taskId(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .code(UUID.randomUUID().toString())
            .source(UUID.randomUUID().toString())
            .target(UUID.randomUUID().toString())
            .config(UUID.randomUUID().toString())
            .status(UUID.randomUUID().toString())
            .tenantId(UUID.randomUUID().toString())
            .dr(intCount.incrementAndGet());
    }
}
