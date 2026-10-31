package com.data.datasource.provider.jdbc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MySQL Connector/J 对 DATETIME(3)/TIME(3) 返回 DECIMAL_DIGITS=0，
 * 需要从 COLUMN_SIZE 推导小数秒精度。
 */
public class JdbcMetadataTest {

    @Test
    public void shouldDeriveFractionalSecondScaleFromColumnSize() {
        assertEquals(3, JdbcMetadata.resolveTemporalScale("DATETIME", 23));
        assertEquals(6, JdbcMetadata.resolveTemporalScale("DATETIME", 26));
        assertEquals(3, JdbcMetadata.resolveTemporalScale("TIMESTAMP", 23));
        assertEquals(3, JdbcMetadata.resolveTemporalScale("TIME", 12));
        assertEquals(6, JdbcMetadata.resolveTemporalScale("TIME", 15));
    }

    @Test
    public void shouldNotAddScaleForWholeSecondsOrUnknownTypes() {
        assertEquals(0, JdbcMetadata.resolveTemporalScale("DATETIME", 19));
        assertEquals(0, JdbcMetadata.resolveTemporalScale("TIME", 8));
        assertEquals(0, JdbcMetadata.resolveTemporalScale("TIMESTAMP WITH TIME ZONE", 35));
    }
}
