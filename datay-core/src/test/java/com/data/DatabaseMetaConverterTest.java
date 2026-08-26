package com.data;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.impl.MysqlConverter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DatabaseMetaConverterTest {

    @Test
    public void testRegisterAndConvert() {
        // 注册数据库类型转换器
        DatabaseConverter.register("mysql", new MysqlConverter());

        ColumnMeta sourceColumnMeta = new ColumnMeta("id", "INT");
        sourceColumnMeta.setComment("ID");
        sourceColumnMeta.setLength(11);
        sourceColumnMeta.setNullable(false);
        sourceColumnMeta.setPrimaryKey(true);
        ColumnMeta convertedColumnMeta = DatabaseConverter.convert("mysql", "oracle", sourceColumnMeta);

        // 断言转换结果
        Assertions.assertNotNull(convertedColumnMeta);
    }

    @Test
    public void testGenerateDDL() {
        // 注册数据库类型转换器
        DatabaseConverter.register("mysql", new MysqlConverter());

        TableMeta table = new TableMeta("test_table");
        table.addColumn(new ColumnMeta("id", "INT"));

        String ddl = DatabaseConverter.generateTableDDL("mysql", table);
        System.out.println(ddl);

        // 断言生成的 DDL 语句
        Assertions.assertTrue(ddl.contains("CREATE TABLE test_table"));
        Assertions.assertTrue(ddl.contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"));
    }

    @Test
    public void testUnregisteredConverter() {
        // 尝试转换未注册的数据库类型
        ColumnMeta sourceColumnMeta = new ColumnMeta("id", "INT");
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            DatabaseConverter.convert("unregistered_db", "oracle", sourceColumnMeta);
        });

        // 尝试生成未注册数据库类型的 DDL
        TableMeta table = new TableMeta("test_table");
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            DatabaseConverter.generateTableDDL("unregistered_db", table);
        });
    }
}
