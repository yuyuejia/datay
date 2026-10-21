package com.data.datafusion.service.alert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.AlertChannel;
import com.data.datafusion.domain.AlertEvent;
import com.data.datafusion.repository.AlertChannelRepository;
import com.data.datafusion.service.dto.AlertChannelDTO;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 告警渠道管理。站内信是内置能力，不作为可配置渠道；这里只管理邮件和 Webhook。
 */
@Service
@Transactional
public class AlertChannelService {

    private static final Logger LOG = LoggerFactory.getLogger(AlertChannelService.class);

    private final AlertChannelRepository alertChannelRepository;

    private final Map<String, AlertChannelSender> senders;

    public AlertChannelService(AlertChannelRepository alertChannelRepository, List<AlertChannelSender> senders) {
        this.alertChannelRepository = alertChannelRepository;
        this.senders = senders.stream().collect(Collectors.toMap(AlertChannelSender::getType, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<AlertChannelDTO> findAll() {
        return alertChannelRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<AlertChannelDTO> findOne(String id) {
        return alertChannelRepository.findById(id).map(this::toDto);
    }

    public AlertChannelDTO save(AlertChannelDTO dto) {
        validate(dto);
        String code = normalizeCode(dto.code(), null);
        ZonedDateTime now = ZonedDateTime.now();
        AlertChannel entity = new AlertChannel();
        entity.setName(dto.name().trim());
        entity.setCode(code);
        entity.setType(dto.type().toUpperCase(Locale.ROOT));
        entity.setConfig(normalizeConfig(dto.config()));
        entity.setEnabled(dto.enabled() == null ? Boolean.TRUE : dto.enabled());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return toDto(alertChannelRepository.save(entity));
    }

    public Optional<AlertChannelDTO> update(String id, AlertChannelDTO dto) {
        validate(dto);
        return alertChannelRepository
            .findById(id)
            .map(existing -> {
                existing.setName(dto.name().trim());
                existing.setCode(normalizeCode(dto.code() == null || dto.code().isBlank() ? existing.getCode() : dto.code(), id));
                existing.setType(dto.type().toUpperCase(Locale.ROOT));
                existing.setConfig(normalizeConfig(dto.config()));
                existing.setEnabled(dto.enabled() == null ? existing.getEnabled() : dto.enabled());
                existing.setUpdateTime(ZonedDateTime.now());
                return toDto(alertChannelRepository.save(existing));
            });
    }

    public void delete(String id) {
        alertChannelRepository.deleteById(id);
    }

    /**
     * 发送测试消息。用于验证 SMTP / Webhook 配置是否可用。
     */
    public void test(String id) {
        AlertChannel channel = alertChannelRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("渠道不存在"));
        AlertChannelSender sender = senders.get(channel.getType());
        if (sender == null) {
            throw new IllegalArgumentException("不支持的渠道类型：" + channel.getType());
        }
        AlertEvent event = new AlertEvent();
        event.setId("test");
        event.setTenantId(channel.getTenantId());
        event.setTitle("DataY 告警渠道测试");
        event.setContent("这是一条来自 DataY 告警中心的测试消息。收到此消息说明渠道配置可用。");
        event.setSeverity("P2");
        event.setStatus(AlertEvent.STATUS_FIRING);
        event.setSourceType("JOB");
        event.setEventType("TEST");
        event.setJobCode("alert-center-test");
        event.setJobName("告警渠道测试");
        event.setFirstSeen(ZonedDateTime.now());
        event.setLastSeen(ZonedDateTime.now());
        event.setEventCount(1);
        try {
            sender.send(event, channel);
        } catch (Exception e) {
            LOG.warn("Alert channel test failed: channel={}", channel.getName(), e);
            String message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
            throw new IllegalArgumentException("测试发送失败：" + message);
        }
    }

    private void validate(AlertChannelDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("渠道不能为空");
        }
        if (dto.name() == null || dto.name().isBlank()) {
            throw new IllegalArgumentException("渠道名称不能为空");
        }
        String type = dto.type() == null ? "" : dto.type().toUpperCase(Locale.ROOT);
        if (!AlertChannel.TYPE_EMAIL.equals(type) && !AlertChannel.TYPE_WEBHOOK.equals(type)) {
            throw new IllegalArgumentException("渠道类型仅支持邮件和 Webhook");
        }
        JSONObject config = parseConfig(dto.config());
        if (AlertChannel.TYPE_EMAIL.equals(type)) {
            String to = config.getString("to");
            if (to == null || to.isBlank()) {
                throw new IllegalArgumentException("邮件渠道必须配置收件人 to");
            }
        } else {
            String url = config.getString("url");
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("Webhook 渠道必须配置 url");
            }
            String normalizedUrl = url.toLowerCase(Locale.ROOT);
            if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
                throw new IllegalArgumentException("Webhook url 必须以 http:// 或 https:// 开头");
            }
        }
    }

    private JSONObject parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return new JSONObject();
        }
        try {
            JSONObject object = JSON.parseObject(config);
            return object == null ? new JSONObject() : object;
        } catch (Exception e) {
            throw new IllegalArgumentException("渠道配置不是合法 JSON");
        }
    }

    private String normalizeConfig(String config) {
        return parseConfig(config).toJSONString();
    }

    private String normalizeCode(String requested, String excludeId) {
        String code = requested == null ? null : requested.trim();
        if (code == null || code.isBlank()) {
            code = "alert_channel_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        if (!code.matches("[A-Za-z0-9_\\-]+")) {
            throw new IllegalArgumentException("渠道编码只能包含字母、数字、下划线和中划线");
        }
        Optional<AlertChannel> conflict = excludeId == null
            ? alertChannelRepository.findByCode(code)
            : alertChannelRepository.findByCode(code).filter(channel -> !channel.getId().equals(excludeId));
        if (conflict.isPresent()) {
            throw new IllegalArgumentException("渠道编码已存在：" + code);
        }
        return code;
    }

    private AlertChannelDTO toDto(AlertChannel entity) {
        return new AlertChannelDTO(
            entity.getId(),
            entity.getName(),
            entity.getCode(),
            entity.getType(),
            entity.getConfig(),
            entity.getEnabled(),
            entity.getCreateTime(),
            entity.getUpdateTime()
        );
    }
}
