package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ETLComponentTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static ETLComponent getETLComponentSample1() {
        return new ETLComponent()
            .id(1L)
            .name("name1")
            .code("code1")
            .desc("desc1")
            .group("group1")
            .type("type1")
            .config("config1")
            .status("status1")
            .creater("creater1")
            .tenantId("tenantId1")
            .dr(1);
    }

    public static ETLComponent getETLComponentSample2() {
        return new ETLComponent()
            .id(2L)
            .name("name2")
            .code("code2")
            .desc("desc2")
            .group("group2")
            .type("type2")
            .config("config2")
            .status("status2")
            .creater("creater2")
            .tenantId("tenantId2")
            .dr(2);
    }

    public static ETLComponent getETLComponentRandomSampleGenerator() {
        return new ETLComponent()
            .id(longCount.incrementAndGet())
            .name(UUID.randomUUID().toString())
            .code(UUID.randomUUID().toString())
            .desc(UUID.randomUUID().toString())
            .group(UUID.randomUUID().toString())
            .type(UUID.randomUUID().toString())
            .config(UUID.randomUUID().toString())
            .status(UUID.randomUUID().toString())
            .creater(UUID.randomUUID().toString())
            .tenantId(UUID.randomUUID().toString())
            .dr(intCount.incrementAndGet());
    }
}
