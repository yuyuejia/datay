package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ETLTaskTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static ETLTask getETLTaskSample1() {
        return new ETLTask()
            .id("1")
            .taskName("taskName1")
            .taskCode("taskCode1")
            .jobId("1")
            .taskDesc("taskDesc1")
            .dir("dir1")
            .type("type1")
            .cron("cron1")
            .jobContext("jobContext1")
            .status("status1")
            .lastStatus("lastStatus1")
            .creater("creater1")
            .project("project1")
            .tenantId("tenantId1")
            .dr(1);
    }

    public static ETLTask getETLTaskSample2() {
        return new ETLTask()
            .id("2")
            .taskName("taskName2")
            .taskCode("taskCode2")
            .jobId("2")
            .taskDesc("taskDesc2")
            .dir("dir2")
            .type("type2")
            .cron("cron2")
            .jobContext("jobContext2")
            .status("status2")
            .lastStatus("lastStatus2")
            .creater("creater2")
            .project("project2")
            .tenantId("tenantId2")
            .dr(2);
    }

    public static ETLTask getETLTaskRandomSampleGenerator() {
        return new ETLTask()
            .id(UUID.randomUUID().toString())
            .taskName(UUID.randomUUID().toString())
            .taskCode(UUID.randomUUID().toString())
            .jobId(UUID.randomUUID().toString())
            .taskDesc(UUID.randomUUID().toString())
            .dir(UUID.randomUUID().toString())
            .type(UUID.randomUUID().toString())
            .cron(UUID.randomUUID().toString())
            .jobContext(UUID.randomUUID().toString())
            .status(UUID.randomUUID().toString())
            .lastStatus(UUID.randomUUID().toString())
            .creater(UUID.randomUUID().toString())
            .project(UUID.randomUUID().toString())
            .tenantId(UUID.randomUUID().toString())
            .dr(intCount.incrementAndGet());
    }
}
