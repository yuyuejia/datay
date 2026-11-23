package com.data.metadata.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DBUtils#createStreamStatement(String, Connection)} 的流式读取开关回归测试。
 *
 * <p>不同 JDBC 驱动开启流式读取的方式不同，配错会静默退化为「全量加载」：
 * <ul>
 *   <li>MySQL 协议：必须使用 {@link Integer#MIN_VALUE}；</li>
 *   <li>PostgreSQL 协议：必须关闭 autoCommit，否则 fetchSize 不生效；</li>
 *   <li>Oracle / SQL Server / 其他：使用正数 fetchSize 分批预取。</li>
 * </ul>
 * 这里用动态代理模拟 Connection / Statement，不依赖真实数据库。
 */
public class DBUtilsStreamStatementTest {

    @Test
    public void postgresqlProtocolDisablesAutoCommitForCursor() throws Exception {
        for (String dbType : new String[] { "postgresql", "POSTGRESQL", "kingbase", "greenplum", "gaussdb", "ymatrix" }) {
            FakeConnection conn = new FakeConnection();
            Statement statement = DBUtils.createStreamStatement(dbType, conn.proxy());

            assertEquals(1000, statement.getFetchSize(), dbType + " 应使用正数 fetchSize");
            assertFalse(conn.autoCommit, dbType + " 必须关闭 autoCommit 才能使用服务端游标");
            assertEquals(1, conn.setAutoCommitCalls, dbType + " 应显式关闭 autoCommit");
            assertEquals(ResultSet.TYPE_FORWARD_ONLY, conn.resultSetType);
            assertEquals(ResultSet.CONCUR_READ_ONLY, conn.resultSetConcurrency);
        }
    }

    @Test
    public void postgresqlProtocolKeepsExistingTransactionState() throws Exception {
        FakeConnection conn = new FakeConnection();
        conn.autoCommit = false;

        DBUtils.createStreamStatement("postgresql", conn.proxy());

        assertEquals(0, conn.setAutoCommitCalls, "已关闭 autoCommit 时不应重复设置");
        assertFalse(conn.autoCommit);
    }

    @Test
    public void mysqlProtocolTypesUseStreamingFetchSize() throws Exception {
        for (String dbType : new String[] { "mysql", "MYSQL", "mariadb", "tidb", "doris", "starrocks", "oceanbase", "polardb" }) {
            FakeConnection conn = new FakeConnection();
            Statement statement = DBUtils.createStreamStatement(dbType, conn.proxy());

            assertEquals(Integer.MIN_VALUE, statement.getFetchSize(), dbType + " 必须使用 MIN_VALUE 流式读取");
            assertTrue(conn.autoCommit, dbType + " 不需要关闭 autoCommit");
            assertEquals(0, conn.setAutoCommitCalls);
        }
    }

    @Test
    public void otherTypesUseBatchedFetchSize() throws Exception {
        for (String dbType : new String[] {
            "oracle",
            "sqlserver",
            "clickhouse",
            "hana",
            "dm",
            "tdengine",
            "duckdb",
            "sqlite",
            "unknown",
            null
        }) {
            FakeConnection conn = new FakeConnection();
            Statement statement = DBUtils.createStreamStatement(dbType, conn.proxy());

            assertEquals(1000, statement.getFetchSize(), String.valueOf(dbType));
            assertTrue(conn.autoCommit, String.valueOf(dbType) + " 不应改变 autoCommit");
            assertEquals(0, conn.setAutoCommitCalls);
        }
    }

    private static final class FakeConnection implements InvocationHandler {

        private boolean autoCommit = true;
        private int setAutoCommitCalls;
        private int resultSetType;
        private int resultSetConcurrency;

        private Connection proxy() {
            return (Connection) Proxy.newProxyInstance(
                DBUtilsStreamStatementTest.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getAutoCommit":
                    return autoCommit;
                case "setAutoCommit":
                    autoCommit = (Boolean) args[0];
                    setAutoCommitCalls++;
                    return null;
                case "createStatement":
                    resultSetType = (Integer) args[0];
                    resultSetConcurrency = (Integer) args[1];
                    return fakeStatement();
                case "toString":
                    return "FakeConnection";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        }

        private Statement fakeStatement() {
            return (Statement) Proxy.newProxyInstance(
                DBUtilsStreamStatementTest.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                new FakeStatement()
            );
        }
    }

    private static final class FakeStatement implements InvocationHandler {

        private int fetchSize;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "setFetchSize":
                    fetchSize = (Integer) args[0];
                    return null;
                case "getFetchSize":
                    return fetchSize;
                case "toString":
                    return "FakeStatement";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }
}
