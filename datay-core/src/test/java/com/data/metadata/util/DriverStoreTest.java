package com.data.metadata.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.DBType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DriverStoreTest {

    private static final String TEST_TYPE = "unitdriver";
    private static final String TEST_OWNER = "unitowner";

    @AfterEach
    void cleanup() throws IOException {
        DriverStore.deleteDrivers(TEST_TYPE);
        DriverStore.deleteDrivers(DriverStore.GENERIC_JDBC_TYPE, TEST_OWNER);
        deleteTree(DriverStore.getDriverDir(TEST_TYPE).toPath().getParent());
        deleteTree(DriverStore.getDriverDir(DriverStore.GENERIC_JDBC_TYPE, TEST_OWNER).toPath().getParent());
    }

    @Test
    void preinstalledDriversAreMarked() {
        assertTrue(DBType.MYSQL.isDriverPreinstalled());
        assertTrue(DBType.POSTGRESQL.isDriverPreinstalled());
        assertTrue(DBType.ORACLE.isDriverPreinstalled());
        assertTrue(DBType.DUCKDB.isDriverPreinstalled());
        assertTrue(DBType.SQLITE.isDriverPreinstalled());

        assertFalse(DBType.DM.isDriverPreinstalled());
        assertFalse(DBType.HANA.isDriverPreinstalled());
        assertFalse(DBType.TDENGINE.isDriverPreinstalled());
        assertFalse(DBType.SQLSERVER.isDriverPreinstalled());
        assertFalse(DBType.CLICKHOUSE.isDriverPreinstalled());
    }

    @Test
    void driverDirAlwaysUsesDefaultDir() {
        assertTrue(DriverStore.getDriverDir(DBType.DM).getPath().endsWith("drivers/dm/default"));
        assertTrue(DriverStore.getDriverDir(DriverStore.GENERIC_JDBC_TYPE).getPath().endsWith("drivers/jdbc/default"));
        assertTrue(
            DriverStore.getDriverDir(DriverStore.GENERIC_JDBC_TYPE, TEST_OWNER).getPath().endsWith("drivers/jdbc/unitowner/default")
        );
    }

    @Test
    void installsDriversInIsolatedOwnerDir() throws Exception {
        Path jar = jar("generic-driver.jar");
        List<java.io.File> installed = DriverStore.installDrivers(DriverStore.GENERIC_JDBC_TYPE, TEST_OWNER, List.of(jar), true);
        assertEquals(List.of("generic-driver.jar"), installed.stream().map(java.io.File::getName).toList());
        assertTrue(DriverStore.isDriverInstalled(DriverStore.GENERIC_JDBC_TYPE, TEST_OWNER));
        assertFalse(DriverStore.isDriverInstalled(DriverStore.GENERIC_JDBC_TYPE, "other-owner"));
    }

    @Test
    void installsReplacesAndListsDrivers() throws Exception {
        Path first = jar("dm-driver-1.0.jar");
        List<java.io.File> installed = DriverStore.installDrivers(TEST_TYPE, List.of(first), true);
        assertEquals(1, installed.size());
        assertEquals("dm-driver-1.0.jar", installed.get(0).getName());
        assertTrue(DriverStore.isDriverInstalled(TEST_TYPE));

        Path second = jar("dm-driver-2.0.jar");
        installed = DriverStore.installDrivers(TEST_TYPE, List.of(second), true);
        assertEquals(List.of("dm-driver-2.0.jar"), installed.stream().map(java.io.File::getName).toList());

        Path third = jar("dm-driver-2.0.jar");
        installed = DriverStore.installDrivers(TEST_TYPE, List.of(third), false);
        assertEquals(List.of("dm-driver-2.0.jar"), installed.stream().map(java.io.File::getName).toList());
    }

    @Test
    void rejectsInvalidJarAndInvalidName() throws Exception {
        Path notJar = Files.createTempFile("not-a-jar", ".jar");
        Files.write(notJar, "hello".getBytes());
        assertThrows(IOException.class, () -> DriverStore.installDrivers(TEST_TYPE, List.of(notJar), true));

        assertThrows(IOException.class, () -> DriverStore.sanitizeJarName("../evil.txt"));
        assertEquals("driver.jar", DriverStore.sanitizeJarName("/tmp/driver.jar"));
        assertThrows(IllegalArgumentException.class, () -> DriverStore.getDriverDir(TEST_TYPE, "../evil"));
    }

    private static void deleteTree(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        List<Path> paths;
        try (var stream = Files.walk(dir)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private static Path jar(String name) throws IOException {
        Path dir = Files.createTempDirectory("driver-test-");
        Path file = dir.resolve(name);
        Files.write(file, new byte[] { 'P', 'K', 3, 4, 0, 0 });
        return file;
    }
}
