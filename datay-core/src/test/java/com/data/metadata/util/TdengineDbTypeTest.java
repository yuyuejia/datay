package com.data.metadata.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.DBType;
import org.junit.jupiter.api.Test;

class TdengineDbTypeTest {

    @Test
    void recognizesAllTdengineProtocols() {
        assertEquals(DBType.TDENGINE.toString(), DBUtils.getDBType("jdbc:TAOS-WS://localhost:6041/power"));
        assertEquals(DBType.TDENGINE, DBUtils.getDBTypeEnum("jdbc:TAOS-WS://localhost:6041/power"));

        assertEquals(DBType.TDENGINE, DBUtils.getDBTypeEnum("jdbc:TAOS-RS://localhost:6041/power"));
        assertEquals(DBType.TDENGINE, DBUtils.getDBTypeEnum("jdbc:TAOS://localhost:6030/power"));
    }

    @Test
    void protocolSelectsMatchingDriverClass() {
        assertEquals(
            TdengineJdbcSupport.DRIVER_WS,
            DBUtils.getDriverClassName("jdbc:TAOS-WS://localhost:6041/power")
        );
        assertEquals(
            TdengineJdbcSupport.DRIVER_RS,
            DBUtils.getDriverClassName("jdbc:TAOS-RS://localhost:6041/power")
        );
        assertEquals(
            TdengineJdbcSupport.DRIVER_NATIVE,
            DBUtils.getDriverClassName("jdbc:TAOS://localhost:6030/power")
        );
        assertEquals(
            TdengineJdbcSupport.DRIVER_WS,
            DBUtils.getDriverClassName("jdbc:TAOS-WS://localhost:6041/power", "3.10.0")
        );
    }

    @Test
    void tdengineUrlsAreDetectedCaseInsensitively() {
        assertTrue(TdengineJdbcSupport.isTdengineUrl("jdbc:taos-ws://localhost:6041/power"));
        assertTrue(TdengineJdbcSupport.isTdengineUrl("jdbc:Taos://localhost:6030/power"));
        assertFalse(TdengineJdbcSupport.isTdengineUrl("jdbc:mysql://localhost:3306/power"));
        assertFalse(TdengineJdbcSupport.isTdengineUrl(null));
    }

    @Test
    void driverMetadataIsDownloadableFromMaven() {
        assertTrue(DBType.TDENGINE.isDownloadable());
        assertEquals("com.taosdata.jdbc", DBType.TDENGINE.getMavenGroupId());
        assertEquals("taos-jdbcdriver", DBType.TDENGINE.getMavenArtifactId());
        assertEquals("3.10.0", DBType.TDENGINE.getDefaultVersion());
        assertEquals(
            "com/taosdata/jdbc/taos-jdbcdriver/3.10.0/taos-jdbcdriver-3.10.0-dist.jar",
            DBType.TDENGINE.getMavenDriverRelativePath("default")
        );
        assertEquals("taos-jdbcdriver-3.5.2-dist.jar", DBType.TDENGINE.getDriverJarForVersion("3.5.2"));
    }
}
