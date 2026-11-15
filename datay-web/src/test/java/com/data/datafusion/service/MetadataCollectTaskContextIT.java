package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.MetadataCollectTaskDTO;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 验证元数据采集配置表、实体映射与保存流程。
 */
@IntegrationTest
@WithMockUser
class MetadataCollectTaskContextIT {

    @Autowired
    private MetadataCollectTaskService metadataCollectTaskService;

    @Autowired
    private DataSourceService dataSourceService;

    @TempDir
    Path tempDir;

    private String dataSourceId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0L);
        TenantContext.setTenantCode("default");
    }

    @AfterEach
    void tearDown() {
        if (dataSourceId != null) {
            metadataCollectTaskService.deleteByDataSourceId(dataSourceId);
            dataSourceService.delete(dataSourceId);
        }
        TenantContext.clear();
    }

    @Test
    void shouldSaveAndReadMetadataCollectConfiguration() {
        DataSourceDTO dataSource = createDataSource();

        MetadataCollectTaskDTO dto = new MetadataCollectTaskDTO();
        dto.setDataSourceId(dataSourceId);
        dto.setTaskName("测试元数据采集");
        dto.setSchemaNames(java.util.List.of("main"));
        dto.setEnabled(false);

        MetadataCollectTaskDTO saved = metadataCollectTaskService.save(dto);

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getJobId()).isNotBlank();
        assertThat(saved.getStatus()).isEqualTo("OFFLINE");
        assertThat(saved.getSchemaNames()).containsExactly("main");

        MetadataCollectTaskDTO loaded = metadataCollectTaskService.findByDataSourceId(dataSourceId).orElseThrow();
        assertThat(loaded.getTaskName()).isEqualTo("测试元数据采集");
        assertThat(loaded.getDataSourceName()).isEqualTo(dataSource.getName());
    }

    @Test
    void shouldOnlineAndOfflineMetadataCollectTask() {
        createDataSource();

        MetadataCollectTaskDTO dto = new MetadataCollectTaskDTO();
        dto.setDataSourceId(dataSourceId);
        dto.setTaskName("定时元数据采集");
        dto.setSchemaNames(java.util.List.of("main"));
        dto.setEnabled(true);
        dto.setCron("0 0 2 * * ?");

        MetadataCollectTaskDTO saved = metadataCollectTaskService.save(dto);
        assertThat(saved.getStatus()).isEqualTo("ONLINE");

        MetadataCollectTaskDTO disabledDto = new MetadataCollectTaskDTO();
        disabledDto.setId(saved.getId());
        disabledDto.setDataSourceId(dataSourceId);
        disabledDto.setTaskName(saved.getTaskName());
        disabledDto.setSchemaNames(saved.getSchemaNames());
        disabledDto.setEnabled(false);
        disabledDto.setCron(saved.getCron());
        MetadataCollectTaskDTO disabled = metadataCollectTaskService.save(disabledDto);
        assertThat(disabled.getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    void shouldAllowNonJdbcDataSources() {
        for (String[] item : java.util.List.of(
            new String[] { "ELASTICSEARCH", "http://127.0.0.1:9200", "_all" },
            new String[] { "MONGODB", "mongodb://127.0.0.1:27017/md_verify", "md_verify" }
        )) {
            DataSourceDTO dataSource = new DataSourceDTO();
            dataSource.setName(item[0].toLowerCase(java.util.Locale.ROOT) + "-" + System.nanoTime());
            dataSource.setType(item[0]);
            dataSource.setUrl(item[1]);
            dataSource.setSchemaName(item[2]);
            String id = dataSourceService.save(dataSource).getId();
            try {
                MetadataCollectTaskDTO dto = new MetadataCollectTaskDTO();
                dto.setDataSourceId(id);
                dto.setTaskName(item[0] + " 元数据采集");
                dto.setSchemaNames(java.util.List.of(item[2]));
                dto.setEnabled(false);

                MetadataCollectTaskDTO saved = metadataCollectTaskService.save(dto);
                assertThat(saved.getStatus()).isEqualTo("OFFLINE");
                assertThat(metadataCollectTaskService.findByDataSourceId(id)).isPresent();
            } finally {
                metadataCollectTaskService.deleteByDataSourceId(id);
                dataSourceService.delete(id);
            }
        }
    }

    private DataSourceDTO createDataSource() {
        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setName("metadata-context-" + System.currentTimeMillis());
        dataSource.setType("DUCKDB");
        dataSource.setUrl("jdbc:duckdb:" + tempDir.resolve("source.duckdb").toAbsolutePath());
        dataSource.setSchemaName("main");
        dataSourceId = dataSourceService.save(dataSource).getId();
        return dataSource;
    }
}
