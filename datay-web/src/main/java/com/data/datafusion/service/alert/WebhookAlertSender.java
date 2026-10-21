package com.data.datafusion.service.alert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.AlertChannel;
import com.data.datafusion.domain.AlertEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Webhook 渠道。配置示例：{"url":"https://example.com/hook","secret":"xxx","headers":{"X-Token":"xxx"}}。
 */
@Component
public class WebhookAlertSender implements AlertChannelSender {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookAlertSender.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public String getType() {
        return AlertChannel.TYPE_WEBHOOK;
    }

    @Override
    public void send(AlertEvent event, AlertChannel channel) throws Exception {
        JSONObject config = parseConfig(channel);
        String url = config.getString("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook 渠道未配置 url");
        }
        String normalizedUrl = url.toLowerCase(Locale.ROOT);
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Webhook url 必须以 http:// 或 https:// 开头");
        }

        String body = JSON.toJSONString(buildPayload(event));
        int timeoutSeconds = config.getIntValue("timeoutSeconds", 10);
        timeoutSeconds = Math.max(1, Math.min(timeoutSeconds, 60));

        HttpRequest.Builder builder = HttpRequest
            .newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("User-Agent", "DataY-Alert-Center/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        JSONObject headers = config.getJSONObject("headers");
        if (headers != null) {
            for (String name : headers.keySet()) {
                if (headers.getString(name) != null) {
                    builder.header(name, headers.getString(name));
                }
            }
        }
        String secret = config.getString("secret");
        if (secret != null && !secret.isBlank()) {
            builder.header("X-DataY-Signature", "sha256=" + hmacHex(secret, body));
        }

        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            LOG.info("Alert webhook sent to {}, status={}, event={}", url, response.statusCode(), event.getId());
            return;
        }
        String responseBody = response.body() == null ? "" : response.body();
        if (responseBody.length() > 300) {
            responseBody = responseBody.substring(0, 300);
        }
        throw new IllegalStateException("Webhook 返回 HTTP " + response.statusCode() + "：" + responseBody);
    }

    private JSONObject parseConfig(AlertChannel channel) {
        if (channel.getConfig() == null || channel.getConfig().isBlank()) {
            throw new IllegalArgumentException("Webhook 渠道配置为空");
        }
        try {
            JSONObject config = JSON.parseObject(channel.getConfig());
            return config == null ? new JSONObject() : config;
        } catch (Exception e) {
            throw new IllegalArgumentException("Webhook 渠道配置不是合法 JSON");
        }
    }

    private Map<String, Object> buildPayload(AlertEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", event.getId());
        payload.put("title", event.getTitle());
        payload.put("content", event.getContent());
        payload.put("severity", event.getSeverity());
        payload.put("status", event.getStatus());
        payload.put("sourceType", event.getSourceType());
        payload.put("eventType", event.getEventType());
        payload.put("jobCode", event.getJobCode());
        payload.put("jobName", event.getJobName());
        payload.put("instanceCode", event.getInstanceCode());
        payload.put("firstSeen", event.getFirstSeen());
        payload.put("lastSeen", event.getLastSeen());
        payload.put("eventCount", event.getEventCount());
        return payload;
    }

    private String hmacHex(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
