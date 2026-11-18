package com.data.datasource.provider.redis;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.job.DatasourceInfo;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TypeConverter;
import java.util.List;

/**
 * Redis 数据源提供者（非 JDBC）。
 *
 * <p>复用项目已有的 Redisson 客户端访问 Redis，支持 {@code redis://} 与 {@code rediss://} 单机连接。
 * Schema 作为 Redis DB 索引使用，key 在元数据浏览中映射为“表”。
 */
@DataSourceRegister("redis")
public class RedisDataSourceProvider implements DataSourceProvider {

    public static final String TYPE = "REDIS";

    @Override
    public String providerId() {
        return "redis";
    }

    @Override
    public List<DataSourceTypeDescriptor> descriptors() {
        return List.of(
            new DataSourceTypeDescriptor()
                .setType(TYPE)
                .setDisplayName("Redis")
                .setJdbc(false)
                .setDefaultPort("6379")
        );
    }

    @Override
    public boolean supports(String type) {
        return TYPE.equalsIgnoreCase(type);
    }

    @Override
    public boolean supportsUrl(String url) {
        if (url == null) {
            return false;
        }
        String normalized = url.trim().toLowerCase();
        return normalized.startsWith("redis://") || normalized.startsWith("rediss://");
    }

    @Override
    public DataSourceConnection connect(DatasourceInfo info) {
        return new RedisConnection(info);
    }

    @Override
    public TypeConverter typeConverter(String type) {
        return DatabaseConverter.getCommonConverter();
    }
}
