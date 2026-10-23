package com.data.datafusion.service.dto;

import java.util.List;

/**
 * 告警规则可选的站内信接收人。
 *
 * <p>只返回当前租户范围内、已激活的用户，以及这些用户实际拥有的角色，
 * 避免普通用户为了配置规则而访问管理员接口。</p>
 */
public record AlertRecipientOptionsDTO(List<RoleOption> roles, List<UserOption> users) {
    public record RoleOption(String name, String description) {}

    public record UserOption(Long id, String login, String firstName, String lastName) {}
}
