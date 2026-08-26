package com.data.job.bpr;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BPRTest {

    // DuckDB连接URL（本地文件模式，也可使用内存模式: jdbc:duckdb:）
    private static final String DUCKDB_URL = "jdbc:duckdb:bpr_daily.duckdb";
    public String bprFile = "/Users/chenjie/Downloads/BPR - YonBIP Monitoring Center-rf7y5tzev3.html";
    public String action = "付款单保存";

    @Test
    @Timeout(6000000)
    public void testInsert() throws Exception {
        testSummary();
        testServiceCalls();
        testSqlDetails();
    }

    @Test
    @Timeout(6000000)
    public void testDirectoryImport() throws Exception {
        String directoryPath = "/Users/chenjie/Downloads/bpr_download";
        importDirectory(directoryPath);
    }

    /**
     * 导入目录下的所有HTML文件到DuckDB
     */
    public void importDirectory(String directoryPath) throws Exception {
        java.nio.file.Path dirPath = Paths.get(directoryPath);
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            System.out.println("目录不存在或不是目录: " + directoryPath);
            return;
        }

        try (java.nio.file.DirectoryStream<java.nio.file.Path> stream = Files.newDirectoryStream(dirPath, "*.html")) {
            for (java.nio.file.Path filePath : stream) {
                String fileName = filePath.getFileName().toString();
                String actionName = fileName.substring(0, fileName.lastIndexOf('.'));
                System.out.println("正在处理文件: " + fileName + "，action: " + actionName);
                importFile(filePath.toString(), actionName);
            }
        }
    }

    /**
     * 导入单个文件到DuckDB
     */
    public void importFile(String filePath, String actionName) throws Exception {
        try (Connection conn = DriverManager.getConnection(DUCKDB_URL)) {
            // 处理summary数据
            String summaryJson = extractJson(filePath, "summary:");
            if (summaryJson != null) {
                JSONObject summaryObj = JSONObject.parseObject(summaryJson);
                createSummaryTable(conn);
                insertSummaryToDuckDB(conn, summaryObj, actionName);
            }

            // 处理serviceCalls数据
            String serviceCallsJson = extractJson(filePath, "serviceCalls:");
            if (serviceCallsJson != null) {
                JSONArray serviceCallsArray = JSONArray.parseArray(serviceCallsJson);
                List<JSONObject> flatDataList = new ArrayList<>();
                flattenJson(serviceCallsArray, flatDataList);
                PreparedStatement createStmt = conn.prepareStatement(CREATE_TABLE_SQL);
                createStmt.executeUpdate();
                createStmt.close();
                int batchSize = 1000;
                int count = 0;
                PreparedStatement insertStmt = conn.prepareStatement(INSERT_SQL);
                for (JSONObject flatObj : flatDataList) {
                    setPreparedStatementParams(insertStmt, flatObj, actionName);
                    insertStmt.addBatch();
                    count++;
                    if (count % batchSize == 0) {
                        insertStmt.executeBatch();
                    }
                }
                if (count % batchSize != 0) {
                    insertStmt.executeBatch();
                }
                insertStmt.close();
                System.out.println("serviceCalls数据插入完成，共插入 " + count + " 条记录");
            }

            // 处理sqlDetails数据
            String sqlDetailsJson = extractJson(filePath, "sqlDetailList:");
            if (sqlDetailsJson != null) {
                JSONArray sqlDetailsArray = JSONArray.parseArray(sqlDetailsJson);
                createSqlTable(conn);
                batchInsertToDuckDB(conn, sqlDetailsArray, actionName);
            }
        }
    }

    @Test
    @Timeout(6000000)
    public void testSummary() throws Exception {
        // 1. 初始化DuckDB连接
        try (Connection conn = DriverManager.getConnection(DUCKDB_URL)) {
            // 2. 创建目标表（字段与summary JSON中key对应）
            createSummaryTable(conn);
            // 读取HTML文件
            String jsonData = extractJson(bprFile, "summary:");
            JSONObject summaryObj = JSONObject.parseObject(jsonData);

            // 3. 插入数据到DuckDB
            insertSummaryToDuckDB(conn, summaryObj, action);

        }
    }

    /**
     * 创建DuckDB summary表
     */
    private void createSummaryTable(Connection conn) throws SQLException {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS main.summary_log (
                staticReqCnt VARCHAR,
                readFromClient VARCHAR,
                totalRuleNum VARCHAR,
                totalSqlNum VARCHAR,
                writeToClientTime VARCHAR,
                notClosedConnecttion VARCHAR,
                totalSqlCostTime VARCHAR,
                errorSize VARCHAR,
                totalRedisCostTime VARCHAR,
                totalReadRowNum VARCHAR,
                serviceReqCnt VARCHAR,
                totalRpcNum VARCHAR,
                remotenum VARCHAR,
                ubaErrorCnt VARCHAR,
                busiaction VARCHAR,
                appCostTime VARCHAR,
                readFromClientTime VARCHAR,
                readFromServer VARCHAR,
                totalCallRedisCount VARCHAR,
                totalRuleCostTime VARCHAR,
                totalThreadNum VARCHAR,
                totalCostTime VARCHAR,
                totalReadrResultTime VARCHAR,
                action VARCHAR
            )
            """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            System.out.println("summary表创建成功！");
        }
    }

    /**
     * 插入summary数据到DuckDB
     */
    private void insertSummaryToDuckDB(Connection conn, JSONObject summaryObj, String actionName) throws SQLException {
        String insertSql = """
            INSERT INTO main.summary_log (
                staticReqCnt, readFromClient, totalRuleNum, totalSqlNum, writeToClientTime,
                notClosedConnecttion, totalSqlCostTime, errorSize, totalRedisCostTime, totalReadRowNum,
                serviceReqCnt, totalRpcNum, remotenum, ubaErrorCnt, busiaction,
                appCostTime, readFromClientTime, readFromServer, totalCallRedisCount, totalRuleCostTime,
                totalThreadNum, totalCostTime, totalReadrResultTime, action
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            // 按字段顺序设置参数
            pstmt.setString(1, summaryObj.getString("staticReqCnt"));
            pstmt.setString(2, summaryObj.getString("readFromClient"));
            pstmt.setString(3, summaryObj.getString("totalRuleNum"));
            pstmt.setString(4, summaryObj.getString("totalSqlNum"));
            pstmt.setString(5, summaryObj.getString("writeToClientTime"));
            pstmt.setString(6, summaryObj.getString("notClosedConnecttion"));
            pstmt.setString(7, summaryObj.getString("totalSqlCostTime"));
            pstmt.setString(8, summaryObj.getString("errorSize"));
            pstmt.setString(9, summaryObj.getString("totalRedisCostTime"));
            pstmt.setString(10, summaryObj.getString("totalReadRowNum"));
            pstmt.setString(11, summaryObj.getString("serviceReqCnt"));
            pstmt.setString(12, summaryObj.getString("totalRpcNum"));
            pstmt.setString(13, summaryObj.getString("remotenum"));
            pstmt.setString(14, summaryObj.getString("ubaErrorCnt"));
            pstmt.setString(15, summaryObj.getString("busiaction"));
            pstmt.setString(16, summaryObj.getString("appCostTime"));
            pstmt.setString(17, summaryObj.getString("readFromClientTime"));
            pstmt.setString(18, summaryObj.getString("readFromServer"));
            pstmt.setString(19, summaryObj.getString("totalCallRedisCount"));
            pstmt.setString(20, summaryObj.getString("totalRuleCostTime"));
            pstmt.setString(21, summaryObj.getString("totalThreadNum"));
            pstmt.setString(22, summaryObj.getString("totalCostTime"));
            pstmt.setString(23, summaryObj.getString("totalReadrResultTime"));
            pstmt.setString(24, actionName);

            // 执行插入
            int affectedRows = pstmt.executeUpdate();
            System.out.println("summary数据插入完成，共插入 " + affectedRows + " 条记录");
        }
    }

    @Test
    @Timeout(6000000)
    public void testServiceCalls() throws Exception {
        // 读取HTML文件
        String jsonData = extractJson(bprFile, "serviceCalls:");
//        String jsonData = "你的JSON数据字符串";
        JSONArray rootArray = JSONArray.parseArray(jsonData);

        // 2. 递归拍平JSON数据
        List<JSONObject> flatDataList = new ArrayList<>();
        flattenJson(rootArray, flatDataList);

        // 3. 连接DuckDB并插入数据
        try (Connection conn = DriverManager.getConnection(DUCKDB_URL)
        ) {
            PreparedStatement createStmt = conn.prepareStatement(CREATE_TABLE_SQL);
            // 创建表
            createStmt.executeUpdate();
            createStmt.close();
            // 批量插入数据
            int batchSize = 1000;
            int count = 0;
            PreparedStatement insertStmt = conn.prepareStatement(INSERT_SQL);
            for (JSONObject flatObj : flatDataList) {
                setPreparedStatementParams(insertStmt, flatObj, action);
                insertStmt.addBatch();
                count++;

                // 达到批次大小执行批量插入
                if (count % batchSize == 0) {
                    insertStmt.executeBatch();
                }
            }
            // 执行剩余批次
            if (count % batchSize != 0) {
                insertStmt.executeBatch();
            }
            insertStmt.close();
            System.out.println("数据插入完成，共插入 " + count + " 条记录");

        }
    }

    @Test
    @Timeout(6000000)
    public void testSqlDetails () throws Exception{
        // 1. 初始化DuckDB连接和JSON解析器
        try (Connection conn = DriverManager.getConnection(DUCKDB_URL)) {
            // 2. 创建目标表（字段与JSON中key对应）
            createSqlTable(conn);
            // 读取HTML文件
            String jsonData = extractJson(bprFile, "sqlDetailList:");
            JSONArray rootArray = JSONArray.parseArray(jsonData);

            // 4. 批量插入数据到DuckDB
            batchInsertToDuckDB(conn, rootArray, action);

        }
    }


    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS main.trace_log (
                beginTs VARCHAR,
                beginTsInt BIGINT,
                busiaction VARCHAR,
                callrediscount INT,
                costtime INT,
                errorSize INT,
                isProcess VARCHAR,
                leval INT,
                md5Id VARCHAR,
                notclosedconnectioncount INT,
                parentId VARCHAR,
                privateCosttime INT,
                privateRatio DOUBLE,
                pspanid VARCHAR,
                ratio DOUBLE,
                readfromclientbytes BIGINT,
                readfromclienttime INT,
                readresulttime INT,
                readrownum INT,
                rediscosttime INT,
                remoteAddr VARCHAR,
                remoteCallMethod VARCHAR,
                rpccosttime INT,
                rpccount INT,
                server VARCHAR,
                service VARCHAR,
                serviceSource VARCHAR,
                spanid VARCHAR,
                sqlcosttime INT,
                sqlnum INT,
                thread VARCHAR,
                traceid VARCHAR,
                ts VARCHAR,
                userid VARCHAR,
                writetoclientbytes BIGINT,
                writetoclienttime INT,
                ruleName VARCHAR,
                action VARCHAR
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO main.trace_log (
                beginTs, beginTsInt, busiaction, callrediscount, costtime, errorSize,
                isProcess, leval, md5Id, notclosedconnectioncount, parentId, privateCosttime,
                privateRatio, pspanid, ratio, readfromclientbytes, readfromclienttime,
                readresulttime, readrownum, rediscosttime, remoteAddr, remoteCallMethod,
                rpccosttime, rpccount, server, service, serviceSource, spanid, sqlcosttime,
                sqlnum, thread, traceid, ts, userid, writetoclientbytes, writetoclienttime,
                ruleName, action
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

    private final static String sqllog = """
                CREATE TABLE IF NOT EXISTS main.sql_log (
                    spanid VARCHAR,
                    beginTs VARCHAR,
                    costtime VARCHAR,
                    service VARCHAR,
                    linenum VARCHAR,
                    sqlrownum VARCHAR,
                    id VARCHAR,
                    sql_text VARCHAR,
                    action VARCHAR
                );
                """;

    private final static String insertSqllog = """
                INSERT INTO main.sql_log (
                    spanid, beginTs, costtime, service, linenum,
                    sqlrownum, id, sql_text, action
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

    /**
     * 递归拍平嵌套的JSON数组（处理children字段）
     */
    private void flattenJson(JSONArray jsonArray, List<JSONObject> resultList) {
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            // 移除children字段（避免重复处理），并添加到结果列表
            JSONArray children = obj.getJSONArray("children");
            obj.remove("children");
            resultList.add(obj);

            // 递归处理子节点
            if (children != null && !children.isEmpty()) {
                flattenJson(children, resultList);
            }
        }
    }

    /**
     * 设置PreparedStatement参数（处理null值）
     */
    private void setPreparedStatementParams(PreparedStatement stmt, JSONObject obj, String actionName) throws SQLException {
        // 按INSERT_SQL字段顺序设置参数
        stmt.setString(1, obj.getString("beginTs"));
        stmt.setLong(2, obj.getLong("beginTsInt"));
        stmt.setString(3, obj.getString("busiaction"));
        stmt.setInt(4, obj.getInteger("callrediscount"));
        stmt.setInt(5, obj.getInteger("costtime"));
        stmt.setInt(6, obj.getInteger("errorSize"));
        stmt.setString(7, obj.getString("isProcess"));
        stmt.setInt(8, obj.getInteger("leval"));
        stmt.setString(9, obj.getString("md5Id"));
        stmt.setInt(10, obj.getInteger("notclosedconnectioncount"));
        stmt.setString(11, obj.getString("parentId"));
        stmt.setInt(12, obj.getInteger("privateCosttime"));
        stmt.setDouble(13, obj.getDouble("privateRatio"));
        stmt.setString(14, obj.getString("pspanid"));
        stmt.setDouble(15, obj.getDouble("ratio"));
        stmt.setLong(16, obj.getLong("readfromclientbytes"));
        stmt.setInt(17, obj.getInteger("readfromclienttime"));
        stmt.setInt(18, obj.getInteger("readresulttime"));
        stmt.setInt(19, obj.getInteger("readrownum"));
        stmt.setInt(20, obj.getInteger("rediscosttime"));
        stmt.setString(21, obj.getString("remoteAddr"));
        stmt.setString(22, obj.getString("remoteCallMethod"));
        stmt.setInt(23, obj.getInteger("rpccosttime"));
        stmt.setInt(24, obj.getInteger("rpccount"));
        stmt.setString(25, obj.getString("server"));
        stmt.setString(26, obj.getString("service"));
        stmt.setString(27, obj.getString("serviceSource"));
        stmt.setString(28, obj.getString("spanid"));
        stmt.setInt(29, obj.getInteger("sqlcosttime"));
        stmt.setInt(30, obj.getInteger("sqlnum"));
        stmt.setString(31, obj.getString("thread"));
        stmt.setString(32, obj.getString("traceid"));
        stmt.setString(33, obj.getString("ts"));
        stmt.setString(34, obj.getString("userid"));
        stmt.setLong(35, obj.getLong("writetoclientbytes"));
        stmt.setInt(36, obj.getInteger("writetoclienttime"));
        stmt.setString(37, obj.getString("ruleName"));
        stmt.setString(38, actionName);
    }

    /**
     * 从HTML文件中提取指定前缀开头的JSON数据
     */
    private String extractJson(String filePath, String startsWith) throws IOException {
        StringBuilder result = new StringBuilder();
        boolean found = false;

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                // 查找指定前缀开头的行
                if (line.trim().startsWith(startsWith)) {
                    found = true;

                    // 提取前缀后面的JSON部分
                    String jsonPart = line.substring(line.indexOf(startsWith) + startsWith.length()).trim();

                    // 根据JSON类型处理
                    if (jsonPart.startsWith("{")) {
                        // JSON对象，截取到最后一个}的位置
                        int lastBraceIndex = jsonPart.lastIndexOf('}');
                        if (lastBraceIndex != -1) {
                            String jsonPartWithoutLastBrace = jsonPart.substring(0, lastBraceIndex + 1);
                            result.append(jsonPartWithoutLastBrace);
                        }
                    } else if (jsonPart.startsWith("[")) {
                        // JSON数组，截取到最后一个]的位置
                        int lastBracketIndex = jsonPart.lastIndexOf(']');
                        if (lastBracketIndex != -1) {
                            String jsonPartWithoutLastBracket = jsonPart.substring(0, lastBracketIndex + 1);
                            result.append(jsonPartWithoutLastBracket);
                        }
                    }
                    break;
                }
            }
        }

        return found ? result.toString() : null;
    }

    /**
     * 创建DuckDB表（字段匹配JSON中的属性）
     */
    private void createSqlTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqllog);
            System.out.println("表创建成功！");
        }
    }

    /**
     * 批量插入JSON解析后的数据到DuckDB
     */
    private void batchInsertToDuckDB(Connection conn,JSONArray dataList, String actionName) throws SQLException {
        // 关闭自动提交，开启批量插入
        conn.setAutoCommit(false);
        try (PreparedStatement pstmt = conn.prepareStatement(insertSqllog)) {
            for (int i = 0; i < dataList.size(); i++) {
                JSONObject data = dataList.getJSONObject(i);
                // 按字段顺序设置参数（注意JSON中null值处理）
                pstmt.setString(1, getStringValue(data, "spanid"));
                pstmt.setString(2, getStringValue(data, "beginTs"));
                pstmt.setString(3, getStringValue(data, "costtime"));
                pstmt.setString(4, getStringValue(data, "service"));
                pstmt.setString(5, getStringValue(data, "linenum"));
                pstmt.setString(6, getStringValue(data, "sqlrownum"));
                pstmt.setString(7, getStringValue(data, "id"));
                pstmt.setString(8, getStringValue(data, "sql_text"));
                pstmt.setString(9, actionName);

                // 添加到批量操作
                pstmt.addBatch();
            }
            // 执行批量插入
            int[] affectedRows = pstmt.executeBatch();
            conn.commit();
            System.out.println("批量插入完成，共插入 " + affectedRows.length + " 条数据！");
        } catch (SQLException e) {
            conn.rollback(); // 插入失败回滚
            throw e;
        } finally {
            conn.setAutoCommit(true); // 恢复自动提交
        }
    }

    /**
     * 安全获取Map中的字符串值（处理null）
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }


}