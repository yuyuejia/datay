package com.data.datafusion.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.data.datafusion.domain.Authority;
import com.data.datafusion.domain.RoleDataScope;
import com.data.datafusion.repository.AuthorityRepository;
import com.data.datafusion.repository.RoleDataScopeRepository;
import com.data.datafusion.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AuthorityResource} 角色管理（新增/描述/删除保护）的接口测试。
 */
@WebMvcTest(controllers = AuthorityResource.class, properties = "jhipster.clientApp.name=datafusionApp")
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = { "ROLE_ADMIN" })
class AuthorityManagementResourceTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthorityRepository authorityRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RoleDataScopeRepository roleDataScopeRepository;

    @Test
    void shouldPrefixRoleNameOnCreate() throws Exception {
        when(authorityRepository.existsById("ROLE_REGION")).thenReturn(false);
        when(authorityRepository.save(any(Authority.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mvc
            .perform(
                post("/api/authorities")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"region\",\"description\":\"区域角色\"}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("ROLE_REGION"))
            .andExpect(jsonPath("$.description").value("区域角色"));
    }

    @Test
    void shouldCreateRoleWithChineseName() throws Exception {
        when(authorityRepository.existsById("ROLE_华东")).thenReturn(false);
        when(authorityRepository.save(any(Authority.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mvc
            .perform(post("/api/authorities").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"华东\",\"description\":\"华东区\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("ROLE_华东"));
    }

    @Test
    void shouldRejectDuplicateRoleOnCreate() throws Exception {
        when(authorityRepository.existsById("ROLE_ADMIN")).thenReturn(true);

        mvc
            .perform(post("/api/authorities").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"admin\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectDeletingBuiltInRole() throws Exception {
        mvc.perform(delete("/api/authorities/ROLE_ADMIN")).andExpect(status().isBadRequest());
        verify(authorityRepository, never()).deleteById(any());
    }

    @Test
    void shouldRejectDeletingAssignedRole() throws Exception {
        when(userRepository.countByAuthoritiesName("ROLE_REGION")).thenReturn(2L);

        mvc.perform(delete("/api/authorities/ROLE_REGION")).andExpect(status().isBadRequest());
        verify(authorityRepository, never()).deleteById(any());
    }

    @Test
    void shouldRejectDeletingRoleWithDataScopes() throws Exception {
        when(userRepository.countByAuthoritiesName("ROLE_REGION")).thenReturn(0L);
        when(roleDataScopeRepository.findByRoleName("ROLE_REGION")).thenReturn(List.of(new RoleDataScope()));

        mvc.perform(delete("/api/authorities/ROLE_REGION")).andExpect(status().isBadRequest());
        verify(authorityRepository, never()).deleteById(any());
    }

    @Test
    void shouldDeleteUnusedRole() throws Exception {
        when(userRepository.countByAuthoritiesName("ROLE_REGION")).thenReturn(0L);
        when(roleDataScopeRepository.findByRoleName("ROLE_REGION")).thenReturn(List.of());

        mvc.perform(delete("/api/authorities/ROLE_REGION")).andExpect(status().isNoContent());
        verify(authorityRepository).deleteById("ROLE_REGION");
    }

    @Test
    void shouldUpdateDescription() throws Exception {
        Authority existing = new Authority().name("ROLE_REGION");
        when(authorityRepository.findById("ROLE_REGION")).thenReturn(Optional.of(existing));
        when(authorityRepository.save(any(Authority.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mvc
            .perform(
                put("/api/authorities/ROLE_REGION").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"华东区域\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("华东区域"));
    }
}
