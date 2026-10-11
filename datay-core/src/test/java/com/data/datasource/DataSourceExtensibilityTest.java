package com.data.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证「新增数据源只需提供扩展」：无论是非 JDBC 还是 JDBC 数据源，只要新增一个 Provider
 * （必要时附带 Connection），即可被注册表发现并端到端工作，无需改动任何中心化分支逻辑。
 */
class DataSourceExtensibilityTest {

    @Test
    void newNonJdbcDatasourceWorksWithOnlyProviderAndConnection() throws Exception {
        DataSourceProviderFactory.register(new MockDbProvider());

        // 1) 按类型解析
        DataSourceProvider provider = DataSourceProviderFactory.provider("mockdb");
        assertNotNull(provider);
        assertFalse(provider.descriptors().get(0).isJdbc());

        // 2) 按 URL 解析
        assertEquals(provider, DataSourceProviderFactory.providerForUrl("mockdb://localhost:1"));

        // 3) 端到端连接能力，全部由 Provider/Connection 自身提供
        DatasourceInfo info = new DatasourceInfo();
        info.setType("MOCKDB");
        info.setUrl("mockdb://localhost:1");
        try (DataSourceConnection connection = DataSourceProviderFactory.connect(info)) {
            assertTrue(connection.testConnection());

            QueryResult result = connection.executeQuery("select 1", null, 100);
            assertEquals(List.of("id", "name"), result.getColumns());
            assertEquals(1, result.getRows().size());
            assertEquals(2, result.getAffectedRows());

            assertEquals(List.of("default"), connection.getSchemas());
            assertEquals("mock_table", connection.getTables(null, null, null).get(0).getTable());
            assertEquals("id", connection.getTableMeta(null, "mock_table").columns().get(0).getName());
        }
    }

    @Test
    void newJdbcDatasourceReusesStandardCapabilitiesWithoutDbType() {
        // 该 Provider 未在任何中心化表中登记（无 DBType / 无 DBUtils.getDBTypeEnum 分支）
        DataSourceProviderFactory.register(new PlainJdbcProvider());

        DataSourceProvider provider = DataSourceProviderFactory.provider("plainjdbc");
        assertNotNull(provider);
        assertTrue(provider instanceof JdbcDataSourceProvider);

        // 按 URL 前缀自动识别（由 descriptor 的 jdbcUrlTemplate 推导）
        assertEquals(provider, DataSourceProviderFactory.providerForUrl("jdbc:plainjdbc://host:1234/db"));

        JdbcDataSourceProvider jdbc = (JdbcDataSourceProvider) provider;
        assertEquals("SELECT 1", jdbc.getTestQuery());
        assertNotNull(jdbc.typeConverter("mysql"));
        assertTrue(jdbc.descriptors().get(0).isJdbc());
    }

    @Test
    void centralDbUtilsRoutesToNewProviderViaUrlWithoutEdits() {
        // 注册一个未在 DBType / getDBTypeEnum 中登记的新 JDBC 类型
        DataSourceProviderFactory.register(new CustomQueryJdbcProvider());

        // DBUtils 的公共能力通过 URL 前缀路由到新 Provider，无需改动中心化分支
        assertEquals("SELECT 1 FROM CUSTOMPLAIN", com.data.metadata.util.DBUtils.getTestQuery("jdbc:customplain://host:1/db"));
    }

    // ------------------------------------------------------------------ 测试用扩展

    private static final class MockDbProvider implements DataSourceProvider {

        @Override
        public String providerId() {
            return "mockdb";
        }

        @Override
        public List<DataSourceTypeDescriptor> descriptors() {
            return List.of(new DataSourceTypeDescriptor().setType("MOCKDB").setDisplayName("Mock DB").setJdbc(false));
        }

        @Override
        public boolean supports(String type) {
            return "MOCKDB".equalsIgnoreCase(type);
        }

        @Override
        public boolean supportsUrl(String url) {
            return url != null && url.startsWith("mockdb:");
        }

        @Override
        public DataSourceConnection connect(DatasourceInfo info) {
            return new MockConnection();
        }
    }

    private static final class MockConnection implements DataSourceConnection {

        @Override
        public QueryResult executeQuery(String statement, List<Object> params, int maxRows) {
            return new QueryResult(
                List.of("id", "name"),
                List.of(Map.of("id", 1, "name", "mock")),
                2
            );
        }

        @Override
        public int executeUpdate(String statement, List<Object> params) {
            return 1;
        }

        @Override
        public List<String> getSchemas() {
            return List.of("default");
        }

        @Override
        public List<TableMeta> getTables(String schema, Integer limit, String search) {
            return List.of(new TableMeta("mock_table"));
        }

        @Override
        public TableMeta getTableMeta(String schema, String table) {
            return new TableMeta(table, List.of(new ColumnMeta("id", "INT")));
        }

        @Override
        public boolean testConnection() {
            return true;
        }
    }

    private static final class PlainJdbcProvider extends JdbcDataSourceProvider {

        @Override
        protected DataSourceTypeDescriptor descriptor() {
            return new DataSourceTypeDescriptor()
                .setType("PLAINJDBC")
                .setDisplayName("Plain JDBC")
                .setJdbc(true)
                .setJdbcUrlTemplate("jdbc:plainjdbc://{host}:{port}/{database}");
        }
    }

    private static final class CustomQueryJdbcProvider extends JdbcDataSourceProvider {

        @Override
        protected DataSourceTypeDescriptor descriptor() {
            return new DataSourceTypeDescriptor()
                .setType("CUSTOMPLAIN")
                .setDisplayName("Custom Plain")
                .setJdbc(true)
                .setJdbcUrlTemplate("jdbc:customplain://{host}:{port}/{database}");
        }

        @Override
        public String getTestQuery() {
            return "SELECT 1 FROM CUSTOMPLAIN";
        }
    }
}
