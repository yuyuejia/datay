package com.data.metadata.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DateTimeParsingTest {

    @Test
    void parsesSqlServerDatetime2WithNanosecondPrecision() {
        assertEquals(
            LocalDateTime.of(2026, 10, 24, 12, 34, 56, 123_456_700),
            DBUtils.parseDateTime("2026-10-24 12:34:56.1234567")
        );
    }

    @Test
    void parsesSqlServerDatetimeOffsetWithSpaceBeforeOffset() {
        assertEquals(LocalDateTime.of(2026, 10, 24, 12, 34, 56), DBUtils.parseDateTime("2026-10-24 12:34:56 +08:00"));
        assertEquals(LocalDateTime.of(2026, 10, 24, 12, 34, 56), DBUtils.parseDateTime("2026-10-24T12:34:56+08:00"));
    }

    @Test
    void keepsParsingExistingDateTimeFormats() {
        assertEquals(LocalDateTime.of(2026, 10, 24, 12, 34, 56), DBUtils.parseDateTime("2026-10-24 12:34:56"));
        assertEquals(LocalDateTime.of(2026, 10, 24, 12, 34, 56), DBUtils.parseDateTime("2026-10-24T12:34:56"));
        assertEquals(LocalDateTime.of(2026, 10, 24, 12, 34), DBUtils.parseDateTime("2026-10-24T12:34"));
        assertEquals(LocalDateTime.of(2026, 10, 24, 12, 34, 56), DBUtils.parseDateTime("2026-10-24 12:34:56.0"));
    }
}
