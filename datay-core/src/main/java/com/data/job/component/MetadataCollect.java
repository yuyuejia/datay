package com.data.job.component;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.job.ComponentField;
import com.data.job.ComponentFieldOption;
import com.data.job.ComponentFieldType;
import com.data.job.ComponentFieldValueFormat;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.DuckDBEngine;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 元数据采集组件。
 *
 * <p>从数据源读取表与字段元数据，写入 DuckDB 临时表后，通过 DuckDB
 * {@code COPY} 将 CSV 结果写入本地文件或 MinIO。每次执行生成一份独立快照，
 * 成功完成后再更新 {@code latest} 目录。</p>
 */
@ComponentRegister(
    value = "MetadataCollect",
    name = "元数据采集",
    group = "数据输入",
    desc = "采集数据源的表和字段元数据，并通过 DuckDB SQL 写入本地文件或 MinIO。",
    order = 5
)
public class MetadataCollect extends FlowComponent {

    private static final String DEFAULT_OUTPUT_DIR = "metadata";

    private static final String DEFAULT_LOCAL_BASE_PATH = "./data/files";

    private static final String DEFAULT_MINIO_BUCKET = "datay-files";

    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
        "information_schema",
        "performance_schema",
        "pg_catalog",
        "pg_toast",
        "mysql",
        "sys",
        "system",
        "temp"
    );

    @ComponentField(
        key = "sourceId",
        label = "数据源",
        type = ComponentFieldType.DATASOURCE,
        required = true,
        group = "基础配置",
        order = 10,
        help = "选择需要采集表结构和字段信息的数据源"
    )
    private DatasourceInfo datasource;

    @ComponentField(
        label = "Schema 列表",
        type = ComponentFieldType.MULTI_SELECT,
        group = "基础配置",
        order = 20,
        valueFormat = ComponentFieldValueFormat.ARRAY,
        help = "不配置时使用数据源默认 Schema；默认 Schema 也为空时采集全部非系统 Schema"
    )
    private List<String> schemaNames;

    @ComponentField(
        label = "数据源 ID",
        type = ComponentFieldType.INPUT,
        group = "高级配置",
        order = 10,
        help = "用于生成采集结果的文件目录"
    )
    private String dataSourceId;

    @ComponentField(
        label = "数据源名称",
        type = ComponentFieldType.INPUT,
        group = "高级配置",
        order = 20,
        help = "写入元数据文件，便于识别来源"
    )
    private String dataSourceName;

    @ComponentField(
        label = "输出目录",
        type = ComponentFieldType.INPUT,
        group = "高级配置",
        order = 30,
        defaultValue = DEFAULT_OUTPUT_DIR,
        help = "相对于文件存储根目录的输出路径，默认 metadata"
    )
    private String outputDir = DEFAULT_OUTPUT_DIR;

    @ComponentField(
        label = "存储类型",
        type = ComponentFieldType.SELECT,
        group = "存储配置",
        order = 10,
        defaultValue = "local",
        help = "与系统文件存储配置保持一致",
        options = {
            @ComponentFieldOption(value = "local", label = "本地文件"),
            @ComponentFieldOption(value = "minio", label = "MinIO")
        }
    )
    private String storageType = "local";

    @ComponentField(
        label = "本地根目录",
        type = ComponentFieldType.INPUT,
        group = "存储配置",
        order = 20,
        defaultValue = DEFAULT_LOCAL_BASE_PATH,
        dependsOn = "storageType",
        visibleWhen = { "local" }
    )
    private String localBasePath = DEFAULT_LOCAL_BASE_PATH;

    @ComponentField(label = "MinIO Endpoint", type = ComponentFieldType.INPUT, group = "存储配置", order = 30, dependsOn = "storageType", visibleWhen = { "minio" })
    private String minioEndpoint;

    @ComponentField(label = "MinIO Access Key", type = ComponentFieldType.INPUT, group = "存储配置", order = 40, dependsOn = "storageType", visibleWhen = { "minio" })
    private String minioAccessKey;

    @ComponentField(label = "MinIO Secret Key", type = ComponentFieldType.PASSWORD, group = "存储配置", order = 50, secret = true, dependsOn = "storageType", visibleWhen = { "minio" })
    private String minioSecretKey;

    @ComponentField(
        label = "MinIO Bucket",
        type = ComponentFieldType.INPUT,
        group = "存储配置",
        order = 60,
        defaultValue = DEFAULT_MINIO_BUCKET,
        dependsOn = "storageType",
        visibleWhen = { "minio" }
    )
    private String minioBucketName = DEFAULT_MINIO_BUCKET;

    private String minioUrlStyle = "path";

    @ComponentField(
        label = "租户目录前缀",
        type = ComponentFieldType.INPUT,
        group = "存储配置",
        order = 70,
        defaultValue = "tenants/default",
        help = "由平台生成，用于按租户隔离采集结果"
    )
    private String tenantPrefix = "tenants/default";

    public MetadataCollect() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile == null || flowFile.getAttribute("_end") != null) {
            return;
        }
        if (datasource == null) {
            throw new IllegalArgumentException("元数据采集未配置数据源");
        }

        List<TableRow> tableRows = new ArrayList<>();
        List<ColumnRow> columnRows = new ArrayList<>();
        List<ErrorRow> errorRows = new ArrayList<>();
        Timestamp collectedAt = Timestamp.from(Instant.now());

        try (DataSourceConnection connection = openConnection()) {
            List<Scope> scopes = resolveScopes(connection);
            if (scopes.isEmpty()) {
                throw new IllegalArgumentException("未找到可采集的 Schema");
            }
            logInfo("开始采集元数据，数据源=" + resolveDataSourceName() + "，Schema 数=" + scopes.size());
            for (Scope scope : scopes) {
                collectScope(connection, scope, collectedAt, tableRows, columnRows, errorRows);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("元数据采集失败: " + e.getMessage(), e);
        }

        writeMetadataFiles(tableRows, columnRows, errorRows);
        logInfo("元数据采集完成，表数=" + tableRows.size() + "，字段数=" + columnRows.size() + "，错误数=" + errorRows.size());
        if (!errorRows.isEmpty()) {
            throw new RuntimeException("元数据采集存在 " + errorRows.size() + " 个表读取失败，latest 目录未更新，请查看 errors.csv");
        }
    }

    private DataSourceConnection openConnection() throws Exception {
        if (isBlank(datasource.getType())) {
            DataSourceProvider provider = DataSourceProviderFactory.providerForUrl(datasource.getUrl());
            if (provider != null) {
                List<DataSourceTypeDescriptor> descriptors = provider.descriptors();
                if (descriptors != null && !descriptors.isEmpty()) {
                    datasource.setType(descriptors.get(0).getType());
                }
            }
        }
        return DataSourceProviderFactory.connect(datasource);
    }

    private List<Scope> resolveScopes(DataSourceConnection connection) throws Exception {
        List<String> catalogs = safeList(connection.getCatalogs());
        List<String> configuredSchemas = normalizeSchemaNames(schemaNames);
        Set<Scope> scopes = new LinkedHashSet<>();

        if (!configuredSchemas.isEmpty()) {
            if (catalogs.isEmpty()) {
                configuredSchemas.forEach(schema -> scopes.add(new Scope(null, schema)));
            } else {
                for (String catalog : catalogs) {
                    List<String> available = safeList(connection.getSchemas(catalog));
                    for (String schema : configuredSchemas) {
                        if (available.isEmpty() || available.contains(schema)) {
                            scopes.add(new Scope(catalog, schema));
                        }
                    }
                }
            }
            return new ArrayList<>(scopes);
        }

        String defaultSchema = trimToNull(datasource.getDbschema());
        if (defaultSchema != null) {
            if (catalogs.isEmpty()) {
                scopes.add(new Scope(null, defaultSchema));
            } else {
                for (String catalog : catalogs) {
                    scopes.add(new Scope(catalog, defaultSchema));
                }
            }
            return new ArrayList<>(scopes);
        }

        if (catalogs.isEmpty()) {
            for (String schema : safeList(connection.getSchemas())) {
                if (!isSystemSchema(schema)) {
                    scopes.add(new Scope(null, schema));
                }
            }
        } else {
            for (String catalog : catalogs) {
                for (String schema : safeList(connection.getSchemas(catalog))) {
                    if (!isSystemSchema(schema)) {
                        scopes.add(new Scope(catalog, schema));
                    }
                }
            }
        }
        return new ArrayList<>(scopes);
    }

    private void collectScope(
        DataSourceConnection connection,
        Scope scope,
        Timestamp collectedAt,
        List<TableRow> tableRows,
        List<ColumnRow> columnRows,
        List<ErrorRow> errorRows
    ) {
        List<TableMeta> tables;
        try {
            tables = safeList(connection.getTables(scope.catalog(), scope.schema(), null, null));
        } catch (Exception e) {
            errorRows.add(new ErrorRow(scope.catalog(), scope.schema(), null, describe(e), collectedAt));
            logWarn("读取 Schema 表清单失败: " + scope.display() + " - " + describe(e));
            return;
        }
        for (TableMeta table : tables) {
            String tableName = trimToNull(table.getTable());
            if (tableName == null) {
                continue;
            }
            try {
                TableMeta detail = connection.getTableDetail(scope.catalog(), scope.schema(), tableName);
                if (detail == null) {
                    detail = table;
                }
                String catalogName = firstNonBlank(detail.getCatalog(), table.getCatalog(), scope.catalog());
                String schemaName = firstNonBlank(detail.getSchema(), table.getSchema(), scope.schema());
                String tableComment = firstNonBlank(detail.getComment(), table.getComment());
                tableRows.add(new TableRow(catalogName, schemaName, tableName, tableComment, collectedAt));

                List<ColumnMeta> columns = detail.columns() == null ? List.of() : detail.columns();
                int ordinal = 0;
                for (ColumnMeta column : columns) {
                    if (column == null || isBlank(column.getName())) {
                        continue;
                    }
                    ordinal++;
                    columnRows.add(new ColumnRow(catalogName, schemaName, tableName, column, ordinal, collectedAt));
                }
            } catch (Exception e) {
                errorRows.add(new ErrorRow(scope.catalog(), scope.schema(), tableName, describe(e), collectedAt));
                logWarn("读取表元数据失败: " + scope.display() + "." + tableName + " - " + describe(e));
            }
        }
        logInfo("Schema 元数据采集完成: " + scope.display() + "，表数=" + tables.size());
    }

    private void writeMetadataFiles(List<TableRow> tableRows, List<ColumnRow> columnRows, List<ErrorRow> errorRows) {
        String baseDir = joinPath(
            tenantPrefix,
            normalizeOutputDir(outputDir),
            trimToNull(dataSourceId) == null ? "unknown-source" : sanitizePathPart(dataSourceId),
            instanceCode()
        );
        String latestDir = joinPath(
            tenantPrefix,
            normalizeOutputDir(outputDir),
            trimToNull(dataSourceId) == null ? "unknown-source" : sanitizePathPart(dataSourceId),
            "latest"
        );

        try (Connection duckdb = DuckDBEngine.getInstance().getConnection(instanceCode())) {
            if ("minio".equalsIgnoreCase(storageType)) {
                setupMinioSecret(duckdb);
            }
            createTempTables(duckdb);
            insertTableRows(duckdb, tableRows);
            insertColumnRows(duckdb, columnRows);
            insertErrorRows(duckdb, errorRows);

            copyTable(duckdb, "metadata_tables", joinPath(baseDir, "tables.csv"), "CSV");
            copyTable(duckdb, "metadata_columns", joinPath(baseDir, "columns.csv"), "CSV");
            if (!errorRows.isEmpty()) {
                copyTable(duckdb, "metadata_errors", joinPath(baseDir, "errors.csv"), "CSV");
            }

            if (errorRows.isEmpty()) {
                copyTable(duckdb, "metadata_tables", joinPath(latestDir, "tables.csv"), "CSV");
                copyTable(duckdb, "metadata_columns", joinPath(latestDir, "columns.csv"), "CSV");
            }
            logInfo("元数据文件输出目录: " + baseDir + (errorRows.isEmpty() ? "，latest 已更新: " + latestDir : ""));
        } catch (SQLException e) {
            throw new RuntimeException("元数据写入文件存储失败: " + e.getMessage(), e);
        }
    }

    private void createTempTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "CREATE OR REPLACE TABLE main.metadata_tables (" +
                "data_source_id VARCHAR, data_source_name VARCHAR, catalog_name VARCHAR, schema_name VARCHAR, " +
                "table_name VARCHAR, table_comment VARCHAR, collected_at TIMESTAMP)"
            );
            statement.execute(
                "CREATE OR REPLACE TABLE main.metadata_columns (" +
                "data_source_id VARCHAR, data_source_name VARCHAR, catalog_name VARCHAR, schema_name VARCHAR, " +
                "table_name VARCHAR, column_name VARCHAR, ordinal_position INTEGER, data_type VARCHAR, " +
                "column_length INTEGER, numeric_precision INTEGER, numeric_scale INTEGER, nullable BOOLEAN, " +
                "primary_key BOOLEAN, default_value VARCHAR, column_comment VARCHAR, collected_at TIMESTAMP)"
            );
            statement.execute(
                "CREATE OR REPLACE TABLE main.metadata_errors (" +
                "data_source_id VARCHAR, data_source_name VARCHAR, catalog_name VARCHAR, schema_name VARCHAR, " +
                "table_name VARCHAR, error_message VARCHAR, collected_at TIMESTAMP)"
            );
        }
    }

    private void insertTableRows(Connection connection, List<TableRow> rows) throws SQLException {
        String sql =
            "INSERT INTO main.metadata_tables VALUES (" +
            "?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 0;
            for (TableRow row : rows) {
                statement.setString(1, blankToNull(dataSourceId));
                statement.setString(2, blankToNull(dataSourceName));
                statement.setString(3, blankToNull(row.catalog()));
                statement.setString(4, blankToNull(row.schema()));
                statement.setString(5, row.table());
                statement.setString(6, blankToNull(row.comment()));
                statement.setTimestamp(7, row.collectedAt());
                statement.addBatch();
                if (++index % 500 == 0) {
                    statement.executeBatch();
                }
            }
            if (index % 500 != 0 || index == 0) {
                statement.executeBatch();
            }
        }
    }

    private void insertColumnRows(Connection connection, List<ColumnRow> rows) throws SQLException {
        String sql =
            "INSERT INTO main.metadata_columns VALUES (" +
            "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 0;
            for (ColumnRow row : rows) {
                ColumnMeta column = row.column();
                statement.setString(1, blankToNull(dataSourceId));
                statement.setString(2, blankToNull(dataSourceName));
                statement.setString(3, blankToNull(row.catalog()));
                statement.setString(4, blankToNull(row.schema()));
                statement.setString(5, row.table());
                statement.setString(6, column.getName());
                statement.setInt(7, row.ordinal());
                statement.setString(8, blankToNull(column.getType()));
                statement.setInt(9, column.getLength());
                statement.setInt(10, column.getPrecision());
                statement.setInt(11, column.getScale());
                statement.setBoolean(12, column.isNullable());
                statement.setBoolean(13, column.isPrimaryKey());
                statement.setString(14, blankToNull(column.getDefaultValue()));
                statement.setString(15, blankToNull(column.getComment()));
                statement.setTimestamp(16, row.collectedAt());
                statement.addBatch();
                if (++index % 500 == 0) {
                    statement.executeBatch();
                }
            }
            if (index % 500 != 0 || index == 0) {
                statement.executeBatch();
            }
        }
    }

    private void insertErrorRows(Connection connection, List<ErrorRow> rows) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO main.metadata_errors VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ErrorRow row : rows) {
                statement.setString(1, blankToNull(dataSourceId));
                statement.setString(2, blankToNull(dataSourceName));
                statement.setString(3, blankToNull(row.catalog()));
                statement.setString(4, blankToNull(row.schema()));
                statement.setString(5, blankToNull(row.table()));
                statement.setString(6, truncate(row.error(), 2000));
                statement.setTimestamp(7, row.collectedAt());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void copyTable(Connection connection, String tableName, String logicalPath, String format) throws SQLException {
        String target = resolveStorageTarget(logicalPath);
        String sql = "COPY (SELECT * FROM main." + tableName + ") TO " + literal(target) + " (FORMAT " + format + (isCsv(format) ? ", HEADER" : "") + ")";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
        logInfo("写入元数据文件: " + logicalPath);
    }

    private String resolveStorageTarget(String logicalPath) {
        if ("minio".equalsIgnoreCase(storageType)) {
            String bucket = trimSlashes(isBlank(minioBucketName) ? DEFAULT_MINIO_BUCKET : minioBucketName);
            return "s3://" + bucket + "/" + trimSlashes(logicalPath);
        }
        Path base = Paths.get(isBlank(localBasePath) ? DEFAULT_LOCAL_BASE_PATH : localBasePath);
        Path target = base.resolve(trimSlashes(logicalPath)).normalize().toAbsolutePath();
        try {
            Files.createDirectories(target.getParent());
        } catch (Exception e) {
            throw new RuntimeException("创建元数据输出目录失败: " + target.getParent(), e);
        }
        return target.toString();
    }

    private void setupMinioSecret(Connection connection) throws SQLException {
        if (isBlank(minioEndpoint)) {
            throw new IllegalArgumentException("MinIO 未配置 endpoint");
        }
        String endpoint = minioEndpoint.replaceFirst("^https?://", "");
        String urlStyle = isBlank(minioUrlStyle) ? "path" : minioUrlStyle;
        String secretName = "metadata_s3_" + sanitizeIdentifier(getId() == null ? "component" : getId());
        String sql =
            "CREATE OR REPLACE SECRET " +
            secretName +
            " (TYPE s3, KEY_ID " +
            literal(minioAccessKey) +
            ", SECRET " +
            literal(minioSecretKey) +
            ", ENDPOINT " +
            literal(endpoint) +
            ", url_style " +
            literal(urlStyle) +
            ", USE_SSL " +
            (minioEndpoint.startsWith("https://") ? "true" : "false") +
            ")";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String resolveDataSourceName() {
        return isBlank(dataSourceName) ? "unknown" : dataSourceName;
    }

    private String instanceCode() {
        String code = getContext() == null ? null : getContext().getJobInstanceCode();
        return isBlank(code) ? "manual" : code;
    }

    private static List<String> normalizeSchemaNames(List<String> values) {
        List<String> result = new java.util.ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (!isBlank(value) && !result.contains(value.trim())) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private static boolean isSystemSchema(String schema) {
        if (isBlank(schema)) {
            return true;
        }
        String lower = schema.toLowerCase(Locale.ROOT);
        return SYSTEM_SCHEMAS.contains(lower) || lower.startsWith("__ducklake_");
    }

    private static boolean isCsv(String format) {
        return "CSV".equalsIgnoreCase(format);
    }

    private static String describe(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = firstNonBlank(root.getMessage(), throwable.getMessage(), root.getClass().getSimpleName());
        return truncate(message, 2000);
    }

    private static String normalizeOutputDir(String value) {
        String result = isBlank(value) ? DEFAULT_OUTPUT_DIR : value.trim().replace('\\', '/');
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.contains("..")) {
            throw new IllegalArgumentException("输出目录不能包含 ..");
        }
        return result.isEmpty() ? DEFAULT_OUTPUT_DIR : result;
    }

    private static String joinPath(String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (isBlank(part)) {
                continue;
            }
            String normalized = part.replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (normalized.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(normalized);
        }
        return result.toString();
    }

    private static String trimSlashes(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String sanitizePathPart(String value) {
        return value.replaceAll("[^\\p{L}\\p{N}_.-]", "_");
    }

    private static String sanitizeIdentifier(String value) {
        return value.replaceAll("[^\\p{L}\\p{N}_]", "_");
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String literal(String value) {
        return "'" + (value == null ? "" : value.replace("'", "''")) + "'";
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record Scope(String catalog, String schema) {
        private String display() {
            if (catalog == null || catalog.isBlank()) {
                return schema == null ? "(default)" : schema;
            }
            return catalog + "." + (schema == null ? "" : schema);
        }
    }

    private record TableRow(String catalog, String schema, String table, String comment, Timestamp collectedAt) {}

    private record ColumnRow(String catalog, String schema, String table, ColumnMeta column, int ordinal, Timestamp collectedAt) {}

    private record ErrorRow(String catalog, String schema, String table, String error, Timestamp collectedAt) {}

    public DatasourceInfo getDatasource() {
        return datasource;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public List<String> getSchemaNames() {
        return schemaNames;
    }

    public void setSchemaNames(List<String> schemaNames) {
        this.schemaNames = schemaNames;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getLocalBasePath() {
        return localBasePath;
    }

    public void setLocalBasePath(String localBasePath) {
        this.localBasePath = localBasePath;
    }

    public String getMinioEndpoint() {
        return minioEndpoint;
    }

    public void setMinioEndpoint(String minioEndpoint) {
        this.minioEndpoint = minioEndpoint;
    }

    public String getMinioAccessKey() {
        return minioAccessKey;
    }

    public void setMinioAccessKey(String minioAccessKey) {
        this.minioAccessKey = minioAccessKey;
    }

    public String getMinioSecretKey() {
        return minioSecretKey;
    }

    public void setMinioSecretKey(String minioSecretKey) {
        this.minioSecretKey = minioSecretKey;
    }

    public String getMinioBucketName() {
        return minioBucketName;
    }

    public void setMinioBucketName(String minioBucketName) {
        this.minioBucketName = minioBucketName;
    }

    public String getMinioUrlStyle() {
        return minioUrlStyle;
    }

    public void setMinioUrlStyle(String minioUrlStyle) {
        this.minioUrlStyle = minioUrlStyle;
    }

    public String getTenantPrefix() {
        return tenantPrefix;
    }

    public void setTenantPrefix(String tenantPrefix) {
        this.tenantPrefix = tenantPrefix;
    }
}
