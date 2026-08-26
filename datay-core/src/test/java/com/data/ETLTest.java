package com.data;

import cn.hutool.core.io.FileUtil;
import com.data.job.ETLFlowTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ETLTest {

    @Test
    @Timeout(6000000)
    public void testSqlUnitCompatible() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/etl_test1.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testDim_y_hred_refer_political() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/etl_local.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }
    @Test
    @Timeout(6000000)
    public void testDwd() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/etl_dwd.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

    @Test
    @Timeout(6000000)
    public void testDWS() throws Exception {
        // 创建临时配置文件
        String job = FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/etl_dws.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }

}
