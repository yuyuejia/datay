package com.data.job.pm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class PMTest {
    private static final String PublicKeyStr = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAypb+rsUmLNREplxycLmJIwtH9vhOJZaNyZyE8GYKRWyXbAV" +
            "wlhObr0mfRehPjsMTTD6Oh9ZlUAyOWov+1HQV2z+bcm2F9O440lm8Ywk1x7zGvXqFRBhHPvwhdTQsQ+/bsu0Wg3wY5em" +
            "JbovE59Mq0/7LgJCAGaCs7GaSPMfnYQwdv0zN5NfLR1M/pqkG5hI+ghahy84z+4eSKqv3Z2WNc5eGY5yN4u8LjrPMK4e" +
            "y6WrUOGW1cj+rdd4EGN3ti8vPWQv8Rj4y8lpljGy+j/byWO4zF3WnQ2GTM9B+fKobjYViRyQCPlcyQ5jh3zGUNvAtQkY" +
            "VbnTV5Tt/ZBKq/+zEYwIDAQAB";

    private static final String ClientSecret = "ee4933c5-009e-11f1-866a-b6e8b97ae37b";
    //客户端 id
    private static final String ClientID = "PERFORMANCE";
    @Test
    @Timeout(6000000)
    public void testInsert() throws Exception {
        String clientSecret = ClientSecret + ":" + System.currentTimeMillis();
        String encryptByPublicKey = RSAUtil.encryptByPublicKey(clientSecret, PublicKeyStr);

        try {
            PMClient authUtil = new PMClient();
            String accessToken = authUtil.getAccessTokenWithFullAuth(ClientID, encryptByPublicKey);
            System.out.println("完整的认证流程获取到的访问令牌: " + accessToken);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @Timeout(6000000)
    public void testInsert1() throws Exception {
        String clientSecret = ClientSecret + ":" + System.currentTimeMillis();
        String encryptByPublicKey = RSAUtil.encryptByPublicKey(clientSecret, PublicKeyStr);

        // 方式1：使用已有的访问令牌获取产品线
        try {
            PMClient authUtil = new PMClient();
            String accessToken = authUtil.getAccessTokenWithFullAuth(ClientID, encryptByPublicKey);
            System.out.println("完整的认证流程获取到的访问令牌: " + accessToken);

            java.util.List<PMClient.ProductLine> productLines = authUtil.getProductLines(accessToken);

            System.out.println("获取到的产品线列表:");
            for (PMClient.ProductLine productLine : productLines) {
                System.out.println(productLine);
                // 输出示例:
                // 产品线[编码: BIPTEST, 名称: BIP测试用产品线, ID: 4d3ba32828aaf0a6beb803f753710a75]
                // 产品线[编码: YYY, 名称: YonBIP, ID: 3058614d-5e02-45b3-8084-33d4c6e6a49b]
                // 产品线[编码: YISV, 名称: YonBIP生态公共, ID: a41b1a7f73dda6b294200208ec6c0175]
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    @Timeout(6000000)
    public void testInsert3() throws Exception {
        String clientSecret = ClientSecret + ":" + System.currentTimeMillis();
        String encryptByPublicKey = RSAUtil.encryptByPublicKey(clientSecret, PublicKeyStr);

        PMClient util = new PMClient();
        for(int i = 1; i <= 10; i++){
            PMClient.MicroServiceResponse response = util.getMicroServicesWithFullAuth(
                    ClientID,
                    encryptByPublicKey,
                    i,  // pageNumber
                    300  // pageSize
            );
            // 遍历微服务列表
            for (MicroService ms : response.getList()) {
                System.out.println(String.format("put(\"%s\", \"%s\");", ms.getCode(), ms.getId()));
            }
        }


    }

    @Test
    @Timeout(6000000)
    public void testInsert2() throws Exception {
        String clientSecret = ClientSecret + ":" + System.currentTimeMillis();
        String encryptByPublicKey = RSAUtil.encryptByPublicKey(clientSecret, PublicKeyStr);

        // 创建缺陷数据对象
        PMClient.DefectData defectData = new PMClient.DefectData();
        defectData.setSeverityLevel("0477ef28-29f8-42b8-bd9f-d1db4d3c5d05");
        defectData.setLineId("3058614d-5e02-45b3-8084-33d4c6e6a49b");
        defectData.setReporter("0000016826");
        defectData.setAssignee("0000016826");
        defectData.setDefectType("TECHNOLOGY_DEFECT");
        defectData.setPriority("高");

        //影响版本
        defectData.setInfluenceVersion(1816);
        //微服务
        defectData.setMicroService("3003");
        defectData.setTitle("【26BIP性能稳定性专项】技术缺陷自动创建测试");
        defectData.setDesc("创建接口测试");

// 设置属性列表
        java.util.List<PMClient.DefectProperty> properties = new java.util.ArrayList<>();
        properties.add(new PMClient.DefectProperty("char5", "8dbab9db-20bc-4cfa-9b66-693d07ef87fa"));
        properties.add(new PMClient.DefectProperty("char3", "176e87f7-fb35-434b-9220-80efe8ac3e7d"));
        properties.add(new PMClient.DefectProperty("char4", "ddedf854-6eb7-40e8-9929-e2cf0238e27a"));
        properties.add(new PMClient.DefectProperty("char20", "35548010-ff39-4a92-b55a-d14ae1708d8e"));
        defectData.setProperties(properties);

// 方式2：完整的认证和缺陷创建流程
        try {
            PMClient authUtil = new PMClient();
            PMClient.DefectCreateResult result = authUtil.createDefectWithFullAuth(ClientID, encryptByPublicKey, defectData);
            System.out.println("完整的缺陷创建流程成功: " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
