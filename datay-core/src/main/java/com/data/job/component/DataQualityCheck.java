package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DuckDBEngine;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.quality.CompiledQualityRule;
import com.data.job.quality.DataQualityRuleCompiler;
import com.data.job.quality.DuckDBFlowBuffer;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据质量检核组件。
 *
 * <p>支持两种检核模式：</p>
 * <ul>
 *   <li>{@code STREAM}：逐批检核当前 FlowFile，通过后立即下发到下游（默认）；</li>
 *   <li>{@code FULL}：上游数据先写入 DuckDB，所有数据到齐后做全量检核，通过后再从 DuckDB 下发。</li>
 * </ul>
 *
 * <p>检核结果写入文件存储（本地 / MinIO）。存在质量不达标的 ERROR 规则时抛出异常，
 * 由 ETL 引擎将任务标记为失败。</p>
 */
@ComponentRegister(
    value = "DataQualityCheck",
    name = "数据质量检核",
    group = "数据质量",
    desc = "支持流式/全量模式按表级/字段级规则检核数据，结果写入文件存储，质量不达标时任务失败。",
    order = 10
)
public class DataQualityCheck extends FlowComponent {

    private static final String SCHEMA = "main";

    private static final String SUMMARY_TABLE_PREFIX = "dq_summary_";

    private static final String DEFAULT_OUTPUT_DIR = "data-quality/etl";

    private final DataQualityRuleCompiler ruleCompiler = new DataQualityRuleCompiler();

    private volatile boolean checked;

    private DuckDBFlowBuffer flowBuffer;

    private int batchIndex;

    /** 全量模式下保留首个上游 FlowFile 的属性，用于检核通过后按原表信息下发。 */
    private final Map<String, Object> sourceAttributes = new LinkedHashMap<>();

    /** 全量模式下合并上游状态，保证增量水位等状态继续向下游传递。 */
    private final Map<String, Object> sourceStatus = new LinkedHashMap<>();

    private String checkMode = "STREAM";

    private Object rules;

    private boolean failOnError = true;

    private boolean failOnWarn;

    private boolean writeResult = true;

    private int sampleLimit = 100;

    private String outputFormat = "CSV";

    private String outputDir = DEFAULT_OUTPUT_DIR;

    private String resultPrefix = "quality";

    private String tenantPrefix = "tenants/default";

    private String storageType = "local";

    private String localBasePath = "./data/files";

    private String minioEndpoint;

    private String minioAccessKey;

    private String minioSecretKey;

    private String minioBucketName = "datay-files";

    private String minioUrlStyle = "path";

    public DataQualityCheck() {
        setType(ComponentType.OPERATOR);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile == null) {
            return;
        }
        if (flowFile.getAttribute("_start") != null) {
            return;
        }
        if (flowFile.getAttribute("_end") != null) {
            if (upstreamFinish) {
                if (isFullMode()) {
                    runFullCheck();
                } else {
                    logInfo("流式质量检核完成，共检核 " + batchIndex + " 个批次");
                }
            }
            return;
        }

        if (isFullMode()) {
            materializeFull(flowFile);
            return;
        }

        // 流式模式：先检核当前 FlowFile，通过后再下发。
        runStreamCheck(flowFile);
        forward(flowFile);
    }

    private boolean isFullMode() {
        return "FULL".equalsIgnoreCase(checkMode == null ? "" : checkMode.trim());
    }

    /**
     * 全量模式：上游数据先持续写入 DuckDB，等待上游结束后再做全量检核。
     */
    private void materializeFull(FlowFile flowFile) {
        if (flowFile.getJsonArray() == null) {
            return;
        }
        if (flowBuffer == null) {
            flowBuffer = new DuckDBFlowBuffer(resolveSourceTable(), true);
        }
        if (sourceAttributes.isEmpty()) {
            sourceAttributes.putAll(flowFile.getAttributeMap());
        }
        sourceStatus.putAll(flowFile.getStatusMap());
        try (Connection connection = duckDBConnection()) {
            flowBuffer.append(connection, flowFile);
        } catch (SQLException e) {
            throw new RuntimeException("数据质量检核写入 DuckDB 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式模式：对当前 FlowFile 独立检核。通过（或仅 WARN）后由调用方下发。
     */
    private void runStreamCheck(FlowFile flowFile) {
        JSONArray records = flowFile.getJsonArray();
        if (records == null) {
            return;
        }
        batchIndex++;
        String table = resolveSourceTable();
        if (flowBuffer == null) {
            // 流式模式每个批次替换 DuckDB 表内容，只检核当前 FlowFile。
            flowBuffer = new DuckDBFlowBuffer(table, false);
        }
        try (Connection connection = duckDBConnection()) {
            flowBuffer.append(connection, flowFile);
            List<CompiledQualityRule> compiledRules = ruleCompiler.compile(rules, table);
            List<RuleResult> results = evaluate(connection, compiledRules, table);
            logSummary("批次 " + batchIndex, results);
            if (results.stream().anyMatch(result -> !"PASS".equals(result.status()))) {
                writeResultFiles(connection, results, table, "_batch_" + batchIndex);
                assertQuality(results);
            }
        } catch (SQLException e) {
            throw new RuntimeException("数据质量检核执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 全量模式：所有数据到齐后统一检核；通过后将 DuckDB 中的数据下发到下游。
     */
    private synchronized void runFullCheck() {
        if (checked) {
            return;
        }
        checked = true;
        String table = resolveSourceTable();
        List<CompiledQualityRule> compiledRules = ruleCompiler.compile(rules, table);
        try (Connection connection = duckDBConnection()) {
            List<RuleResult> results = evaluate(connection, compiledRules, table);
            logSummary("全量", results);
            writeResultFiles(connection, results, table, "");
            assertQuality(results);
            emitFullData(connection, table);
        } catch (SQLException e) {
            throw new RuntimeException("数据质量检核执行失败: " + e.getMessage(), e);
        }
    }

    private void writeResultFiles(Connection connection, List<RuleResult> results, String table, String suffix) throws SQLException {
        boolean debug = getContext() != null && getContext().isDebugMode();
        if (debug || !writeResult) {
            return;
        }
        createSummaryTable(connection);
        insertSummary(connection, results);
        writeSummaryFile(connection, suffix);
        writeDetailFiles(connection, results, table, suffix);
    }

    /**
     * 透传上游数据，使组件可以放在流程中间。开始/结束标记由 FlowComponent 统一处理。
     */
    private void forward(FlowFile flowFile) {
        if (getOutput() == null || getOutput().isEmpty()) {
            return;
        }
        JSONArray records = flowFile.getJsonArray();
        if (records == null || records.isEmpty()) {
            return;
        }
        FlowFile outFile = new FlowFile();
        outFile.setJsonArray(records);
        for (Map.Entry<String, Object> entry : flowFile.getAttributeMap().entrySet()) {
            outFile.setAttribute(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Object> entry : flowFile.getStatusMap().entrySet()) {
            outFile.setStatus(entry.getKey(), entry.getValue());
        }
        writeRecords(outFile);
    }

    /**
     * 全量检核通过后，从 DuckDB 分批发回下游。
     */
    private void emitFullData(Connection connection, String table) throws SQLException {
        if (getOutput() == null || getOutput().isEmpty()) {
            return;
        }
        String schema = SCHEMA;
        String tableName = table;
        int index = table.lastIndexOf('.');
        if (index > 0) {
            schema = table.substring(0, index);
            tableName = table.substring(index + 1);
        }
        boolean emitted = false;
        try (Statement statement = connection.createStatement()) {
            statement.setFetchSize(5000);
            try (ResultSet rs = statement.executeQuery("SELECT * FROM " + table)) {
                TableMeta tableMeta = DBUtils.getTableMetaFromResultSet(rs.getMetaData());
                tableMeta.setDbType("duckdb");
                tableMeta.setSchema(schema);
                tableMeta.setTable(tableName);
                JSONArray batch = new JSONArray();
                while (rs.next()) {
                    JSONObject record = new JSONObject();
                    for (int i = 1; i <= tableMeta.columns().size(); i++) {
                        record.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
                    }
                    batch.add(record);
                    if (batch.size() >= 5000) {
                        emitted = true;
                        writeFullBatch(batch, tableMeta);
                        batch = new JSONArray();
                    }
                }
                if (!batch.isEmpty()) {
                    emitted = true;
                    writeFullBatch(batch, tableMeta);
                } else if (!emitted) {
                    // 空表也向下游传递表结构，避免下游无法感知表边界。
                    writeFullBatch(new JSONArray(), tableMeta);
                }
            }
        }
        logInfo("全量数据检核通过，已从 DuckDB 下发 " + tableName);
    }

    private void writeFullBatch(JSONArray records, TableMeta tableMeta) {
        FlowFile outFile = new FlowFile();
        outFile.setJsonArray(records);
        for (Map.Entry<String, Object> entry : sourceAttributes.entrySet()) {
            outFile.setAttribute(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Object> entry : sourceStatus.entrySet()) {
            outFile.setStatus(entry.getKey(), entry.getValue());
        }
        // 优先沿用上游原始表信息，避免下游把质量组件的 DuckDB 临时表当成目标表。
        if (outFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA) == null) {
            outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        }
        if (outFile.getAttribute(FlowFile.ATTRIBUTE_TABLE) == null) {
            outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, tableMeta.getTable());
        }
        if (outFile.getAttribute(FlowFile.ATTRIBUTE_DATABASE) == null) {
            outFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, tableMeta.getSchema());
        }
        writeRecords(outFile);
    }

    private List<RuleResult> evaluate(Connection connection, List<CompiledQualityRule> rules, String table) throws SQLException {
        List<RuleResult> results = new ArrayList<>(rules.size());
        for (CompiledQualityRule rule : rules) {
            long checkedRows = 0L;
            long invalidRows = 0L;
            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(rule.getCheckSql())) {
                if (rs.next()) {
                    checkedRows = rs.getLong("checked_rows");
                    invalidRows = rs.getLong("invalid_rows");
                }
            } catch (SQLException e) {
                throw new SQLException("规则[" + rule.getName() + "]执行失败: " + e.getMessage(), e);
            }
            double invalidRatio = checkedRows <= 0 ? 0.0D : (double) invalidRows / (double) checkedRows;
            boolean exceeded = invalidRows > rule.getMaxInvalidRows() || invalidRatio > rule.getMaxInvalidRatio();
            String status;
            if (!exceeded) {
                status = "PASS";
            } else if ("WARN".equalsIgnoreCase(rule.getSeverity())) {
                status = "WARN";
            } else {
                status = "FAIL";
            }
            String message = exceeded
                ? "无效行数 " +
                invalidRows +
                "/" +
                checkedRows +
                "（比例 " +
                String.format(Locale.ROOT, "%.4f", invalidRatio) +
                "），超过阈值 行数=" +
                rule.getMaxInvalidRows() +
                " 比例=" +
                rule.getMaxInvalidRatio()
                : "通过";
            results.add(new RuleResult(rule, checkedRows, invalidRows, invalidRatio, status, message));
            logInfo(
                "规则[" +
                rule.getName() +
                "] 检核完成: checkedRows=" +
                checkedRows +
                ", invalidRows=" +
                invalidRows +
                ", status=" +
                status
            );
        }
        return results;
    }

    private void createSummaryTable(Connection connection) throws SQLException {
        String ddl =
            "CREATE OR REPLACE TABLE " +
            summaryTable() +
            " (" +
            "rule_key VARCHAR, rule_name VARCHAR, rule_level VARCHAR, column_name VARCHAR, rule_type VARCHAR, " +
            "severity VARCHAR, checked_rows BIGINT, invalid_rows BIGINT, invalid_ratio DOUBLE, " +
            "max_invalid_rows BIGINT, max_invalid_ratio DOUBLE, status VARCHAR, message VARCHAR)";
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private void insertSummary(Connection connection, List<RuleResult> results) throws SQLException {
        String sql = "INSERT INTO " + summaryTable() + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (RuleResult result : results) {
                CompiledQualityRule rule = result.rule();
                statement.setString(1, rule.getKey());
                statement.setString(2, rule.getName());
                statement.setString(3, rule.getLevel());
                statement.setString(4, rule.getColumn());
                statement.setString(5, rule.getType());
                statement.setString(6, rule.getSeverity());
                statement.setLong(7, result.checkedRows());
                statement.setLong(8, result.invalidRows());
                statement.setDouble(9, result.invalidRatio());
                statement.setLong(10, rule.getMaxInvalidRows());
                statement.setDouble(11, rule.getMaxInvalidRatio());
                statement.setString(12, result.status());
                statement.setString(13, result.message());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void writeSummaryFile(Connection connection, String suffix) throws SQLException {
        String format = normalizeFormat();
        if ("minio".equalsIgnoreCase(storageType)) {
            setupMinioSecret(connection);
        }
        // 汇总文件始终保留 CSV，便于结果页读取和预览；PARQUET 作为额外副本输出。
        String csvLogicalPath = joinPath(outputDir, instanceCode(), resultPrefix + suffix + "_summary.csv");
        String csvTarget = resolveStorageTarget(csvLogicalPath);
        logInfo("写入数据质量汇总文件: " + csvLogicalPath);
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "COPY (SELECT * FROM " + summaryTable() + ") TO " + literal(csvTarget) + " (FORMAT CSV, HEADER)"
            );
        }
        if ("PARQUET".equals(format)) {
            String parquetLogicalPath = joinPath(outputDir, instanceCode(), resultPrefix + suffix + "_summary.parquet");
            String parquetTarget = resolveStorageTarget(parquetLogicalPath);
            logInfo("写入数据质量汇总 Parquet 文件: " + parquetLogicalPath);
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "COPY (SELECT * FROM " + summaryTable() + ") TO " + literal(parquetTarget) + " (FORMAT PARQUET)"
                );
            }
        }
    }

    private void writeDetailFiles(Connection connection, List<RuleResult> results, String table, String suffix) throws SQLException {
        if (sampleLimit <= 0) {
            return;
        }
        int index = 0;
        for (RuleResult result : results) {
            if ("PASS".equals(result.status()) || result.rule().getSampleCondition() == null) {
                continue;
            }
            index++;
            String logicalPath = joinPath(
                outputDir,
                instanceCode(),
                resultPrefix + suffix + "_detail_" + index + "_" + safeFileName(result.rule().getKey()) + ".csv"
            );
            String target = resolveStorageTarget(logicalPath);
            if ("minio".equalsIgnoreCase(storageType)) {
                setupMinioSecret(connection);
            }
            String sql =
                "COPY (SELECT * FROM " +
                table +
                " WHERE " +
                result.rule().getSampleCondition() +
                " LIMIT " +
                sampleLimit +
                ") TO " +
                literal(target) +
                " (FORMAT CSV, HEADER)";
            logInfo("写入数据质量明细文件: " + logicalPath);
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    private void assertQuality(List<RuleResult> results) {
        List<String> failures = new ArrayList<>();
        for (RuleResult result : results) {
            if ("FAIL".equals(result.status()) && failOnError) {
                failures.add(result.rule().getName() + "（" + result.message() + "）");
            } else if ("WARN".equals(result.status()) && failOnWarn) {
                failures.add(result.rule().getName() + "（" + result.message() + "）");
            }
        }
        if (!failures.isEmpty()) {
            throw new RuntimeException("数据质量检核未通过: " + String.join("; ", failures));
        }
    }

    private void logSummary(String scope, List<RuleResult> results) {
        long pass = results.stream().filter(r -> "PASS".equals(r.status())).count();
        long warn = results.stream().filter(r -> "WARN".equals(r.status())).count();
        long fail = results.stream().filter(r -> "FAIL".equals(r.status())).count();
        logInfo(scope + " 数据质量检核完成: 规则数=" + results.size() + ", PASS=" + pass + ", WARN=" + warn + ", FAIL=" + fail);
    }

    private String resolveSourceTable() {
        String prefix = isFullMode() ? "dq_full_" : "dq_stream_";
        return SCHEMA + "." + prefix + safeFileName(getId());
    }

    private String summaryTable() {
        return SUMMARY_TABLE_PREFIX + safeFileName(getId());
    }

    private Connection duckDBConnection() throws SQLException {
        String instanceCode = getContext() == null ? null : getContext().getJobInstanceCode();
        return DuckDBEngine.getInstance().getConnection(instanceCode);
    }

    private String instanceCode() {
        String instanceCode = getContext() == null ? null : getContext().getJobInstanceCode();
        return instanceCode == null || instanceCode.isBlank() ? "manual" : instanceCode;
    }

    private String normalizeFormat() {
        String format = outputFormat == null ? "CSV" : outputFormat.trim().toUpperCase(Locale.ROOT);
        return "PARQUET".equals(format) ? "PARQUET" : "CSV";
    }

    private String resolveStorageTarget(String logicalPath) {
        String path = joinPath(tenantPrefix, logicalPath);
        if ("minio".equalsIgnoreCase(storageType)) {
            return "s3://" + trimSlashes(minioBucketName) + "/" + path;
        }
        Path base = Paths.get(localBasePath == null || localBasePath.isBlank() ? "./data/files" : localBasePath);
        Path target = base.resolve(path).normalize().toAbsolutePath();
        try {
            Files.createDirectories(target.getParent());
        } catch (Exception e) {
            throw new RuntimeException("创建数据质量结果目录失败: " + target.getParent(), e);
        }
        return target.toString();
    }

    private void setupMinioSecret(Connection connection) throws SQLException {
        if (minioEndpoint == null || minioEndpoint.isBlank()) {
            throw new IllegalArgumentException("MinIO 未配置 minio.endpoint");
        }
        String endpoint = minioEndpoint.replaceFirst("^https?://", "");
        String urlStyle = minioUrlStyle == null || minioUrlStyle.isBlank() ? "path" : minioUrlStyle;
        String sql =
            "CREATE OR REPLACE SECRET dq_s3_" +
            safeFileName(getId()) +
            " (" +
            "TYPE s3, " +
            "KEY_ID " +
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

    private static String joinPath(String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
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

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "rule";
        }
        return value.replaceAll("[^\\p{L}\\p{N}_-]", "_");
    }

    private static String literal(String value) {
        return "'" + (value == null ? "" : value.replace("'", "''")) + "'";
    }

    private record RuleResult(
        CompiledQualityRule rule,
        long checkedRows,
        long invalidRows,
        double invalidRatio,
        String status,
        String message
    ) {}

    public String getCheckMode() {
        return checkMode;
    }

    public void setCheckMode(String checkMode) {
        this.checkMode = checkMode;
    }

    public Object getRules() {
        return rules;
    }

    public void setRules(Object rules) {
        this.rules = rules;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public boolean isFailOnWarn() {
        return failOnWarn;
    }

    public void setFailOnWarn(boolean failOnWarn) {
        this.failOnWarn = failOnWarn;
    }

    public boolean isWriteResult() {
        return writeResult;
    }

    public void setWriteResult(boolean writeResult) {
        this.writeResult = writeResult;
    }

    public int getSampleLimit() {
        return sampleLimit;
    }

    public void setSampleLimit(int sampleLimit) {
        this.sampleLimit = sampleLimit;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getResultPrefix() {
        return resultPrefix;
    }

    public void setResultPrefix(String resultPrefix) {
        this.resultPrefix = resultPrefix;
    }

    public String getTenantPrefix() {
        return tenantPrefix;
    }

    public void setTenantPrefix(String tenantPrefix) {
        this.tenantPrefix = tenantPrefix;
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
}
