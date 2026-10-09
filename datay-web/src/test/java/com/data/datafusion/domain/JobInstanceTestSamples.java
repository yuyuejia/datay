package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class JobInstanceTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static JobInstance getJobInstanceSample1() {
        return new JobInstance()
            .id("1")
            .instanceCode("instanceCode1")
            .jobName("jobName1")
            .jobCode("jobCode1")
            .type("type1")
            .jobContext("jobContext1")
            .status("status1")
            .jobMessage("jobMessage1")
            .execNode("execNode1")
            .startTime("startTime1")
            .endTime("endTime1")
            .project("project1")
            .tenantId("tenantId1");
    }

    public static JobInstance getJobInstanceSample2() {
        return new JobInstance()
            .id("2")
            .instanceCode("instanceCode2")
            .jobName("jobName2")
            .jobCode("jobCode2")
            .type("type2")
            .jobContext("jobContext2")
            .status("status2")
            .jobMessage("jobMessage2")
            .execNode("execNode2")
            .startTime("startTime2")
            .endTime("endTime2")
            .project("project2")
            .tenantId("tenantId2");
    }

    public static JobInstance getJobInstanceRandomSampleGenerator() {
        return new JobInstance()
            .id(UUID.randomUUID().toString())
            .instanceCode(UUID.randomUUID().toString())
            .jobName(UUID.randomUUID().toString())
            .jobCode(UUID.randomUUID().toString())
            .type(UUID.randomUUID().toString())
            .jobContext(UUID.randomUUID().toString())
            .status(UUID.randomUUID().toString())
            .jobMessage(UUID.randomUUID().toString())
            .execNode(UUID.randomUUID().toString())
            .startTime(UUID.randomUUID().toString())
            .endTime(UUID.randomUUID().toString())
            .project(UUID.randomUUID().toString())
            .tenantId(UUID.randomUUID().toString());
    }
}
