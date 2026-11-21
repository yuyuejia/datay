package com.data.job.component;

import com.data.job.ETLFlowTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证：本地 CSV / Excel 文件（FileInput） -> MySQL（StreamJdbcOutput）。
 * <p>依赖本地 MySQL（127.0.0.1:3306），可通过系统属性覆盖：
 * <pre>
 *   -Dmysql.url=... -Dmysql.username=... -Dmysql.password=... -Dmysql.dbschema=...
 *   -Dfileinput.csv=/absolute/path/to/employees.csv
 *   -Dfileinput.excel=/absolute/path/to/employees.xlsx
 * </pre>
 */
public class FileInputToMysqlTest {

    private static final String MYSQL_URL = System.getProperty("mysql.url", "jdbc:mysql://127.0.0.1:3306/test_db");
    private static final String MYSQL_USER = System.getProperty("mysql.username", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("mysql.password", "1234qwer");
    private static final String DBSCHEMA = System.getProperty("mysql.dbschema", "test_db");

    private static final String CSV_PATH = System.getProperty("fileinput.csv",
        System.getProperty("user.dir") + "/src/test/fileinput/employees.csv");

    private static final String EXCEL_PATH = System.getProperty("fileinput.excel",
        System.getProperty("user.dir") + "/src/test/fileinput/employees.xlsx");

    @Test
    @Timeout(120000)
    public void testCsvToMysqlAppend() throws Exception {
        String targetTable = "fileinput_employees";
        prepareTable(targetTable);

        String job = buildJob(targetTable, true);

        ETLFlowTask runner = new ETLFlowTask();
        runner.runJob(job);

        verifyAppendResult(targetTable);
    }

    @Test
    @Timeout(120000)
    public void testCsvToMysqlAutoCreate() throws Exception {
        String targetTable = "fileinput_auto_employees";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + targetTable);
        }

        String job = buildJob(targetTable, false);

        ETLFlowTask runner = new ETLFlowTask();
        runner.runJob(job);

        verifyAutoCreateResult(targetTable);
    }

    @Test
    @Timeout(120000)
    public void testCsvWithCustomQuery() throws Exception {
        String targetTable = "fileinput_summary";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + targetTable);
        }

        String customQuery = "SELECT department, COUNT(*) AS emp_count, AVG(salary) AS avg_salary " +
            "FROM read_csv('" + CSV_PATH + "', header = true, auto_detect = true) " +
            "GROUP BY department";

        String job = "{\n" +
            "  \"units\": [\n" +
            "    {\n" +
            "      \".id\": \"FileInput\",\n" +
            "      \".name\": \"FileInput\",\n" +
            "      \"storageType\": \"local\",\n" +
            "      \"filePath\": \"" + CSV_PATH.replace("\\", "/") + "\",\n" +
            "      \"format\": \"csv\",\n" +
            "      \"csvHasHeader\": true,\n" +
            "      \"query\": \"" + customQuery.replace("\\", "\\\\").replace("\"", "\\\"") + "\"\n" +
            "    },\n" +
            "    {\n" +
            "      \".id\": \"StreamJdbcOutput\",\n" +
            "      \".name\": \"StreamJdbcOutput\",\n" +
            "      \"datasource\": {\n" +
            "        \"url\": \"" + MYSQL_URL + "\",\n" +
            "        \"driver\": \"com.mysql.cj.jdbc.Driver\",\n" +
            "        \"username\": \"" + MYSQL_USER + "\",\n" +
            "        \"password\": \"" + MYSQL_PASSWORD + "\",\n" +
            "        \"dbschema\": \"" + DBSCHEMA + "\"\n" +
            "      },\n" +
            "      \"table\": \"" + targetTable + "\",\n" +
            "      \"schema\": \"" + DBSCHEMA + "\",\n" +
            "      \"model\": \"append\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"connections\": [\n" +
            "    { \"sourceId\": \"FileInput\", \"targetId\": \"StreamJdbcOutput\" }\n" +
            "  ]\n" +
            "}";

        ETLFlowTask runner = new ETLFlowTask();
        runner.runJob(job);

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
                "SELECT department, emp_count, avg_salary FROM " + targetTable + " ORDER BY department")) {
                int rows = 0;
                while (rs.next()) {
                    rows++;
                    String dept = rs.getString("department");
                    long count = rs.getLong("emp_count");
                    assertTrue(count >= 1, "department " + dept + " should have >= 1 employee");
                }
                assertEquals(4, rows, "should have 4 departments");
            }
        }
    }

    @Test
    @Timeout(120000)
    public void testExcelToMysqlAppend() throws Exception {
        String targetTable = "fileinput_excel_employees";
        prepareTable(targetTable);

        String job = buildJob(targetTable, true, EXCEL_PATH, "excel", null);

        ETLFlowTask runner = new ETLFlowTask();
        runner.runJob(job);

        verifyAppendResult(targetTable);
    }

    @Test
    @Timeout(120000)
    public void testExcelToMysqlAutoCreate() throws Exception {
        String targetTable = "fileinput_excel_auto_employees";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + targetTable);
        }

        String job = buildJob(targetTable, false, EXCEL_PATH, "excel", null);

        ETLFlowTask runner = new ETLFlowTask();
        runner.runJob(job);

        verifyAutoCreateResult(targetTable);
    }

    @Test
    @Timeout(120000)
    public void testExcelSheetSelection() throws Exception {
        String targetTable = "fileinput_excel_departments";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + targetTable);
            stmt.execute("CREATE TABLE " + targetTable + " (" +
                "department VARCHAR(128), " +
                "emp_count BIGINT" +
                ")");
        }

        String job = buildJob(targetTable, true, EXCEL_PATH, "excel", "Departments");

        ETLFlowTask runner = new ETLFlowTask();
        runner.runJob(job);

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
                "SELECT department, emp_count FROM " + targetTable + " ORDER BY department")) {
                int rows = 0;
                while (rs.next()) {
                    rows++;
                    if ("Engineering".equals(rs.getString("department"))) {
                        assertEquals(4, rs.getLong("emp_count"));
                    }
                }
                assertEquals(4, rows, "should have 4 departments");
            }
        }
    }

    private String buildJob(String targetTable, boolean preCreate) {
        return buildJob(targetTable, preCreate, CSV_PATH, "csv", null);
    }

    private String buildJob(String targetTable, boolean preCreate, String filePath, String format, String excelSheet) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"units\": [\n");
        sb.append("    {\n");
        sb.append("      \".id\": \"FileInput\",\n");
        sb.append("      \".name\": \"FileInput\",\n");
        sb.append("      \"storageType\": \"local\",\n");
        sb.append("      \"filePath\": \"").append(filePath.replace("\\", "/")).append("\",\n");
        sb.append("      \"format\": \"").append(format).append("\",\n");
        if ("csv".equals(format)) {
            sb.append("      \"csvHasHeader\": true\n");
        } else {
            if (excelSheet != null) {
                sb.append("      \"excelSheet\": \"").append(excelSheet).append("\"\n");
            } else {
                sb.append("      \"query\": \"\"\n");
            }
        }
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \".id\": \"StreamJdbcOutput\",\n");
        sb.append("      \".name\": \"StreamJdbcOutput\",\n");
        sb.append("      \"datasource\": {\n");
        sb.append("        \"url\": \"").append(MYSQL_URL).append("\",\n");
        sb.append("        \"driver\": \"com.mysql.cj.jdbc.Driver\",\n");
        sb.append("        \"username\": \"").append(MYSQL_USER).append("\",\n");
        sb.append("        \"password\": \"").append(MYSQL_PASSWORD).append("\",\n");
        sb.append("        \"dbschema\": \"").append(DBSCHEMA).append("\"\n");
        sb.append("      },\n");
        sb.append("      \"table\": \"").append(targetTable).append("\",\n");
        if (!preCreate) {
            sb.append("      \"schema\": \"").append(DBSCHEMA).append("\",\n");
        }
        sb.append("      \"model\": \"append\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"connections\": [\n");
        sb.append("    { \"sourceId\": \"FileInput\", \"targetId\": \"StreamJdbcOutput\" }\n");
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private void prepareTable(String targetTable) throws Exception {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + targetTable);
            stmt.execute("CREATE TABLE " + targetTable + " (" +
                "id BIGINT PRIMARY KEY, " +
                "name VARCHAR(255), " +
                "department VARCHAR(128), " +
                "salary DOUBLE, " +
                "hire_date DATE, " +
                "active TINYINT(1)" +
                ")");
        }
    }

    private void verifyAppendResult(String targetTable) throws Exception {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM " + targetTable)) {
                rs.next();
                assertEquals(10, rs.getInt("cnt"));
            }
            try (ResultSet rs = stmt.executeQuery(
                "SELECT id, name, department, salary, hire_date, active FROM " + targetTable + " WHERE id = 103")) {
                assertTrue(rs.next(), "row id=103 should exist");
                assertEquals(103, rs.getInt("id"));
                assertEquals("Carol White", rs.getString("name"));
                assertEquals("Engineering", rs.getString("department"));
                assertEquals(110000.00, rs.getDouble("salary"), 0.01);
                assertEquals(true, rs.getBoolean("active"));
            }
            try (ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) AS cnt FROM " + targetTable + " WHERE department = 'Engineering'")) {
                rs.next();
                assertEquals(4, rs.getInt("cnt"));
            }
        }
    }

    private void verifyAutoCreateResult(String targetTable) throws Exception {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM " + targetTable)) {
                rs.next();
                assertEquals(10, rs.getInt("cnt"));
            }
            try (ResultSet rs = stmt.executeQuery(
                "SELECT id, name, salary, department FROM " + targetTable + " WHERE name = 'Grace Kim'")) {
                assertTrue(rs.next(), "Grace Kim row should exist");
                assertEquals(107, rs.getInt("id"));
                assertEquals("Finance", rs.getString("department"));
                assertEquals(88000.00, rs.getDouble("salary"), 0.01);
            }
        }
    }

    private Connection getConnection() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
    }
}