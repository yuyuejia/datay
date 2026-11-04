package com.data.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStatusStorageStrategyTest {

    @TempDir
    Path tempDir;

    @Test
    void saveLoadListDeleteRoundTrip() {
        LocalFileStatusStorageStrategy strategy = new LocalFileStatusStorageStrategy(tempDir.toString());

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("node1.binlogFile", "mysql-bin.000001");
        status.put("node1.binlogPosition", 12345);

        assertTrue(strategy.saveStatus(status, "1001"));

        Map<String, Object> loaded = strategy.loadStatus("1001");
        assertEquals("mysql-bin.000001", loaded.get("node1.binlogFile"));
        assertEquals(12345, loaded.get("node1.binlogPosition"));

        assertTrue(strategy.listJobCodes().contains("1001"));

        assertTrue(strategy.deleteStatus("1001"));
        assertNull(strategy.loadStatus("1001"));
        assertTrue(strategy.listJobCodes().isEmpty());
    }

    @Test
    void saveShouldReplaceExistingStatusWithoutLeavingTempFile() throws Exception {
        LocalFileStatusStorageStrategy strategy = new LocalFileStatusStorageStrategy(tempDir.toString());

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("node1.binlogFile", "mysql-bin.000001");
        first.put("node1.binlogPosition", 12345);
        assertTrue(strategy.saveStatus(first, "1001"));

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("node1.binlogFile", "mysql-bin.000002");
        second.put("node1.binlogPosition", 67890);
        assertTrue(strategy.saveStatus(second, "1001"));

        Map<String, Object> loaded = strategy.loadStatus("1001");
        assertEquals("mysql-bin.000002", loaded.get("node1.binlogFile"));
        assertEquals(67890, loaded.get("node1.binlogPosition"));

        try (Stream<Path> files = Files.list(tempDir)) {
            List<String> names = files.map(path -> path.getFileName().toString()).sorted().toList();
            assertEquals(List.of("status_1001.json"), names, "原子替换后不应残留临时文件");
        }
    }

    @Test
    void emptyStatusFileShouldReportEmptyContent() throws Exception {
        LocalFileStatusStorageStrategy strategy = new LocalFileStatusStorageStrategy(tempDir.toString());
        Files.writeString(tempDir.resolve("status_1001.json"), "");

        StatusStorageException exception = assertThrows(StatusStorageException.class, () -> strategy.loadStatus("1001"));
        assertEquals(StatusStorageException.Reason.EMPTY_CONTENT, exception.getReason());
    }

    @Test
    void invalidStatusFileShouldReportInvalidContent() throws Exception {
        LocalFileStatusStorageStrategy strategy = new LocalFileStatusStorageStrategy(tempDir.toString());
        Files.writeString(tempDir.resolve("status_1001.json"), "{\"node1.binlogFile\":");

        StatusStorageException exception = assertThrows(StatusStorageException.class, () -> strategy.loadStatus("1001"));
        assertEquals(StatusStorageException.Reason.INVALID_CONTENT, exception.getReason());
    }

    @Test
    void unreadableStatusPathShouldReportReadFailure() throws Exception {
        LocalFileStatusStorageStrategy strategy = new LocalFileStatusStorageStrategy(tempDir.toString());
        Files.createDirectory(tempDir.resolve("status_1001.json"));

        StatusStorageException exception = assertThrows(StatusStorageException.class, () -> strategy.loadStatus("1001"));
        assertEquals(StatusStorageException.Reason.READ_FAILED, exception.getReason());
    }

    @Test
    void emptyJsonObjectShouldBeValidEmptyStatus() throws Exception {
        LocalFileStatusStorageStrategy strategy = new LocalFileStatusStorageStrategy(tempDir.toString());
        Files.writeString(tempDir.resolve("status_1001.json"), "{}");

        Map<String, Object> loaded = strategy.loadStatus("1001");
        assertTrue(loaded.isEmpty());
    }

    @Test
    void deleteMissingStatusIsSuccess() {
        LocalFileStatusStorageStrategy strategy = new LocalFileStatusStorageStrategy(tempDir.toString());
        assertTrue(strategy.deleteStatus("not-exist"));
    }

    @Test
    void listReturnsAllSavedJobCodes() {
        LocalFileStatusStorageStrategy strategy = new LocalFileStatusStorageStrategy(tempDir.toString());
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("k", "v");

        strategy.saveStatus(status, "1");
        strategy.saveStatus(status, "2");

        assertEquals(2, strategy.listJobCodes().size());
    }
}
