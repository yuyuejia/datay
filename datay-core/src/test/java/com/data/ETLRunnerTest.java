package com.data;

import cn.hutool.core.io.FileUtil;
import com.data.job.ETLFlowTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ETLRunnerTest {

    @Test
    @Timeout(6000000)
    public void testMysqlFlowJob() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/test.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

    @Test
    @Timeout(6000000)
    public void testMysqlToDuckLakeJob() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/mysqlToDuckLake.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

    @Test
    @Timeout(6000000)
    public void testsqlunit() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/sqlunit.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testDuckDBsqlunit() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/duckdbsqlunit.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testIncrColumn() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/incrColum.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testSqlInput() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/sqlinput.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testDuckDBRegister() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/duckdbregister.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testJavaScriptComponent() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/javascript.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testGenerateFlowFile() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/genFlowFile2log.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testTablesIncrColumn() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/tablesIncrColum.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testTablesAll() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/tablesAll.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }
    //    @Test
    //    @Timeout(6000000)
    //    public void testMySQLBinlogInput() throws Exception {
    //        // 创建临时配置文件
    //        String job = cn.hutool.core.io.FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/mysqlbinlog.json");
    //        ETLFlowTask runner = new ETLFlowTask();
    //        // 调用 runJob 方法
    //        runner.runJob(job);
    //    }
    @Test
    @Timeout(6000000)
    public void testStreamSQL() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/streamSQL.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testMysqlToDuckDB() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/MysqlToDuckDB.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testDuckLakeSQL() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/ducklakeregister_duckdbsql.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testHashRouter() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/hashRouter.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testRandomRouter() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/RandomRouter.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testMysqlToPostgres() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/mysqlTopg.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testPostgresToMysql() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/pgToMysql.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testPostgresToDuckDB() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/pgToDuckDB.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testEventToDuckDB() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/EventToDuckdb.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testMysqlToDuckDBByStreamJdbcOutput() throws Exception {
        String job = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/MysqlToDuckDBByStreamJdbcOutput.json");
        ETLFlowTask runner = new ETLFlowTask();
        runner.runJob(job);
    }

}