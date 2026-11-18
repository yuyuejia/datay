package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.provider.redis.RedisDataSourceProvider;
import org.junit.jupiter.api.Test;

class RedisDataSourceProviderTest {

    @Test
    void resolvesRedisToNonJdbcProvider() {
        DataSourceProvider provider = DataSourceProviderFactory.provider("redis");
        assertInstanceOf(RedisDataSourceProvider.class, provider);
        assertTrue(DataSourceProviderFactory.hasType(RedisDataSourceProvider.TYPE));
        assertFalse(provider.descriptors().get(0).isJdbc());
        assertEquals("6379", provider.descriptors().get(0).getDefaultPort());
    }

    @Test
    void recognizesRedisUrls() {
        DataSourceProvider provider = DataSourceProviderFactory.provider("redis");
        assertTrue(provider.supportsUrl("redis://127.0.0.1:6379"));
        assertTrue(provider.supportsUrl("rediss://redis.example.com:6380"));
        assertFalse(provider.supportsUrl("jdbc:mysql://127.0.0.1:3306/test"));
    }
}
