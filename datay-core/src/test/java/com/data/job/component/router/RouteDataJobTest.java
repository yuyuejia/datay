package com.data.job.component.router;

import cn.hutool.core.io.FileUtil;
import com.data.job.ETLFlowTask;
import com.data.job.TaskInstance;
import com.data.job.TaskLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证：GenerateFlowFile 生成测试数据 -> RouteOnAttribute 按数据项分流 -> LogFlowFile 记录日志。
 */
public class RouteDataJobTest {

    @Test
    @Timeout(120000)
    public void testRouteByDataItemToLog() throws Exception {
        String jobJson = FileUtil.readUtf8String(System.getProperty("user.dir") + "/src/test/routeData2log.json");

        String jobCode = "route-data-test";
        String instanceCode = "route-data-" + System.currentTimeMillis();

        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setJobCode(jobCode);
        taskInstance.setInstanceCode(instanceCode);
        taskInstance.setType("ETL");

        TaskLogger logger = new TaskLogger(jobCode, instanceCode);
        ETLFlowTask task = new ETLFlowTask(taskInstance, logger);
        try {
            task.runJob(jobJson);
        } finally {
            logger.closeLogFile();
        }

        String logFile = System.getProperty("user.dir") + "/log/" + jobCode + "/" + instanceCode + ".log";
        String log = FileUtil.readUtf8String(logFile);

        String vipLine = lineOf(log, "log_vip");
        String normalLine = lineOf(log, "log_normal");
        String otherLine = lineOf(log, "log_other");

        assertNotNull(vipLine, "vip 连线应收到数据");
        assertTrue(vipLine.contains("{\"id\":1,\"type\":\"vip\"}"), "vip 连线应包含 id=1");
        assertTrue(vipLine.contains("{\"id\":3,\"type\":\"vip\"}"), "vip 连线应包含 id=3");
        assertFalse(vipLine.contains("\"id\":2"), "vip 连线不应包含 normal 数据");

        assertNotNull(normalLine, "normal 连线应收到数据");
        assertTrue(normalLine.contains("{\"id\":2,\"type\":\"normal\"}"), "normal 连线应包含 id=2");
        assertTrue(normalLine.contains("{\"id\":5,\"type\":\"normal\"}"), "normal 连线应包含 id=5");

        assertNotNull(otherLine, "other 兜底连线应收到未匹配数据");
        assertTrue(otherLine.contains("{\"id\":4,\"type\":\"unknown\"}"), "other 兜底连线应包含 id=4");
    }

    /**
     * 在日志中查找指定 LogFlowFile 节点输出数据内容的那一行。
     */
    private String lineOf(String log, String nodeId) {
        for (String line : log.split("\\r?\\n")) {
            if (line.contains("[LogFlowFile-" + nodeId + "]") && line.contains("数据内容:")) {
                return line;
            }
        }
        return null;
    }
}
