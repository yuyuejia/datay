package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.domain.RoleDataScope;
import com.data.datafusion.repository.AuthorityRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.repository.RoleDataScopeRepository;
import com.data.datafusion.service.dto.RoleDataScopeDTO;
import com.data.datafusion.service.mapper.RoleDataScopeMapper;
import com.data.datafusion.service.metric.ScopedDimension;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link RoleDataScopeService} 角色数据范围解析的单元测试。
 */
class RoleDataScopeServiceTest {

    private RoleDataScopeRepository roleDataScopeRepository;
    private AuthorityRepository authorityRepository;
    private DataModelRepository dataModelRepository;
    private ModelFieldRepository modelFieldRepository;
    private RoleDataScopeMapper roleDataScopeMapper;
    private RoleDataScopeService roleDataScopeService;

    @BeforeEach
    void setUp() {
        roleDataScopeRepository = mock(RoleDataScopeRepository.class);
        authorityRepository = mock(AuthorityRepository.class);
        dataModelRepository = mock(DataModelRepository.class);
        modelFieldRepository = mock(ModelFieldRepository.class);
        roleDataScopeMapper = mock(RoleDataScopeMapper.class);
        roleDataScopeService = new RoleDataScopeService(
            roleDataScopeRepository,
            authorityRepository,
            dataModelRepository,
            modelFieldRepository,
            roleDataScopeMapper,
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

    private RoleDataScope scope(String roleName, String dimensionModelId, String filterConfig) {
        RoleDataScope entity = new RoleDataScope();
        entity.setRoleName(roleName);
        entity.setDimensionModelId(dimensionModelId);
        entity.setFilterConfig(filterConfig);
        entity.setEnabled(true);
        return entity;
    }

    @Test
    void shouldReturnEmptyForAdmin() {
        authenticate("ROLE_ADMIN");
        assertThat(roleDataScopeService.resolveEffectiveScopes()).isEmpty();
        verify(roleDataScopeRepository, never()).findByRoleNameInAndEnabledTrue(any());
    }

    @Test
    void shouldReturnEmptyWhenNoRules() {
        authenticate("ROLE_REGION");
        when(roleDataScopeRepository.findByRoleNameInAndEnabledTrue(Set.of("ROLE_REGION"))).thenReturn(List.of());
        assertThat(roleDataScopeService.resolveEffectiveScopes()).isEmpty();
    }

    @Test
    void shouldGroupConditionsByDimension() {
        authenticate("ROLE_REGION");
        RoleDataScope store = scope(
            "ROLE_REGION",
            "3002",
            "{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionFieldName\":\"city\",\"operator\":\"EQ\",\"value\":\"北京\"}]}"
        );
        RoleDataScope store2 = scope(
            "ROLE_REGION",
            "3002",
            "{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionFieldName\":\"city\",\"operator\":\"EQ\",\"value\":\"上海\"}]}"
        );
        RoleDataScope product = scope(
            "ROLE_REGION",
            "3003",
            "{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionFieldName\":\"category_l1\",\"operator\":\"IN\",\"value\":\"手机,电脑\"}]}"
        );
        when(roleDataScopeRepository.findByRoleNameInAndEnabledTrue(any())).thenReturn(List.of(store, store2, product));

        List<ScopedDimension> scopes = roleDataScopeService.resolveEffectiveScopes();
        assertThat(scopes).hasSize(2);
        assertThat(scopes.get(0).getDimensionModelId()).isEqualTo("3002");
        assertThat(scopes.get(0).getConditions()).hasSize(2);
        assertThat(scopes.get(1).getDimensionModelId()).isEqualTo("3003");
        assertThat(scopes.get(1).getConditions()).hasSize(1);
    }

    @Test
    void shouldIgnoreRulesWithoutConditions() {
        authenticate("ROLE_REGION");
        RoleDataScope empty = scope("ROLE_REGION", "3002", null);
        when(roleDataScopeRepository.findByRoleNameInAndEnabledTrue(any())).thenReturn(List.of(empty));
        assertThat(roleDataScopeService.resolveEffectiveScopes()).isEmpty();
    }

    @Test
    void shouldRejectUnknownRoleOnSave() {
        RoleDataScopeDTO dto = new RoleDataScopeDTO();
        dto.setRoleName("ROLE_UNKNOWN");
        dto.setDimensionModelId("3002");
        when(roleDataScopeMapper.toEntity(dto)).thenReturn(dtoEntity("ROLE_UNKNOWN", "3002", validConfig()));
        when(authorityRepository.existsById("ROLE_UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> roleDataScopeService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("角色不存在");
    }

    @Test
    void shouldRejectNonDimensionModelOnSave() {
        RoleDataScopeDTO dto = new RoleDataScopeDTO();
        dto.setRoleName("ROLE_REGION");
        dto.setDimensionModelId("3101");
        when(roleDataScopeMapper.toEntity(dto)).thenReturn(dtoEntity("ROLE_REGION", "3101", validConfig()));
        when(authorityRepository.existsById("ROLE_REGION")).thenReturn(true);
        DataModel fact = new DataModel();
        fact.setModelType("DWD");
        fact.setCode("fact_sales_order_item");
        when(dataModelRepository.findById("3101")).thenReturn(Optional.of(fact));

        assertThatThrownBy(() -> roleDataScopeService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("只能绑定维度模型");
    }

    @Test
    void shouldRejectInvalidOperatorOnSave() {
        RoleDataScopeDTO dto = new RoleDataScopeDTO();
        dto.setRoleName("ROLE_REGION");
        dto.setDimensionModelId("3002");
        String config = "{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionFieldName\":\"city\",\"operator\":\"BAD\",\"value\":\"北京\"}]}";
        when(roleDataScopeMapper.toEntity(dto)).thenReturn(dtoEntity("ROLE_REGION", "3002", config));
        when(authorityRepository.existsById("ROLE_REGION")).thenReturn(true);
        when(dataModelRepository.findById("3002")).thenReturn(Optional.of(dimensionModel("3002", "dim_store")));
        ModelField city = new ModelField();
        city.setFieldName("city");
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("3002")).thenReturn(List.of(city));

        assertThatThrownBy(() -> roleDataScopeService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的条件运算符");
    }

    @Test
    void shouldPreserveTenantIdOnUpdate() {
        RoleDataScopeDTO dto = new RoleDataScopeDTO();
        dto.setId("1");
        dto.setRoleName("ROLE_REGION");
        dto.setDimensionModelId("3002");
        dto.setFilterConfig(validConfig());
        dto.setEnabled(true);

        RoleDataScope existing = new RoleDataScope();
        existing.setId("1");
        existing.setTenantId("1");
        when(roleDataScopeRepository.findById("1")).thenReturn(Optional.of(existing));

        RoleDataScope mapped = dtoEntity("ROLE_REGION", "3002", validConfig());
        mapped.setId("1");
        when(roleDataScopeMapper.toEntity(dto)).thenReturn(mapped);
        when(roleDataScopeMapper.toDto(any(RoleDataScope.class))).thenReturn(dto);
        when(authorityRepository.existsById("ROLE_REGION")).thenReturn(true);
        when(dataModelRepository.findById("3002")).thenReturn(Optional.of(dimensionModel("3002", "dim_store")));
        ModelField city = new ModelField();
        city.setFieldName("city");
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("3002")).thenReturn(List.of(city));
        when(roleDataScopeRepository.save(any(RoleDataScope.class))).thenAnswer(invocation -> invocation.getArgument(0));

        roleDataScopeService.update(dto);

        assertThat(mapped.getTenantId()).isEqualTo("1");
    }

    private RoleDataScope dtoEntity(String roleName, String dimensionModelId, String filterConfig) {
        RoleDataScope entity = new RoleDataScope();
        entity.setRoleName(roleName);
        entity.setDimensionModelId(dimensionModelId);
        entity.setFilterConfig(filterConfig);
        entity.setEnabled(true);
        return entity;
    }

    private DataModel dimensionModel(String id, String code) {
        DataModel model = new DataModel();
        model.setId(id);
        model.setCode(code);
        model.setModelType("DIMENSION");
        return model;
    }

    private String validConfig() {
        return "{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionFieldName\":\"city\",\"operator\":\"EQ\",\"value\":\"北京\"}]}";
    }
}
