package com.data.job;

import cn.hutool.core.io.FileUtil;
import com.data.job.ETLFlowTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ETLRunnerTest {

    @Test
    @Timeout(6000000)
    public void testMysqlSchema() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/mysql.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

    @Test
    @Timeout(6000000)
    public void testGenerateTableSelectSql() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/generate_table_select_sql.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

    @Test
    @Timeout(6000000)
    public void testHttpInvoke() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/http_invoke_log.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

    @Test
    @Timeout(6000000)
    public void testGenerateSequenceNumber() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/generate_sn_http.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

    @Test
    @Timeout(6000000)
    public void testYmcHttp() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/ymc_http.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

    @Test
    @Timeout(6000000)
    public void testYmcHttpFromDb() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/ymc_http_fromdb.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

    @Test
    @Timeout(6000000)
    public void testKafkaToDuckDB() throws Exception {
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/kafka_to_duckdb.json");
        ETLFlowTask runner = new ETLFlowTask();

        runner.runJob(job);
    }

}
