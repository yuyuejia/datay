package com.data;

import cn.hutool.core.io.FileUtil;
import com.data.job.ETLFlowTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class BigdataTest {


        @Test
    @Timeout(6000000)
    public void testMysqlFlow1Job() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/bigdata.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

    @Test
    @Timeout(6000000)
    public void testDuckDBStreamQueryJob() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/duckdb_stream_query.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

    @Test
    @Timeout(6000000)
    public void testMysqlToDuckDBJob() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/bigdataToDuckDB.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

    @Test
    @Timeout(6000000)
    public void testHttpListener() throws Exception {
        // 创建临时配置文件
        //        File configFile = createTempConfigFile();
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/httpListener2log.json");
        ETLFlowTask runner = new ETLFlowTask();

        // 调用 runJob 方法
        runner.runJob(job);
        // 断言执行结果或进行其他验证
        // 可以根据具体需求添加更多的断言和验证逻辑
    }

}
