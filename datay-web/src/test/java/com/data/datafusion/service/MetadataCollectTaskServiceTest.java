package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.json.JSONObject;
import com.data.datafusion.domain.MetadataCollectTask;
import com.data.datafusion.service.FileStorageConfigService.FileStorageConfig;
import com.data.datafusion.service.dto.DataSourceDTO;
import org.junit.jupiter.api.Test;

class MetadataCollectTaskServiceTest {

    @Test
    void shouldBuildEtlJobJsonWithMetadataCollect() {
        MetadataCollectTaskService service = new MetadataCollectTaskService(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        MetadataCollectTask task = new MetadataCollectTask();
        task.setDataSourceId("ds-1");
        task.setSchemaNamesJson("[\"public\",\"analytics\"]");
        task.setOutputDir("metadata");
        task.setCron("0 0 2 * * ?");

        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setName("业务库");
        dataSource.setType("MYSQL");
        dataSource.setUrl("jdbc:mysql://localhost:3306/shop");
        dataSource.setUsername("u");
        dataSource.setPassword("p");
        dataSource.setSchemaName("shop");

        FileStorageConfig storage = new FileStorageConfig();
        storage.type = "local";
        storage.localBasePath = "./data/files";

        JSONObject job = service.buildJobJson(task, dataSource, storage, "tenants/default");

        assertThat(job.getJSONArray("units")).hasSize(1);
        assertThat(job.getJSONArray("connections")).isEmpty();
        String text = job.toString();
        assertThat(text).contains("MetadataCollect", "metadata", "业务库", "tenants/default");
        assertThat(text).contains("jdbc:mysql://localhost:3306/shop");
        assertThat(text).contains("\"schemaNames\":[\"public\",\"analytics\"]");
        assertThat(text).contains("\"storageType\":\"local\"");
        assertThat(text).contains("./data/files");
    }

    @Test
    void shouldBuildMinioJobJsonWithSecretConfig() {
        MetadataCollectTaskService service = new MetadataCollectTaskService(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        MetadataCollectTask task = new MetadataCollectTask();
        task.setDataSourceId("ds-2");
        task.setSchemaNamesJson("[]");

        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setName("业务库");
        dataSource.setType("POSTGRESQL");
        dataSource.setUrl("jdbc:postgresql://localhost:5432/shop");

        FileStorageConfig storage = new FileStorageConfig();
        storage.type = "minio";
        storage.minioEndpoint = "http://localhost:9000";
        storage.minioAccessKey = "ak";
        storage.minioSecretKey = "sk";
        storage.minioBucketName = "datay-files";

        String text = service.buildJobJson(task, dataSource, storage, "tenants/demo").toString();

        assertThat(text).contains("\"storageType\":\"minio\"");
        assertThat(text).contains("http://localhost:9000", "ak", "sk", "datay-files");
        assertThat(text).contains("\"minioUrlStyle\":\"path\"");
    }
}
