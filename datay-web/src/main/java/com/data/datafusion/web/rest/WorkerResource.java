package com.data.datafusion.web.rest;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import com.data.datafusion.service.TaskManagementService;
import com.data.job.TaskLogger;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;

@RestController
@RequestMapping("/api/worker")
public class WorkerResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerResource.class);

    // 每次最大读取大小（10MB）
    private static final long MAX_READ_SIZE = 10 * 1024 * 1024;

    // 存储每个任务的最后读取位置
    private static Map<String, Long> lastReadPositions = new HashMap<>();

    private final TaskManagementService taskManagementService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public WorkerResource(TaskManagementService taskManagementService) {
        this.taskManagementService = taskManagementService;
    }

    /**
     * {@code POST  /job-instances/{instanceCode}/stop} : Stop a running job instance.
     *
     * @param instanceCode the instance code of the job instance to stop.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} if the job was stopped successfully,
     * or with status {@code 404 (Not Found)} if the job instance is not running.
     */
    @PostMapping("/{instanceCode}/stop")
    public ResponseEntity<Void> stopJobInstance(@PathVariable String instanceCode) {
        LOG.debug("REST request to stop JobInstance : {}", instanceCode);

        try {
            boolean stopped = taskManagementService.stopTask(instanceCode);

            if (stopped) {
                LOG.info("Job instance stopped successfully: {}", instanceCode);
                return ResponseEntity.ok()
                    .headers(HeaderUtil.createAlert(applicationName, "Job instance stopped successfully", instanceCode))
                    .build();
            } else {
                LOG.warn("Job instance not found or not running: {}", instanceCode);
                return ResponseEntity.notFound()
                    .headers(HeaderUtil.createAlert(applicationName, "Job instance not found or not running", instanceCode))
                    .build();
            }
        } catch (Exception e) {
            LOG.error("Error stopping job instance: {}", instanceCode, e);
            return ResponseEntity.internalServerError()
                .headers(HeaderUtil.createAlert(applicationName, "Error stopping job instance: " + e.getMessage(), instanceCode))
                .build();
        }
    }

    //根据任务实例信息，获取对应的任务执行日志，支持增量获取日志文件内容
    @RequestMapping("/log")
    public ResponseEntity<Map<String, Object>> getTaskLog(
        @RequestParam String jobCode,
        @RequestParam String jobInstanceCode,
        @RequestParam(required = false, defaultValue = "0") long offset,
        @RequestParam(required = false, defaultValue = "10485760") long maxSize
    ) {
        //TODO DAG任务实例日志，需要根据任务实例代码，获取对应的日志文件路径
        Map<String, Object> result = new HashMap<>();

        try {
            String logFileName = getLogFileName(jobCode, jobInstanceCode);
            File logFile = new File(logFileName);

            if (!logFile.exists()) {
                result.put("success", false);
                result.put("message", "日志文件不存在");
                result.put("content", "");
                result.put("newOffset", 0);
                result.put("readSize", 0);
                return ResponseEntity.ok(result);
            }

            // 获取文件大小
            long fileSize = logFile.length();

            // 如果offset为0，表示从头开始读取
            // 如果offset大于文件大小，说明文件被截断或重新创建，从头开始读取
            if (offset > fileSize) {
                offset = 0;
            }

            // 限制最大读取大小，避免内存溢出
            long actualMaxSize = Math.min(maxSize, MAX_READ_SIZE);
            if (actualMaxSize <= 0) {
                actualMaxSize = MAX_READ_SIZE;
            }

            // 计算实际读取大小
            long availableSize = fileSize - offset;
            long readSize = Math.min(availableSize, actualMaxSize);

            // 读取增量内容
            String incrementalContent = readIncrementalContent(logFile, offset, readSize);

            long newOffset = offset + readSize;

            result.put("success", true);
            result.put("message", "获取日志成功");
            result.put("content", incrementalContent);
            result.put("newOffset", newOffset);
            result.put("fileSize", fileSize);
            result.put("readSize", readSize);
            result.put("maxSize", actualMaxSize);
            result.put("hasMore", newOffset < fileSize);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取日志失败: " + e.getMessage());
            result.put("content", "");
            result.put("newOffset", offset);
            result.put("readSize", 0);
            result.put("hasMore", false);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 读取增量日志内容，使用RandomAccessFile提高性能
     */
    private String readIncrementalContent(File logFile, long offset, long readSize) throws IOException {
        if (readSize <= 0) {
            return "";
        }

        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            raf.seek(offset);

            byte[] buffer = new byte[(int) readSize];
            int bytesRead = raf.read(buffer);

            if (bytesRead > 0) {
                return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            }

            return "";
        }
    }

    /**
     * 获取完整的日志内容（用于一次性获取所有日志，但有大小限制）
     */
    @RequestMapping("/log/full")
    public Map<String, Object> getFullTaskLog(
        @RequestParam String jobCode,
        @RequestParam String jobInstanceCode,
        @RequestParam(required = false, defaultValue = "10485760") long maxSize
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            String logFileName = getLogFileName(jobCode, jobInstanceCode);
            File logFile = new File(logFileName);

            if (!logFile.exists()) {
                result.put("success", false);
                result.put("message", "日志文件不存在");
                result.put("content", "");
                result.put("fileSize", 0);
                return result;
            }

            long fileSize = logFile.length();
            long actualMaxSize = Math.min(maxSize, MAX_READ_SIZE);

            String content;
            if (fileSize <= actualMaxSize) {
                // 文件大小在限制范围内，读取全部内容
                FileReader fileReader = new FileReader(logFile, StandardCharsets.UTF_8);
                content = fileReader.readString();
            } else {
                // 文件过大，只读取最后的部分内容
                content = readLastContent(logFile, actualMaxSize);
                result.put("warning", "文件过大，只显示最后 " + actualMaxSize + " 字节内容");
            }

            result.put("success", true);
            result.put("message", "获取日志成功");
            result.put("content", content);
            result.put("fileSize", fileSize);
            result.put("readSize", Math.min(fileSize, actualMaxSize));
            result.put("truncated", fileSize > actualMaxSize);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取完整日志失败: " + e.getMessage());
            result.put("content", "");
            result.put("fileSize", 0);
            result.put("readSize", 0);
        }

        return result;
    }

    /**
     * 读取文件最后的部分内容
     */
    private String readLastContent(File logFile, long maxSize) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long fileSize = raf.length();
            long startPos = Math.max(0, fileSize - maxSize);

            raf.seek(startPos);
            long readSize = fileSize - startPos;

            byte[] buffer = new byte[(int) readSize];
            int bytesRead = raf.read(buffer);

            if (bytesRead > 0) {
                return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            }

            return "";
        }
    }

    /**
     * 获取日志文件信息（文件大小、是否存在等）
     */
    @RequestMapping("/log/info")
    public Map<String, Object> getLogInfo(@RequestParam String jobCode, @RequestParam String jobInstanceCode) {
        Map<String, Object> result = new HashMap<>();

        try {
            String logFileName = getLogFileName(jobCode, jobInstanceCode);
            File logFile = new File(logFileName);

            result.put("success", true);
            result.put("exists", logFile.exists());
            result.put("fileSize", logFile.exists() ? logFile.length() : 0);
            result.put("filePath", logFileName);
            result.put("lastModified", logFile.exists() ? logFile.lastModified() : 0);
            result.put("maxReadSize", MAX_READ_SIZE);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取日志信息失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 重置读取位置（用于重新开始读取）
     */
    @RequestMapping("/log/reset")
    public Map<String, Object> resetLogPosition(@RequestParam String jobCode, @RequestParam String jobInstanceCode) {
        Map<String, Object> result = new HashMap<>();

        try {
            String key = jobCode + "_" + jobInstanceCode;
            lastReadPositions.remove(key);

            result.put("success", true);
            result.put("message", "重置读取位置成功");
            result.put("newOffset", 0);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重置读取位置失败: " + e.getMessage());
        }

        return result;
    }

    private String getLogFileName(String jobCode, String jobInstanceCode) {
        // 获取当前工作目录的绝对路径
        String currentDir = System.getProperty("user.dir");
        // 构建绝对路径的日志文件路径
        return currentDir + "/log/" + jobCode + "/" + jobInstanceCode + TaskLogger.LOG_FILE_SUFFIX;
    }
}
