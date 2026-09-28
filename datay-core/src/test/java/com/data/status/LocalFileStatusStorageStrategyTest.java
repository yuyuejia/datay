package com.data.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
