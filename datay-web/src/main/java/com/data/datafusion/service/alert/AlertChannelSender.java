package com.data.datafusion.service.alert;

import com.data.datafusion.domain.AlertChannel;
import com.data.datafusion.domain.AlertEvent;

/**
 * 告警渠道发送器。实现类通过 {@link #getType()} 与渠道类型匹配。
 */
public interface AlertChannelSender {
    String getType();

    void send(AlertEvent event, AlertChannel channel) throws Exception;
}
