package com.data;

import cn.hutool.core.io.FileUtil;
import com.data.job.ETLFlowTask;

/**
 * Hello world!
 *
 */
public class DataY
{
    public static void main( String[] args )
    {
        // 默认文件路径
        String defaultFilePath = "/Users/datay/src/test/test.json";
        String filePath = defaultFilePath;
        
        // 检查是否有命令行参数传入
        if (args.length > 0) {
            filePath = args[0];
            System.out.println("使用传入的文件路径: " + filePath);
        } else {
            System.out.println("未传入文件路径，使用默认路径: " + defaultFilePath);
        }
        
        try {
            // 读取指定路径的文件
            String job = FileUtil.readUtf8String(filePath);
            ETLFlowTask runner = new ETLFlowTask();
            // 调用 runJob 方法
            runner.runJob(job);
        } catch (Exception e) {
            System.err.println("作业执行失败: " + e.getMessage());
        }
    }
}