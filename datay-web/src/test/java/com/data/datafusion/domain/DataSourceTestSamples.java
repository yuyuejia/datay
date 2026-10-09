package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class DataSourceTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static DataSource getDataSourceSample1() {
        return new DataSource()
            .id("1")
            .name("name1")
            .description("description1")
            .type("type1")
            .url("url1")
            .hostname("hostname1")
            .port("port1")
            .schemaName("schemaName1")
            .username("username1")
            .password("password1")
            .tenantId("tenantId1");
    }

    public static DataSource getDataSourceSample2() {
        return new DataSource()
            .id("2")
            .name("name2")
            .description("description2")
            .type("type2")
            .url("url2")
            .hostname("hostname2")
            .port("port2")
            .schemaName("schemaName2")
            .username("username2")
            .password("password2")
            .tenantId("tenantId2");
    }

    public static DataSource getDataSourceRandomSampleGenerator() {
        return new DataSource()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .type(UUID.randomUUID().toString())
            .url(UUID.randomUUID().toString())
            .hostname(UUID.randomUUID().toString())
            .port(UUID.randomUUID().toString())
            .schemaName(UUID.randomUUID().toString())
            .username(UUID.randomUUID().toString())
            .password(UUID.randomUUID().toString())
            .tenantId(UUID.randomUUID().toString());
    }
}
