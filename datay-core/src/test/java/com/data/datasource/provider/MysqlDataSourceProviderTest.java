package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.TableMeta;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MysqlDataSourceProviderTest {

    @Test
    void tableListIncludesViews() throws Exception {
        List<String> requestedTypes = new ArrayList<>();
        Connection connection = connectionCapturingTableTypes(requestedTypes);

        List<TableMeta> tables = new MysqlDataSourceProvider().getTables(connection, "demo", null, null);

        assertTrue(tables.isEmpty());
        assertTrue(requestedTypes.contains("TABLE"), "表清单应包含普通表，实际类型: " + requestedTypes);
        assertTrue(requestedTypes.contains("VIEW"), "表清单应包含视图，实际类型: " + requestedTypes);
    }

    /**
     * 构造只用于捕获 {@link DatabaseMetaData#getTables} 类型参数的 JDBC 代理，返回空结果集。
     */
    private static Connection connectionCapturingTableTypes(List<String> requestedTypes) {
        ResultSet emptyResultSet = (ResultSet) Proxy.newProxyInstance(
            MysqlDataSourceProviderTest.class.getClassLoader(),
            new Class<?>[] { ResultSet.class },
            (proxy, method, args) -> {
                if ("next".equals(method.getName())) {
                    return false;
                }
                return defaultValue(method.getReturnType());
            }
        );
        DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
            MysqlDataSourceProviderTest.class.getClassLoader(),
            new Class<?>[] { DatabaseMetaData.class },
            (proxy, method, args) -> {
                if ("getTables".equals(method.getName())) {
                    Collections.addAll(requestedTypes, (String[]) args[3]);
                    return emptyResultSet;
                }
                return defaultValue(method.getReturnType());
            }
        );
        return (Connection) Proxy.newProxyInstance(
            MysqlDataSourceProviderTest.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                if ("getMetaData".equals(method.getName())) {
                    return metaData;
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
