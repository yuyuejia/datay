package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class JobTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Job getJobSample1() {
        return new Job()
            .id("1")
            .jobName("jobName1")
            .jobGroup("jobGroup1")
            .type("type1")
            .cron("cron1")
            .jobContext("jobContext1")
            .status("status1")
            .project("project1")
            .tenantId("tenantId1");
    }

    public static Job getJobSample2() {
        return new Job()
            .id("2")
            .jobName("jobName2")
            .jobGroup("jobGroup2")
            .type("type2")
            .cron("cron2")
            .jobContext("jobContext2")
            .status("status2")
            .project("project2")
            .tenantId("tenantId2");
    }

    public static Job getJobRandomSampleGenerator() {
        return new Job()
            .id(UUID.randomUUID().toString())
            .jobName(UUID.randomUUID().toString())
            .jobGroup(UUID.randomUUID().toString())
            .type(UUID.randomUUID().toString())
            .cron(UUID.randomUUID().toString())
            .jobContext(UUID.randomUUID().toString())
            .status(UUID.randomUUID().toString())
            .project(UUID.randomUUID().toString())
            .tenantId(UUID.randomUUID().toString());
    }
}
