package com.data.datafusion.service.datamap;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.domain.MetadataCollectTask;
import com.data.file.LocalFileStorageStrategy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataSnapshotLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadLatestCsvSnapshot() {
        LocalFileStorageStrategy storage = new LocalFileStorageStrategy(tempDir.toString());
        upload(
            storage,
            "metadata/ds1/latest/tables.csv",
            "data_source_id,data_source_name,catalog_name,schema_name,table_name,table_comment,collected_at\n" +
            "ds1,订单库,def,public,orders,订单表,2026-01-01T00:00:00Z\n"
        );
        upload(
            storage,
            "metadata/ds1/latest/columns.csv",
            "data_source_id,data_source_name,catalog_name,schema_name,table_name,column_name,ordinal_position,data_type,column_length,numeric_precision,numeric_scale,nullable,primary_key,default_value,column_comment,collected_at\n" +
            "ds1,订单库,def,public,orders,order_id,1,VARCHAR,64,0,0,false,true,,订单号,2026-01-01T00:00:00Z\n"
        );

        MetadataCollectTask task = new MetadataCollectTask();
        task.setDataSourceId("ds1");
        task.setOutputDir("metadata");

        MetadataSnapshotLoader loader = new MetadataSnapshotLoader(storage);
        Optional<MetadataSnapshotLoader.Snapshot> snapshot = loader.load("default", task);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().tables()).hasSize(1);
        assertThat(snapshot.get().tables().get(0).table()).isEqualTo("orders");
        assertThat(snapshot.get().tables().get(0).comment()).isEqualTo("订单表");
        assertThat(snapshot.get().columns()).hasSize(1);
        assertThat(snapshot.get().columns().get(0).column()).isEqualTo("order_id");
        assertThat(snapshot.get().columns().get(0).primaryKey()).isTrue();
    }

    private void upload(LocalFileStorageStrategy storage, String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        storage.upload(path, new ByteArrayInputStream(bytes), bytes.length, "text/csv");
    }
}
