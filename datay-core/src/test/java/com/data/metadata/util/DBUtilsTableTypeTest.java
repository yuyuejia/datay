package com.data.metadata.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.data.metadata.TableMeta;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DBUtilsTableTypeTest {

    @Test
    void baseTableListExcludesViewsButTableListKeepsThem() throws Exception {
        List<Map<String, String>> rows = List.of(
            row("t_base", "TABLE"),
            row("v_test", "VIEW")
        );
        Connection connection = connectionReturning(rows);

        List<TableMeta> tables = DBUtils.getTableList(connection, "demo");
        assertEquals(List.of("t_base", "v_test"), tables.stream().map(TableMeta::getTable).toList());
        assertEquals("TABLE", tables.get(0).getTableType());
        assertEquals("VIEW", tables.get(1).getTableType());

        List<TableMeta> baseTables = DBUtils.getBaseTableList(connection, "demo");
        assertEquals(List.of("t_base"), baseTables.stream().map(TableMeta::getTable).toList());
    }

    private static Map<String, String> row(String name, String type) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("TABLE_NAME", name);
        row.put("TABLE_TYPE", type);
        row.put("REMARKS", null);
        row.put("TABLE_CAT", null);
        row.put("TABLE_SCHEM", null);
        return row;
    }

    private static Connection connectionReturning(List<Map<String, String>> rows) {
        ResultSet emptySchemas = resultSet(List.of());
        DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
            DBUtilsTableTypeTest.class.getClassLoader(),
            new Class<?>[] { DatabaseMetaData.class },
            (proxy, method, args) -> {
                if ("getURL".equals(method.getName())) {
                    return null;
                }
                if ("getSchemas".equals(method.getName())) {
                    return emptySchemas;
                }
                if ("getTables".equals(method.getName())) {
                    return resultSet(rows);
                }
                return defaultValue(method.getReturnType());
            }
        );
        return (Connection) Proxy.newProxyInstance(
            DBUtilsTableTypeTest.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                if ("getMetaData".equals(method.getName())) {
                    return metaData;
                }
                return defaultValue(method.getReturnType());
            }
        );
    }

    private static ResultSet resultSet(List<Map<String, String>> rows) {
        List<Map<String, String>> data = new ArrayList<>(rows);
        int[] index = { -1 };
        return (ResultSet) Proxy.newProxyInstance(
            DBUtilsTableTypeTest.class.getClassLoader(),
            new Class<?>[] { ResultSet.class },
            (proxy, method, args) -> {
                if ("next".equals(method.getName())) {
                    index[0]++;
                    return index[0] < data.size();
                }
                if ("getString".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof String) {
                    return data.get(index[0]).get(args[0]);
                }
                if ("close".equals(method.getName())) {
                    return null;
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
