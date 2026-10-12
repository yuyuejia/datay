package com.data.datasource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.datasource.provider.elasticsearch.ElasticsearchDataSourceProvider;
import com.data.datasource.provider.mongodb.MongoDataSourceProvider;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DataSourceProviderFactoryTest {

    @Test
    void resolvesJdbcTypesToJdbcProvider() {
        DataSourceProvider provider = DataSourceProviderFactory.provider("mysql");
        assertInstanceOf(JdbcDataSourceProvider.class, provider);
        assertTrue(DataSourceProviderFactory.hasType("oracle"));
        assertTrue(DataSourceProviderFactory.hasType("duckdb"));
        // 通用 JDBC
        DataSourceProvider generic = DataSourceProviderFactory.provider("jdbc");
        assertInstanceOf(JdbcDataSourceProvider.class, generic);
        assertTrue(generic.descriptors().get(0).isJdbc());
    }

    @Test
    void resolvesNonJdbcTypeToElasticsearchProvider() {
        DataSourceProvider provider = DataSourceProviderFactory.provider(ElasticsearchDataSourceProvider.TYPE);
        assertInstanceOf(ElasticsearchDataSourceProvider.class, provider);
        assertFalse(DataSourceProviderFactory.hasType("hbase"));
    }

    @Test
    void resolvesMongoDbToNonJdbcProvider() {
        DataSourceProvider provider = DataSourceProviderFactory.provider("mongodb");
        assertInstanceOf(MongoDataSourceProvider.class, provider);
        assertTrue(DataSourceProviderFactory.hasType(MongoDataSourceProvider.TYPE));
        assertFalse(provider.descriptors().get(0).isJdbc());
    }

    @Test
    void requiresRegisteredType() {
        assertThrows(IllegalArgumentException.class, () -> DataSourceProviderFactory.require("not-a-type"));
    }

    @Test
    void descriptorsExposeJdbcAndNonJdbcTypes() {
        List<String> types = DataSourceProviderFactory
            .descriptors()
            .stream()
            .map(DataSourceTypeDescriptor::getType)
            .collect(Collectors.toList());
        assertTrue(types.contains("MYSQL"));
        assertTrue(types.contains(ElasticsearchDataSourceProvider.TYPE));
        assertTrue(types.contains("MONGODB"));

        DataSourceTypeDescriptor es = DataSourceProviderFactory
            .descriptors()
            .stream()
            .filter(d -> ElasticsearchDataSourceProvider.TYPE.equals(d.getType()))
            .findFirst()
            .orElse(null);
        assertNotNull(es);
        assertFalse(es.isJdbc());
    }
}
