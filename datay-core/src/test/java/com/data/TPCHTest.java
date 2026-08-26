package com.data;

import cn.hutool.core.io.FileUtil;
import com.data.job.ETLFlowTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class TPCHTest {

    @Test
    @Timeout(6000000)
    public void testTPCH() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/DuckDBToMysql.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testMsqlStreamQuery() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/bigdata.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testDuckDBStreamQuery() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/duckdb_stream_query.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }
}
