package com.data.datafusion.service.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.Authority;
import com.data.datafusion.domain.User;
import com.data.datafusion.repository.AuthorityRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.service.dto.AlertRecipientOptionsDTO;
import com.data.datafusion.service.dto.AlertRecipientOptionsDTO.RoleOption;
import com.data.datafusion.service.dto.AlertRecipientOptionsDTO.UserOption;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AlertRecipientOptionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthorityRepository authorityRepository;

    @InjectMocks
    private AlertRecipientOptionService service;

    @Test
    void returnsTenantUsersAndRoles() {
        User user = new User();
        user.setId(1L);
        user.setLogin("alice");
        user.setFirstName("Alice");
        user.setActivated(true);

        Authority tenantAdmin = authority("ROLE_TENANT_ADMIN", "租户管理员");
        Authority ops = authority("ROLE_OPS", "运维");
        Authority admin = authority("ROLE_ADMIN", "全局管理员");
        user.setAuthorities(new LinkedHashSet<>(List.of(tenantAdmin, ops, admin)));

        when(userRepository.findAllByTenantId(eq(7L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        when(authorityRepository.findAllById(anySet())).thenReturn(List.of(tenantAdmin, ops, admin));

        AlertRecipientOptionsDTO options = service.getOptions(7L);

        assertThat(options.users()).containsExactly(new UserOption(1L, "alice", "Alice", null));
        assertThat(options.roles()).containsExactly(new RoleOption("ROLE_OPS", "运维"), new RoleOption("ROLE_TENANT_ADMIN", "租户管理员"));
    }

    @Test
    void returnsEmptyOptionsWithoutTenant() {
        AlertRecipientOptionsDTO options = service.getOptions(null);

        assertThat(options.roles()).isEmpty();
        assertThat(options.users()).isEmpty();
        verifyNoInteractions(userRepository, authorityRepository);
    }

    private Authority authority(String name, String description) {
        Authority authority = new Authority();
        authority.setName(name);
        authority.setDescription(description);
        return authority;
    }
}
