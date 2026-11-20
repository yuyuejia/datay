package com.data.metadata.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.data.job.DatasourceInfo;
import com.data.metadata.DBType;
import org.junit.jupiter.api.Test;

class KingbaseDbTypeTest {

    @Test
    void recognizesKingbaseJdbcUrl() {
        String url = "jdbc:kingbase8://localhost:54321/test";
        assertEquals(DBType.KINGBASE.toString(), DBUtils.getDBType(url));
        assertEquals(DBType.KINGBASE, DBUtils.getDBTypeEnum(url));
        assertEquals(DBType.KINGBASE.toString(), DBUtils.getDBType("KINGBASE", url));
        assertEquals("com.kingbase8.Driver", DBUtils.getDriverClassName(url));
    }

    @Test
    void parsesConnectionUrl() {
        DatasourceInfo info = new DatasourceInfo();
        info.setUrl("jdbc:kingbase8://127.0.0.1:54321/analytics");

        DatasourceInfo parsed = DBUtils.parseConnectionUrl(info);

        assertEquals("127.0.0.1", parsed.getHostname());
        assertEquals("54321", parsed.getPort());
        assertEquals("analytics", parsed.getDbschema());
    }

    @Test
    void suggestsVersionSpecificDrivers() {
        assertEquals("kingbase8-8.2.0-V008R003C002B0100.jar", DBType.KINGBASE.getDriverJarForVersion("V8R3"));
        assertEquals("kingbase8-8.6.0.jar", DBType.KINGBASE.getDriverJarForVersion("V8R6"));
        assertEquals("kingbase8-9.0.1.jar", DBType.KINGBASE.getDriverJarForVersion("V9R1"));
        assertFalse(DBType.KINGBASE.isDriverPreinstalled());
    }
}
