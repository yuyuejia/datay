package com.data.datafusion.service.alert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.data.datafusion.domain.User;
import com.data.datafusion.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把规则中的接收人表达式解析成当前租户内的用户。
 *
 * <p>支持 {@code role:ROLE_TENANT_ADMIN} 与 {@code user:login} 两种写法。默认规则使用
 * {@code role:ROLE_TENANT_ADMIN}；如果租户没有租户管理员，则回退到该租户前 20 个已激活用户，
 * 避免默认告警完全无人接收。显式配置的 {@code user:} 或非管理员角色不会触发这个兜底。</p>
 */
@Service
public class AlertRecipientResolver {

    private static final Logger LOG = LoggerFactory.getLogger(AlertRecipientResolver.class);

    private final UserRepository userRepository;

    public AlertRecipientResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<User> resolve(String tenantId, String recipientsJson) {
        Long tenant = parseTenant(tenantId);
        if (tenant == null) {
            return List.of();
        }

        Map<Long, User> users = new LinkedHashMap<>();
        JSONArray recipients = parseRecipients(recipientsJson);
        if (recipients.isEmpty()) {
            recipients.add("role:ROLE_TENANT_ADMIN");
        }
        boolean tenantAdminFallback = false;

        for (Object item : recipients) {
            String expression = String.valueOf(item).trim();
            if (expression.isEmpty()) {
                continue;
            }
            String normalized = expression.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("role:")) {
                String authority = expression.substring("role:".length()).trim();
                if ("ROLE_TENANT_ADMIN".equalsIgnoreCase(authority)) {
                    tenantAdminFallback = true;
                }
                for (User user : userRepository.findAllByTenantIdAndAuthority(tenant, authority)) {
                    if (user.isActivated()) {
                        users.putIfAbsent(user.getId(), user);
                    }
                }
            } else if (normalized.startsWith("user:")) {
                String login = expression.substring("user:".length()).trim();
                userRepository.findByLoginAndTenantId(login, tenant).filter(User::isActivated).ifPresent(user -> users.putIfAbsent(user.getId(), user));
            }
        }

        if (users.isEmpty() && tenantAdminFallback) {
            // 内置默认规则在租户没有 TENANT_ADMIN 时仍然要有人能收到站内信。
            userRepository
                .findAllByTenantId(tenant, PageRequest.of(0, 20))
                .getContent()
                .stream()
                .filter(User::isActivated)
                .forEach(user -> users.putIfAbsent(user.getId(), user));
            LOG.debug("Alert recipients fallback to tenant users: tenant={}, count={}", tenant, users.size());
        }

        return new ArrayList<>(users.values());
    }

    private JSONArray parseRecipients(String recipientsJson) {
        if (recipientsJson == null || recipientsJson.trim().isEmpty()) {
            return new JSONArray();
        }
        try {
            JSONArray array = JSON.parseArray(recipientsJson);
            return array == null ? new JSONArray() : array;
        } catch (Exception e) {
            LOG.warn("Invalid alert recipients JSON: {}", recipientsJson);
            return new JSONArray();
        }
    }

    private Long parseTenant(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(tenantId.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Invalid tenant id for alert recipients: {}", tenantId);
            return null;
        }
    }
}
