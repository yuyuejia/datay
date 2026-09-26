package com.data.datafusion.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DimensionInfoTool} 维度信息查询工具测试。
 */
class DimensionInfoToolTest {

    private DataModelRepository dataModelRepository;
    private ModelFieldRepository modelFieldRepository;
    private DimensionInfoTool tool;

    @BeforeEach
    void setUp() {
        dataModelRepository = mock(DataModelRepository.class);
        modelFieldRepository = mock(ModelFieldRepository.class);
        tool = new DimensionInfoTool(dataModelRepository, modelFieldRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnBasicInfoAndRecommendedNameField() {
        DataModel model = new DataModel();
        model.setId(3002L);
        model.setCode("dim_store");
        model.setName("门店维度");
        model.setModelType("DIMENSION");
        model.setDimensionKind("NORMAL");
        when(dataModelRepository.findFirstByCode("dim_store")).thenReturn(Optional.of(model));

        ModelField sk = field("store_sk", "LONG", true, null, null);
        ModelField code = field("store_code", "VARCHAR", false, null, null);
        ModelField name = field("store_name", "VARCHAR", false, null, null);
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3002L)).thenReturn(List.of(sk, code, name));

        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("dimensionModelCode", "dim_store"), null);

        assertThat(result).containsEntry("code", "dim_store");
        assertThat(result).containsEntry("isHierarchy", false);
        assertThat(result).containsEntry("recommendedDisplayField", "store_name");
        assertThat((List<String>) result.get("primaryKeys")).containsExactly("store_sk");
        assertThat((List<?>) result.get("fields")).hasSize(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRecommendLevelNameFieldForHierarchy() {
        DataModel model = new DataModel();
        model.setId(3004L);
        model.setCode("dim_category");
        model.setName("品类层级维度");
        model.setModelType("DIMENSION");
        model.setDimensionKind("HIERARCHY");
        model.setLevelCount(2);
        when(dataModelRepository.findFirstByCode("dim_category")).thenReturn(Optional.of(model));

        ModelField l1id = field("level1_id", "LONG", true, "LEVEL_ID", 1);
        ModelField l1name = field("level1_name", "VARCHAR", false, "LEVEL_NAME", 1);
        ModelField l2id = field("level2_id", "LONG", true, "LEVEL_ID", 2);
        ModelField l2name = field("level2_name", "VARCHAR", false, "LEVEL_NAME", 2);
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3004L)).thenReturn(List.of(l1id, l1name, l2id, l2name));

        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("dimensionModelCode", "dim_category"), null);

        assertThat(result).containsEntry("isHierarchy", true);
        assertThat(result).containsEntry("levelCount", 2);
        assertThat((List<?>) result.get("levels")).hasSize(2);
        assertThat(result).containsEntry("recommendedDisplayField", "level2_name");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreferConfiguredDisplayField() {
        DataModel model = new DataModel();
        model.setId(3002L);
        model.setCode("dim_store");
        model.setName("门店维度");
        model.setModelType("DIMENSION");
        model.setDimensionKind("NORMAL");
        model.setDisplayFieldName("store_code");
        when(dataModelRepository.findFirstByCode("dim_store")).thenReturn(Optional.of(model));

        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc(3002L)).thenReturn(
            List.of(field("store_sk", "LONG", true, null, null), field("store_code", "VARCHAR", false, null, null), field("store_name", "VARCHAR", false, null, null))
        );

        Map<String, Object> result = (Map<String, Object>) tool.execute(Map.of("dimensionModelCode", "dim_store"), null);

        assertThat(result).containsEntry("displayFieldName", "store_code");
        assertThat(result).containsEntry("recommendedDisplayField", "store_code");
    }

    @Test
    void shouldReturnErrorWhenDimensionMissing() {
        when(dataModelRepository.findFirstByCode("nope")).thenReturn(Optional.empty());
        assertThat(tool.execute(Map.of("dimensionModelCode", "nope"), null)).isEqualTo(Map.of("error", "维度模型不存在：nope"));
    }

    private static ModelField field(String name, String type, boolean pk, String role, Integer level) {
        ModelField field = new ModelField();
        field.setFieldName(name);
        field.setFieldType(type);
        field.setIsPrimaryKey(pk);
        field.setFieldRole(role);
        field.setLevelIndex(level);
        return field;
    }
}
