package com.data;

import com.data.job.DuckDBEngine;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.impl.DuckDBConverter;
import com.data.metadata.impl.MysqlConverter;
import com.data.metadata.impl.OracleConverter;
import com.data.metadata.impl.PostgresqlConverter;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class DBUtilsTest {

    public void setUp() {
        // 在每个测试方法之前执行一些初始化操作
        DatabaseConverter.register("mysql", new MysqlConverter());
        DatabaseConverter.register("duckdb", new DuckDBConverter());
        DatabaseConverter.register(DBType.POSTGRESQL.name(), new PostgresqlConverter());
        DatabaseConverter.register(DBType.ORACLE.name(), new OracleConverter());
    }

    @Test
    public void testGetTableMetaData() throws SQLException {
        // 调用被测试方法
        TableMeta tableMeta = DBUtils.getTableMetaData(DBUtils.getConnection(), "dscheduler", "df_job");
        String ddl = DatabaseConverter.generateTableDDL("mysql", tableMeta);
        System.out.println("mysql:" + ddl);
        TableMeta tableMeta2 = DatabaseConverter.convert("mysql", "duckdb", tableMeta);
        tableMeta2.setTable("tmpview_0000_0");
        ddl = DatabaseConverter.generateTableDDL("duckdb", tableMeta2);
        Connection duckdbConn = DuckDBEngine.getInstance().getConnection();
        System.out.println("duckdb:" + ddl);
        duckdbConn.createStatement().execute(ddl);
        TableMeta tableMeta1 = DBUtils.getTableMetaData(duckdbConn, null, "tmpview_0000_0");
        ddl = DatabaseConverter.generateTableDDL("duckdb", tableMeta1);
        System.out.println("duckdb:" + ddl);

    }

    @Test
    public void testGetMetaData() throws SQLException {
        // 调用被测试方法
        TableMeta tableMeta = DBUtils.getTableMetaData(DBUtils.getConnection(), "source", "common_data_types1");
        System.out.println(tableMeta);
        // 生成MySQL DDL并验证
        TableMeta tableMeta2 = DatabaseConverter.convert("mysql", "mysql", tableMeta);
        String ddl = DatabaseConverter.generateTableDDL("mysql", tableMeta);
        System.out.println("MySQL DDL:\n" + ddl);
//        TableMeta tableMeta2 = DatabaseConverter.convert("mysql", "duckdb", tableMeta);
//        ddl = DatabaseConverter.generateTableDDL("duckdb", tableMeta2);
//        System.out.println("DuckDB DDL:\n" + ddl);
//        Connection duckdbConn = DuckDBEngine.getInstance().getConnection();
//        duckdbConn.createStatement().execute("drop table if exists common_data_types");
//        duckdbConn.createStatement().execute(ddl);
//        TableMeta tableMeta1 = DBUtils.getTableMetaData(duckdbConn, null, "common_data_types");
//        tableMeta1 = DatabaseConverter.convert("duckdb", "mysql", tableMeta1);
//        ddl = DatabaseConverter.generateTableDDL("mysql", tableMeta1);
//        System.out.println("DuckDB -》 MySQL DDL:\n" + ddl);
    }

    @Test
    public void testMysqlToPG() throws SQLException {
        // 调用被测试方法
        TableMeta tableMeta = DBUtils.getTableMetaData(DBUtils.getConnection(), "source", "common_data_types");
        // 生成MySQL DDL并验证
        String ddl = DatabaseConverter.generateTableDDL("mysql", tableMeta);
        System.out.println("MySQL DDL:\n" + ddl);
        TableMeta tableMeta2 = DatabaseConverter.convert("mysql", "postgresql", tableMeta);
        ddl = DatabaseConverter.generateTableDDL("postgresql", tableMeta2);
        System.out.println("PostgreSQL DDL:\n" + ddl);
    }

    @Test
    public void testMysqlToDoris() throws SQLException {
        // 调用被测试方法
        TableMeta tableMeta = DBUtils.getTableMetaData(DBUtils.getConnection(), "source", "common_data_types");
        // 生成MySQL DDL并验证
        String ddl = DatabaseConverter.generateTableDDL("mysql", tableMeta);
        System.out.println("MySQL DDL:\n" + ddl);
        TableMeta tableMeta2 = DatabaseConverter.convert("mysql", "doris", tableMeta);
        ddl = DatabaseConverter.generateTableDDL("doris", tableMeta2);
        System.out.println("Doris DDL:\n" + ddl);
    }

    @Test
    public void testGetSchema() throws SQLException {
        // 调用被测试方法
        TableMeta tableMeta = DBUtils.getTableMetaData(DBUtils.getConnection(), "source", "common_data_types");
        System.out.println(tableMeta);
    }

    @Test
    public void testGetTables() throws SQLException {
        // 调用被测试方法
        List<TableMeta> schema = DBUtils.getTableList(DBUtils.getConnection(), "test_db");
        //打印表结构
        for (TableMeta tableMeta : schema) {
            System.out.println(tableMeta.getTable());
            System.out.println(tableMeta.getComment());
        }
    }

    @Test
    public void testOracleToMySQLTypeConversion() throws SQLException {
        setUp();
        // 模拟Oracle表结构（替代真实数据库连接）
        TableMeta oracleTable = new TableMeta(
            "EMPLOYEE",
            List.of(
                new ColumnMeta("ID", "NUMBER", 38, 0, 0), // 整数类型
                new ColumnMeta("NAME", "VARCHAR2", 255, 0, 0), // 变长字符串
                new ColumnMeta("GENDER", "CHAR", 1, 0, 0), // 定长字符串
                new ColumnMeta("SALARY", "NUMBER", 15, 13, 2), // 高精度小数
                // 日期时间类型
                new ColumnMeta("HIRE_DATE", "DATE", 0, 0, 0), // 日期时间
                new ColumnMeta("LOG_TS", "TIMESTAMP", 6, 0, 0), // 时间戳（微秒精度）
                //                new ColumnMeta("TZ_TS", "TIMESTAMP WITH TIME ZONE", 6, 0, 0),// 带时区时间戳

                // 大对象类型
                new ColumnMeta("RESUME", "CLOB", 0, 0, 0), // 大文本数据
                new ColumnMeta("PHOTO", "BLOB", 0, 0, 0) // 二进制
            )
        );

        // 执行类型转换
        TableMeta mysqlTable = DatabaseConverter.convert("oracle", "mysql", oracleTable);

        // 生成MySQL DDL并验证
        String ddl = DatabaseConverter.generateTableDDL("mysql", mysqlTable);
        System.out.println("Oracle->MySQL DDL:\n" + ddl);

        // 断言字段类型转换
        assertIterableEquals(
            List.of(
                "`id` BIGINT,",
                "`name` VARCHAR(255),",
                "`gender` CHAR(1),",
                "`salary` DECIMAL(13,2),",
                "`hire_date` DATE,",
                "`log_ts` DATETIME,",
                "`resume` LONGTEXT,",
                "`photo` BLOB"
            ),
            extractColumnDefinitions(ddl)
        );
    }

    // 辅助方法提取DDL中的列定义
    private List<String> extractColumnDefinitions(String ddl) {
        return Arrays.stream(ddl.split("\n")).filter(line -> line.startsWith("  `")).map(String::trim).toList();
    }

    @Test
    public void testOracleGetTableMetaData() throws SQLException {
        // 1. 从Oracle获取表元数据（示例表）
        Connection oracleConn = DBUtils.getConnection("jdbc:oracle:thin:@//172.20.49.61:11521/ORCL", "system", "oracle2024"); // 假设已实现Oracle连接获取
        TableMeta oracleTable = DBUtils.getTableMetaData(oracleConn, "USR1", "ALL_FIELDS");
        System.out.println(oracleTable);
    }

    @Test
    public void testOracleToMySQLTableConversion() throws SQLException {
        // 初始化转换器注册（在setUp中已包含）
        setUp();

        // 1. 从Oracle获取表元数据（示例表）
        Connection oracleConn = DBUtils.getConnection("jdbc:oracle:thin:@//172.20.49.61:11521/ORCL", "system", "oracle2024"); // 假设已实现Oracle连接获取
        TableMeta oracleTable = DBUtils.getTableMetaData(oracleConn, "USR1", "ALL_FIELDS_FROM_ORACLE");
        System.out.println(oracleTable);

        // 2. 执行元数据转换
        TableMeta mysqlTable = DatabaseConverter.convert("oracle", "mysql", oracleTable);

        // 3. 生成MySQL DDL
        String ddl = DatabaseConverter.generateTableDDL("mysql", mysqlTable);
        Connection connection = DBUtils.getConnection(); // 假设已实现Oracle连接获取
        DBUtils.execute(connection, "drop table " + mysqlTable.getTable().toLowerCase());
        DBUtils.execute(connection, ddl);
        System.out.println("Generated MySQL DDL:\n" + ddl);
    }

    @Test
    public void testOracleToPostgresqlTableConversion() throws SQLException {
        // 初始化转换器注册（在setUp中已包含）
        setUp();

        // 1. 从Oracle获取表元数据（示例表）
        Connection oracleConn = DBUtils.getConnection("jdbc:oracle:thin:@//172.20.49.61:11521/ORCL", "system", "oracle2024"); // 假设已实现Oracle连接获取
        TableMeta oracleTable = DBUtils.getTableMetaData(oracleConn, "USR1", "ALL_FIELDS_FROM_ORACLE");
        System.out.println(oracleTable);

        // 2. 执行元数据转换
        TableMeta mysqlTable = DatabaseConverter.convert("oracle", "postgresql", oracleTable);

        // 3. 生成MySQL DDL
        String ddl = DatabaseConverter.generateTableDDL("postgresql", mysqlTable);
        System.out.println("Generated postgresql DDL:\n" + ddl);
        Connection pgConn = DBUtils.getConnection("jdbc:postgresql://172.20.49.61:25432/postgres", "postgres", "Yonyou@2023"); // 假设已实现Oracle连接获取
        DBUtils.execute(pgConn, "drop table " + mysqlTable.getTable().toLowerCase());
        DBUtils.execute(pgConn, ddl);
    }

    @Test
    public void testMysqlToOracleTableConversion() throws SQLException {
        // 初始化转换器注册（在setUp中已包含）
        setUp();

        // 1. 从Oracle获取表元数据（示例表）
        Connection connection = DBUtils.getConnection(); // 假设已实现Oracle连接获取
        TableMeta table = DBUtils.getTableMetaData(connection, "dscheduler", "all_fields_from_oracle");
        System.out.println(table);

        // 2. 执行元数据转换
        TableMeta newTable = DatabaseConverter.convert("mysql", "oracle", table);

        // 3. 生成MySQL DDL
        String ddl = DatabaseConverter.generateTableDDL("oracle", newTable);
        System.out.println("Generated oracle DDL:\n" + ddl);
    }

    @Test
    public void testMysqlTableConversion() throws SQLException {
        // 初始化转换器注册（在setUp中已包含）
        setUp();

        // 1. 从Oracle获取表元数据（示例表）
        Connection connection = DBUtils.getConnection(); // 假设已实现Oracle连接获取
        TableMeta table = DBUtils.getTableMetaData(connection, "dscheduler", "product");
        System.out.println(table);

        // 2. 执行元数据转换
        TableMeta newTable = DatabaseConverter.convert("mysql", "postgresql", table);
        newTable = DatabaseConverter.convert("mysql", "duckdb", table);
        newTable = DatabaseConverter.convert("mysql", "oracle", table);
        // 3. postgresql DDL
        //        String ddl = DatabaseMetaConverter.generateTableDDL("postgresql", newTable);
        //        System.out.println("Generated postgresql DDL:\n" + ddl);
        //        Connection pgConn = DBUtils.getConnection("jdbc:postgresql://172.20.49.61:25432/postgres","postgres","Yonyou@2023"); // 假设已实现Oracle连接获取
        //        DBUtils.execute(pgConn, ddl);
    }

    @Test
    public void testPGTableConversion() throws SQLException {
        // 初始化转换器注册（在setUp中已包含）
        setUp();

        // 1. 从Oracle获取表元数据（示例表）
        Connection pgConn = DBUtils.getConnection("jdbc:postgresql://172.20.49.61:25432/postgres", "postgres", "Yonyou@2023"); // 假设已实现Oracle连接获取
        TableMeta table = DBUtils.getTableMetaData(pgConn, "public", "ALL_FIELDS_FROM_ORACLE".toLowerCase());
        System.out.println(table);

        // 2. 执行元数据转换
        TableMeta newTable = DatabaseConverter.convert("postgresql", "duckdb", table);
        newTable = DatabaseConverter.convert("postgresql", "mysql", table);
        newTable = DatabaseConverter.convert("postgresql", "oracle", table);
        // 3. postgresql DDL
        //        String ddl = DatabaseMetaConverter.generateTableDDL("duckdb", newTable);
        //        System.out.println("Generated postgresql DDL:\n" + ddl);
        //        Connection connection = DuckDBEngine.getInstance().getConnection();
        //        DBUtils.execute(connection, ddl);
    }

    @Test
    public void testOracleTableConversion() throws SQLException {
        // 初始化转换器注册（在setUp中已包含）
        setUp();

        // 1. 从Oracle获取表元数据（示例表）
        Connection oracleConn = DBUtils.getConnection("jdbc:oracle:thin:@//172.20.49.61:11521/ORCL", "system", "oracle2024"); // 假设已实现Oracle连接获取
        TableMeta table = DBUtils.getTableMetaData(oracleConn, "USR1", "ALL_FIELDS_FROM_ORACLE");
        System.out.println(table);

        // 2. 执行元数据转换
        TableMeta newTable = DatabaseConverter.convert("oracle", "duckdb", table);
        newTable = DatabaseConverter.convert("oracle", "mysql", table);
        //        newTable = DatabaseMetaConverter.convert("oracle","postgresql",  table);

        // 3. postgresql DDL
        String ddl = DatabaseConverter.generateTableDDL("mysql", newTable);
        System.out.println("Generated postgresql DDL:\n" + ddl);
        //        Connection connection = DuckDBEngine.getInstance().getConnection();
        //        DBUtils.execute(connection, ddl);
    }

    @Test
    public void testDuckDBTableConversion() throws SQLException {
        // 初始化转换器注册（在setUp中已包含）
        setUp();

        // 1. 从Oracle获取表元数据（示例表）
        Connection connection = DuckDBEngine.getInstance().getConnection();
        TableMeta table = DBUtils.getTableMetaData(connection, "dscheduler", "product");
        System.out.println(table);

        // 2. 执行元数据转换
        TableMeta newTable = DatabaseConverter.convert("mysql", "postgresql", table);
        newTable = DatabaseConverter.convert("mysql", "duckdb", table);
        newTable = DatabaseConverter.convert("mysql", "oracle", table);
        // 3. postgresql DDL
        //        String ddl = DatabaseMetaConverter.generateTableDDL("postgresql", newTable);
        //        System.out.println("Generated postgresql DDL:\n" + ddl);
        //        Connection pgConn = DBUtils.getConnection("jdbc:postgresql://172.20.49.61:25432/postgres","postgres","Yonyou@2023"); // 假设已实现Oracle连接获取
        //        DBUtils.execute(pgConn, ddl);
    }

    @Test
    public void testSplitSqlStatements() {
        String sql = "-- 先聚合, 再笛卡尔积\n-- 用全排列的维度去关联原始数据，将金额补齐，没关联到的置0\n-- 目的是之后用窗口函数计算期初期末的时候，保证最细粒度下，每个期间都有值，这样在后续汇总的时候才能保证窗口函数计算出来的数据能够被正确汇总 。\nSELECT uuid() AS id, a.accentity_id AS accentity_id, a.accsubject_direction AS accsubject_direction, a.project_id AS project_id, a.currtype_id AS currtype_id\n\t, a.inventory_org_id AS inventory_org_id, a.material_sort AS material_sort, a.material_id AS material_id, a.accbook_id AS accbook_id, a.profit_center_id AS profit_center_id\n\t, a.accperiodscheme AS accperiodscheme, a.ytenant_id AS ytenant_id, a.customer_id AS customer_id, a.customer_type_id AS customer_type_id, a.supplier_id AS supplier_id\n\t, a.period_id AS period_id, a.bank_account AS bank_account, a.fi_event_name AS fi_event_name, a.fi_event_id AS fi_event_id, a.period_code AS period_code\n\t, a.warehouse_id AS warehouse_id, a.busi_dept_id AS busi_dept_id, a.product_line_id AS product_line_id, a.cash_flow_main_item AS cash_flow_main_item, a.cost_item_id AS cost_item_id\n\t, a.accsubject_id AS accsubject_id, a.accentity_id_orgid AS accentity_id_orgid, a.settle_redflush_type AS settle_redflush_type, a.period_begin_type AS period_begin_type, a.gl_voucher_no AS gl_voucher_no\n\t, a.send_gl_state AS send_gl_state, a.fi_app_id AS fi_app_id, a.bln_period_begin AS bln_period_begin\n\t, md5(concat_ws('_', coalesce(accentity_id, 'NULL'), coalesce(accsubject_direction, 'NULL'), coalesce(project_id, 'NULL'), coalesce(currtype_id, 'NULL'), coalesce(inventory_org_id, 'NULL'), coalesce(material_sort, 'NULL'), coalesce(material_id, 'NULL'), coalesce(accbook_id, 'NULL'), coalesce(profit_center_id, 'NULL'), coalesce(accperiodscheme, 'NULL'), coalesce(ytenant_id, 'NULL'), coalesce(customer_id, 'NULL'), coalesce(customer_type_id, 'NULL'), coalesce(supplier_id, 'NULL'), coalesce(bank_account, 'NULL'), coalesce(fi_event_name, 'NULL'), coalesce(fi_event_id, 'NULL'), coalesce(warehouse_id, 'NULL'), coalesce(busi_dept_id, 'NULL'), coalesce(product_line_id, 'NULL'), coalesce(cash_flow_main_item, 'NULL'), coalesce(cost_item_id, 'NULL'), coalesce(accsubject_id, 'NULL'), coalesce(accentity_id_orgid, 'NULL'))) AS partial_dim_md5\n\t, md5(concat_ws('_', coalesce(accentity_id, 'NULL'), coalesce(accsubject_direction, 'NULL'), coalesce(project_id, 'NULL'), coalesce(currtype_id, 'NULL'), coalesce(inventory_org_id, 'NULL'), coalesce(material_sort, 'NULL'), coalesce(material_id, 'NULL'), coalesce(accbook_id, 'NULL'), coalesce(profit_center_id, 'NULL'), coalesce(accperiodscheme, 'NULL'), coalesce(ytenant_id, 'NULL'), coalesce(customer_id, 'NULL'), coalesce(customer_type_id, 'NULL'), coalesce(supplier_id, 'NULL'), coalesce(bank_account, 'NULL'), coalesce(fi_event_name, 'NULL'), coalesce(fi_event_id, 'NULL'), coalesce(warehouse_id, 'NULL'), coalesce(busi_dept_id, 'NULL'), coalesce(product_line_id, 'NULL'), coalesce(cash_flow_main_item, 'NULL'), coalesce(cost_item_id, 'NULL'), coalesce(accsubject_id, 'NULL'), coalesce(accentity_id_orgid, 'NULL'), coalesce(substr(period_code, 1, 4), 'NULL'))) AS partial_dim_with_year_md5\n\t, coalesce(debit_amount, 0) AS debit_amount\n\t, coalesce(credit_amount, 0) AS credit_amount\nFROM (\n\t-- 笛卡尔积，生成期间和剩余维度的排列组合\n\tSELECT a.accentity_id, a.accsubject_direction, a.project_id, a.currtype_id, a.inventory_org_id\n\t\t, a.material_sort, a.material_id, a.accbook_id, a.profit_center_id, a.accperiodscheme\n\t\t, a.ytenant_id, a.customer_id, a.customer_type_id, a.supplier_id, a.bank_account\n\t\t, a.fi_event_name, a.fi_event_id, a.warehouse_id, a.busi_dept_id, a.product_line_id\n\t\t, a.cash_flow_main_item, a.cost_item_id, a.accsubject_id, a.accentity_id_orgid, a.settle_redflush_type\n\t\t, a.period_begin_type, a.gl_voucher_no, a.send_gl_state, a.fi_app_id, a.bln_period_begin\n\t\t, b.period_code, period_id, a.partial_dim_md5\n\t\t, md5(concat_ws('_', coalesce(accentity_id, 'NULL'), coalesce(accsubject_direction, 'NULL'), coalesce(project_id, 'NULL'), coalesce(currtype_id, 'NULL'), coalesce(inventory_org_id, 'NULL'), coalesce(material_sort, 'NULL'), coalesce(material_id, 'NULL'), coalesce(accbook_id, 'NULL'), coalesce(profit_center_id, 'NULL'), coalesce(accperiodscheme, 'NULL'), coalesce(ytenant_id, 'NULL'), coalesce(customer_id, 'NULL'), coalesce(customer_type_id, 'NULL'), coalesce(supplier_id, 'NULL'), coalesce(bank_account, 'NULL'), coalesce(fi_event_name, 'NULL'), coalesce(fi_event_id, 'NULL'), coalesce(warehouse_id, 'NULL'), coalesce(busi_dept_id, 'NULL'), coalesce(product_line_id, 'NULL'), coalesce(cash_flow_main_item, 'NULL'), coalesce(cost_item_id, 'NULL'), coalesce(accsubject_id, 'NULL'), coalesce(accentity_id_orgid, 'NULL'), coalesce(settle_redflush_type, 'NULL'), coalesce(period_begin_type, 'NULL'), coalesce(gl_voucher_no, 'NULL'), coalesce(send_gl_state, 'NULL'), coalesce(fi_app_id, 'NULL'), coalesce(bln_period_begin, 'NULL'), coalesce(substr(period_code, 1, 4), 'NULL'))) AS partial_dim_with_year_md5\n\tFROM (\n\t\t-- 除了期间，剩余维度的枚举值\n\t\tSELECT a.accentity_id, a.accsubject_direction, a.project_id, a.currtype_id, a.inventory_org_id\n\t\t\t, a.material_sort, a.material_id, a.accbook_id, a.profit_center_id, a.accperiodscheme\n\t\t\t, a.ytenant_id, a.customer_id, a.customer_type_id, a.supplier_id, a.bank_account\n\t\t\t, a.fi_event_name, a.fi_event_id, a.warehouse_id, a.busi_dept_id, a.product_line_id\n\t\t\t, a.cash_flow_main_item, a.cost_item_id, a.accsubject_id, a.accentity_id_orgid, a.settle_redflush_type\n\t\t\t, a.period_begin_type, a.gl_voucher_no, a.send_gl_state, a.fi_app_id, a.bln_period_begin\n\t\t\t, partial_dim_md5, MAX(period_code) AS max_value, MIN(period_code) AS min_value\n\t\tFROM base_query a\n\t\tGROUP BY a.accentity_id, a.accsubject_direction, a.project_id, a.currtype_id, a.inventory_org_id, a.material_sort, a.material_id, a.accbook_id, a.profit_center_id, a.accperiodscheme, a.ytenant_id, a.customer_id, a.customer_type_id, a.supplier_id, a.bank_account, a.fi_event_name, a.fi_event_id, a.warehouse_id, a.busi_dept_id, a.product_line_id, a.cash_flow_main_item, a.cost_item_id, a.accsubject_id, a.accentity_id_orgid, a.settle_redflush_type, a.period_begin_type, a.gl_voucher_no, a.send_gl_state, a.fi_app_id, a.bln_period_begin, partial_dim_md5\n\t) a\n\t\tCROSS JOIN (\n\t\t\tSELECT period_code, period_id\n\t\t\tFROM (\n\t\t\t\tSELECT period_code, period_id, row_number() OVER (PARTITION BY period_code ORDER BY period_id) AS rn\n\t\t\t\tFROM base_query\n\t\t\t) a\n\t\t\tWHERE rn = 1\n\t\t) b\n\tWHERE b.period_code >= a.min_value\n) a\n\tLEFT JOIN (\n\t\t-- 原始汇总表数据，用于取出度量字段\n\t\tSELECT period_code AS timeColumnName, coalesce(debit_amount, 0) AS debit_amount\n\t\t\t, coalesce(credit_amount, 0) AS credit_amount, partial_dim_md5\n\t\tFROM base_query\n\t) b\n\tON a.partial_dim_md5 = b.partial_dim_md5\n\t\tAND a.period_code = b.timeColumnName";
        List<String> expected = splitSqlStatements(sql);
        for (String statement : expected) {
            System.out.println("statement: " + statement);
        }
//        assertIterableEquals(expected, splitSqlStatements(sql));
    }

        @Test
        public void testSplitSqlStatementsWithMultiLineComment() {
            String sqlWithMultiLineComment = "SELECT * FROM -- name\n" +
                    "table1;  ---- table1注释\n" +
                    " -- table1注释\n" +
                    "-- table2注释\n" +
                    "/*\n" +
                    "   --这是一个多行注释\n" +
                    "*/SELECT * FROM table2 where name='张--三'";
            for (String statement : splitSqlStatements(sqlWithMultiLineComment)) {
                System.out.println("statement: " + statement);
            }
//            List<String> expectedWithMultiLineComment = Arrays.asList(
//                    "SELECT * FROM \n" +
//                            "table1",
//                    "SELECT * FROM table2 where name='张--三'"
//            );
//            assertIterableEquals(expectedWithMultiLineComment, splitSqlStatements(sqlWithMultiLineComment));
        }


    /**
     * 分割SQL语句（按分号分割）
     */
    private List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        if (sql == null || sql.trim().isEmpty()) {
            return statements;
        }

        // 第一步：按行处理，去除行注释（-- 开始到行尾）
        StringBuilder cleanedSql = new StringBuilder();
        String[] lines = sql.split("\n");

        for (String line : lines) {
            // 查找行注释开始位置
            int commentStart = -1;
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);

                // 处理引号状态
                if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                } else if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                }

                // 检查行注释开始（--），但不在引号内
                if (!inSingleQuote && !inDoubleQuote && i < line.length() - 1 &&
                        c == '-' && line.charAt(i + 1) == '-') {
                    commentStart = i;
                    break;
                }
            }

            // 如果找到行注释，只保留注释之前的部分
            if (commentStart >= 0) {
                cleanedSql.append(line.substring(0, commentStart).trim());
            } else {
                cleanedSql.append(line);
            }
            cleanedSql.append("\n");
        }

        // 第二步：处理多行注释（/* */）
        StringBuilder finalSql = new StringBuilder();
        boolean inMultiLineComment = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        String processedSql = cleanedSql.toString();
        for (int i = 0; i < processedSql.length(); i++) {
            char c = processedSql.charAt(i);

            // 处理多行注释开始（/*）
            if (!inSingleQuote && !inDoubleQuote && !inMultiLineComment &&
                    i < processedSql.length() - 1 && c == '/' && processedSql.charAt(i + 1) == '*') {
                inMultiLineComment = true;
                i++; // 跳过下一个字符 '*'
                continue;
            }

            // 处理多行注释结束（*/）
            if (inMultiLineComment && i < processedSql.length() - 1 &&
                    c == '*' && processedSql.charAt(i + 1) == '/') {
                inMultiLineComment = false;
                i++; // 跳过下一个字符 '/'
                continue;
            }

            // 处理引号状态（不在多行注释中）
            if (!inMultiLineComment) {
                if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                } else if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                }
            }

            // 只添加不在多行注释中的字符
            if (!inMultiLineComment) {
                finalSql.append(c);
            }
        }

        // 第三步：按分号分割语句，忽略引号内的分号
        StringBuilder currentStatement = new StringBuilder();
        inSingleQuote = false;
        inDoubleQuote = false;

        String finalSqlStr = finalSql.toString();
        for (int i = 0; i < finalSqlStr.length(); i++) {
            char c = finalSqlStr.charAt(i);

            // 处理引号状态
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }

            // 如果是分号且不在引号内，则分割语句
            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                String statement = currentStatement.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                currentStatement.setLength(0);
            } else {
                currentStatement.append(c);
            }
        }

        // 处理最后一条语句（如果没有分号结尾）
        String lastStatement = currentStatement.toString().trim();
        if (!lastStatement.isEmpty()) {
            statements.add(lastStatement);
        }

        return statements;
    }

}
