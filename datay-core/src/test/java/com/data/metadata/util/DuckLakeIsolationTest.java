package com.data.metadata.util;

import com.data.job.DatasourceInfo;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证多个 DuckLake 数据源使用相互独立的本地 DuckDB 文件，避免 catalog 冲突。
 */
public class DuckLakeIsolationTest {

    private static DatasourceInfo duckLake(String url, Map<String, String> extraParams) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType("DUCKLAKE");
        info.setUrl(url);
        info.setExtraParams(extraParams);
        return info;
    }

    @Test
    public void differentDuckLakeMetadataResolveToDifferentLocalFiles() {
        String a = DBUtils.resolveDuckLakeJdbcUrl(duckLake("ducklake:/data/a.ducklake", null));
        String b = DBUtils.resolveDuckLakeJdbcUrl(duckLake("ducklake:/data/b.ducklake", null));

        assertNotEquals(a, b);
        assertTrue(a.startsWith("jdbc:duckdb:"));
        assertTrue(a.endsWith(".duckdb"));
    }

    @Test
    public void extraParamsDoNotAffectLocalFile() {
        // 同一元数据必须复用同一个本地 DuckDB 文件，否则重复挂载会触发 Unique file handle conflict
        Map<String, String> paramsA = new HashMap<>();
        paramsA.put("s3.data_path", "s3://bucket-a/data");
        Map<String, String> paramsB = new HashMap<>();
        paramsB.put("s3.data_path", "s3://bucket-b/data");

        String a = DBUtils.resolveDuckLakeJdbcUrl(duckLake("ducklake:/data/shared.ducklake", paramsA));
        String b = DBUtils.resolveDuckLakeJdbcUrl(duckLake("ducklake:/data/shared.ducklake", paramsB));

        assertEquals(a, b);
    }

    @Test
    public void relativeAndAbsoluteMetadataResolveToSameLocalFile() {
        String relative = DBUtils.resolveDuckLakeJdbcUrl(duckLake("ducklake:metadata.ducklake", null));
        String absolute = DBUtils.resolveDuckLakeJdbcUrl(
            duckLake("ducklake:" + new java.io.File(System.getProperty("user.dir"), "metadata.ducklake").getAbsolutePath(), null)
        );

        assertEquals(relative, absolute);
    }

    @Test
    public void sameIdentityResolvesToSameLocalFile() {
        Map<String, String> params = new HashMap<>();
        params.put("s3.data_path", "s3://bucket/data");
        params.put("s3.key_id", "key");

        String first = DBUtils.resolveDuckLakeJdbcUrl(duckLake("ducklake:/data/x.ducklake", params));
        String second = DBUtils.resolveDuckLakeJdbcUrl(duckLake("ducklake:/data/x.ducklake", new HashMap<>(params)));

        assertEquals(first, second);
    }

    @Test
    public void duckdbDatabasesFunctionIsAvailable() throws Exception {
        try (
            Connection conn = DriverManager.getConnection("jdbc:duckdb:");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT 1 FROM duckdb_databases() WHERE database_name = 'memory'"
            )
        ) {
            assertTrue(rs.next());
        }
    }
}
