package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.RoleDatasetScope;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.AuthorityRepository;
import com.data.datafusion.repository.RoleDatasetScopeRepository;
import com.data.datafusion.service.dataset.DatasetScopeCondition;
import com.data.datafusion.service.dto.RoleDatasetScopeDTO;
import com.data.datafusion.service.mapper.RoleDatasetScopeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link RoleDatasetScopeService} 数据集数据权限解析与校验的单元测试。
 */
class RoleDatasetScopeServiceTest {

    private RoleDatasetScopeRepository roleDatasetScopeRepository;
    private AuthorityRepository authorityRepository;
    private AnalysisDatasetRepository analysisDatasetRepository;
    private RoleDatasetScopeMapper roleDatasetScopeMapper;
    private RoleDatasetScopeService roleDatasetScopeService;

    @BeforeEach
    void setUp() {
        roleDatasetScopeRepository = mock(RoleDatasetScopeRepository.class);
        authorityRepository = mock(AuthorityRepository.class);
        analysisDatasetRepository = mock(AnalysisDatasetRepository.class);
        roleDatasetScopeMapper = mock(RoleDatasetScopeMapper.class);
        roleDatasetScopeService = new RoleDatasetScopeService(
            roleDatasetScopeRepository,
            authorityRepository,
            analysisDatasetRepository,
            roleDatasetScopeMapper,
            new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "user",
                    "pwd",
                    java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()
                )
            );
    }

    private RoleDatasetScope scope(String roleName, String datasetId, String filterConfig) {
        RoleDatasetScope entity = new RoleDatasetScope();
        entity.setRoleName(roleName);
        entity.setDatasetId(datasetId);
        entity.setFilterConfig(filterConfig);
        entity.setEnabled(true);
        return entity;
    }

    private AnalysisDataset dataset(String id, String fields) {
        AnalysisDataset dataset = new AnalysisDataset();
        dataset.setId(id);
        dataset.setFields(fields);
        return dataset;
    }

    @Test
    void shouldReturnEmptyForAdmin() {
        authenticate("ROLE_ADMIN");
        assertThat(roleDatasetScopeService.resolveConditions("ds-1")).isEmpty();
        verify(roleDatasetScopeRepository, never()).findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), anyString());
    }

    @Test
    void shouldReturnEmptyWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertThat(roleDatasetScopeService.resolveConditions("ds-1")).isEmpty();
        verify(roleDatasetScopeRepository, never()).findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), anyString());
    }

    @Test
    void shouldReturnEmptyWhenDatasetIdIsBlank() {
        authenticate("ROLE_REGION");

        assertThat(roleDatasetScopeService.resolveConditions(" ")).isEmpty();
        verify(roleDatasetScopeRepository, never()).findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), anyString());
    }

    @Test
    void shouldReturnEmptyForAdminEvenWithOtherRoles() {
        authenticate("ROLE_ADMIN", "ROLE_REGION");

        assertThat(roleDatasetScopeService.resolveConditions("ds-1")).isEmpty();
        verify(roleDatasetScopeRepository, never()).findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), anyString());
    }

    @Test
    void shouldReturnEmptyWhenNoRules() {
        authenticate("ROLE_REGION");
        when(roleDatasetScopeRepository.findByRoleNameInAndDatasetIdAndEnabledTrue(Set.of("ROLE_REGION"), "ds-1")).thenReturn(List.of());
        assertThat(roleDatasetScopeService.resolveConditions("ds-1")).isEmpty();
    }

    @Test
    void shouldMergeConditionsAcrossMultipleRoles() {
        authenticate("ROLE_REGION", "ROLE_SALES");
        RoleDatasetScope east = scope(
            "ROLE_REGION",
            "ds-1",
            "{\"conditions\":[{\"fieldName\":\"region\",\"operator\":\"EQ\",\"value\":\"华东\"}]}"
        );
        RoleDatasetScope south = scope(
            "ROLE_SALES",
            "ds-1",
            "{\"conditions\":[{\"fieldName\":\"region\",\"operator\":\"EQ\",\"value\":\"华南\"}]}"
        );
        when(roleDatasetScopeRepository.findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), eq("ds-1")))
            .thenReturn(List.of(east, south));

        List<DatasetScopeCondition> conditions = roleDatasetScopeService.resolveConditions("ds-1");

        assertThat(conditions).hasSize(2);
        assertThat(conditions).extracting(DatasetScopeCondition::getValue).containsExactly("华东", "华南");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> rolesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(roleDatasetScopeRepository).findByRoleNameInAndDatasetIdAndEnabledTrue(rolesCaptor.capture(), eq("ds-1"));
        assertThat(rolesCaptor.getValue()).containsExactlyInAnyOrder("ROLE_REGION", "ROLE_SALES");
    }

    @Test
    void shouldMergeConditionsFromMatchedScopes() {
        authenticate("ROLE_REGION");
        RoleDatasetScope east = scope(
            "ROLE_REGION",
            "ds-1",
            "{\"conditions\":[{\"fieldName\":\"region\",\"operator\":\"EQ\",\"value\":\"华东\"}]}"
        );
        RoleDatasetScope west = scope(
            "ROLE_REGION",
            "ds-1",
            "{\"conditions\":[{\"fieldName\":\"region\",\"operator\":\"EQ\",\"value\":\"华南\"}]}"
        );
        when(roleDatasetScopeRepository.findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), anyString()))
            .thenReturn(List.of(east, west));

        List<DatasetScopeCondition> conditions = roleDatasetScopeService.resolveConditions("ds-1");

        assertThat(conditions).hasSize(2);
        assertThat(conditions.get(0).getFieldName()).isEqualTo("region");
        assertThat(conditions.get(1).getValue()).isEqualTo("华南");
    }

    @Test
    void shouldIgnoreRulesWithoutConditions() {
        authenticate("ROLE_REGION");
        when(roleDatasetScopeRepository.findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), anyString()))
            .thenReturn(List.of(scope("ROLE_REGION", "ds-1", null)));

        assertThat(roleDatasetScopeService.resolveConditions("ds-1")).isEmpty();
    }

    @Test
    void shouldRejectUnknownRoleOnSave() {
        RoleDatasetScopeDTO dto = new RoleDatasetScopeDTO();
        dto.setRoleName("ROLE_UNKNOWN");
        dto.setDatasetId("ds-1");
        when(roleDatasetScopeMapper.toEntity(dto)).thenReturn(entity("ROLE_UNKNOWN", "ds-1", validConfig()));
        when(authorityRepository.existsById("ROLE_UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> roleDatasetScopeService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("角色不存在");
    }

    @Test
    void shouldRejectUnknownDatasetOnSave() {
        RoleDatasetScopeDTO dto = new RoleDatasetScopeDTO();
        dto.setRoleName("ROLE_REGION");
        dto.setDatasetId("ds-404");
        when(roleDatasetScopeMapper.toEntity(dto)).thenReturn(entity("ROLE_REGION", "ds-404", validConfig()));
        when(authorityRepository.existsById("ROLE_REGION")).thenReturn(true);
        when(analysisDatasetRepository.findById("ds-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleDatasetScopeService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("数据集不存在");
    }

    @Test
    void shouldRejectDatasetWithoutFieldsOnSave() {
        RoleDatasetScopeDTO dto = new RoleDatasetScopeDTO();
        dto.setRoleName("ROLE_REGION");
        dto.setDatasetId("ds-1");
        when(roleDatasetScopeMapper.toEntity(dto)).thenReturn(entity("ROLE_REGION", "ds-1", validConfig()));
        when(authorityRepository.existsById("ROLE_REGION")).thenReturn(true);
        when(analysisDatasetRepository.findById("ds-1")).thenReturn(Optional.of(dataset("ds-1", null)));

        assertThatThrownBy(() -> roleDatasetScopeService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("请先预览数据集");
    }

    @Test
    void shouldRejectUnknownFieldOnSave() {
        RoleDatasetScopeDTO dto = new RoleDatasetScopeDTO();
        dto.setRoleName("ROLE_REGION");
        dto.setDatasetId("ds-1");
        String config = "{\"conditions\":[{\"fieldName\":\"unknown\",\"operator\":\"EQ\",\"value\":\"x\"}]}";
        when(roleDatasetScopeMapper.toEntity(dto)).thenReturn(entity("ROLE_REGION", "ds-1", config));
        when(authorityRepository.existsById("ROLE_REGION")).thenReturn(true);
        when(analysisDatasetRepository.findById("ds-1"))
            .thenReturn(Optional.of(dataset("ds-1", "{\"columns\":[{\"name\":\"region\"}]}")));

        assertThatThrownBy(() -> roleDatasetScopeService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("数据集字段不存在");
    }

    @Test
    void shouldRejectInvalidOperatorOnSave() {
        RoleDatasetScopeDTO dto = new RoleDatasetScopeDTO();
        dto.setRoleName("ROLE_REGION");
        dto.setDatasetId("ds-1");
        String config = "{\"conditions\":[{\"fieldName\":\"region\",\"operator\":\"BAD\",\"value\":\"x\"}]}";
        when(roleDatasetScopeMapper.toEntity(dto)).thenReturn(entity("ROLE_REGION", "ds-1", config));
        when(authorityRepository.existsById("ROLE_REGION")).thenReturn(true);
        when(analysisDatasetRepository.findById("ds-1"))
            .thenReturn(Optional.of(dataset("ds-1", "{\"columns\":[{\"name\":\"region\"}]}")));

        assertThatThrownBy(() -> roleDatasetScopeService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的条件运算符");
    }

    @Test
    void shouldPreserveTenantIdOnUpdate() {
        RoleDatasetScopeDTO dto = new RoleDatasetScopeDTO();
        dto.setId("1");
        dto.setRoleName("ROLE_REGION");
        dto.setDatasetId("ds-1");
        dto.setFilterConfig(validConfig());
        dto.setEnabled(true);

        RoleDatasetScope existing = new RoleDatasetScope();
        existing.setId("1");
        existing.setTenantId("1");
        when(roleDatasetScopeRepository.findById("1")).thenReturn(Optional.of(existing));

        RoleDatasetScope mapped = entity("ROLE_REGION", "ds-1", validConfig());
        mapped.setId("1");
        when(roleDatasetScopeMapper.toEntity(dto)).thenReturn(mapped);
        when(roleDatasetScopeMapper.toDto(any(RoleDatasetScope.class))).thenReturn(dto);
        when(authorityRepository.existsById("ROLE_REGION")).thenReturn(true);
        when(analysisDatasetRepository.findById("ds-1"))
            .thenReturn(Optional.of(dataset("ds-1", "{\"columns\":[{\"name\":\"region\"}]}")));
        when(roleDatasetScopeRepository.save(any(RoleDatasetScope.class))).thenAnswer(invocation -> invocation.getArgument(0));

        roleDatasetScopeService.update(dto);

        assertThat(mapped.getTenantId()).isEqualTo("1");
    }

    private RoleDatasetScope entity(String roleName, String datasetId, String filterConfig) {
        RoleDatasetScope entity = new RoleDatasetScope();
        entity.setRoleName(roleName);
        entity.setDatasetId(datasetId);
        entity.setFilterConfig(filterConfig);
        entity.setEnabled(true);
        return entity;
    }

    private String validConfig() {
        return "{\"conditions\":[{\"fieldName\":\"region\",\"operator\":\"EQ\",\"value\":\"华东\"}]}";
    }
}
