package com.data.datafusion.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.provider.elasticsearch.ElasticsearchConnection;
import com.data.datasource.provider.mongodb.MongoConnection;
import com.data.job.DatasourceInfo;
import com.data.job.DuckDBEngine;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.job.component.MetadataCollect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 多数据库元数据采集人工验证。
 *
 * <p>依赖本机已有的 Docker 数据库容器或本地数据库；不可用时自动跳过。</p>
 */
class MetadataCollectMultiDatabaseVerificationIT {

    @TempDir
    Path tempDir;

    @Test
    void shouldCollectDuckDbMetadata() throws Exception {
        String schema = "md_verify";
        String table = "MD_VERIFY_ORDERS";
        verify(
            new DbCase(
                "DUCKDB",
                "jdbc:duckdb:" + tempDir.resolve("duckdb-source.duckdb").toAbsolutePath(),
                null,
                null,
                schema,
                table,
                List.of("CREATE SCHEMA " + schema, createTableSql("DUCKDB", schema, table)),
                List.of("DROP TABLE " + schema + "." + table, "DROP SCHEMA " + schema)
            )
        );
    }

    @Test
    void shouldCollectSqliteMetadata() throws Exception {
        String table = "MD_VERIFY_ORDERS";
        verify(
            new DbCase(
                "SQLITE",
                "jdbc:sqlite:" + tempDir.resolve("sqlite-source.db").toAbsolutePath(),
                null,
                null,
                "main",
                table,
                List.of(createTableSql("SQLITE", "main", table)),
                List.of("DROP TABLE " + table)
            )
        );
    }

    @Test
    void shouldCollectMysqlMetadata() throws Exception {
        String database = "md_verify_" + System.nanoTime();
        String table = "MD_VERIFY_ORDERS";
        String baseUrl = "jdbc:mysql://127.0.0.1:3307/";
        String url = baseUrl + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        verify(
            new DbCase(
                "MYSQL",
                url,
                "root",
                "password",
                database,
                table,
                List.of("CREATE DATABASE " + database, createTableSql("MYSQL", database, table)),
                List.of("DROP DATABASE " + database),
                baseUrl + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
            )
        );
    }

    @Test
    void shouldCollectPostgresqlMetadata() throws Exception {
        String table = "MD_VERIFY_ORDERS";
        verify(
            new DbCase(
                "POSTGRESQL",
                "jdbc:postgresql://127.0.0.1:5432/postgres",
                "chenjie",
                "",
                "public",
                table,
                List.of(createTableSql("POSTGRESQL", "public", table)),
                List.of("DROP TABLE public." + table)
            )
        );
    }

    @Test
    void shouldCollectSqlServerMetadata() throws Exception {
        String table = "MD_VERIFY_ORDERS";
        verify(
            new DbCase(
                "SQLSERVER",
                "jdbc:sqlserver://127.0.0.1:14333;databaseName=datay_cdc;encrypt=true;trustServerCertificate=true;loginTimeout=5",
                "sa",
                "Datay123456!",
                "dbo",
                table,
                List.of(createTableSql("SQLSERVER", "dbo", table)),
                List.of("DROP TABLE dbo." + table)
            )
        );
    }

    @Test
    void shouldCollectOracleMetadata() throws Exception {
        String table = "MD_VERIFY_ORDERS";
        verify(
            new DbCase(
                "ORACLE",
                "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1",
                "datay",
                "datay123",
                "DATAY",
                table,
                List.of(createTableSql("ORACLE", "DATAY", table)),
                List.of("DROP TABLE " + table)
            )
        );
    }

    @Test
    void shouldCollectDmMetadata() throws Exception {
        String table = "MD_VERIFY_ORDERS";
        verify(
            new DbCase(
                "DM",
                "jdbc:dm://127.0.0.1:5236",
                "SYSDBA",
                "Dameng123",
                "SYSDBA",
                table,
                List.of(createTableSql("DM", "SYSDBA", table)),
                List.of("DROP TABLE " + table)
            )
        );
    }

    @Test
    void shouldCollectElasticsearchMetadata() throws Exception {
        String index = "md-verify-orders-" + System.nanoTime();
        DatasourceInfo info = new DatasourceInfo();
        info.setType("ELASTICSEARCH");
        info.setUrl("http://127.0.0.1:9200");
        info.setDbschema("_all");
        assumeTrue(canConnect(info), "Elasticsearch 不可用，跳过");

        try (
            DataSourceConnection connection = DataSourceProviderFactory.connect(info)
        ) {
            ElasticsearchConnection es = (ElasticsearchConnection) connection;
            es.recreateIndex(index);
            es.bulkIndex(
                index,
                List.of(
                    Map.of("order_id", 1, "amount", 10.5, "customer_name", "alice"),
                    Map.of("order_id", 2, "amount", 20.0, "customer_name", "bob")
                ),
                null
            );
        }

        try {
            collectAndAssert(info, "ds-elasticsearch", "_all", index, "order_id", "amount", "customer_name");
            System.out.println("[PASS] ELASTICSEARCH metadata collect, index=" + index);
        } finally {
            deleteElasticsearchIndex(index);
        }
    }

    @Test
    void shouldCollectMongoMetadata() throws Exception {
        String database = "md_verify_" + System.nanoTime();
        DatasourceInfo info = new DatasourceInfo();
        info.setType("MONGODB");
        info.setUrl("mongodb://127.0.0.1:27017/" + database);
        info.setDbschema(database);
        assumeTrue(canConnect(info), "MongoDB 不可用，跳过");

        try (DataSourceConnection connection = DataSourceProviderFactory.connect(info)) {
            MongoConnection mongo = (MongoConnection) connection;
            mongo.collection(database, "orders").insertOne(
                new Document("order_id", 1).append("amount", 10.5).append("customer_name", "alice")
            );
        }

        try {
            collectAndAssert(info, "ds-mongodb", database, "orders", "order_id", "amount", "customer_name");
            System.out.println("[PASS] MONGODB metadata collect, database=" + database + ", collection=orders");
        } finally {
            try (DataSourceConnection connection = DataSourceProviderFactory.connect(info)) {
                ((MongoConnection) connection).mongoClient().getDatabase(database).drop();
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        }
    }

    private void collectAndAssert(
        DatasourceInfo info,
        String dataSourceId,
        String schema,
        String table,
        String... expectedColumns
    ) throws Exception {
        String instanceCode = "metadata-" + dataSourceId + "-" + System.nanoTime();
        Path outputBase = tempDir.resolve("output-" + dataSourceId);
        MetadataCollect component = new MetadataCollect();
        component.setId("metadata_collect");
        component.setName("MetadataCollect");
        component.setDatasource(info);
        component.setSchemaNames(List.of(schema));
        component.setDataSourceId(dataSourceId);
        component.setDataSourceName(dataSourceId);
        component.setStorageType("local");
        component.setLocalBasePath(outputBase.toString());
        component.setTenantPrefix("tenants/verify");

        ExecutionContext context = new ExecutionContext();
        context.setJobCode("job-" + dataSourceId);
        context.setJobInstanceCode(instanceCode);
        component.setContext(context);
        component.setTaskLogger(new TaskLogger("job-" + dataSourceId, instanceCode));

        try {
            component.execute(new FlowFile());
            Path outputDir = outputBase.resolve("tenants/verify/metadata/" + dataSourceId + "/" + instanceCode);
            String tables = Files.readString(outputDir.resolve("tables.csv"), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            String columns = Files.readString(outputDir.resolve("columns.csv"), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

            assertTrue(tables.contains(table.toLowerCase(Locale.ROOT)), info.getType() + " tables.csv 未包含测试表");
            for (String column : expectedColumns) {
                assertTrue(columns.contains(column.toLowerCase(Locale.ROOT)), info.getType() + " columns.csv 未包含字段 " + column);
            }
        } finally {
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    private void deleteElasticsearchIndex(String index) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:9200/" + index))
                .DELETE()
                .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // ignore cleanup failure
        }
    }

    private boolean canConnect(DatasourceInfo info) {
        try (DataSourceConnection connection = DataSourceProviderFactory.connect(info)) {
            return connection.testConnection();
        } catch (Throwable e) {
            System.out.println("[SKIP] " + info.getType() + ": " + e.getMessage());
            return false;
        }
    }

    private void verify(DbCase dbCase) throws Exception {
        assumeTrue(canConnect(dbCase), dbCase.type + " 不可用，跳过");
        String suffix = "_" + System.nanoTime();
        String table = dbCase.table + suffix;
        String instanceCode = "metadata-" + dbCase.type.toLowerCase(Locale.ROOT) + "-" + System.nanoTime();
        String dataSourceId = "ds-" + dbCase.type.toLowerCase(Locale.ROOT);
        Path outputBase = tempDir.resolve("output-" + dbCase.type.toLowerCase(Locale.ROOT));

        try {
            execute(dbCase, "DROP TABLE " + table, true);
            for (String statement : dbCase.setupStatements) {
                execute(dbCase, statement.replace(dbCase.table, table), false);
            }

            MetadataCollect component = new MetadataCollect();
            component.setId("metadata_collect");
            component.setName("MetadataCollect");
            component.setDatasource(toInfo(dbCase));
            component.setSchemaNames(List.of(dbCase.schema));
            component.setDataSourceId(dataSourceId);
            component.setDataSourceName(dbCase.type + " 验证数据源");
            component.setStorageType("local");
            component.setLocalBasePath(outputBase.toString());
            component.setTenantPrefix("tenants/verify");

            ExecutionContext context = new ExecutionContext();
            context.setJobCode("job-" + dbCase.type.toLowerCase(Locale.ROOT));
            context.setJobInstanceCode(instanceCode);
            component.setContext(context);
            component.setTaskLogger(new TaskLogger("job-" + dbCase.type.toLowerCase(Locale.ROOT), instanceCode));

            component.execute(new FlowFile());

            Path outputDir = outputBase.resolve("tenants/verify/metadata/" + dataSourceId + "/" + instanceCode);
            String tables = Files.readString(outputDir.resolve("tables.csv"), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            String columns = Files.readString(outputDir.resolve("columns.csv"), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

            assertTrue(tables.contains(table.toLowerCase(Locale.ROOT)), dbCase.type + " tables.csv 未包含测试表");
            assertTrue(columns.contains("order_id"), dbCase.type + " columns.csv 未包含 order_id");
            assertTrue(columns.contains("amount"), dbCase.type + " columns.csv 未包含 amount");
            assertTrue(columns.contains("customer_name"), dbCase.type + " columns.csv 未包含 customer_name");
            System.out.println("[PASS] " + dbCase.type + " metadata collect, table=" + table);
        } finally {
            for (String statement : dbCase.cleanupStatements) {
                execute(dbCase, statement.replace(dbCase.table, table), true);
            }
            DuckDBEngine.deleteDBFile(instanceCode);
        }
    }

    private boolean canConnect(DbCase dbCase) {
        return canConnect(toInfoForSetup(dbCase));
    }

    private void execute(DbCase dbCase, String sql, boolean ignoreFailure) throws Exception {
        try (DataSourceConnection connection = DataSourceProviderFactory.connect(toInfoForSetup(dbCase))) {
            connection.executeUpdate(sql, null);
        } catch (Exception e) {
            if (!ignoreFailure) {
                throw e;
            }
        }
    }

    private DatasourceInfo toInfo(DbCase dbCase) {
        return toInfo(dbCase, dbCase.url);
    }

    private DatasourceInfo toInfoForSetup(DbCase dbCase) {
        return toInfo(dbCase, dbCase.setupUrl == null ? dbCase.url : dbCase.setupUrl);
    }

    private DatasourceInfo toInfo(DbCase dbCase, String url) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(dbCase.type);
        info.setUrl(url);
        info.setUsername(dbCase.username);
        info.setPassword(dbCase.password);
        info.setDbschema(dbCase.schema);
        return info;
    }

    private static String createTableSql(String type, String schema, String table) {
        return switch (type) {
            case "MYSQL", "POSTGRESQL" -> "CREATE TABLE " + schema + "." + table + " (order_id INT PRIMARY KEY, amount DECIMAL(18,2), customer_name VARCHAR(100))";
            case "SQLSERVER" -> "CREATE TABLE " + schema + "." + table + " (order_id INT PRIMARY KEY, amount DECIMAL(18,2), customer_name NVARCHAR(100))";
            case "ORACLE" -> "CREATE TABLE " + table + " (order_id NUMBER(10) PRIMARY KEY, amount NUMBER(18,2), customer_name VARCHAR2(100))";
            case "DM" -> "CREATE TABLE " + table + " (order_id INT PRIMARY KEY, amount DECIMAL(18,2), customer_name VARCHAR(100))";
            default -> "CREATE TABLE " + schema + "." + table + " (order_id INTEGER PRIMARY KEY, amount DECIMAL(18,2), customer_name VARCHAR(100))";
        };
    }

    private record DbCase(
        String type,
        String url,
        String username,
        String password,
        String schema,
        String table,
        List<String> setupStatements,
        List<String> cleanupStatements,
        String setupUrl
    ) {
        DbCase(
            String type,
            String url,
            String username,
            String password,
            String schema,
            String table,
            List<String> setupStatements,
            List<String> cleanupStatements
        ) {
            this(type, url, username, password, schema, table, setupStatements, cleanupStatements, null);
        }
    }
}
