package com.data.job.component.cdc;

import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CDC 快照并发读取回归测试。
 *
 * <p>使用本地 DuckDB 文件作为源库，验证按主键分区并发读取时：
 * <ul>
 *   <li>所有行都被读取且只读取一次；</li>
 *   <li>快照完成标记与起始位点写入同一个 FlowFile。</li>
 * </ul>
 */
class CdcSnapshotReaderTest {

    @Test
    void shouldReadAllRowsExactlyOnceWithParallelSnapshot() throws Exception {
        Path dbFile = Files.createTempFile("cdc-snapshot-test-", ".duckdb");
        Files.deleteIfExists(dbFile);
        String url = "jdbc:duckdb:" + dbFile.toAbsolutePath();

        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType("duckdb");
        datasource.setUrl(url);
        datasource.setDbschema("main");

        try (Connection conn = DBUtils.getConnection(datasource);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, name VARCHAR)");
            for (int i = 1; i <= 200; i++) {
                stmt.execute("INSERT INTO t VALUES (" + i + ", 'row-" + i + "')");
            }
        }

        CapturingComponent component = new CapturingComponent();
        component.setId("cdc-snapshot");
        component.setTaskLogger(new TaskLogger("cdc-snapshot-test", "cdc-snapshot-test"));

        Map<String, Object> startStatus = new HashMap<>();
        startStatus.put("binlogPosition", "0x0000002A0000003C0004");

        new CdcSnapshotReader(component, datasource, 17, false, 4)
            .snapshot(Collections.singletonList(new CdcSnapshotReader.TableRef("main", "t")), startStatus);

        List<Integer> ids = new ArrayList<>();
        List<FlowFile> dataFiles = new ArrayList<>();
        FlowFile marker = null;
        for (FlowFile flowFile : component.written) {
            if (flowFile.getJsonArray().isEmpty()) {
                marker = flowFile;
                continue;
            }
            dataFiles.add(flowFile);
            flowFile.getJsonArray().forEach(item -> ids.add(((com.alibaba.fastjson2.JSONObject) item).getInteger("id")));
        }

        assertEquals(200, ids.size(), "并发分区读取不能重复或遗漏数据");
        assertEquals(200, ids.stream().distinct().count(), "并发分区读取出现重复行");
        assertTrue(ids.containsAll(java.util.stream.IntStream.rangeClosed(1, 200).boxed().toList()));
        assertTrue(dataFiles.size() >= 1);
        assertTrue(
            component.logs.stream().anyMatch(message -> message.contains("使用字段 id 分区")),
            "并发快照应按主键分区读取"
        );
        org.junit.jupiter.api.Assertions.assertNotNull(marker, "快照完成后必须写入 snapshotDone 标记");
        assertEquals("0x0000002A0000003C0004", marker.getStatusMap().get("cdc-snapshot.binlogPosition"));
        assertEquals(true, marker.getStatusMap().get("cdc-snapshot.snapshotDone"));

        Files.deleteIfExists(dbFile);
    }

    private static class CapturingComponent extends FlowComponent {

        private final List<FlowFile> written = Collections.synchronizedList(new ArrayList<>());
        private final List<String> logs = Collections.synchronizedList(new ArrayList<>());

        private CapturingComponent() {
            setType(ComponentType.SOURCE);
        }

        @Override
        public void execute(FlowFile flowFile) {
            // 测试无需实现
        }

        @Override
        public void logInfo(String... messages) {
            logs.add(String.join(" ", messages));
        }

        @Override
        public void writeRecords(FlowFile flowFile) {
            written.add(flowFile);
        }
    }
}
