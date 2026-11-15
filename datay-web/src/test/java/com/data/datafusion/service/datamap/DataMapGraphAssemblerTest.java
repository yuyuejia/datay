package com.data.datafusion.service.datamap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DataQualityTask;
import com.data.datafusion.domain.DataSource;
import com.data.datafusion.domain.DataSync;
import com.data.datafusion.domain.DataSyncTableConfig;
import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.domain.MetadataCollectTask;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.DataApiRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.DataQualityTaskRepository;
import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.repository.DataSyncRepository;
import com.data.datafusion.repository.DataSyncTableConfigRepository;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.MetadataCollectTaskRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dashboard.AnalysisDatasetResolver;
import com.data.datafusion.service.datamap.MetadataSnapshotLoader.ColumnSnapshot;
import com.data.datafusion.service.datamap.MetadataSnapshotLoader.Snapshot;
import com.data.datafusion.service.datamap.MetadataSnapshotLoader.TableSnapshot;
import com.data.datafusion.service.dto.datamap.DataMapAssetTypes;
import com.data.datafusion.service.lineage.SqlLineageParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataMapGraphAssemblerTest {

    @Test
    void shouldBuildAssetGraphFromPlatformObjectsAndMetadataSnapshot() {
        DataSourceRepository dataSourceRepository = mock(DataSourceRepository.class);
        MetadataCollectTaskRepository metadataRepository = mock(MetadataCollectTaskRepository.class);
        ETLTaskRepository etlTaskRepository = mock(ETLTaskRepository.class);
        ETLNodeRepository etlNodeRepository = mock(ETLNodeRepository.class);
        DataSyncRepository dataSyncRepository = mock(DataSyncRepository.class);
        DataSyncTableConfigRepository dataSyncTableConfigRepository = mock(DataSyncTableConfigRepository.class);
        DataModelRepository dataModelRepository = mock(DataModelRepository.class);
        ModelFieldRepository modelFieldRepository = mock(ModelFieldRepository.class);
        MetricRepository metricRepository = mock(MetricRepository.class);
        AnalysisDatasetRepository datasetRepository = mock(AnalysisDatasetRepository.class);
        AnalysisDashboardRepository dashboardRepository = mock(AnalysisDashboardRepository.class);
        DataApiRepository dataApiRepository = mock(DataApiRepository.class);
        DataQualityTaskRepository qualityTaskRepository = mock(DataQualityTaskRepository.class);
        MetadataSnapshotLoader snapshotLoader = mock(MetadataSnapshotLoader.class);
        AnalysisDatasetResolver datasetResolver = mock(AnalysisDatasetResolver.class);

        DataSource dataSource = new DataSource();
        dataSource.setId("ds1");
        dataSource.setName("订单库");
        dataSource.setType("mysql");
        dataSource.setSchemaName("public");

        MetadataCollectTask collectTask = new MetadataCollectTask();
        collectTask.setDataSourceId("ds1");
        collectTask.setOutputDir("metadata");

        DataModel model = new DataModel();
        model.setId("model1");
        model.setName("订单事实模型");
        model.setCode("fact_order");
        model.setModelType("FACT");
        model.setDataSourceId("ds1");
        model.setSchemaName("public");
        model.setTableName("order_detail");

        ModelField field = new ModelField();
        field.setId("field1");
        field.setModelId("model1");
        field.setFieldName("order_id");
        field.setFieldType("VARCHAR");

        Metric metric = new Metric();
        metric.setId("metric1");
        metric.setName("订单金额");
        metric.setCode("order_amount");
        metric.setMetricType(Metric.TYPE_ATOMIC);
        metric.setFactModelId("model1");

        when(dataSourceRepository.findAll()).thenReturn(List.of(dataSource));
        when(metadataRepository.findAll()).thenReturn(List.of(collectTask));
        when(etlTaskRepository.findAll()).thenReturn(List.of());
        when(etlNodeRepository.findAll()).thenReturn(List.of());
        when(dataSyncRepository.findAll()).thenReturn(List.of());
        when(dataSyncTableConfigRepository.findAll()).thenReturn(List.of());
        when(dataModelRepository.findAll()).thenReturn(List.of(model));
        when(modelFieldRepository.findAll()).thenReturn(List.of(field));
        when(metricRepository.findAll()).thenReturn(List.of(metric));
        when(datasetRepository.findAll()).thenReturn(List.of());
        when(dashboardRepository.findAll()).thenReturn(List.of());
        when(dataApiRepository.findAll()).thenReturn(List.of());
        when(qualityTaskRepository.findAll()).thenReturn(List.of());
        when(snapshotLoader.load(any(), any()))
            .thenReturn(
                Optional.of(
                    new Snapshot(
                        "metadata/ds1/latest/tables.csv",
                        "metadata/ds1/latest/columns.csv",
                        List.of(new TableSnapshot("def", "public", "order_detail", "订单明细", "2026-01-01")),
                        List.of(
                            new ColumnSnapshot(
                                "def",
                                "public",
                                "order_detail",
                                "order_id",
                                1,
                                "VARCHAR",
                                64,
                                0,
                                0,
                                false,
                                true,
                                null,
                                "订单号",
                                "2026-01-01"
                            )
                        )
                    )
                )
            );

        DataMapGraphAssembler assembler = new DataMapGraphAssembler(
            dataSourceRepository,
            metadataRepository,
            etlTaskRepository,
            etlNodeRepository,
            dataSyncRepository,
            dataSyncTableConfigRepository,
            dataModelRepository,
            modelFieldRepository,
            metricRepository,
            datasetRepository,
            dashboardRepository,
            dataApiRepository,
            qualityTaskRepository,
            snapshotLoader,
            new EtlTableRefResolver(new SqlLineageParser()),
            new SqlLineageParser(),
            datasetResolver,
            new ObjectMapper()
        );

        var result = assembler.build("default");

        assertThat(result.coverage()).isEqualTo("1/1");
        assertThat(result.nodes()).extracting(node -> node.type()).contains(
            DataMapAssetTypes.DATASOURCE,
            DataMapAssetTypes.SCHEMA,
            DataMapAssetTypes.TABLE,
            DataMapAssetTypes.COLUMN,
            DataMapAssetTypes.DATA_MODEL,
            DataMapAssetTypes.MODEL_FIELD,
            DataMapAssetTypes.METRIC
        );
        assertThat(result.edges()).extracting(edge -> edge.relation()).contains("CONTAINS", "MAPS_TO", "COMPUTES");
    }
}
