package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link PolarisDataSourceProvider.PolarisAddress} 的地址解析单元测试（不建立真实连接）。
 */
class PolarisDataSourceProviderTest {

    @Test
    void parsesHostPortWarehouseAndDefaultEndpoint() throws SQLException {
        PolarisDataSourceProvider.PolarisAddress address = PolarisDataSourceProvider.PolarisAddress.parse(
            "polaris://polaris.example.com:8181/quickstart_catalog",
            null
        );
        assertEquals("polaris.example.com", address.host);
        assertEquals("8181", address.port);
        assertEquals("quickstart_catalog", address.warehouse);
        assertEquals("http://polaris.example.com:8181/api/catalog", address.endpoint);
    }

    @Test
    void appliesDefaultPortAndTrimsTrailingSlash() throws SQLException {
        PolarisDataSourceProvider.PolarisAddress address = PolarisDataSourceProvider.PolarisAddress.parse(
            "polaris://localhost/quickstart_catalog/",
            null
        );
        assertEquals("8181", address.port);
        assertEquals("quickstart_catalog", address.warehouse);
    }

    @Test
    void honorsCatalogUriOverride() throws SQLException {
        Map<String, String> extraParams = new HashMap<>();
        extraParams.put("polaris.catalog_uri", "https://polaris.example.com/catalog/");
        PolarisDataSourceProvider.PolarisAddress address = PolarisDataSourceProvider.PolarisAddress.parse(
            "polaris://polaris.example.com:443/quickstart_catalog",
            extraParams
        );
        assertEquals("https://polaris.example.com/catalog", address.endpoint);
    }

    @Test
    void supportsSslScheme() throws SQLException {
        Map<String, String> extraParams = new HashMap<>();
        extraParams.put("polaris.use_ssl", "true");
        PolarisDataSourceProvider.PolarisAddress address = PolarisDataSourceProvider.PolarisAddress.parse(
            "polaris://polaris.example.com:8443/warehouse",
            extraParams
        );
        assertEquals("https://polaris.example.com:8443/api/catalog", address.endpoint);
    }

    @Test
    void rejectsMissingWarehouse() {
        assertThrows(
            SQLException.class,
            () -> PolarisDataSourceProvider.PolarisAddress.parse("polaris://localhost:8181", null)
        );
    }

    @Test
    void rejectsNonPolarisUrl() {
        assertThrows(
            SQLException.class,
            () -> PolarisDataSourceProvider.PolarisAddress.parse("jdbc:duckdb:/tmp/x.duckdb", null)
        );
    }
}
