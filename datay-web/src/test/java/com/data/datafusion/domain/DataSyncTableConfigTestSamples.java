package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DataSyncTableConfigTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static DataSyncTableConfig getDataSyncTableConfigSample1() {
        return new DataSyncTableConfig()
            .id("1")
            .syncTask("syncTask1")
            .srcDatasource("srcDatasource1")
            .srcSchemaName("srcSchemaName1")
            .srcTableName("srcTableName1")
            .srcColPks("srcColPks1")
            .desDatasource("desDatasource1")
            .desSchemaName("desSchemaName1")
            .desTableName("desTableName1")
            .desColPks("desColPks1")
            .jobDesc("jobDesc1")
            .columeConfig("columeConfig1")
            .project("project1")
            .tenantId("tenantId1")
            .dr(1);
    }

    public static DataSyncTableConfig getDataSyncTableConfigSample2() {
        return new DataSyncTableConfig()
            .id("2")
            .syncTask("syncTask2")
            .srcDatasource("srcDatasource2")
            .srcSchemaName("srcSchemaName2")
            .srcTableName("srcTableName2")
            .srcColPks("srcColPks2")
            .desDatasource("desDatasource2")
            .desSchemaName("desSchemaName2")
            .desTableName("desTableName2")
            .desColPks("desColPks2")
            .jobDesc("jobDesc2")
            .columeConfig("columeConfig2")
            .project("project2")
            .tenantId("tenantId2")
            .dr(2);
    }

    public static DataSyncTableConfig getDataSyncTableConfigRandomSampleGenerator() {
        return new DataSyncTableConfig()
            .id(UUID.randomUUID().toString())
            .syncTask(UUID.randomUUID().toString())
            .srcDatasource(UUID.randomUUID().toString())
            .srcSchemaName(UUID.randomUUID().toString())
            .srcTableName(UUID.randomUUID().toString())
            .srcColPks(UUID.randomUUID().toString())
            .desDatasource(UUID.randomUUID().toString())
            .desSchemaName(UUID.randomUUID().toString())
            .desTableName(UUID.randomUUID().toString())
            .desColPks(UUID.randomUUID().toString())
            .jobDesc(UUID.randomUUID().toString())
            .columeConfig(UUID.randomUUID().toString())
            .project(UUID.randomUUID().toString())
            .tenantId(UUID.randomUUID().toString())
            .dr(intCount.incrementAndGet());
    }
}
