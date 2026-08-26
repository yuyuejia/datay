package com.data;

import com.data.job.ETLFlowTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class FlowTest {

    @Test
    @Timeout(6000000)
    public void testMySQLBinlogInput() throws Exception {
        // 创建临时配置文件
        String job = cn.hutool.core.io.FileUtil.readUtf8String("/Users/chenjie/code/datay/src/test/mysqlbinlog.json");
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }
}
