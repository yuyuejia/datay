package com.data.job;

import cn.hutool.core.io.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TaskLogger {

    private static final Logger log = LoggerFactory.getLogger(TaskLogger.class);

    public static final String LOG_FILE_PREFIX = "./log/";
    public static final String LOG_FILE_SUFFIX = ".log";

    private OutputStream logWriter;

    private final String jobCode;
    private final String jobInstanceCode;

    public TaskLogger(String jobCode, String jobInstanceCode) {
        this.jobCode = jobCode;
        this.jobInstanceCode = jobInstanceCode;
        initLogFile();
    }

    public void initLogFile() {
        String fileName = getLogFileName();
        File file = FileUtil.file(fileName);
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            this.logWriter = Files.newOutputStream(
                file.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            log.error("create file error:", e);
        }
    }

    private String getLogFileName() {
        // 获取当前工作目录的绝对路径
        String currentDir = System.getProperty("user.dir");
        // 构建绝对路径的日志文件路径
        return currentDir + "/log/" + jobCode + "/" + jobInstanceCode + TaskLogger.LOG_FILE_SUFFIX;
    }

    public void writeLog(String logMessage) {
        try {
            StringBuilder sb = new StringBuilder();
            //添加事件戳，按年月日志格式
            sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append(" - ").append(logMessage).append("\n");
            //写入日志文件,需要支持中文
            System.out.print(sb);
            this.logWriter.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            this.logWriter.flush();
        } catch (IOException e) {
            log.error("write log error:", e);
            throw new RuntimeException(e);
        }
    }

    public void closeLogFile() {
        if (logWriter != null) {
            try {
                logWriter.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    //
    //    private Logger createTaskLogger() {
    //        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    //
    //        String timestamp = String.valueOf(System.currentTimeMillis());
    //
    //        String loggerName = "TASK_" + jobCode + "_" + jobInstanceCode;
    //
    //        // 创建文件appender
    //        FileAppender fileAppender = new FileAppender();
    //        fileAppender.setContext(context);
    //        fileAppender.setName("FILE_" + loggerName);
    //
    //        // 设置日志文件路径，包含任务ID和时间戳
    //        String logFilePath = "D:/log/tasks/" + jobCode + "/" + jobInstanceCode + "_" + timestamp + ".log";
    //        fileAppender.setFile(logFilePath);
    //
    //        // 配置encoder
    //        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    //        encoder.setContext(context);
    //        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level - %msg%n");
    //        encoder.start();
    //
    //        fileAppender.setEncoder(encoder);
    //        fileAppender.start();
    //
    //        // 创建logger
    //        ch.qos.logback.classic.Logger logger = context.getLogger(loggerName);
    //        logger.setAdditive(false); // 不继承父logger的appender
    //        logger.setLevel(Level.INFO);
    //        logger.addAppender(fileAppender);
    //
    //        return logger;
    //    }
    //
    //    // 新增方法：移除动态appender
    //    private void removeDynamicAppender() {
    //        try {
    //            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    //            ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    //
    //            // 根据您的appender命名规则来识别和移除动态appender
    //            String appenderName = "TASK_" + jobCode + "_" + jobInstanceCode;
    //
    //            Appender<ILoggingEvent> appender = rootLogger.getAppender(appenderName);
    //            if (appender != null) {
    //                appender.stop();
    //                rootLogger.detachAppender(appenderName);
    //                log.info("成功移除动态appender: {}", appenderName);
    //            }
    //        } catch (Exception e) {
    //            log.warn("移除动态appender时发生异常: {}", e.getMessage());
    //        }
    //    }

    public OutputStream getLogWriter() {
        return logWriter;
    }
}
