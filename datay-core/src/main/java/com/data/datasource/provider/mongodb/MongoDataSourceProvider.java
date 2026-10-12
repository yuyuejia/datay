package com.data.datasource.provider.mongodb;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.job.DatasourceInfo;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TypeConverter;
import java.util.List;

/**
 * MongoDB 数据源提供者（非 JDBC）。
 *
 * <p>通过官方 MongoDB Java 同步驱动访问：查询以 JSON 信封提交（{@code find} / {@code aggregate}），
 * 元数据取自 {@code listDatabaseNames} / {@code listCollectionNames} 并对集合采样推断字段，
 * 因此不依赖 JDBC。属于本平台「非 JDBC 数据源通过 SPI 扩展」的又一实现。
 */
@DataSourceRegister("mongodb")
public class MongoDataSourceProvider implements DataSourceProvider {

    public static final String TYPE = "MONGODB";

    @Override
    public String providerId() {
        return "mongodb";
    }

    @Override
    public List<DataSourceTypeDescriptor> descriptors() {
        return List.of(
            new DataSourceTypeDescriptor()
                .setType(TYPE)
                .setDisplayName("MongoDB")
                .setJdbc(false)
                .setDefaultPort("27017")
        );
    }

    @Override
    public boolean supports(String type) {
        return TYPE.equalsIgnoreCase(type);
    }

    @Override
    public boolean supportsUrl(String url) {
        return url != null && (url.startsWith("mongodb://") || url.startsWith("mongodb+srv://"));
    }

    @Override
    public DataSourceConnection connect(DatasourceInfo info) {
        return new MongoConnection(info);
    }

    @Override
    public TypeConverter typeConverter(String type) {
        return DatabaseConverter.getCommonConverter();
    }
}
