package com.data.datafusion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.status.LocalFileStatusStorageStrategy;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EtlTaskStateServiceTest {

    @TempDir
    Path tempDir;

    private ETLTaskRepository etlTaskRepository;

    private ETLNodeRepository etlNodeRepository;

    private StatusStorageConfigService statusStorageConfigService;

    private JobInstanceService jobInstanceService;

    private LocalFileStatusStorageStrategy storage;

    private EtlTaskStateService service;

    private ETLTask task;

    @BeforeEach
    void setUp() {
        etlTaskRepository = mock(ETLTaskRepository.class);
        etlNodeRepository = mock(ETLNodeRepository.class);
        statusStorageConfigService = mock(StatusStorageConfigService.class);
        jobInstanceService = mock(JobInstanceService.class);

        storage = new LocalFileStatusStorageStrategy(tempDir.toString());
        when(statusStorageConfigService.getActiveStrategy()).thenReturn(storage);

        task = new ETLTask();
        task.setId("5");
        task.setJobId("1001");
        task.setTaskName("sync-task");

        when(etlTaskRepository.findById("5")).thenReturn(Optional.of(task));
        when(etlTaskRepository.findByJobId("1001")).thenReturn(Optional.of(task));
        when(jobInstanceService.findByJobCodeAndStatus(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.Collections.emptyList());

        ETLNode node = new ETLNode();
        node.setCode("node1");
        node.setLabel("订单输入");
        when(etlNodeRepository.findAllByTaskId("5")).thenReturn(Optional.of(java.util.List.of(node)));

        service = new EtlTaskStateService(etlTaskRepository, etlNodeRepository, statusStorageConfigService, jobInstanceService);
    }

    @Test
    void getNodeNamesReturnsLabel() {
        assertEquals("订单输入", service.getNodeNames("5").get("node1"));
    }

    @Test
    void patchMergesKeysAndNullDeletes() {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("node1.binlogFile", "mysql-bin.000001");
        initial.put("node1.binlogPosition", 100);
        storage.saveStatus(initial, "1001");

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("node1.binlogPosition", 200);
        patch.put("node1.binlogFile", null);

        Map<String, Object> merged = service.patchState("5", patch);

        assertFalse(merged.containsKey("node1.binlogFile"));
        assertEquals(200, merged.get("node1.binlogPosition"));
        assertEquals(200, service.loadState("5").get("node1.binlogPosition"));
    }

    @Test
    void deleteSingleKeyKeepsOthers() {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("a", "1");
        initial.put("b", "2");
        storage.saveStatus(initial, "1001");

        Map<String, Object> result = service.deleteState("5", "a");

        assertFalse(result.containsKey("a"));
        assertEquals("2", result.get("b"));
    }

    @Test
    void deleteWholeState() {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("a", "1");
        storage.saveStatus(initial, "1001");

        assertTrue(service.loadState("5").containsKey("a"));
        service.deleteState("5", null);
        assertTrue(service.loadState("5").isEmpty());
    }
}
