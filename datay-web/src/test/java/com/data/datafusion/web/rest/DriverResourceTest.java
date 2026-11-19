package com.data.datafusion.web.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.util.DriverStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

class DriverResourceTest {

    private String owner;

    @AfterEach
    void cleanup() throws IOException {
        if (owner == null) {
            return;
        }
        DriverStore.deleteDrivers(DriverStore.GENERIC_JDBC_TYPE, owner);
        Path ownerDir = DriverStore.getDriverDir(DriverStore.GENERIC_JDBC_TYPE, owner).toPath().getParent();
        if (ownerDir == null || !Files.exists(ownerDir)) {
            return;
        }
        List<Path> paths;
        try (var stream = Files.walk(ownerDir)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
        Path jdbcDir = ownerDir.getParent();
        if (jdbcDir != null && Files.isDirectory(jdbcDir)) {
            try (var entries = Files.list(jdbcDir)) {
                if (entries.findAny().isEmpty()) {
                    Files.deleteIfExists(jdbcDir);
                }
            }
        }
    }

    @Test
    void genericUploadCreatesIsolatedDriverDirAndFillsRelativePath() {
        DriverResource resource = new DriverResource();
        MockMultipartFile file = new MockMultipartFile(
            "files",
            "custom-driver.jar",
            "application/java-archive",
            new byte[] { 'P', 'K', 3, 4 }
        );

        ResponseEntity<Map<String, Object>> response = resource.uploadDrivers("JDBC", new MockMultipartFile[] { file }, true, null);
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        owner = (String) body.get("owner");
        assertNotNull(owner);
        assertEquals(true, body.get("installed"));
        assertEquals(List.of("custom-driver.jar"), body.get("jars"));

        String driverPath = (String) body.get("driverPath");
        assertNotNull(driverPath);
        assertFalse(new java.io.File(driverPath).isAbsolute());
        assertTrue(driverPath.replace('\\', '/').endsWith("drivers/jdbc/" + owner + "/default"));

        String otherOwner = "missing" + UUID.randomUUID().toString().replace("-", "");
        ResponseEntity<Map<String, Object>> otherStatus = resource.getDriverStatus("JDBC", null, otherOwner);
        assertEquals(200, otherStatus.getStatusCode().value());
        assertNotNull(otherStatus.getBody());
        assertEquals(false, otherStatus.getBody().get("installed"));
        assertEquals(List.of(), otherStatus.getBody().get("jars"));
    }
}
