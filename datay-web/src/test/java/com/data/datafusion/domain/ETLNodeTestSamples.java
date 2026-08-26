package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ETLNodeTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static ETLNode getETLNodeSample1() {
        return new ETLNode()
            .id(1L)
            .taskId("taskId1")
            .label("label1")
            .code("code1")
            .desc("desc1")
            .type("type1")
            .config("config1")
            .xAxis("xAxis1")
            .yAxis("yAxis1")
            .status("status1")
            .tenantId("tenantId1")
            .dr(1);
    }

    public static ETLNode getETLNodeSample2() {
        return new ETLNode()
            .id(2L)
            .taskId("taskId2")
            .label("label2")
            .code("code2")
            .desc("desc2")
            .type("type2")
            .config("config2")
            .xAxis("xAxis2")
            .yAxis("yAxis2")
            .status("status2")
            .tenantId("tenantId2")
            .dr(2);
    }

    public static ETLNode getETLNodeRandomSampleGenerator() {
        return new ETLNode()
            .id(longCount.incrementAndGet())
            .taskId(UUID.randomUUID().toString())
            .label(UUID.randomUUID().toString())
            .code(UUID.randomUUID().toString())
            .desc(UUID.randomUUID().toString())
            .type(UUID.randomUUID().toString())
            .config(UUID.randomUUID().toString())
            .xAxis(UUID.randomUUID().toString())
            .yAxis(UUID.randomUUID().toString())
            .status(UUID.randomUUID().toString())
            .tenantId(UUID.randomUUID().toString())
            .dr(intCount.incrementAndGet());
    }
}
