package com.data.datafusion.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class DpTableTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static DpTable getDpTableSample1() {
        return new DpTable()
            .id(1L)
            .name("name1")
            .schemaName("schemaName1")
            .description("description1")
            .sourceDBType("sourceDBType1")
            .sourceId("sourceId1")
            .sourceSchema("sourceSchema1")
            .sourceTable("sourceTable1")
            .sqlContent("sqlContent1")
            .fileType("fileType1")
            .filePath("filePath1")
            .tenantId("tenantId1");
    }

    public static DpTable getDpTableSample2() {
        return new DpTable()
            .id(2L)
            .name("name2")
            .schemaName("schemaName2")
            .description("description2")
            .sourceDBType("sourceDBType2")
            .sourceId("sourceId2")
            .sourceSchema("sourceSchema2")
            .sourceTable("sourceTable2")
            .sqlContent("sqlContent2")
            .fileType("fileType2")
            .filePath("filePath2")
            .tenantId("tenantId2");
    }

    public static DpTable getDpTableRandomSampleGenerator() {
        return new DpTable()
            .id(longCount.incrementAndGet())
            .name(UUID.randomUUID().toString())
            .schemaName(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .sourceDBType(UUID.randomUUID().toString())
            .sourceId(UUID.randomUUID().toString())
            .sourceSchema(UUID.randomUUID().toString())
            .sourceTable(UUID.randomUUID().toString())
            .sqlContent(UUID.randomUUID().toString())
            .fileType(UUID.randomUUID().toString())
            .filePath(UUID.randomUUID().toString())
            .tenantId(UUID.randomUUID().toString());
    }
}
