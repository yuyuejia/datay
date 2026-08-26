package com.data.job.pm;

import com.data.job.DuckDBEngine;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ExcelDefectCreator {

    private static final String PublicKeyStr = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAypb+rsUmLNREplxycLmJIwtH9vhOJZaNyZyE8GYKRWyXbAV" +
            "wlhObr0mfRehPjsMTTD6Oh9ZlUAyOWov+1HQV2z+bcm2F9O440lm8Ywk1x7zGvXqFRBhHPvwhdTQsQ+/bsu0Wg3wY5em" +
            "JbovE59Mq0/7LgJCAGaCs7GaSPMfnYQwdv0zN5NfLR1M/pqkG5hI+ghahy84z+4eSKqv3Z2WNc5eGY5yN4u8LjrPMK4e" +
            "y6WrUOGW1cj+rdd4EGN3ti8vPWQv8Rj4y8lpljGy+j/byWO4zF3WnQ2GTM9B+fKobjYViRyQCPlcyQ5jh3zGUNvAtQkY" +
            "VbnTV5Tt/ZBKq/+zEYwIDAQAB";

    private static final String ClientSecret = "ee4933c5-009e-11f1-866a-b6e8b97ae37b";
    private static final String ClientID = "PERFORMANCE";

    public static void main(String[] args) throws Exception {
        // 读取Excel文件并提交缺陷
        String excelPath = "/Users/chenjie/Downloads/redis_count_bip.xlsx";
        List<DefectInfo> defectInfos = readExcelWithDuckDB(excelPath);
        submitDefects(defectInfos);
    }

    /**
     * 使用DuckDB读取Excel文件
     */
    public static List<DefectInfo> readExcelWithDuckDB(String excelPath) throws Exception {
        List<DefectInfo> defectInfos = new ArrayList<>();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            // 获取DuckDB连接
            conn = DuckDBEngine.getInstance().getConnection();
            stmt = conn.createStatement();

            // 注册Excel扩展（如果需要）
            stmt.execute("INSTALL 'excel';");
            stmt.execute("LOAD 'excel';");

            // 读取Excel文件中的数据 read_xlsx('test.xlsx', header = true);
            String sql = String.format("SELECT * FROM read_xlsx('%s', header = true);", excelPath);
            rs = stmt.executeQuery(sql);

            // 解析结果集
            while (rs.next()) {
                DefectInfo defectInfo = new DefectInfo();
                // 读取Excel中的字段
                String microservice =MicroService.MS_MAP.get(rs.getString("appCode"));
                if(microservice == null){
                    continue;
                }
                defectInfo.setAppCode(rs.getString("appCode"));
                defectInfo.setMicroService(microservice);
                defectInfo.setBusiAction(rs.getString("busiAction"));
                defectInfo.setRemoteCallMethod(rs.getString("remoteCallMethod"));
                defectInfo.setSqltime(rs.getDouble("time"));
                defectInfo.setSqlcount(rs.getInt("count"));
                
                // 生成合适的title和description
                defectInfo.setTitle(generateTitle(defectInfo));
                defectInfo.setDescription(generateDescription(defectInfo));
                
                defectInfos.add(defectInfo);
                System.out.println("解析结果：" + defectInfo);
            }

            System.out.println("成功读取Excel文件，共找到 " + defectInfos.size() + " 条缺陷信息");

        } finally {
            // 关闭资源
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }

        return defectInfos;
    }

    /**
     * 生成缺陷标题
     */
    public static String generateTitle(DefectInfo defectInfo) {
        return String.format("【性能优化】 微服务 %s 业务 %s 接口 %s ，redis执行次数超 10000 次",
                defectInfo.getAppCode(), defectInfo.getBusiAction(), defectInfo.getRemoteCallMethod());
    }

    /**
     * 生成缺陷描述
     */
    public static String generateDescription(DefectInfo defectInfo) {
        return String.format("微服务: %s\n" +
                "业务动作: %s\n" +
                "远程调用方法: %s\n" +
                "执行时间: %.2f ms\n" +
                "执行次数: %d\n" +
                "问题描述: 该接口redis执行次数过多，需要做批量化处理以提高性能。",
                defectInfo.getAppCode(),
                defectInfo.getBusiAction(),
                defectInfo.getRemoteCallMethod(),
                defectInfo.getSqltime(),
                defectInfo.getSqlcount());
    }

    /**
     * 提交缺陷到PM系统
     */
    public static void submitDefects(List<DefectInfo> defectInfos) throws Exception {
        String clientSecret = ClientSecret + ":" + System.currentTimeMillis();
        String encryptByPublicKey = RSAUtil.encryptByPublicKey(clientSecret, PublicKeyStr);

        PMClient pmClient = new PMClient();

        for (DefectInfo defectInfo : defectInfos) {
            try {
                // 创建缺陷数据对象
                PMClient.DefectData defectData = new PMClient.DefectData();
                defectData.setSeverityLevel("0477ef28-29f8-42b8-bd9f-d1db4d3c5d05"); // 假设使用默认的严重程度
                defectData.setLineId("3058614d-5e02-45b3-8084-33d4c6e6a49b"); // 假设使用默认的产品线
                defectData.setReporter("0000016826"); // 假设使用默认的报告人
                defectData.setAssignee("0000016826"); // 假设使用默认的负责人
                defectData.setDefectType("TECHNOLOGY_DEFECT"); // 技术缺陷类型
                defectData.setPriority("高"); // 假设使用默认的优先级
                defectData.setInfluenceVersion(1816); // 假设使用默认的影响版本
                defectData.setMicroService(defectInfo.getMicroService()); // 使用appCode作为microService
                defectData.setTitle(defectInfo.getTitle()); // 生成的标题
                defectData.setDesc(defectInfo.getDescription()); // 生成的描述

                // 设置属性列表（与测试代码保持一致）
                List<PMClient.DefectProperty> properties = new ArrayList<>();
                properties.add(new PMClient.DefectProperty("char5", "8dbab9db-20bc-4cfa-9b66-693d07ef87fa"));
                properties.add(new PMClient.DefectProperty("char3", "176e87f7-fb35-434b-9220-80efe8ac3e7d"));
                properties.add(new PMClient.DefectProperty("char4", "ddedf854-6eb7-40e8-9929-e2cf0238e27a"));
                properties.add(new PMClient.DefectProperty("char20", "35548010-ff39-4a92-b55a-d14ae1708d8e"));
                defectData.setProperties(properties);

                // 创建缺陷
                PMClient.DefectCreateResult result = pmClient.createDefectWithFullAuth(ClientID, encryptByPublicKey, defectData);
                System.out.println("成功创建缺陷: " + result);

            } catch (Exception e) {
                System.err.println("创建缺陷失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        pmClient.close();
    }

    /**
     * 缺陷信息类，用于存储从Excel中读取的数据
     */
    public static class DefectInfo {
        private String appCode; // 对应microService
        private String microService;
        private String busiAction;
        private String remoteCallMethod;
        private double sqltime;
        private int sqlcount;
        private String title;
        private String description;

        public String getAppCode() {
            return appCode;
        }

        public void setAppCode(String appCode) {
            this.appCode = appCode;
        }

        public String getBusiAction() {
            return busiAction;
        }

        public void setBusiAction(String busiAction) {
            this.busiAction = busiAction;
        }

        public String getRemoteCallMethod() {
            return remoteCallMethod;
        }

        public void setRemoteCallMethod(String remoteCallMethod) {
            this.remoteCallMethod = remoteCallMethod;
        }

        public double getSqltime() {
            return sqltime;
        }

        public void setSqltime(double sqltime) {
            this.sqltime = sqltime;
        }

        public int getSqlcount() {
            return sqlcount;
        }

        public void setSqlcount(int sqlcount) {
            this.sqlcount = sqlcount;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return "DefectInfo{" +
                    "appCode='" + appCode + '\'' +
                    ", busiAction='" + busiAction + '\'' +
                    ", remoteCallMethod='" + remoteCallMethod + '\'' +
                    ", sqltime=" + sqltime +
                    ", sqlcount=" + sqlcount +
                    ", title='" + title + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }

        public String getMicroService() {
            return microService;
        }

        public void setMicroService(String microService) {
            this.microService = microService;
        }
    }
}