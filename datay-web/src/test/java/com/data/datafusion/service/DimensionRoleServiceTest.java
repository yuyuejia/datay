package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DimensionRole;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.DimensionRoleRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.DimensionRoleDTO;
import com.data.datafusion.service.mapper.DimensionRoleMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 维度角色管理单元测试。
 */
class DimensionRoleServiceTest {

    private DimensionRoleRepository dimensionRoleRepository;
    private DataModelRepository dataModelRepository;
    private ModelFieldRepository modelFieldRepository;
    private DimensionRoleService service;

    @BeforeEach
    void setUp() {
        dimensionRoleRepository = mock(DimensionRoleRepository.class);
        dataModelRepository = mock(DataModelRepository.class);
        modelFieldRepository = mock(ModelFieldRepository.class);
        DimensionRoleMapper mapper = mock(DimensionRoleMapper.class);
        when(mapper.toDto(any(DimensionRole.class))).thenAnswer(invocation -> {
            DimensionRole role = invocation.getArgument(0);
            DimensionRoleDTO dto = new DimensionRoleDTO();
            dto.setId(role.getId());
            dto.setDimensionModelId(role.getDimensionModelId());
            dto.setCode(role.getCode());
            dto.setName(role.getName());
            dto.setDescription(role.getDescription());
            dto.setSortOrder(role.getSortOrder());
            return dto;
        });
        when(mapper.toDto(any(List.class))).thenAnswer(invocation -> {
            List<DimensionRole> roles = invocation.getArgument(0);
            return roles
                .stream()
                .map(role -> {
                    DimensionRoleDTO dto = new DimensionRoleDTO();
                    dto.setDimensionModelId(role.getDimensionModelId());
                    dto.setCode(role.getCode());
                    dto.setName(role.getName());
                    return dto;
                })
                .toList();
        });
        service = new DimensionRoleService(dimensionRoleRepository, dataModelRepository, modelFieldRepository, mapper);

        DataModel dimDate = new DataModel();
        dimDate.setId("dim-1");
        dimDate.setName("日期维度");
        dimDate.setModelType("DIMENSION");
        when(dataModelRepository.findById("dim-1")).thenReturn(Optional.of(dimDate));
        when(dimensionRoleRepository.findByDimensionModelIdOrderBySortOrderAscCodeAsc("dim-1")).thenReturn(List.of());
    }

    @Test
    void shouldNormalizeRoleCodeToUppercase() {
        DimensionRoleDTO dto = new DimensionRoleDTO();
        dto.setCode("order_date");
        dto.setName("下单日期");
        when(dimensionRoleRepository.save(any(DimensionRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DimensionRoleDTO saved = service.create("dim-1", dto);

        assertThat(saved.getCode()).isEqualTo("ORDER_DATE");
        assertThat(saved.getName()).isEqualTo("下单日期");
    }

    @Test
    void shouldRejectInvalidRoleCode() {
        DimensionRoleDTO dto = new DimensionRoleDTO();
        dto.setCode("下单日期");

        assertThatThrownBy(() -> service.create("dim-1", dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("维度角色编码");
    }

    @Test
    void shouldRejectDuplicateRoleCode() {
        DimensionRoleDTO dto = new DimensionRoleDTO();
        dto.setCode("ORDER_DATE");
        when(dimensionRoleRepository.existsByDimensionModelIdAndCodeIgnoreCase("dim-1", "ORDER_DATE")).thenReturn(true);

        assertThatThrownBy(() -> service.create("dim-1", dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("已存在");
        verify(dimensionRoleRepository, never()).save(any(DimensionRole.class));
    }

    @Test
    void shouldRejectDeletingReferencedRole() {
        DimensionRole role = new DimensionRole();
        role.setId("role-1");
        role.setDimensionModelId("dim-1");
        role.setCode("ORDER_DATE");
        when(dimensionRoleRepository.findById("role-1")).thenReturn(Optional.of(role));
        when(modelFieldRepository.existsByDimensionModelIdAndDimensionRoleIgnoreCase("dim-1", "ORDER_DATE")).thenReturn(true);

        assertThatThrownBy(() -> service.delete("dim-1", "role-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能删除");
    }
}
