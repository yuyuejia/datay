package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;
import com.data.metadata.TypeConverter;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcDataSourceProviderTest {

    @Test
    void eachDbTypeHasItsOwnProviderReusingJdbcBase() {
        for (DBType dbType : DBType.values()) {
            DataSourceProvider provider = DataSourceProviderFactory.provider(dbType.name());
            assertNotNull(provider, "缺少 Provider: " + dbType.name());
            assertInstanceOf(JdbcDataSourceProvider.class, provider);

            List<DataSourceTypeDescriptor> descriptors = provider.descriptors();
            assertTrue(descriptors.stream().anyMatch(d -> dbType.name().equals(d.getType()) && d.isJdbc()));
            assertTrue(provider.supports(dbType.name()));
        }
    }

    @Test
    void supportsOnlyItsOwnType() {
        MysqlDataSourceProvider provider = new MysqlDataSourceProvider();
        assertTrue(provider.supports("mysql"));
        assertTrue(provider.supports("MYSQL"));
        assertFalse(provider.supports("oracle"));
        assertFalse(provider.supports("elasticsearch"));
    }

    @Test
    void descriptorsReflectDbTypeMetadata() {
        DataSourceTypeDescriptor descriptor = new DmDataSourceProvider().descriptors().get(0);
        assertTrue(descriptor.isDownloadable());
        assertNotNull(descriptor.getJdbcUrlTemplate());
        assertNotNull(descriptor.getDriverClassName());
        assertFalse(descriptor.getSupportedVersions().isEmpty());
    }

    @Test
    void exposesTypeConverterByName() {
        TypeConverter converter = new MysqlDataSourceProvider().typeConverter("mysql");
        assertNotNull(converter);
    }

    @Test
    void hanaUsesDummyTestQueryAndUrlPrefix() {
        HanaDataSourceProvider provider = new HanaDataSourceProvider();
        assertEquals("SELECT 1 FROM DUMMY", provider.getTestQuery());
        assertTrue(provider.supportsUrl("jdbc:sap://localhost:30015/?databaseName=HXE"));
        assertFalse(provider.supportsUrl("jdbc:mysql://localhost:3306/test"));
    }
}
