package com.data.datafusion.service;

import cn.hutool.core.net.NetUtil;
import com.data.job.TaskLogger;
import org.junit.jupiter.api.Test;

public class HutoolTest {

    @Test
    public void testNetUtil() {
        System.out.println(NetUtil.getLocalhostStr());
    }

    @Test
    public void testTaskLogger() {
        TaskLogger taskLogger = new TaskLogger("1", "test");
        taskLogger.writeLog("中文");
        taskLogger.closeLogFile();
    }
}
