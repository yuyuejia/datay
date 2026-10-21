package com.data.datafusion.service.alert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.AlertChannel;
import com.data.datafusion.domain.AlertDelivery;
import com.data.datafusion.domain.AlertEvent;
import com.data.datafusion.domain.Notification;
import com.data.datafusion.domain.User;
import com.data.datafusion.repository.AlertChannelRepository;
import com.data.datafusion.repository.AlertDeliveryRepository;
import com.data.datafusion.repository.AlertEventRepository;
import com.data.datafusion.repository.NotificationRepository;
import com.data.datafusion.security.TenantContext;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 告警分发：写入站内信，并异步投递邮件/Webhook。
 *
 * <p>MVP 采用进程内最多 3 次重试，不引入持久化重试队列。每次投递都会写入
 * {@code dp_alert_delivery}，便于在详情里排查失败原因。</p>
 */
@Service
public class AlertDispatchService {

    private static final Logger LOG = LoggerFactory.getLogger(AlertDispatchService.class);

    private static final int MAX_ATTEMPTS = 3;

    private final AlertEventRepository alertEventRepository;

    private final AlertChannelRepository alertChannelRepository;

    private final AlertDeliveryRepository alertDeliveryRepository;

    private final NotificationRepository notificationRepository;

    private final AlertRecipientResolver alertRecipientResolver;

    private final Map<String, AlertChannelSender> senders;

    public AlertDispatchService(
        AlertEventRepository alertEventRepository,
        AlertChannelRepository alertChannelRepository,
        AlertDeliveryRepository alertDeliveryRepository,
        NotificationRepository notificationRepository,
        AlertRecipientResolver alertRecipientResolver,
        List<AlertChannelSender> senders
    ) {
        this.alertEventRepository = alertEventRepository;
        this.alertChannelRepository = alertChannelRepository;
        this.alertDeliveryRepository = alertDeliveryRepository;
        this.notificationRepository = notificationRepository;
        this.alertRecipientResolver = alertRecipientResolver;
        this.senders = senders.stream().collect(Collectors.toMap(AlertChannelSender::getType, Function.identity()));
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(String tenantId, String eventId) {
        setTenantContext(tenantId);
        try {
            AlertEvent event = alertEventRepository.findById(eventId).orElse(null);
            if (event == null) {
                LOG.warn("Alert event not found for dispatch: {}", eventId);
                return;
            }
            createInAppNotifications(event);
            dispatchExternalChannels(event);
        } catch (Exception e) {
            LOG.error("Alert dispatch failed, tenant={}, event={}", tenantId, eventId, e);
        } finally {
            TenantContext.clear();
        }
    }

    private void createInAppNotifications(AlertEvent event) {
        List<User> recipients = alertRecipientResolver.resolve(event.getTenantId(), event.getRecipients());
        ZonedDateTime now = ZonedDateTime.now();
        for (User user : recipients) {
            Notification notification = new Notification();
            notification.setTenantId(event.getTenantId());
            notification.setUserId(user.getId());
            notification.setEventId(event.getId());
            notification.setTitle(event.getTitle());
            notification.setContent(event.getContent());
            notification.setReadFlag(Boolean.FALSE);
            notification.setCreateTime(now);
            notificationRepository.save(notification);
        }
        LOG.debug("Created in-app alert notifications, event={}, recipients={}", event.getId(), recipients.size());
    }

    private void dispatchExternalChannels(AlertEvent event) {
        JSONArray channelIds = parseArray(event.getChannelIds());
        for (Object item : channelIds) {
            String channelId = String.valueOf(item);
            AlertChannel channel = alertChannelRepository.findById(channelId).orElse(null);
            if (channel == null) {
                LOG.warn("Alert channel not found, event={}, channel={}", event.getId(), channelId);
                continue;
            }
            if (Boolean.FALSE.equals(channel.getEnabled())) {
                continue;
            }
            deliver(event, channel);
        }
    }

    private void deliver(AlertEvent event, AlertChannel channel) {
        AlertChannelSender sender = senders.get(channel.getType());
        AlertDelivery delivery = new AlertDelivery();
        delivery.setTenantId(event.getTenantId());
        delivery.setEventId(event.getId());
        delivery.setChannelId(channel.getId());
        delivery.setChannelType(channel.getType());
        delivery.setTarget(resolveTarget(channel));
        delivery.setStatus(AlertDelivery.STATUS_PENDING);
        delivery.setAttempt(0);
        delivery.setCreateTime(ZonedDateTime.now());
        delivery = alertDeliveryRepository.save(delivery);

        if (sender == null) {
            delivery.setStatus(AlertDelivery.STATUS_FAILED);
            delivery.setErrorMessage("不支持的渠道类型：" + channel.getType());
            alertDeliveryRepository.save(delivery);
            return;
        }

        String lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            delivery.setAttempt(attempt);
            try {
                sender.send(event, channel);
                delivery.setStatus(AlertDelivery.STATUS_SUCCESS);
                delivery.setErrorMessage(null);
                delivery.setSentTime(ZonedDateTime.now());
                alertDeliveryRepository.save(delivery);
                return;
            } catch (Exception e) {
                lastError = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
                LOG.warn(
                    "Alert delivery attempt failed, event={}, channel={}, attempt={}/{}",
                    event.getId(),
                    channel.getName(),
                    attempt,
                    MAX_ATTEMPTS,
                    e
                );
                if (attempt < MAX_ATTEMPTS) {
                    sleep(attempt);
                }
            }
        }
        delivery.setStatus(AlertDelivery.STATUS_FAILED);
        delivery.setErrorMessage(lastError == null ? "发送失败" : truncate(lastError));
        alertDeliveryRepository.save(delivery);
    }

    private String resolveTarget(AlertChannel channel) {
        try {
            JSONObject config = channel.getConfig() == null || channel.getConfig().isBlank() ? new JSONObject() : JSON.parseObject(channel.getConfig());
            if (AlertChannel.TYPE_EMAIL.equals(channel.getType())) {
                return config.getString("to");
            }
            if (AlertChannel.TYPE_WEBHOOK.equals(channel.getType())) {
                return config.getString("url");
            }
        } catch (Exception e) {
            LOG.debug("Could not resolve alert channel target: {}", channel.getCode());
        }
        return channel.getCode();
    }

    private JSONArray parseArray(String json) {
        if (json == null || json.isBlank()) {
            return new JSONArray();
        }
        try {
            JSONArray array = JSON.parseArray(json);
            return array == null ? new JSONArray() : array;
        } catch (Exception e) {
            LOG.warn("Invalid alert channel ids JSON: {}", json);
            return new JSONArray();
        }
    }

    private void setTenantContext(String tenantId) {
        try {
            TenantContext.setTenantId(Long.valueOf(tenantId));
        } catch (Exception e) {
            LOG.warn("Invalid tenant id for alert dispatch: {}", tenantId);
        }
    }

    private void sleep(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String value) {
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
