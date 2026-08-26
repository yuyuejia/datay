package com.data.job.pm;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSAUtil {
    //签名算法名称
    private static final String RSA_KEY_ALGORITHM = "RSA";
    //RSA密钥长度,默认密钥长度是1024,密钥长度必须是64的倍数，在512到65536位之间,不管是RSA还是RSA2长度推荐使用2048
    private static final int KEY_SIZE = 2048;

    private static final String PublicKeyStr = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAypb+rsUmLNREplxycLmJIwtH9vhOJZaNyZyE8GYKRWyXbAV" +
            "wlhObr0mfRehPjsMTTD6Oh9ZlUAyOWov+1HQV2z+bcm2F9O440lm8Ywk1x7zGvXqFRBhHPvwhdTQsQ+/bsu0Wg3wY5em" +
            "JbovE59Mq0/7LgJCAGaCs7GaSPMfnYQwdv0zN5NfLR1M/pqkG5hI+ghahy84z+4eSKqv3Z2WNc5eGY5yN4u8LjrPMK4e" +
            "y6WrUOGW1cj+rdd4EGN3ti8vPWQv8Rj4y8lpljGy+j/byWO4zF3WnQ2GTM9B+fKobjYViRyQCPlcyQ5jh3zGUNvAtQkY" +
            "VbnTV5Tt/ZBKq/+zEYwIDAQAB";
    //私钥
    private static final String ClientSecret = "ee4933c5-009e-11f1-866a-b6e8b97ae37b";
    //客户端 id
    private static final String ClientID = "PERFORMANCE";

    /**
     * 公钥加密(用于数据加密)
     *
     * @param data         加密前的字符串
     * @param publicKeyStr base64编码后的公钥
     * @return base64编码后的字符串
     * @throws Exception
     */
    public static String encryptByPublicKey(String data, String publicKeyStr) throws
            Exception {
//Java原生base64解码
        byte[] pubKey = Base64.getDecoder().decode(publicKeyStr);
//创建X509编码密钥规范
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(pubKey);
//返回转换指定算法的KeyFactory对象
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_KEY_ALGORITHM);
//根据X509编码密钥规范产生公钥对象
        PublicKey publicKey = keyFactory.generatePublic(x509KeySpec);
//根据转换的名称获取密码对象Cipher（转换的名称：算法/工作模式/填充模式）
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
//用公钥初始化此Cipher对象（加密模式）
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
//对数据加密
        byte[] encrypt = cipher.doFinal(data.getBytes());
//返回base64编码后的字符串
        return Base64.getEncoder().encodeToString(encrypt);
    }

}