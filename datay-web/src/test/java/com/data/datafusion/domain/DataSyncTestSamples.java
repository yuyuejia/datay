package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DataSyncTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static DataSync getDataSyncSample1() {
        return new DataSync()
            .id(1L)
            .jobName("jobName1")
            .jobCode("jobCode1")
            .jobDesc("jobDesc1")
            .dir("dir1")
            .type("type1")
            .source("source1")
            .target("target1")
            .cron("cron1")
            .jobContext("jobContext1")
            .status("status1")
            .lastStatus("lastStatus1")
            .project("project1")
            .tenantId("tenantId1")
            .dr(1);
    }

    public static DataSync getDataSyncSample2() {
        return new DataSync()
            .id(2L)
            .jobName("jobName2")
            .jobCode("jobCode2")
            .jobDesc("jobDesc2")
            .dir("dir2")
            .type("type2")
            .source("source2")
            .target("target2")
            .cron("cron2")
            .jobContext("jobContext2")
            .status("status2")
            .lastStatus("lastStatus2")
            .project("project2")
            .tenantId("tenantId2")
            .dr(2);
    }

    public static DataSync getDataSyncRandomSampleGenerator() {
        return new DataSync()
            .id(longCount.incrementAndGet())
            .jobName(UUID.randomUUID().toString())
            .jobCode(UUID.randomUUID().toString())
            .jobDesc(UUID.randomUUID().toString())
            .dir(UUID.randomUUID().toString())
            .type(UUID.randomUUID().toString())
            .source(UUID.randomUUID().toString())
            .target(UUID.randomUUID().toString())
            .cron(UUID.randomUUID().toString())
            .jobContext(UUID.randomUUID().toString())
            .status(UUID.randomUUID().toString())
            .lastStatus(UUID.randomUUID().toString())
            .project(UUID.randomUUID().toString())
            .tenantId(UUID.randomUUID().toString())
            .dr(intCount.incrementAndGet());
    }
}
