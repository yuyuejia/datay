package com.data.datasource.provider.elasticsearch;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.job.DatasourceInfo;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TypeConverter;
import java.util.List;

/**
 * Elasticsearch 数据源提供者（非 JDBC）。
 *
 * <p>通过 Elasticsearch REST API 访问：查询走 {@code _sql}（SQL API），元数据走
 * {@code _cat/indices} 与 {@code _mapping}，因此完全不依赖 JDBC 驱动。
 * 这也是本平台「非 JDBC 数据源通过 SPI 扩展」的示例实现。
 */
@DataSourceRegister("elasticsearch")
public class ElasticsearchDataSourceProvider implements DataSourceProvider {

    public static final String TYPE = "ELASTICSEARCH";

    @Override
    public String providerId() {
        return "elasticsearch";
    }

    @Override
    public List<DataSourceTypeDescriptor> descriptors() {
        return List.of(
            new DataSourceTypeDescriptor()
                .setType(TYPE)
                .setDisplayName("Elasticsearch")
                .setJdbc(false)
                .setDefaultPort("9200")
        );
    }

    @Override
    public boolean supports(String type) {
        return TYPE.equalsIgnoreCase(type);
    }

    @Override
    public boolean supportsUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    @Override
    public DataSourceConnection connect(DatasourceInfo info) {
        return new ElasticsearchConnection(info);
    }

    @Override
    public TypeConverter typeConverter(String type) {
        return DatabaseConverter.getCommonConverter();
    }
}
