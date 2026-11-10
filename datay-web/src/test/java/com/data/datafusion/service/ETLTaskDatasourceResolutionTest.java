package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.repository.ETLEdgeRepository;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.etl.ETLNodeTranslatorRegistry;
import com.data.datafusion.service.mapper.ETLEdgeMapper;
import com.data.datafusion.service.mapper.ETLNodeMapper;
import com.data.datafusion.service.mapper.ETLTaskMapper;
import com.data.job.ComponentFactory;
import com.data.job.ComponentField;
import com.data.job.ComponentFieldType;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ETLTaskDatasourceResolutionTest {

    @Test
    void resolvesAnyDatasourceFieldDeclaredByComponentMetadata() {
        ComponentRegister annotation = TestDatasourceComponent.class.getAnnotation(ComponentRegister.class);
        ComponentFactory.registerComponent("TestDatasourceComponent", TestDatasourceComponent.class, annotation);

        ETLNodeMapper etlNodeMapper = mock(ETLNodeMapper.class);
        ETLEdgeMapper etlEdgeMapper = mock(ETLEdgeMapper.class);
        DataSourceService dataSourceService = mock(DataSourceService.class);
        ETLTaskService testService = new ETLTaskService(
            mock(ETLTaskRepository.class),
            mock(ETLNodeRepository.class),
            mock(ETLEdgeRepository.class),
            mock(ETLTaskMapper.class),
            etlNodeMapper,
            etlEdgeMapper,
            mock(JobService.class),
            dataSourceService,
            mock(ETLNodeTranslatorRegistry.class)
        );

        String config = """
            {
              "sourceDatasource": "ds-target",
              "sourceSchema": "analytics",
              "table": "target_table"
            }
            """;

        ETLNodeDTO nodeDTO = new ETLNodeDTO();
        nodeDTO.setCode("node-target");
        nodeDTO.setType("TestDatasourceComponent");
        nodeDTO.setConfig(config);

        ETLNode node = new ETLNode();
        node.setCode("node-target");
        node.setType("TestDatasourceComponent");
        node.setConfig(config);

        ETLTaskDTO task = new ETLTaskDTO();
        task.setNodes(List.of(nodeDTO));
        task.setEdges(Collections.emptyList());

        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setId("ds-target");
        dataSource.setType("POSTGRESQL");
        dataSource.setUrl("jdbc:postgresql://127.0.0.1:5432/analytics");
        dataSource.setUsername("postgres");
        dataSource.setPassword("secret");
        dataSource.setHostname("127.0.0.1");
        dataSource.setPort("5432");
        dataSource.setExtraParams(Map.of("sslmode", "disable"));

        when(etlNodeMapper.toEntity(anyList())).thenReturn(List.of(node));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(Collections.emptyList());
        when(dataSourceService.findOne("ds-target")).thenReturn(Optional.of(dataSource));

        JSONObject unit = JSONUtil
            .parseObj(testService.generateETLJobJson(task))
            .getJSONArray("units")
            .getJSONObject(0);
        JSONObject resolvedDatasource = unit.getJSONObject("sourceDatasource");

        assertThat(resolvedDatasource != null).isTrue();
        assertThat(resolvedDatasource.getStr("type")).isEqualTo("POSTGRESQL");
        assertThat(resolvedDatasource.getStr("url")).isEqualTo("jdbc:postgresql://127.0.0.1:5432/analytics");
        assertThat(resolvedDatasource.getStr("driver")).isEqualTo("org.postgresql.Driver");
        assertThat(resolvedDatasource.getStr("dbschema")).isEqualTo("analytics");
        assertThat(unit.containsKey("sourceId")).isFalse();
    }

    @ComponentRegister(value = "TestDatasourceComponent", name = "测试数据源组件")
    static class TestDatasourceComponent extends FlowComponent {

        @ComponentField(
            key = "sourceDatasource",
            label = "源数据源",
            type = ComponentFieldType.DATASOURCE,
            schemaField = "sourceSchema"
        )
        private DatasourceInfo sourceDatasource;

        @Override
        public void execute(FlowFile flowFile) {
            // 测试元数据解析，不需要执行逻辑
        }

        public DatasourceInfo getSourceDatasource() {
            return sourceDatasource;
        }

        public void setSourceDatasource(DatasourceInfo sourceDatasource) {
            this.sourceDatasource = sourceDatasource;
        }
    }
}
