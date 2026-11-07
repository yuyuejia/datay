package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.data.job.ComponentRegister;
import com.data.job.DuckDBEngine;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.quality.CompiledQualityRule;
import com.data.job.quality.DataQualityRuleCompiler;
import com.data.job.quality.DuckDBFlowBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据质量检核组件。
 *
 * <p>支持两种检核模式：</p>
 * <ul>
 *   <li>{@code FLOW}：消费上游 FlowFile，持续写入 DuckDB 临时表，所有数据到达后执行规则；</li>
 *   <li>{@code TABLE}：直接检核指定的 DuckDB 表。</li>
 * </ul>
 *
 * <p>检核结果写入文件存储（本地 / MinIO）。存在质量不达标的 ERROR 规则时抛出异常，
 * 由 ETL 引擎将任务标记为失败。</p>
 */
@ComponentRegister(
    value = "DataQualityCheck",
    name = "数据质量检核",
    group = "数据质量",
    desc = "按表级/字段级规则检核上游数据或指定 DuckDB 表，结果写入文件存储，质量不达标时任务失败。",
    order = 10
)
public class DataQualityCheck extends FlowComponent {

    private static final String SCHEMA = "main";

    private static final String SUMMARY_TABLE_PREFIX = "dq_summary_";

    private static final String DEFAULT_OUTPUT_DIR = "data-quality/etl";

    private final DataQualityRuleCompiler ruleCompiler = new DataQualityRuleCompiler();

    private volatile boolean checked;

    private DuckDBFlowBuffer flowBuffer;

    private String checkMode = "FLOW";

    private String sourceTable;

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
                runChecks();
            }
            return;
        }

        if (isFlowMode()) {
            materialize(flowFile);
        }
        forward(flowFile);
    }

    private boolean isFlowMode() {
        return checkMode == null || checkMode.trim().isEmpty() || "FLOW".equalsIgnoreCase(checkMode.trim());
    }

    private void materialize(FlowFile flowFile) {
        JSONArray records = flowFile.getJsonArray();
        if (records == null) {
            return;
        }
        if (flowBuffer == null) {
            flowBuffer = new DuckDBFlowBuffer(resolveSourceTable());
        }
        try (Connection connection = duckDBConnection()) {
            flowBuffer.append(connection, flowFile);
        } catch (SQLException e) {
            throw new RuntimeException("数据质量检核写入 DuckDB 失败: " + e.getMessage(), e);
        }
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

    private synchronized void runChecks() {
        if (checked) {
            return;
        }
        checked = true;
        String table = resolveSourceTable();
        List<CompiledQualityRule> compiledRules = ruleCompiler.compile(rules, table);
        try (Connection connection = duckDBConnection()) {
            List<RuleResult> results = evaluate(connection, compiledRules, table);
            createSummaryTable(connection);
            insertSummary(connection, results);
            boolean debug = getContext() != null && getContext().isDebugMode();
            if (!debug && writeResult) {
                writeSummaryFile(connection);
                writeDetailFiles(connection, results, table);
            }
            logSummary(results);
            assertQuality(results);
        } catch (SQLException e) {
            throw new RuntimeException("数据质量检核执行失败: " + e.getMessage(), e);
        }
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

    private void writeSummaryFile(Connection connection) throws SQLException {
        String format = normalizeFormat();
        if ("minio".equalsIgnoreCase(storageType)) {
            setupMinioSecret(connection);
        }
        // 汇总文件始终保留 CSV，便于结果页读取和预览；PARQUET 作为额外副本输出。
        String csvLogicalPath = joinPath(outputDir, instanceCode(), resultPrefix + "_summary.csv");
        String csvTarget = resolveStorageTarget(csvLogicalPath);
        logInfo("写入数据质量汇总文件: " + csvLogicalPath);
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "COPY (SELECT * FROM " + summaryTable() + ") TO " + literal(csvTarget) + " (FORMAT CSV, HEADER)"
            );
        }
        if ("PARQUET".equals(format)) {
            String parquetLogicalPath = joinPath(outputDir, instanceCode(), resultPrefix + "_summary.parquet");
            String parquetTarget = resolveStorageTarget(parquetLogicalPath);
            logInfo("写入数据质量汇总 Parquet 文件: " + parquetLogicalPath);
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "COPY (SELECT * FROM " + summaryTable() + ") TO " + literal(parquetTarget) + " (FORMAT PARQUET)"
                );
            }
        }
    }

    private void writeDetailFiles(Connection connection, List<RuleResult> results, String table) throws SQLException {
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
                resultPrefix + "_detail_" + index + "_" + safeFileName(result.rule().getKey()) + ".csv"
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

    private void logSummary(List<RuleResult> results) {
        long pass = results.stream().filter(r -> "PASS".equals(r.status())).count();
        long warn = results.stream().filter(r -> "WARN".equals(r.status())).count();
        long fail = results.stream().filter(r -> "FAIL".equals(r.status())).count();
        logInfo("数据质量检核完成: 规则数=" + results.size() + ", PASS=" + pass + ", WARN=" + warn + ", FAIL=" + fail);
    }

    private String resolveSourceTable() {
        if (sourceTable == null || sourceTable.trim().isEmpty()) {
            return SCHEMA + ".dq_data_" + safeFileName(getId());
        }
        return sourceTable.trim();
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

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
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
