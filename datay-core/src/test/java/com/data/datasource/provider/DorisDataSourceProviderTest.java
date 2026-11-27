package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DorisDataSourceProviderTest {

    @Test
    void enablesBatchRewriteForInsertPerformance() {
        Map<String, String> props = new DorisDataSourceProvider().dataSourceProperties();

        assertEquals("true", props.get("rewriteBatchedStatements"),
            "开启批量改写以提升 INSERT 性能；批量 DELETE 需避免依赖驱动改写，否则可能报 Unknown command(27)");
        assertEquals("false", props.get("useServerPrepStmts"),
            "Doris 服务端预编译会破坏多字节字符，必须使用客户端预编译");
        assertEquals("UTF-8", props.get("characterEncoding"));
        assertEquals("enable_unicode_name_support=true", props.get("sessionVariables"),
            "Doris 默认拒绝非 ASCII 列名，需在每个连接上开启 Unicode 标识符支持");
    }

    @Test
    void uniqueKeyColumnsAreMarkedAsPrimaryKey() {
        TableMeta table = new TableMeta("t");
        table.addColumn(new ColumnMeta("id", "INT"));
        table.addColumn(new ColumnMeta("tenant_id", "BIGINT"));
        table.addColumn(new ColumnMeta("name", "VARCHAR", 50, -1, -1));

        String ddl =
            "CREATE TABLE `t` (\n" +
            "  `id` INT NOT NULL,\n" +
            "  `tenant_id` BIGINT NOT NULL,\n" +
            "  `name` VARCHAR(50) NULL\n" +
            ") ENGINE=OLAP\n" +
            "UNIQUE KEY(`id`, `tenant_id`)\n" +
            "DISTRIBUTED BY HASH(`id`) BUCKETS 10";

        DorisDataSourceProvider.applyUniqueKeyFromDdl(table, ddl);

        assertTrue(table.columns().get(0).isPrimaryKey(), "id 应为 UNIQUE KEY 列");
        assertTrue(table.columns().get(1).isPrimaryKey(), "tenant_id 应为 UNIQUE KEY 列");
        assertFalse(table.columns().get(2).isPrimaryKey(), "name 不是 UNIQUE KEY 列");
    }

    @Test
    void duplicateKeyTableKeepsNoPrimaryKey() {
        TableMeta table = new TableMeta("t");
        table.addColumn(new ColumnMeta("id", "INT"));

        String ddl =
            "CREATE TABLE `t` (`id` INT) ENGINE=OLAP\n" +
            "DUPLICATE KEY(`id`)\n" +
            "DISTRIBUTED BY HASH(`id`) BUCKETS 1";

        DorisDataSourceProvider.applyUniqueKeyFromDdl(table, ddl);

        assertFalse(table.columns().get(0).isPrimaryKey(), "DUPLICATE KEY 表没有主键语义");
    }
}
