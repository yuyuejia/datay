package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import com.data.metadata.util.TdengineJdbcSupport;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class TdengineDataSourceProviderTest {

    @Test
    void isRegisteredForTdengine() {
        DataSourceProvider provider = DataSourceProviderFactory.provider(DBType.TDENGINE.name());
        assertNotNull(provider);
        assertTrue(provider instanceof TdengineDataSourceProvider);

        DataSourceTypeDescriptor descriptor = provider.descriptors().get(0);
        assertEquals(DBType.TDENGINE.name(), descriptor.getType());
        assertTrue(descriptor.isJdbc());
        assertTrue(descriptor.isDownloadable());
        assertEquals("jdbc:TAOS-WS://{host}:{port}/{database}", descriptor.getJdbcUrlTemplate());
        assertNotNull(provider.typeConverter(DBType.TDENGINE.name()));
    }

    @Test
    void supportsAllTdengineProtocols() {
        TdengineDataSourceProvider provider = new TdengineDataSourceProvider();
        assertTrue(provider.supportsUrl("jdbc:TAOS-WS://localhost:6041/power"));
        assertTrue(provider.supportsUrl("jdbc:TAOS-RS://localhost:6041/power"));
        assertTrue(provider.supportsUrl("jdbc:TAOS://localhost:6030/power"));
        assertFalse(provider.supportsUrl("jdbc:mysql://localhost:3306/power"));
    }

    @Test
    void usesSelectOneAsTestQuery() {
        assertEquals("SELECT 1", new TdengineDataSourceProvider().getTestQuery());
    }

    @Test
    void rejectsWritesToSuperTable() {
        TdengineDataSourceProvider provider = new TdengineDataSourceProvider();
        TableMeta table = new TableMeta("meters");
        table.setSchema("power");

        SQLException error = assertThrows(
            SQLException.class,
            () -> provider.validateWriteTarget(null, connectionReturning(true), table, "insert")
        );
        assertTrue(error.getMessage().contains("超级表"));
        assertTrue(error.getMessage().contains("SqlSink"));
    }

    @Test
    void allowsWritesToNormalTable() {
        TdengineDataSourceProvider provider = new TdengineDataSourceProvider();
        TableMeta table = new TableMeta("meter_001");
        table.setSchema("power");
        assertDoesNotThrow(() ->
            provider.validateWriteTarget(null, connectionReturning(false), table, "insert")
        );
    }

    @Test
    void rejectsGenericDeleteEvent() {
        TdengineDataSourceProvider provider = new TdengineDataSourceProvider();
        TableMeta table = new TableMeta("meter_001");
        table.setSchema("power");
        SQLException error = assertThrows(
            SQLException.class,
            () -> provider.validateWriteTarget(null, connectionReturning(false), table, "delete")
        );
        assertTrue(error.getMessage().contains("DELETE"));
        assertTrue(error.getMessage().contains("SqlSink"));
    }

    @Test
    void skipsValidationForNonWriteEvent() {
        TdengineDataSourceProvider provider = new TdengineDataSourceProvider();
        TableMeta table = new TableMeta("meters");
        table.setSchema("power");
        // query 事件不写入，即使目标是超级表也不应报错
        assertDoesNotThrow(() ->
            provider.validateWriteTarget(null, connectionReturning(true), table, "query")
        );
    }

    @Test
    void exposesProtocolPrefixes() {
        assertEquals(TdengineJdbcSupport.urlPrefixes(), new TdengineDataSourceProvider().urlPrefixes());
    }

    /**
     * 构造只用于校验 {@code information_schema.ins_stables} 查询的 JDBC 代理。
     */
    private static Connection connectionReturning(boolean stable) {
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
            TdengineDataSourceProviderTest.class.getClassLoader(),
            new Class<?>[] { ResultSet.class },
            (proxy, method, args) -> {
                if ("next".equals(method.getName())) {
                    return stable;
                }
                if ("getString".equals(method.getName())) {
                    return "meters";
                }
                return defaultValue(method.getReturnType());
            }
        );
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
            TdengineDataSourceProviderTest.class.getClassLoader(),
            new Class<?>[] { PreparedStatement.class },
            (proxy, method, args) -> {
                if ("executeQuery".equals(method.getName())) {
                    return resultSet;
                }
                return defaultValue(method.getReturnType());
            }
        );
        return (Connection) Proxy.newProxyInstance(
            TdengineDataSourceProviderTest.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    return statement;
                }
                if ("getCatalog".equals(method.getName())) {
                    return "power";
                }
                return defaultValue(method.getReturnType());
            }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
