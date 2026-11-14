package com.data.job.component.cdc;

import com.data.job.DatasourceInfo;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.serial.SerialClob;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL Server CDC 的 LSN 与取值归一化单元测试。
 */
class SqlServerCdcCollectorTest {

    private final SqlServerCdcCollector collector = new SqlServerCdcCollector(
        new DatasourceInfo(),
        "dbo",
        null,
        null,
        1000,
        false,
        true,
        new NoopHandler()
    );

    @Test
    void shouldRoundTripLsn() {
        String lsn = "0x0000002A0000003C0004";
        assertEquals(lsn, SqlServerCdcCollector.toHex(SqlServerCdcCollector.parseLsn(lsn)));
        assertEquals(lsn, SqlServerCdcCollector.normalizeLsn("0000002A0000003C0004"));
        assertEquals(SqlServerCdcCollector.ZERO_LSN, SqlServerCdcCollector.normalizeLsn("0x0"));
    }

    @Test
    void shouldPadShortLsnToTenBytes() {
        assertArrayEquals(
            new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 },
            SqlServerCdcCollector.parseLsn("0x1")
        );
        assertThrows(IllegalArgumentException.class, () -> SqlServerCdcCollector.parseLsn(""));
        assertThrows(IllegalArgumentException.class, () -> SqlServerCdcCollector.parseLsn("0xZZ"));
    }

    @Test
    void shouldCompareLsnInUnsignedByteOrder() {
        assertTrue(SqlServerCdcCollector.compareLsn("0x00000000000000000001", "0x00000000000000000002") < 0);
        assertTrue(SqlServerCdcCollector.compareLsn("0x0000002A000000000000", "0x00000029000000000000") > 0);
        assertEquals(0, SqlServerCdcCollector.compareLsn("0x00000000000000000001", "0x1"));
    }

    @Test
    void shouldNormalizeJdbcValues() throws Exception {
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid.toString(), collector.normalizeValue(uuid));

        byte[] bytes = new byte[] { 1, 2, 3 };
        assertSame(bytes, collector.normalizeValue(bytes));

        BigDecimal decimal = new BigDecimal("123.45");
        assertSame(decimal, collector.normalizeValue(decimal));

        LocalTime time = LocalTime.of(12, 34, 56);
        assertSame(time, collector.normalizeValue(time));

        OffsetDateTime offsetDateTime = OffsetDateTime.parse("2024-01-01T10:00:00+08:00");
        assertSame(offsetDateTime, collector.normalizeValue(offsetDateTime));

        SerialClob clob = new SerialClob("hello世界".toCharArray());
        assertEquals("hello世界", collector.normalizeValue(clob));

        Object driverSpecific = new Object() {
            @Override
            public String toString() {
                return "POINT(1 2)";
            }
        };
        assertEquals("POINT(1 2)", collector.normalizeValue(driverSpecific));
    }

    private static class NoopHandler implements BinlogEventHandler {

        @Override
        public void handleEvent(BinlogEvent event) {}

        @Override
        public void handleError(BinlogEvent event, Exception exception) {}
    }
}
