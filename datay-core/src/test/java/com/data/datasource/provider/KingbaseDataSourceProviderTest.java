package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.job.DatasourceInfo;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import org.junit.jupiter.api.Test;

class KingbaseDataSourceProviderTest {

    @Test
    void reusesPostgresqlCapabilitiesAndDeclaresKingbaseType() {
        KingbaseDataSourceProvider provider = new KingbaseDataSourceProvider();

        assertEquals("KINGBASE", provider.typeName());
        assertEquals(DBType.KINGBASE, DBType.fromString("KINGBASE"));

        DataSourceTypeDescriptor descriptor = provider.descriptors().get(0);
        assertEquals("KINGBASE", descriptor.getType());
        assertEquals("人大金仓 KingbaseES", descriptor.getDisplayName());
        assertEquals("com.kingbase8.Driver", descriptor.getDriverClassName());
        assertEquals("jdbc:kingbase8://{host}:{port}/{database}", descriptor.getJdbcUrlTemplate());
        assertTrue(descriptor.isJdbc());
        assertFalse(descriptor.isDriverPreinstalled());
        assertTrue(provider.supportsUrl("jdbc:kingbase8://127.0.0.1:54321/test"));
        assertInstanceOf(
            KingbaseDataSourceProvider.class,
            DataSourceProviderFactory.providerForUrl("jdbc:kingbase8://127.0.0.1:54321/test")
        );
    }

    @Test
    void inheritsPostgresqlMetadataAndNamingBehavior() {
        KingbaseDataSourceProvider provider = new KingbaseDataSourceProvider();

        assertInstanceOf(PostgresqlDataSourceProvider.class, provider);
        assertEquals("orders", provider.normalizeTargetTableName(new DatasourceInfo(), "ORDERS"));
    }

    @Test
    void reusesPostgresqlDialect() {
        assertNotNull(DatabaseConverter.getConverter("KINGBASE"));
        assertSame(DatabaseConverter.getConverter("POSTGRESQL"), DatabaseConverter.getConverter("KINGBASE"));
    }
}
