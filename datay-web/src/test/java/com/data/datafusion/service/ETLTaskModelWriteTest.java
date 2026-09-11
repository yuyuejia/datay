package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONUtil;
import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.repository.ETLEdgeRepository;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.service.dto.DataModelDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.mapper.ETLEdgeMapper;
import com.data.datafusion.service.mapper.ETLNodeMapper;
import com.data.datafusion.service.mapper.ETLTaskMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 「模型写入」组件转换为 {@code StreamJdbcOutput} 任务定义的单元测试。
 */
class ETLTaskModelWriteTest {

    private ETLTaskService etlTaskService;

    private ETLNodeMapper eTLNodeMapper;

    private ETLEdgeMapper etlEdgeMapper;

    private DataModelService dataModelService;

    private DataSourceService dataSourceService;

    @BeforeEach
    void setUp() {
        eTLNodeMapper = mock(ETLNodeMapper.class);
        etlEdgeMapper = mock(ETLEdgeMapper.class);
        dataModelService = mock(DataModelService.class);
        dataSourceService = mock(DataSourceService.class);
        etlTaskService = new ETLTaskService(
            mock(ETLTaskRepository.class),
            mock(ETLNodeRepository.class),
            mock(ETLEdgeRepository.class),
            mock(ETLTaskMapper.class),
            eTLNodeMapper,
            etlEdgeMapper,
            mock(JobService.class),
            dataSourceService,
            dataModelService
        );
    }

    private ETLNode modelWriteNode(String config) {
        ETLNode node = new ETLNode();
        node.setCode("MODEL_OUT_01");
        node.setLabel("模型写入");
        node.setType("ModelWrite");
        node.setConfig(config);
        return node;
    }

    @Test
    void shouldGenerateStreamJdbcOutputFromDataModel() {
        when(eTLNodeMapper.toEntity(anyList())).thenReturn(List.of(modelWriteNode("{\"modelId\":3001,\"model\":\"overwrite\"}")));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(List.of());

        DataModelDTO dataModel = new DataModelDTO();
        dataModel.setId(3001L);
        dataModel.setName("客户维度");
        dataModel.setDataSourceId(9L);
        dataModel.setSchemaName("dwd");
        dataModel.setTableName("dim_customer");
        when(dataModelService.findOne(3001L)).thenReturn(Optional.of(dataModel));

        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setId(9L);
        dataSource.setUrl("jdbc:mysql://127.0.0.1:3306");
        dataSource.setUsername("root");
        dataSource.setPassword("password");
        when(dataSourceService.findOne(9L)).thenReturn(Optional.of(dataSource));

        String jobJson = etlTaskService.generateETLJobJson(new ETLTaskDTO());

        assertThat(jobJson).isNotNull();
        var units = JSONUtil.parseObj(jobJson).getJSONArray("units");
        assertThat(units).hasSize(1);
        var unit = units.getJSONObject(0);
        assertThat(unit.getStr(".id")).isEqualTo("MODEL_OUT_01");
        // 后端组件直接使用 StreamJdbcOutput
        assertThat(unit.getStr(".name")).isEqualTo("StreamJdbcOutput");
        assertThat(unit.getStr("table")).isEqualTo("dim_customer");
        assertThat(unit.getStr("schema")).isEqualTo("dwd");
        assertThat(unit.getStr("model")).isEqualTo("overwrite");
        var sourceId = unit.getJSONObject("sourceId");
        assertThat(sourceId.getStr("url")).isEqualTo("jdbc:mysql://127.0.0.1:3306");
        assertThat(sourceId.getStr("username")).isEqualTo("root");
        assertThat(sourceId.getStr("dbschema")).isEqualTo("dwd");
    }

    @Test
    void shouldFailWhenModelIdMissing() {
        when(eTLNodeMapper.toEntity(anyList())).thenReturn(List.of(modelWriteNode("{}")));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> etlTaskService.generateETLJobJson(new ETLTaskDTO()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未选择数据模型");
    }

    @Test
    void shouldFailWhenDataModelNotBoundToDataSource() {
        when(eTLNodeMapper.toEntity(anyList())).thenReturn(List.of(modelWriteNode("{\"modelId\":3001}")));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(List.of());

        DataModelDTO dataModel = new DataModelDTO();
        dataModel.setId(3001L);
        dataModel.setName("客户维度");
        when(dataModelService.findOne(3001L)).thenReturn(Optional.of(dataModel));

        assertThatThrownBy(() -> etlTaskService.generateETLJobJson(new ETLTaskDTO()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未绑定数据源");
    }

    @Test
    void shouldKeepOtherComponentsUnchanged() {
        ETLNode sqlNode = new ETLNode();
        sqlNode.setCode("SQL_01");
        sqlNode.setType("DuckDBSql");
        sqlNode.setConfig("{}");
        when(eTLNodeMapper.toEntity(anyList())).thenReturn(List.of(sqlNode));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(List.of());

        String jobJson = etlTaskService.generateETLJobJson(new ETLTaskDTO());

        var units = JSONUtil.parseObj(jobJson).getJSONArray("units");
        assertThat(units.getJSONObject(0).getStr(".name")).isEqualTo("DuckDBSql");
    }
}
