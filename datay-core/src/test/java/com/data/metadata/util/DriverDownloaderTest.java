package com.data.metadata.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.DBType;
import org.junit.jupiter.api.Test;

class DriverDownloaderTest {

    @Test
    void dmIsDownloadableWithMavenCoordinates() {
        assertTrue(DBType.DM.isDownloadable());
        assertEquals("com.dameng", DBType.DM.getMavenGroupId());
        assertEquals("DmJdbcDriver18", DBType.DM.getMavenArtifactId());
        assertEquals("dm.jdbc.driver.DmDriver", DBType.DM.getDriverClassName());
    }

    @Test
    void dmDefaultVersionResolvesToLatest() {
        assertEquals("8.1.3.140", DBType.DM.getDefaultVersion());
        assertEquals("8.1.3.140", DBType.DM.resolveVersion("default"));
        assertEquals("8.1.3.140", DBType.DM.resolveVersion(null));
        assertEquals("8.1.2.192", DBType.DM.resolveVersion("8.1.2.192"));
    }

    @Test
    void mavenRelativePathPointsToDriverJar() {
        assertEquals(
            "com/dameng/DmJdbcDriver18/8.1.3.140/DmJdbcDriver18-8.1.3.140.jar",
            DBType.DM.getMavenDriverRelativePath("default")
        );
    }

    @Test
    void driverDirAlwaysUsesDefaultDir() {
        // 手动下载与自动下载统一落到 default 目录（连接池按 default 加载）
        assertTrue(DriverDownloader.getDriverDir(DBType.DM, null).getPath().endsWith("drivers/dm/default"));
        assertTrue(DriverDownloader.getDriverDir(DBType.DM, "default").getPath().endsWith("drivers/dm/default"));
        assertTrue(DriverDownloader.getDriverDir(DBType.DM, "8.1.3.140").getPath().endsWith("drivers/dm/default"));
    }

    @Test
    void nonMavenTypeIsNotDownloadable() {
        assertFalse(DBType.MYSQL.isDownloadable());
        assertNotNull(DriverDownloader.getRepoUrls());
        assertFalse(DriverDownloader.getRepoUrls().isEmpty());
    }
}
