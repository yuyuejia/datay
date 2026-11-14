package com.data.job.component.cdc;

import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 达梦日志挖掘的取值与列过滤单元测试。
 */
class DmLogMinerCollectorTest {

    private final DmLogMinerCollector collector = new DmLogMinerCollector(
        new DatasourceInfo(),
        "DATAY",
        null,
        null,
        1000,
        false,
        5000,
        true,
        new NoopHandler()
    );

    @Test
    void shouldPadDecimalScale() {
        ColumnMeta amount = new ColumnMeta("AMOUNT", "DECIMAL", 10, 10, 2);
        assertEquals("10.50", collector.normalizeValue(amount, "10.5"));

        ColumnMeta number = new ColumnMeta("NUM", "NUMBER", 0, 10, 0);
        assertEquals("10", collector.normalizeValue(number, "10.0"));

        // 通用 NUMBER 未声明精度/小数位时不擅自改值
        ColumnMeta generic = new ColumnMeta("NUM", "NUMBER", 0, 0, 0);
        assertEquals("10.5", collector.normalizeValue(generic, "10.5"));
    }

    @Test
    void shouldNormalizeTimestamp() {
        ColumnMeta timestamp = new ColumnMeta("UPDATED_AT", "TIMESTAMP", 0, 0, 6);
        assertEquals("2024-01-01T10:00:00.000000", collector.normalizeValue(timestamp, "2024-01-01 10:00:00.000000"));

        ColumnMeta date = new ColumnMeta("DAY", "DATE", 0, 0, 0);
        assertEquals("2024-01-01", collector.normalizeValue(date, "2024-01-01"));
    }

    @Test
    void shouldKeepNullAndPlainText() {
        ColumnMeta name = new ColumnMeta("NAME", "VARCHAR", 100, 0, 0);
        assertNull(collector.normalizeValue(name, null));
        assertEquals(" alice ", collector.normalizeValue(name, " alice "));
    }

    @Test
    void shouldFilterUnsupportedColumns() {
        TableMeta table = new TableMeta();
        table.setSchema("DATAY");
        table.setTable("CDC_DEMO");
        table.addColumn(new ColumnMeta("ID", "INT"));
        table.addColumn(new ColumnMeta("NAME", "VARCHAR"));
        table.addColumn(new ColumnMeta("CONTENT", "CLOB"));
        table.addColumn(new ColumnMeta("PAYLOAD", "BLOB"));
        table.addColumn(new ColumnMeta("RAW_DATA", "VARBINARY"));
        table.addColumn(new ColumnMeta("DOC", "XML"));

        List<ColumnMeta> columns = collector.supportedColumns(table);

        assertEquals(2, columns.size());
        assertTrue(columns.stream().anyMatch(column -> "ID".equals(column.getName())));
        assertTrue(columns.stream().anyMatch(column -> "NAME".equals(column.getName())));
    }

    private static class NoopHandler implements BinlogEventHandler {

        @Override
        public void handleEvent(BinlogEvent event) {}

        @Override
        public void handleError(BinlogEvent event, Exception exception) {}
    }
}
