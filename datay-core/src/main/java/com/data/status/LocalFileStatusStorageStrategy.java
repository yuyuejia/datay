package com.data.status;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 本地文件保存策略
 */
public class LocalFileStatusStorageStrategy implements StatusStorageStrategy {

    private static final String STATUS_FILE_PREFIX = "./log/status_";
    private static final String STATUS_FILE_SUFFIX = ".json";

    @Override
    public boolean saveStatus(Map<String, Object> status, String jobInstanceId) {
        String fileName = getStatusFileName(jobInstanceId);
        File statusFile = new File(fileName);

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

//            System.out.println("保存的文件路径: " + fileName);
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
    public String getStrategyName() {
        return "LocalFile";
    }

    private String getStatusFileName(String jobInstanceId) {
        // 获取当前工作目录的绝对路径
        String currentDir = System.getProperty("user.dir");
        if (jobInstanceId == null || jobInstanceId.trim().isEmpty()) {
            return currentDir + "/log/etl_status.json"; // 兼容旧版本
        }
        return currentDir + "/log/status_" + jobInstanceId + STATUS_FILE_SUFFIX;
    }
}
