package com.data.job.pm;

import com.data.job.DuckDBEngine;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CodeScanDefectCreator {

    private static final String PublicKeyStr = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAypb+rsUmLNREplxycLmJIwtH9vhOJZaNyZyE8GYKRWyXbAV" +
            "wlhObr0mfRehPjsMTTD6Oh9ZlUAyOWov+1HQV2z+bcm2F9O440lm8Ywk1x7zGvXqFRBhHPvwhdTQsQ+/bsu0Wg3wY5em" +
            "JbovE59Mq0/7LgJCAGaCs7GaSPMfnYQwdv0zN5NfLR1M/pqkG5hI+ghahy84z+4eSKqv3Z2WNc5eGY5yN4u8LjrPMK4e" +
            "y6WrUOGW1cj+rdd4EGN3ti8vPWQv8Rj4y8lpljGy+j/byWO4zF3WnQ2GTM9B+fKobjYViRyQCPlcyQ5jh3zGUNvAtQkY" +
            "VbnTV5Tt/ZBKq/+zEYwIDAQAB";

    private static final String ClientSecret = "ee4933c5-009e-11f1-866a-b6e8b97ae37b";
    private static final String ClientID = "PERFORMANCE";

    private static final String App_Code = "yonbip-cpu-sourcing";
    private static final String File_Prefix = "/Users/chenjie/code/performance-analyzer/docs";

    public static void main(String[] args) throws Exception {
        // 读取Excel文件并提交缺陷
        String excelPath = "/Users/chenjie/Downloads/副本代码性能问题扫描_new.xlsx";
        List<DefectInfo> defectInfos = readExcelWithDuckDB(excelPath);
        submitDefects(defectInfos);
    }

    /**
     * 使用DuckDB读取Excel文件
     */
    public static List<DefectInfo> readExcelWithDuckDB(String excelPath) throws Exception {
        List<DefectInfo> defectInfos = new ArrayList<>();
        Set<String> processedFiles = new HashSet<>();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            conn = DuckDBEngine.getInstance().getConnection("defect_submit_records.db");
            stmt = conn.createStatement();

            stmt.execute("INSTALL 'excel';");
            stmt.execute("LOAD 'excel';");

            String sql = String.format("SELECT * FROM read_xlsx('%s', header = true);", excelPath);
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String file = rs.getString("文件");
                String problemType = rs.getString("问题");
                
                file = file.trim().replace("./", "/").replace(":","");
                
                if (processedFiles.contains(file)) {
                    continue;
                }
                processedFiles.add(file);

                DefectInfo defectInfo = new DefectInfo();
                String microservice = MicroService.MS_MAP.get(App_Code);
                if (microservice == null) {
                    continue;
                }
                defectInfo.setAppCode(App_Code);
                defectInfo.setFile(file);
                defectInfo.setMicroService(microservice);

                defectInfo.setTitle(String.format("【循环调用专项】 文件：%s ，问题类型：%s", file, problemType));
                String fileContent = FileUtils.readFileToString(new File(File_Prefix + file), "UTF-8");
                fileContent = fileContent.replace("\n", "<br>");
                fileContent = "报告在线查看地址：http://172.20.38.214:3001/ ，<br>" + fileContent;
                defectInfo.setDescription(fileContent);

                defectInfos.add(defectInfo);
                System.out.println("解析结果：" + defectInfo);
            }

            System.out.println("成功读取Excel文件，去重后共找到 " + defectInfos.size() + " 条缺陷信息");

        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }

        return defectInfos;
    }


    private static final String DEFECT_RECORDS_TABLE = "defect_submit_records";

    /**
     * 初始化缺陷提交记录表
     */
    private static void initDefectRecordsTable() throws Exception {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = DuckDBEngine.getInstance().getConnection("defect_submit_records.db");
            stmt = conn.createStatement();
            
            String createTableSql = String.format(
                "CREATE TABLE IF NOT EXISTS main.%s (" +
                "file VARCHAR, " +
                "title VARCHAR, " +
                "app_code VARCHAR)", DEFECT_RECORDS_TABLE);
            
            stmt.execute(createTableSql);
        } finally {
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }

    /**
     * 检查缺陷是否已提交过
     */
    private static boolean isDefectSubmitted(String file) throws Exception {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = DuckDBEngine.getInstance().getConnection("defect_submit_records.db");
            stmt = conn.createStatement();
            
            String querySql = String.format("SELECT COUNT(*) FROM main.%s WHERE file = ?", DEFECT_RECORDS_TABLE);
            java.sql.PreparedStatement pstmt = conn.prepareStatement(querySql);
            pstmt.setString(1, file);
            rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }

    /**
     * 保存缺陷提交记录
     */
    private static void saveDefectRecord(DefectInfo defectInfo) throws Exception {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = DuckDBEngine.getInstance().getConnection("defect_submit_records.db");
            
            String insertSql = String.format(
                "INSERT INTO main.%s (file, title, app_code) VALUES (?, ?, ?)",
                DEFECT_RECORDS_TABLE);
            java.sql.PreparedStatement pstmt = conn.prepareStatement(insertSql);
            pstmt.setString(1, defectInfo.getFile());
            pstmt.setString(2, defectInfo.getTitle());
            pstmt.setString(3, defectInfo.getAppCode());
            pstmt.executeUpdate();

        } finally {
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }

    /**
     * 提交缺陷到PM系统
     */
    public static void submitDefects(List<DefectInfo> defectInfos) throws Exception {
        // 初始化记录表
        initDefectRecordsTable();
        
        String clientSecret = ClientSecret + ":" + System.currentTimeMillis();
        String encryptByPublicKey = RSAUtil.encryptByPublicKey(clientSecret, PublicKeyStr);

        PMClient pmClient = new PMClient();
        int submittedCount = 0;
        int skippedCount = 0;

        for (DefectInfo defectInfo : defectInfos) {
            try {
                // 检查是否已提交过
                if (isDefectSubmitted(defectInfo.getFile())) {
                    System.out.println("跳过已提交的缺陷: " + defectInfo.getFile());
                    skippedCount++;
                    continue;
                }

                // 创建缺陷数据对象
                PMClient.DefectData defectData = new PMClient.DefectData();
                defectData.setSeverityLevel("0477ef28-29f8-42b8-bd9f-d1db4d3c5d05"); // 假设使用默认的严重程度
                defectData.setLineId("3058614d-5e02-45b3-8084-33d4c6e6a49b"); // 假设使用默认的产品线
                defectData.setReporter("0000016826"); // 假设使用默认的报告人
                defectData.setAssignee("0000016826"); // 假设使用默认的负责人
                defectData.setDefectType("TECHNOLOGY_DEFECT"); // 技术缺陷类型
                defectData.setPriority("高"); // 假设使用默认的优先级
                defectData.setInfluenceVersion(1821); // 假设使用默认的影响版本
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
                
                // 保存提交记
                saveDefectRecord(defectInfo);
                
                submittedCount++;

            } catch (Exception e) {
                System.err.println("创建缺陷失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        pmClient.close();
        
        System.out.println(String.format("提交完成：成功提交 %d 条，跳过重复 %d 条", submittedCount, skippedCount));
    }

    /**
     * 缺陷信息类，用于存储从Excel中读取的数据
     */
    public static class DefectInfo {
        private String appCode; // 对应microService
        private String microService;
        private String file;
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

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }
    }
}