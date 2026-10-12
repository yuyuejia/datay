package com.data.datasource;

import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import com.data.datasource.provider.AvroDataSourceProvider;
import com.data.datasource.provider.ClickHouseDataSourceProvider;
import com.data.datasource.provider.DmDataSourceProvider;
import com.data.datasource.provider.DorisDataSourceProvider;
import com.data.datasource.provider.DuckDbDataSourceProvider;
import com.data.datasource.provider.DuckLakeDataSourceProvider;
import com.data.datasource.provider.GreenplumDataSourceProvider;
import com.data.datasource.provider.MysqlDataSourceProvider;
import com.data.datasource.provider.OracleDataSourceProvider;
import com.data.datasource.provider.PostgresqlDataSourceProvider;
import com.data.datasource.provider.SqlServerDataSourceProvider;
import com.data.datasource.provider.TidbDataSourceProvider;
import com.data.datasource.provider.elasticsearch.ElasticsearchDataSourceProvider;
import com.data.job.DatasourceInfo;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源提供者注册表。
 *
 * <p>启动时扫描 {@code com.data.datasource.provider} 包（含子包）下所有标注
 * {@link DataSourceRegister} 的 {@link DataSourceProvider} 实现，按类型建立索引，
 * 供上层按 {@code DataSource.type} 解析到具体的 provider。
 *
 * <p>与 ETL {@code ComponentFactory} 一致采用包扫描而非 {@code ServiceLoader}，
 * 以便在 fat jar / 嵌套 jar 环境下稳定发现内置 provider；同时保留
 * {@link #register(DataSourceProvider)} 支持运行时手工扩展。
 */
public final class DataSourceProviderFactory {

    /** provider 扫描包。 */
    private static final String PROVIDER_PACKAGE = "com.data.datasource.provider";

    /** 数据源类型（大写）-> provider。 */
    private static final Map<String, DataSourceProvider> PROVIDERS_BY_TYPE = new LinkedHashMap<>();

    /** 数据源类型（大写）-> 类型描述。 */
    private static final Map<String, DataSourceTypeDescriptor> DESCRIPTORS_BY_TYPE = new LinkedHashMap<>();

    private static final List<DataSourceProvider> PROVIDERS = new ArrayList<>();

    static {
        try {
            scanAndRegister();
        } catch (Exception e) {
            System.err.println("自动扫描数据源 Provider 失败: " + e.getMessage());
        }
        // 兜底：扫描失败时保证内置 provider 可用
        if (PROVIDERS.isEmpty()) {
            registerBuiltins();
        }
    }

    /**
     * 注册内置 provider，仅在包扫描不可用时作为兜底。
     */
    private static void registerBuiltins() {
        register(new MysqlDataSourceProvider());
        register(new TidbDataSourceProvider());
        register(new OracleDataSourceProvider());
        register(new PostgresqlDataSourceProvider());
        register(new SqlServerDataSourceProvider());
        register(new DuckDbDataSourceProvider());
        register(new DuckLakeDataSourceProvider());
        register(new ClickHouseDataSourceProvider());
        register(new GreenplumDataSourceProvider());
        register(new DorisDataSourceProvider());
        register(new AvroDataSourceProvider());
        register(new DmDataSourceProvider());
        register(new ElasticsearchDataSourceProvider());
    }

    private DataSourceProviderFactory() {}

    private static void scanAndRegister() {
        for (Class<?> clazz : ClassUtil.scanPackage(PROVIDER_PACKAGE)) {
            DataSourceRegister annotation = clazz.getAnnotation(DataSourceRegister.class);
            if (
                annotation == null ||
                !DataSourceProvider.class.isAssignableFrom(clazz) ||
                Modifier.isAbstract(clazz.getModifiers()) ||
                clazz.isInterface()
            ) {
                continue;
            }
            try {
                DataSourceProvider provider = (DataSourceProvider) clazz.getDeclaredConstructor().newInstance();
                register(provider);
            } catch (Exception e) {
                System.err.println("实例化数据源 Provider 失败: " + clazz.getName() + " - " + e.getMessage());
            }
        }
    }

    /**
     * 注册一个数据源 provider，重复类型以最后注册者为准。
     */
    public static synchronized void register(DataSourceProvider provider) {
        if (provider == null) {
            return;
        }
        PROVIDERS.add(provider);
        List<DataSourceTypeDescriptor> descriptors = provider.descriptors();
        if (descriptors == null) {
            return;
        }
        for (DataSourceTypeDescriptor descriptor : descriptors) {
            if (descriptor == null || StrUtil.isBlank(descriptor.getType())) {
                continue;
            }
            String key = descriptor.getType().toUpperCase();
            PROVIDERS_BY_TYPE.put(key, provider);
            DESCRIPTORS_BY_TYPE.put(key, descriptor);
        }
    }

    /**
     * 按类型查找 provider，不存在时返回 {@code null}。
     */
    public static DataSourceProvider provider(String type) {
        if (StrUtil.isBlank(type)) {
            return null;
        }
        return PROVIDERS_BY_TYPE.get(type.toUpperCase());
    }

    /**
     * 按类型查找 provider，不存在时抛出异常。
     */
    public static DataSourceProvider require(String type) {
        DataSourceProvider provider = provider(type);
        if (provider == null) {
            throw new IllegalArgumentException("未注册的数据源类型: " + type);
        }
        return provider;
    }

    public static boolean hasType(String type) {
        return provider(type) != null;
    }

    /**
     * 按连接地址解析 Provider（用于仅持有连接地址、无显式类型的场景），无匹配返回 {@code null}。
     */
    public static DataSourceProvider providerForUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        for (DataSourceProvider provider : PROVIDERS) {
            if (provider.supportsUrl(url)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 按 {@link DatasourceInfo#getType()} 建立连接。
     */
    public static DataSourceConnection connect(DatasourceInfo info) throws Exception {
        if (info == null || StrUtil.isBlank(info.getType())) {
            throw new IllegalArgumentException("数据源类型不能为空");
        }
        return require(info.getType()).connect(info);
    }

    /**
     * 所有已注册数据源类型的描述列表。
     */
    public static List<DataSourceTypeDescriptor> descriptors() {
        return Collections.unmodifiableList(new ArrayList<>(DESCRIPTORS_BY_TYPE.values()));
    }

    /**
     * 所有已注册 provider。
     */
    public static List<DataSourceProvider> providers() {
        return Collections.unmodifiableList(new ArrayList<>(PROVIDERS));
    }
}
