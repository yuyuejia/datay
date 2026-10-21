package com.data.datafusion.service.alert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.AlertChannel;
import com.data.datafusion.domain.AlertEvent;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import tech.jhipster.config.JHipsterProperties;

/**
 * 邮件渠道。配置示例：{"to":"ops@example.com","cc":"","subjectPrefix":"[DataY告警]"}。
 */
@Component
public class EmailAlertSender implements AlertChannelSender {

    private static final Logger LOG = LoggerFactory.getLogger(EmailAlertSender.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaMailSender javaMailSender;

    private final JHipsterProperties jHipsterProperties;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public EmailAlertSender(JavaMailSender javaMailSender, JHipsterProperties jHipsterProperties) {
        this.javaMailSender = javaMailSender;
        this.jHipsterProperties = jHipsterProperties;
    }

    @Override
    public String getType() {
        return AlertChannel.TYPE_EMAIL;
    }

    @Override
    public void send(AlertEvent event, AlertChannel channel) throws Exception {
        JSONObject config = parseConfig(channel);
        List<String> to = splitAddresses(config.getString("to"));
        if (to.isEmpty()) {
            throw new IllegalArgumentException("邮件渠道未配置收件人 to");
        }
        List<String> cc = splitAddresses(config.getString("cc"));

        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setTo(to.toArray(String[]::new));
        if (!cc.isEmpty()) {
            helper.setCc(cc.toArray(String[]::new));
        }
        String from = jHipsterProperties.getMail().getFrom();
        if (from == null || from.isBlank()) {
            from = mailUsername;
        }
        if (from == null || from.isBlank()) {
            from = "datay@localhost";
        }
        helper.setFrom(from);
        String subjectPrefix = config.getString("subjectPrefix");
        if (subjectPrefix == null || subjectPrefix.isBlank()) {
            subjectPrefix = "[DataY告警]";
        }
        helper.setSubject(subjectPrefix + " " + safe(event.getTitle()));
        helper.setText(buildHtml(event), true);
        javaMailSender.send(message);
        LOG.info("Alert email sent to {}, event={}", to, event.getId());
    }

    private JSONObject parseConfig(AlertChannel channel) {
        if (channel.getConfig() == null || channel.getConfig().isBlank()) {
            throw new IllegalArgumentException("邮件渠道配置为空");
        }
        try {
            JSONObject config = JSON.parseObject(channel.getConfig());
            return config == null ? new JSONObject() : config;
        } catch (Exception e) {
            throw new IllegalArgumentException("邮件渠道配置不是合法 JSON");
        }
    }

    private List<String> splitAddresses(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String item : value.split("[,;\\s]+")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private String buildHtml(AlertEvent event) {
        String time = event.getLastSeen() == null ? "" : TIME_FORMATTER.format(event.getLastSeen());
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,'Microsoft YaHei',sans-serif;color:#303133;line-height:1.7\">");
        html.append("<h3 style=\"margin:0 0 12px;color:#c45656\">").append(escape(safe(event.getTitle()))).append("</h3>");
        html.append("<p><b>级别：</b>").append(escape(safe(event.getSeverity()))).append("</p>");
        html.append("<p><b>任务：</b>").append(escape(safe(event.getJobName()))).append("（").append(escape(safe(event.getJobCode()))).append("）</p>");
        html.append("<p><b>实例：</b>").append(escape(safe(event.getInstanceCode()))).append("</p>");
        html.append("<p><b>事件：</b>").append(escape(safe(event.getEventType()))).append("</p>");
        html.append("<p><b>时间：</b>").append(escape(time)).append("</p>");
        html.append("<hr style=\"border:none;border-top:1px solid #ebeef5;margin:16px 0\"/>");
        html.append("<div style=\"white-space:pre-wrap\">").append(escape(safe(event.getContent()))).append("</div>");
        html.append("<p style=\"margin-top:20px;color:#909399;font-size:12px\">本邮件由 DataY 告警中心自动发送，请勿直接回复。</p>");
        html.append("</div>");
        return html.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\n", "<br/>");
    }
}
