package com.data.datasource.provider;

import com.data.job.DatasourceInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * SQL Server Provider 的方言与值读取测试。
 */
class SqlServerDataSourceProviderTest {

    private final SqlServerDataSourceProvider provider = new SqlServerDataSourceProvider();

    @Test
    void shouldQuoteSchemaAndTableWithBrackets() {
        DatasourceInfo info = new DatasourceInfo();

        assertEquals("[dbo].[Order]", provider.qualifiedTableName(info, "dbo", "Order"));
        assertEquals("[Order]", provider.qualifiedTableName(info, null, "Order"));
        assertEquals("[Order]", provider.qualifiedTableName(info, " ", "Order"));
        assertEquals("[dbo].[a]]b]", provider.qualifiedTableName(info, "dbo", "a]b"));
    }

    @Test
    void shouldReadSqlServerTemporalValuesAsJavaTime() throws Exception {
        LocalDate date = LocalDate.of(2024, 1, 1);
        assertSame(date, provider.readSourceValue(resultSet(LocalDate.class, date), 1, Types.DATE, "date", null));

        LocalTime time = LocalTime.of(12, 34, 56);
        assertSame(time, provider.readSourceValue(resultSet(LocalTime.class, time), 1, Types.TIME, "time", null));

        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0, 123_000_000);
        assertSame(
            dateTime,
            provider.readSourceValue(resultSet(LocalDateTime.class, dateTime), 1, Types.TIMESTAMP, "datetime2", null)
        );

        OffsetDateTime offsetDateTime = OffsetDateTime.parse("2024-01-01T10:00:00+08:00");
        assertSame(
            offsetDateTime,
            provider.readSourceValue(
                resultSet(OffsetDateTime.class, offsetDateTime),
                1,
                Types.TIMESTAMP_WITH_TIMEZONE,
                "datetimeoffset",
                null
            )
        );
    }

    @Test
    void shouldFallbackWhenDriverDoesNotSupportJavaTime() throws Exception {
        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[] { ResultSet.class },
            (proxy, method, args) -> {
                if ("getObject".equals(method.getName()) && args != null && args.length == 2) {
                    throw new SQLFeatureNotSupportedException("unsupported");
                }
                if ("getObject".equals(method.getName())) {
                    return "2024-01-01 10:00:00";
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );

        assertEquals("2024-01-01 10:00:00", provider.readSourceValue(rs, 1, Types.DATE, "date", null));
    }

    private ResultSet resultSet(Class<?> requestedType, Object value) {
        return (ResultSet) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[] { ResultSet.class },
            (proxy, method, args) -> {
                if ("getObject".equals(method.getName()) && args != null && args.length == 2) {
                    Class<?> type = (Class<?>) args[1];
                    if (type.equals(requestedType)) {
                        return value;
                    }
                }
                if ("getObject".equals(method.getName())) {
                    return value;
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
