package com.data.datafusion.service.alert;

import com.data.datafusion.domain.Authority;
import com.data.datafusion.domain.User;
import com.data.datafusion.repository.AuthorityRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.security.AuthoritiesConstants;
import com.data.datafusion.service.dto.AlertRecipientOptionsDTO;
import com.data.datafusion.service.dto.AlertRecipientOptionsDTO.RoleOption;
import com.data.datafusion.service.dto.AlertRecipientOptionsDTO.UserOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提供告警规则站内信接收人的候选列表。
 */
@Service
public class AlertRecipientOptionService {

    /** 候选用户上限，避免租户用户过多时一次返回过大。 */
    private static final int MAX_USERS = 500;

    private final UserRepository userRepository;

    private final AuthorityRepository authorityRepository;

    public AlertRecipientOptionService(UserRepository userRepository, AuthorityRepository authorityRepository) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
    }

    @Transactional(readOnly = true)
    public AlertRecipientOptionsDTO getOptions(Long tenantId) {
        if (tenantId == null) {
            return new AlertRecipientOptionsDTO(List.of(), List.of());
        }

        List<User> users = userRepository
            .findAllByTenantId(tenantId, PageRequest.of(0, MAX_USERS, Sort.by(Sort.Direction.ASC, "login")))
            .getContent()
            .stream()
            .filter(User::isActivated)
            .toList();

        Set<String> roleNames = users
            .stream()
            .flatMap(user -> user.getAuthorities().stream())
            .map(Authority::getName)
            // 全局管理员角色不作为租户告警接收人候选。
            .filter(name -> !AuthoritiesConstants.ADMIN.equals(name))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        // 默认规则使用租户管理员角色；即使当前没有用户拥有该角色，也保留该选项。
        roleNames.add(AuthoritiesConstants.TENANT_ADMIN);

        Map<String, String> descriptions = new LinkedHashMap<>();
        for (Authority authority : authorityRepository.findAllById(roleNames)) {
            descriptions.put(authority.getName(), authority.getDescription());
        }

        List<RoleOption> roles = roleNames
            .stream()
            .sorted()
            .map(name -> new RoleOption(name, descriptions.get(name)))
            .toList();
        List<UserOption> userOptions = users
            .stream()
            .map(user -> new UserOption(user.getId(), user.getLogin(), user.getFirstName(), user.getLastName()))
            .toList();
        return new AlertRecipientOptionsDTO(roles, userOptions);
    }
}
