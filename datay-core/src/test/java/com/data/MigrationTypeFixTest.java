package com.data;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.impl.MysqlConverter;
import com.data.metadata.impl.PostgresqlConverter;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.serial.SerialBlob;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据迁移类型修复的回归测试：BLOB、DECIMAL、MySQL TINYINT(1)。
 */
public class MigrationTypeFixTest {

    @Test
    public void postgresqlShouldKeepDecimalTypeAndPrecision() {
        DatabaseConverter.register("postgresql", new PostgresqlConverter());
        ColumnMeta source = new ColumnMeta("amount", "DECIMAL(18,4)", 18, 18, 4);

        ColumnMeta target = DatabaseConverter.convert("duckdb", "postgresql", source);

        assertEquals("NUMERIC", target.getType());
        assertEquals(18, target.getPrecision());
        assertEquals(4, target.getScale());
    }

    @Test
    public void mysqlShouldEmitTinyInt1ForLengthOneTinyInt() {
        DatabaseConverter.register("mysql", new MysqlConverter());
        TableMeta table = new TableMeta("t_flag");
        ColumnMeta column = new ColumnMeta("enabled", "TINYINT", 1, 0, 0);
        column.setLength(1);
        table.addColumn(column);

        String ddl = DatabaseConverter.generateTableDDL("mysql", table);

        assertTrue(ddl.contains("`enabled` TINYINT(1)"), ddl);
    }

    @Test
    public void normalizeParameterShouldConvertBlobToBytes() throws Exception {
        byte[] data = new byte[] { 0, 1, 2, (byte) 255 };

        Object normalized = DBUtils.normalizeParameter("BLOB", new SerialBlob(data));

        assertInstanceOf(byte[].class, normalized);
        assertArrayEquals(data, (byte[]) normalized);
    }

    @Test
    public void normalizeParameterShouldConvertByteBufferToBytes() {
        byte[] data = new byte[] { 9, 8, 7 };

        Object normalized = DBUtils.normalizeParameter("VARBINARY", ByteBuffer.wrap(data));

        assertInstanceOf(byte[].class, normalized);
        assertArrayEquals(data, (byte[]) normalized);
    }

    @Test
    public void normalizeParameterShouldDecodeBlobForTextTarget() throws Exception {
        Object normalized = DBUtils.normalizeParameter("TEXT", new SerialBlob("hello".getBytes(StandardCharsets.UTF_8)));

        assertEquals("hello", normalized);
    }
}
