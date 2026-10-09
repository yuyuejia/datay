package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class JobDependTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static JobDepend getJobDependSample1() {
        return new JobDepend()
            .id("1")
            .parentJobCode("parentJobCode1")
            .childJobCode("childJobCode1")
            .jobCode("jobCode1")
            .lastInterval(1L)
            .tenantId("tenantId1");
    }

    public static JobDepend getJobDependSample2() {
        return new JobDepend()
            .id("2")
            .parentJobCode("parentJobCode2")
            .childJobCode("childJobCode2")
            .jobCode("jobCode2")
            .lastInterval(2L)
            .tenantId("tenantId2");
    }

    public static JobDepend getJobDependRandomSampleGenerator() {
        return new JobDepend()
            .id(UUID.randomUUID().toString())
            .parentJobCode(UUID.randomUUID().toString())
            .childJobCode(UUID.randomUUID().toString())
            .jobCode(UUID.randomUUID().toString())
            .lastInterval(longCount.incrementAndGet())
            .tenantId(UUID.randomUUID().toString());
    }
}
