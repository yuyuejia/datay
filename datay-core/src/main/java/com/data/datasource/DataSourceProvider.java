package com.data.datasource;

import com.data.job.DatasourceInfo;
import com.data.metadata.TypeConverter;
import java.util.List;

/**
 * 数据源提供者 SPI。
 *
 * <p>每一种数据源通过实现本接口进行扩展：
 * <ul>
 *   <li>JDBC 家族由 {@code JdbcDataSourceProvider} 统一实现，覆盖 {@code DBType} 中所有关系型数据库；</li>
 *   <li>非 JDBC 数据源（如 Elasticsearch）实现各自的 provider，无需依赖 JDBC。</li>
 * </ul>
 *
 * <p>实现类需标注 {@link DataSourceRegister}，启动时由 {@link DataSourceProviderFactory}
 * 扫描 {@code com.data.datasource.provider} 包自动发现注册。
 */
public interface DataSourceProvider {

    /**
     * provider 唯一标识，如 {@code jdbc}、{@code elasticsearch}。
     */
    String providerId();

    /**
     * 该 provider 负责的数据源类型描述列表。一个 provider 可覆盖多个类型（如 JDBC 家族）。
     */
    List<DataSourceTypeDescriptor> descriptors();

    /**
     * 是否负责处理指定数据源类型（不区分大小写）。
     */
    boolean supports(String type);

    /**
     * 是否负责处理指定连接地址。默认否，JDBC Provider 按 URL 前缀识别，供无显式类型的场景
     * （如仅持有 {@link java.sql.Connection} 时）解析到对应 Provider。
     */
    default boolean supportsUrl(String url) {
        return false;
    }

    /**
     * 建立与该数据源的连接。
     */
    DataSourceConnection connect(DatasourceInfo info) throws Exception;

    /**
     * 返回该类型对应的方言（DDL / 写 SQL 生成器）；无方言时返回 {@code null}。
     */
    default TypeConverter typeConverter(String type) {
        return null;
    }
}
