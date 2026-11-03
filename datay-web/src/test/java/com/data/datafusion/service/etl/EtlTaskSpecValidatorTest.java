package com.data.datafusion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.AiResult;
import com.data.datafusion.ai.assistant.EtlComponentDoc;
import com.data.datafusion.ai.assistant.EtlComponentDocRegistry;
import com.data.datafusion.service.DataModelService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.ETLComponentCatalogService;
import com.data.datafusion.service.ETLTaskService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.ETLComponentDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link EtlTaskSpecValidator} 任务定义校验与归一化测试。
 */
class EtlTaskSpecValidatorTest {

    private final ETLComponentCatalogService catalogService = mock(ETLComponentCatalogService.class);
    private final EtlComponentDocRegistry docRegistry = mock(EtlComponentDocRegistry.class);
    private final DataSourceService dataSourceService = mock(DataSourceService.class);
    private final DataModelService dataModelService = mock(DataModelService.class);
    private final ETLTaskService eTLTaskService = mock(ETLTaskService.class);

    private EtlTaskSpecValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EtlTaskSpecValidator(
            catalogService,
            docRegistry,
            dataSourceService,
            dataModelService,
            eTLTaskService,
            new ObjectMapper()
        );
        when(catalogService.listCatalog()).thenReturn(
            List.of(
                component("StreamJdbcInput", "数据源输入", "数据输入"),
                component("StreamJdbcOutput", "数据源输出", "数据输出"),
                component("SqlInput", "SQL输入", "数据输入"),
                component("Join", "多表关联", "数据处理"),
                component("DorisStreamLoad", "Doris写入", "数据输出")
            )
        );
        when(dataSourceService.findOne("ds1")).thenReturn(Optional.of(new DataSourceDTO()));
        when(dataModelService.findOne(any())).thenReturn(Optional.empty());
        when(eTLTaskService.generateETLJobJson(any())).thenReturn("{}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeFriendlyFieldsAndRemoveEngineFields() {
        when(docRegistry.find("StreamJdbcInput")).thenReturn(Optional.of(doc("StreamJdbcInput", requiredTable("table"))));
        when(docRegistry.find("StreamJdbcOutput")).thenReturn(Optional.of(doc("StreamJdbcOutput", requiredTable("table"))));

        Map<String, Object> inputConfig = new LinkedHashMap<>();
        inputConfig.put("datasource", Map.of("url", "jdbc:mysql://localhost:3306/shop"));
        inputConfig.put("password", "secret");
        inputConfig.put("tables", List.of("orders", "users"));
        inputConfig.put("incrColumns", Map.of("orders", "updated_at"));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put(
            "nodes",
            List.of(
                node("n1", "StreamJdbcInput", inputConfig),
                node("n2", "StreamJdbcOutput", Map.of("sourceId", "ds1", "table", "orders"))
            )
        );
        spec.put("edges", List.of(Map.of("source", "n1", "target", "n2")));

        Map<String, Object> artifacts = new LinkedHashMap<>();
        Map<String, Object> result = validator.validateAndNormalize(spec, artifacts);

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");
        Map<String, Object> config = (Map<String, Object>) nodes.get(0).get("config");
        assertThat(config).doesNotContainKeys("datasource", "password", "tables", "incrColumns");
        assertThat(config).containsEntry("table", "orders,users");
        assertThat(config.get("incrColumn")).isEqualTo("{\"orders\":\"updated_at\"}");
        Map<String, Object> outputConfig = (Map<String, Object>) nodes.get(1).get("config");
        assertThat(outputConfig).containsEntry("model", "overwrite");
        assertThat(artifacts).containsKey(AiResult.ARTIFACT_WARNINGS);
        assertThat((List<String>) artifacts.get(AiResult.ARTIFACT_WARNINGS)).anyMatch(warning -> warning.contains("datasource"));
    }

    @Test
    void shouldRejectUnknownComponentType() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("nodes", List.of(node("n1", "NotExists", Map.of())));
        spec.put("edges", List.of());

        assertThatThrownBy(() -> validator.validateAndNormalize(spec, new LinkedHashMap<>()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未知组件类型");
    }

    @Test
    void shouldRejectCyclicGraph() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put(
            "nodes",
            List.of(node("n1", "StreamJdbcInput", Map.of()), node("n2", "StreamJdbcOutput", Map.of("sourceId", "ds1", "table", "t")))
        );
        spec.put(
            "edges",
            List.of(
                Map.of("source", "n1", "target", "n2"),
                Map.of("source", "n2", "target", "n1")
            )
        );

        assertThatThrownBy(() -> validator.validateAndNormalize(spec, new LinkedHashMap<>()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("环");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWarnWhenRequiredConfigMissing() {
        when(docRegistry.find("SqlInput")).thenReturn(Optional.of(doc("SqlInput", requiredTable("sql"))));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("nodes", List.of(node("n1", "SqlInput", Map.of("sourceId", "ds1"))));
        spec.put("edges", List.of());

        Map<String, Object> artifacts = new LinkedHashMap<>();
        validator.validateAndNormalize(spec, artifacts);

        List<String> warnings = (List<String>) artifacts.get(AiResult.ARTIFACT_WARNINGS);
        assertThat(warnings).anyMatch(warning -> warning.contains("缺少必填配置：sql"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotTreatNestedJoinFieldsAsTopLevelRequired() {
        EtlComponentDoc joinDoc = new EtlComponentDoc(
            "Join",
            "Join",
            "overview",
            List.of(),
            List.of(
                new EtlComponentDoc.ConfigTable(
                    "配置参数说明",
                    List.of("参数名", "类型", "必填", "描述"),
                    List.of(Map.of("参数名", "fromTable", "类型", "String", "必填", "是", "描述", "主表"))
                ),
                new EtlComponentDoc.ConfigTable(
                    "joins 元素说明",
                    List.of("字段", "类型", "必填", "描述"),
                    List.of(
                        Map.of("字段", "table", "类型", "String", "必填", "是", "描述", "关联表"),
                        Map.of("字段", "leftField", "类型", "String", "必填", "是", "描述", "左字段"),
                        Map.of("字段", "rightField", "类型", "String", "必填", "是", "描述", "右字段")
                    )
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "",
            false,
            ""
        );
        when(docRegistry.find("Join")).thenReturn(Optional.of(joinDoc));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put(
            "nodes",
            List.of(
                node(
                    "n1",
                    "Join",
                    Map.of(
                        "fromTable",
                        "orders",
                        "joins",
                        List.of(Map.of("table", "users", "leftField", "user_id", "rightField", "id"))
                    )
                )
            )
        );
        spec.put("edges", List.of());

        Map<String, Object> artifacts = new LinkedHashMap<>();
        validator.validateAndNormalize(spec, artifacts);

        List<String> warnings = (List<String>) artifacts.get(AiResult.ARTIFACT_WARNINGS);
        assertThat(warnings).noneMatch(warning -> warning.contains("缺少必填配置：table"));
        assertThat(warnings).noneMatch(warning -> warning.contains("缺少必填配置：leftField"));
        assertThat(warnings).noneMatch(warning -> warning.contains("缺少必填配置：rightField"));
        assertThat(warnings).noneMatch(warning -> warning.contains("缺少必填配置：fromTable"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotRequireEngineDatasourceFields() {
        EtlComponentDoc dorisDoc = new EtlComponentDoc(
            "DorisStreamLoad",
            "DorisStreamLoad",
            "overview",
            List.of(),
            List.of(
                new EtlComponentDoc.ConfigTable(
                    "配置参数说明",
                    List.of("参数名", "类型", "必填", "描述"),
                    List.of(
                        Map.of("参数名", "datasource", "类型", "Object", "必填", "是", "描述", "数据源"),
                        Map.of("参数名", "table", "类型", "String", "必填", "否", "描述", "目标表")
                    )
                ),
                new EtlComponentDoc.ConfigTable(
                    "datasource数据源配置",
                    List.of("参数名", "类型", "必填", "描述"),
                    List.of(
                        Map.of("参数名", "url", "类型", "String", "必填", "是", "描述", "URL"),
                        Map.of("参数名", "username", "类型", "String", "必填", "是", "描述", "用户名"),
                        Map.of("参数名", "password", "类型", "String", "必填", "是", "描述", "密码")
                    )
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "",
            false,
            ""
        );
        when(docRegistry.find("DorisStreamLoad")).thenReturn(Optional.of(dorisDoc));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("nodes", List.of(node("n1", "DorisStreamLoad", Map.of("sourceId", "ds1", "table", "t"))));
        spec.put("edges", List.of());

        Map<String, Object> artifacts = new LinkedHashMap<>();
        validator.validateAndNormalize(spec, artifacts);

        List<String> warnings = (List<String>) artifacts.get(AiResult.ARTIFACT_WARNINGS);
        assertThat(warnings).noneMatch(warning -> warning.contains("缺少必填配置：url"));
        assertThat(warnings).noneMatch(warning -> warning.contains("缺少必填配置：username"));
        assertThat(warnings).noneMatch(warning -> warning.contains("缺少必填配置：password"));
        assertThat(warnings).noneMatch(warning -> warning.contains("缺少必填配置：datasource"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAutoLayoutNodesWithoutCoordinates() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put(
            "nodes",
            List.of(node("n1", "StreamJdbcInput", Map.of()), node("n2", "StreamJdbcOutput", Map.of("sourceId", "ds1", "table", "t")))
        );
        spec.put("edges", List.of(Map.of("source", "n1", "target", "n2")));

        Map<String, Object> result = validator.validateAndNormalize(spec, new LinkedHashMap<>());

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");
        assertThat(nodes.get(0).get("x")).isEqualTo(100);
        assertThat(nodes.get(1).get("x")).isEqualTo(380);
        assertThat(nodes.get(0).get("y")).isEqualTo(120);
        assertThat(nodes.get(1).get("y")).isEqualTo(280);
    }

    private static Map<String, Object> node(String key, String type, Map<String, Object> config) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("key", key);
        node.put("type", type);
        node.put("label", type);
        node.put("config", new LinkedHashMap<>(config));
        return node;
    }

    private static ETLComponentDTO component(String code, String name, String group) {
        ETLComponentDTO dto = new ETLComponentDTO();
        dto.setCode(code);
        dto.setName(name);
        dto.setGroup(group);
        dto.setDesc(name);
        return dto;
    }

    private static EtlComponentDoc doc(String type, EtlComponentDoc.ConfigTable table) {
        return new EtlComponentDoc(
            type,
            type,
            "overview",
            List.of(),
            List.of(table),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "",
            false,
            ""
        );
    }

    private static EtlComponentDoc.ConfigTable requiredTable(String field) {
        return new EtlComponentDoc.ConfigTable(
            "配置参数",
            List.of("参数名", "类型", "必填", "描述"),
            List.of(Map.of("参数名", field, "类型", "String", "必填", "是", "描述", "字段说明"))
        );
    }
}
