package com.data.datafusion.job;

import com.data.job.ETLFlowTask;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

public class FlowTest {

    /**
     * 手动集成测试：依赖外部 MySQL 与 Binlog 环境。
     *
     * <p>默认跳过；设置环境变量 {@code DATAY_MYSQL_BINLOG_IT=true} 后可执行。</p>
     */
    @Test
    @Timeout(6000000)
    @EnabledIfEnvironmentVariable(named = "DATAY_MYSQL_BINLOG_IT", matches = "true")
    public void testMySQLBinlogInput() throws Exception {
        Path config = Path.of(System.getProperty("user.dir"), "..", "datay-core", "src", "test", "mysqlbinlog.json");
        String job = cn.hutool.core.io.FileUtil.readUtf8String(config.toString());
        ETLFlowTask runner = new ETLFlowTask();
        // 调用 runJob 方法
        runner.runJob(job);
    }
}
