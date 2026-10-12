package com.data.datasource.provider.influxdb;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.job.DatasourceInfo;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TypeConverter;
import java.util.List;

/**
 * InfluxDB 数据源提供者（非 JDBC）。
 *
 * <p>通过 InfluxDB HTTP API 访问，不引入任何 JDBC 驱动：
 * <ul>
 *   <li>InfluxDB 1.x：查询走 InfluxQL（{@code POST /query}），认证支持用户名/密码；</li>
 *   <li>InfluxDB 2.x：查询走 Flux（{@code POST /api/v2/query}），认证使用 API Token；</li>
 *   <li>元数据：1.x 通过 {@code SHOW DATABASES/MEASUREMENTS/FIELD KEYS}，2.x 通过 Buckets API 与
 *       {@code influxdata/influxdb/schema} 包。</li>
 * </ul>
 *
 * <p>属于本平台「非 JDBC 数据源通过 SPI 扩展」的又一实现，新增该类型无需改动任何中心化分支。
 */
@DataSourceRegister("influxdb")
public class InfluxDbDataSourceProvider implements DataSourceProvider {

    public static final String TYPE = "INFLUXDB";

    @Override
    public String providerId() {
        return "influxdb";
    }

    @Override
    public List<DataSourceTypeDescriptor> descriptors() {
        return List.of(
            new DataSourceTypeDescriptor()
                .setType(TYPE)
                .setDisplayName("InfluxDB")
                .setJdbc(false)
                .setDefaultPort("8086")
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
        return url.startsWith("influxdb://") || url.startsWith("http://") || url.startsWith("https://");
    }

    @Override
    public DataSourceConnection connect(DatasourceInfo info) {
        return new InfluxDbConnection(info);
    }

    @Override
    public TypeConverter typeConverter(String type) {
        return DatabaseConverter.getCommonConverter();
    }
}
