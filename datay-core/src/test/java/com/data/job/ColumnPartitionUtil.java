package com.data.job;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 字符串分区工具类（Scala columnPartition 函数的Java实现）
 */
public class ColumnPartitionUtil {

    /**
     * 根据字符串的最小/最大值和分区数量，生成字符维度的分区边界列表
     * @param min 字符串最小值
     * @param max 字符串最大值
     * @param number 分区数量
     * @return 分区边界字符串列表（数量为number-1）
     */
    public static List<String> columnPartition(String min, String max, int number) {
        List<String> result = new ArrayList<>();
        
        // 1. 入参校验：min或max为空则返回空列表
        if (StringUtils.isEmpty(min) || StringUtils.isEmpty(max)) {
            return result;
        }
        
        // 2. 计算min和max的最短长度
        int lim = Math.min(min.length(), max.length());
        
        // 3. 遍历所有字符，找到最小字符(minC)和最大字符(maxC)
        char minC = min.charAt(0);
        char maxC = min.charAt(0);
        
        // 遍历min的所有字符
        for (char c : min.toCharArray()) {
            minC = (char) Math.min(minC, c);
            maxC = (char) Math.max(maxC, c);
        }
        // 遍历max的所有字符
        for (char c : max.toCharArray()) {
            minC = (char) Math.min(minC, c);
            maxC = (char) Math.max(maxC, c);
        }
        
        // 4. 计算字符总数
        int count = maxC - minC + 1;
        
        int k = 0; // 当前遍历的字符位索引
        int ss = 0; // 累计字符差值
        
        // 5. 按字符位拆分生成分区边界
        while (k < lim && result.size() < number - 1) {
            char c1 = min.charAt(k);
            char c2 = max.charAt(k);
            
            // 计算当前位的字符差值是否能满足剩余分区需求
            if (ss * count + (c2 - c1) <= number - 1) {
                ss = ss * count + (c2 - c1);
                k++;
            } else {
                // 计算拆分步长
                int step = (ss * count + (c2 - c1)) / number;
                
                // 生成number-1个分区边界
                for (int i = 0; i < number - 1; i++) {
                    char c = (char) (c1 + (i + 1) * step);
                    // 复制min的字符数组并修改当前位的字符
                    char[] vv = min.toCharArray();
                    vv[k] = c;
                    result.add(new String(vv));
                }
                k++;
            }
        }
        
        return result;
    }

    // 测试示例
    public static void main(String[] args) {
        // 测试用例1：简单字符串分区
        String min1 = "a1";
        String max1 = "c3";
        List<String> result1 = columnPartition(min1, max1, 3);
        System.out.println("测试用例1结果：" + result1);
//        0018d37f-5253-477d-8b67-6eddebcd680e - fff97d7b-385a-45eb-a3fe-b0669ace653f
        // 测试用例2：空值校验
        List<String> result2 = columnPartition("0018d37f-5253-477d-8b67-6eddebcd680e", "fff97d7b-385a-45eb-a3fe-b0669ace653f", 4);
        System.out.println("测试用例2结果：" + result2);
        
        // 测试用例3：单字符分区
        String min3 = "5";
        String max3 = "9";
        List<String> result3 = columnPartition(min3, max3, 4);
        System.out.println("测试用例3结果：" + result3);
    }
}