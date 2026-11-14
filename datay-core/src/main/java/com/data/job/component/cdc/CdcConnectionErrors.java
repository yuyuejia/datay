package com.data.job.component.cdc;

import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;

/**
 * 判断异常是否属于数据源连接失败。
 *
 * <p>连接失败需要触发 CDC 采集器重连，并在重连耗尽后让任务失败；配置错误、权限不足
 * 等非连接错误则不应被反复重试。
 */
public final class CdcConnectionErrors {

    private static final int[] ORACLE_CONNECTION_ERROR_CODES = {
        1017,
        3113,
        3114,
        12170,
        12505,
        12514,
        12541,
        12545,
        12560,
        17002,
        17008,
        17009,
        17068,
        17401,
        17410,
    };

    /** 达梦 DPI/服务器连接异常错误码。 */
    private static final int[] DM_CONNECTION_ERROR_CODES = {
        6001,
        -70019,
        -70025,
        -70028,
        -70065,
        -70066,
    };

    /** SQL Server / mssql-jdbc 连接、登录与网络异常错误码。 */
    private static final int[] SQLSERVER_CONNECTION_ERROR_CODES = {
        53,
        64,
        233,
        4060,
        10053,
        10054,
        10060,
        10061,
        18452,
        18456,
        18470,
        20009,
        20014,
    };

    private static final String[] CONNECTION_MESSAGES = {
        "connection refused",
        "connection reset",
        "connection is not available",
        "connection terminated",
        "connection closed",
        "terminating connection",
        "server closed the connection",
        "socket closed",
        "end of file",
        "unexpected eof",
        "connect timed out",
        "communications link failure",
        "broken pipe",
        "closed connection",
        "io error",
        "unknownhost",
        "no such host",
        "timed out while waiting for a server",
        "failed to connect",
        "authentication failed",
        "not authorized",
        "unauthorized",
        "auth failed",
        "login failed",
        "server was not found",
        "could not open a connection",
        "socket input is dead",
        "connection reset by peer",
        "the connection is broken",
        "no suitable driver",
        "failed to get driver instance",
    };

    private CdcConnectionErrors() {}

    /**
     * 判断异常链中是否包含连接失败。
     */
    public static boolean isConnectionFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException) {
                SQLException sqlException = (SQLException) current;
                String sqlState = sqlException.getSQLState();
                if (
                    sqlState != null &&
                    (sqlState.startsWith("08") ||
                        sqlState.startsWith("28") ||
                        sqlState.startsWith("57P") ||
                        "3D000".equals(sqlState))
                ) {
                    return true;
                }
                for (int code : ORACLE_CONNECTION_ERROR_CODES) {
                    if (sqlException.getErrorCode() == code) {
                        return true;
                    }
                }
                for (int code : DM_CONNECTION_ERROR_CODES) {
                    if (sqlException.getErrorCode() == code) {
                        return true;
                    }
                }
                for (int code : SQLSERVER_CONNECTION_ERROR_CODES) {
                    if (sqlException.getErrorCode() == code) {
                        return true;
                    }
                }
            }
            if (
                current instanceof ConnectException ||
                current instanceof UnknownHostException ||
                current instanceof SocketException ||
                current instanceof SocketTimeoutException ||
                current instanceof IOException ||
                current instanceof MongoTimeoutException ||
                current instanceof MongoSocketException ||
                current instanceof MongoSecurityException
            ) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                for (String keyword : CONNECTION_MESSAGES) {
                    if (lower.contains(keyword)) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
