package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import org.junit.jupiter.api.Test;

class YMatrixDataSourceProviderTest {

    @Test
    void reusesPostgresqlCapabilitiesAndDeclaresYMatrixType() {
        YMatrixDataSourceProvider provider = new YMatrixDataSourceProvider();

        assertEquals("YMATRIX", provider.typeName());
        assertEquals(DBType.YMATRIX, DBType.fromString("YMATRIX"));

        DataSourceTypeDescriptor descriptor = provider.descriptors().get(0);
        assertEquals("YMATRIX", descriptor.getType());
        assertEquals("org.postgresql.Driver", descriptor.getDriverClassName());
        assertEquals("jdbc:postgresql://{host}:{port}/{database}", descriptor.getJdbcUrlTemplate());
        assertTrue(descriptor.isJdbc());
        assertTrue(provider.supportsUrl("jdbc:postgresql://127.0.0.1:5432/ymatrix"));
    }

    @Test
    void reusesPostgresqlDialect() {
        assertNotNull(DatabaseConverter.getConverter("YMATRIX"));
        assertSame(DatabaseConverter.getConverter("POSTGRESQL"), DatabaseConverter.getConverter("YMATRIX"));
    }
}
