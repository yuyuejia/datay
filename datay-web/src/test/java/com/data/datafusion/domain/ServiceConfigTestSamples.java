package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ServiceConfigTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static ServiceConfig getServiceConfigSample1() {
        return new ServiceConfig().id(1L).dfGroup("dfGroup1").dfKey("dfKey1").dfValue("dfValue1").ytenantId("ytenantId1");
    }

    public static ServiceConfig getServiceConfigSample2() {
        return new ServiceConfig().id(2L).dfGroup("dfGroup2").dfKey("dfKey2").dfValue("dfValue2").ytenantId("ytenantId2");
    }

    public static ServiceConfig getServiceConfigRandomSampleGenerator() {
        return new ServiceConfig()
            .id(longCount.incrementAndGet())
            .dfGroup(UUID.randomUUID().toString())
            .dfKey(UUID.randomUUID().toString())
            .dfValue(UUID.randomUUID().toString())
            .ytenantId(UUID.randomUUID().toString());
    }
}
