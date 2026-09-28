package com.data.status;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地文件状态保存策略（单机部署默认使用）。
 *
 * <p>每个任务编码对应一个 {@code status_<jobCode>.json} 文件。
 */
public class LocalFileStatusStorageStrategy implements StatusStorageStrategy {

    private static final String STATUS_FILE_PREFIX = "status_";
    private static final String STATUS_FILE_SUFFIX = ".json";
    private static final String LEGACY_STATUS_FILE = "etl_status.json";
    private static final Pattern STATUS_FILE_PATTERN = Pattern.compile("^status_(.+)\\.json$");

    private final String basePath;

    public LocalFileStatusStorageStrategy() {
        this(defaultBasePath());
    }

    public LocalFileStatusStorageStrategy(String basePath) {
        if (basePath == null || basePath.trim().isEmpty()) {
            this.basePath = defaultBasePath();
        } else {
            this.basePath = new File(basePath.trim()).getAbsolutePath();
        }
    }

    private static String defaultBasePath() {
        return new File(System.getProperty("user.dir"), "log").getAbsolutePath();
    }

    @Override
    public boolean saveStatus(Map<String, Object> status, String jobCode) {
        File statusFile = new File(getStatusFileName(jobCode));

        File parent = statusFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            System.err.println("状态保存到本地文件失败: 无法创建目录 " + parent);
            return false;
        }

        try (
            FileOutputStream fos = new FileOutputStream(statusFile);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            BufferedWriter writer = new BufferedWriter(osw)
        ) {
            //fastjson2 序列化,格式化输出
            String jsonString = JSON.toJSONString(
                status,
                JSONWriter.Feature.PrettyFormat // 美化格式
            );

            writer.write(jsonString);
            writer.flush();
            return true;
        } catch (IOException e) {
            System.err.println("状态保存到本地文件失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> loadStatus(String jobCode) {
        String fileName = getStatusFileName(jobCode);
        File statusFile = new File(fileName);
        if (!statusFile.exists()) {
            return null; // 如果状态文件不存在，返回null
        }

        try (
            FileInputStream fis = new FileInputStream(statusFile);
            InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(isr)
        ) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }

            if (!jsonContent.isEmpty()) {
                return JSON.parseObject(jsonContent.toString(), Map.class);
            }
        } catch (IOException e) {
            System.err.println("从本地文件恢复状态失败: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean deleteStatus(String jobCode) {
        File statusFile = new File(getStatusFileName(jobCode));
        if (!statusFile.exists()) {
            return true;
        }
        boolean deleted = statusFile.delete();
        if (!deleted) {
            System.err.println("删除本地状态文件失败: " + statusFile.getAbsolutePath());
        }
        return deleted;
    }

    @Override
    public List<String> listJobCodes() {
        List<String> jobCodes = new ArrayList<>();
        File dir = new File(basePath);
        if (!dir.isDirectory()) {
            return jobCodes;
        }
        File[] files = dir.listFiles((d, name) -> STATUS_FILE_PATTERN.matcher(name).matches());
        if (files == null) {
            return jobCodes;
        }
        for (File file : files) {
            Matcher matcher = STATUS_FILE_PATTERN.matcher(file.getName());
            if (matcher.matches()) {
                jobCodes.add(matcher.group(1));
            }
        }
        return jobCodes;
    }

    @Override
    public String getStrategyName() {
        return "LocalFile";
    }

    private String getStatusFileName(String jobCode) {
        if (jobCode == null || jobCode.trim().isEmpty()) {
            return new File(basePath, LEGACY_STATUS_FILE).getAbsolutePath(); // 兼容旧版本
        }
        return new File(basePath, STATUS_FILE_PREFIX + jobCode + STATUS_FILE_SUFFIX).getAbsolutePath();
    }
}
