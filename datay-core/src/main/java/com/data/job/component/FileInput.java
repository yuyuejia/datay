package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DuckDBEngine;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * FileInput 组件 - 从文件管理中读取文件（csv / excel / parquet），
 * 通过 DuckDB 流式查询，将结果批量输出到下游。
 * <p>支持两种存储后端：
 * <ul>
 *   <li>local  - 直接使用本地绝对路径，DuckDB 原生 read_csv / read_parquet / read_excel 读取</li>
 *   <li>minio  - 构造 s3:// URL，并在 DuckDB 连接上 CREATE SECRET 注入 MinIO 凭证</li>
 * </ul>
 */
@ComponentRegister("FileInput")
public class FileInput extends FlowComponent {

    private static final int FETCH_SIZE = 10000;

    private String storageType;

    private String filePath;

    private String format;

    private String localBasePath;

    private String minioEndpoint;

    private String minioAccessKey;

    private String minioSecretKey;

    private String minioBucketName;

    private String minioUrlStyle;

    private String csvDelimiter;

    private boolean csvHasHeader = true;

    private String excelSheet;

    private String query;

    public FileInput() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("FileInput 未配置 filePath");
        }

        try (Connection duckdbConn = DuckDBEngine.getInstance().getConnection(getContext().getJobInstanceCode())) {
            ensureDuckDBExtension(duckdbConn);

            String resolvedPath = resolveFileUrl();
            String fileFormat = resolveFormat();
            String readFunc = buildReadFunction(resolvedPath, fileFormat);
            String querySql = (query != null && !query.trim().isEmpty())
                ? query.trim()
                : "SELECT * FROM " + readFunc;

            String displayName = extractDisplayName(filePath);
            logInfo("FileInput 读取文件: storageType=" + storageType + ", file=" + displayName + ", format=" + fileFormat);

            if ("minio".equalsIgnoreCase(storageType)) {
                setupMinioSecret(duckdbConn);
            }

            try (Statement stmt = duckdbConn.createStatement()) {
                stmt.setFetchSize(FETCH_SIZE);
                try (ResultSet rs = stmt.executeQuery(querySql)) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    TableMeta tableMeta = DBUtils.getTableMetaFromResultSet(metaData);
                    tableMeta.setTable(extractTableName(filePath));
                    tableMeta.setDbType("duckdb");

                    int batchCount = 0;
                    List<JSONObject> batchRecords = new ArrayList<>();
                    int total = 0;

                    while (rs.next()) {
                        JSONObject jsonRecord = new JSONObject();
                        int colCount = metaData.getColumnCount();
                        for (int i = 1; i <= colCount; i++) {
                            jsonRecord.put(metaData.getColumnName(i), rs.getObject(i));
                        }
                        batchRecords.add(jsonRecord);
                        batchCount++;
                        total++;

                        if (batchCount >= FETCH_SIZE) {
                            sendBatch(batchRecords, tableMeta);
                            batchRecords.clear();
                            batchCount = 0;
                            logInfo("FileInput 已读取 " + total + " 条记录");
                        }

                        if (getContext() != null && getContext().isDebugMode() && total >= getContext().getDebugRowLimit()) {
                            logInfo("调试模式：FileInput 达到采样行数上限 " + getContext().getDebugRowLimit());
                            break;
                        }
                    }

                    if (!batchRecords.isEmpty() || total == 0) {
                        sendBatch(batchRecords, tableMeta);
                    }

                    logInfo("FileInput 完成，共读取 " + total + " 条记录");
                }
            }
        } catch (SQLException e) {
            String displayName = extractDisplayName(filePath);
            throw new RuntimeException("FileInput 读取文件失败: " + sanitizeErrorMessage(e.getMessage(), displayName), e);
        } catch (RuntimeException e) {
            String displayName = extractDisplayName(filePath);
            String raw = e.getMessage();
            if (raw != null && (raw.contains("./") || raw.contains("s3://") || raw.matches(".*[A-Za-z]:\\\\.*"))) {
                throw new RuntimeException("FileInput 读取文件失败: " + sanitizeErrorMessage(raw, displayName), e.getCause() != null ? e.getCause() : e);
            }
            throw e;
        }
    }

    private void ensureDuckDBExtension(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            String fmt = resolveFormat();
            if ("excel".equalsIgnoreCase(fmt)) {
                stmt.execute("INSTALL spatial;");
                stmt.execute("LOAD spatial;");
            }
        } catch (SQLException e) {
            logInfo("扩展安装失败（可能已安装）: " + e.getMessage());
        }
    }

    private String resolveFormat() {
        if (format != null && !format.trim().isEmpty()) {
            return format.trim().toLowerCase();
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".parquet")) {
            return "parquet";
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return "excel";
        }
        if (lower.endsWith(".csv")) {
            return "csv";
        }
        return "csv";
    }

    private String resolveFileUrl() {
        String normalized = filePath.trim();
        if ("minio".equalsIgnoreCase(storageType)) {
            String bucket = minioBucketName == null ? "" : minioBucketName.trim();
            String object = normalized;
            while (object.startsWith("/")) {
                object = object.substring(1);
            }
            return "s3://" + bucket + "/" + object;
        }
        if (normalized.startsWith("s3://") || normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        Path combined;
        if (localBasePath != null && !localBasePath.isEmpty()) {
            Path base = Paths.get(localBasePath);
            Path rel = Paths.get(normalized);
            combined = base.resolve(rel).normalize().toAbsolutePath();
        } else {
            combined = Paths.get(normalized).normalize().toAbsolutePath();
        }
        return combined.toString();
    }

    private String buildReadFunction(String resolvedPath, String fileFormat) {
        String escapedPath = resolvedPath.replace("'", "''");
        switch (fileFormat) {
            case "parquet":
                return "read_parquet('" + escapedPath + "')";
            case "excel": {
                StringBuilder sb = new StringBuilder();
                sb.append("read_excel('").append(escapedPath).append("'");
                if (excelSheet != null && !excelSheet.trim().isEmpty()) {
                    sb.append(", sheet = '").append(excelSheet.replace("'", "''")).append("'");
                }
                sb.append(")");
                return sb.toString();
            }
            case "csv":
            default: {
                StringBuilder sb = new StringBuilder();
                sb.append("read_csv('").append(escapedPath).append("'");
                sb.append(", header = ").append(csvHasHeader ? "true" : "false");
                if (csvDelimiter != null && !csvDelimiter.trim().isEmpty()) {
                    sb.append(", delim = '").append(csvDelimiter.replace("'", "''")).append("'");
                }
                sb.append(", auto_detect = true");
                sb.append(")");
                return sb.toString();
            }
        }
    }

    private void setupMinioSecret(Connection conn) throws SQLException {
        if (minioEndpoint == null || minioEndpoint.isEmpty()) {
            throw new IllegalArgumentException("MinIO 未配置 minio.endpoint");
        }
        String endpoint = minioEndpoint.replaceFirst("^https?://", "");
        String urlStyle = (minioUrlStyle != null && !minioUrlStyle.isEmpty()) ? minioUrlStyle : "path";
        String sql = "CREATE OR REPLACE SECRET file_input_s3_secret ("
            + "TYPE s3,"
            + "KEY_ID '" + safe(minioAccessKey) + "',"
            + "SECRET '" + safe(minioSecretKey) + "',"
            + "ENDPOINT '" + safe(endpoint) + "',"
            + "url_style '" + urlStyle + "',"
            + "USE_SSL '" + (minioEndpoint.startsWith("https://") ? "true" : "false") + "'"
            + ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static String extractTableName(String filePath) {
        if (filePath == null) {
            return "file_input";
        }
        String name = filePath;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        if (name.isEmpty()) {
            name = "file_input";
        }
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String extractDisplayName(String filePath) {
        if (filePath == null) {
            return "";
        }
        String name = filePath;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name;
    }

    private static String sanitizeErrorMessage(String raw, String displayName) {
        if (raw == null) {
            return "FileInput 读取文件失败";
        }
        String msg = raw;
        int nl = msg.indexOf('\n');
        if (nl >= 0) {
            msg = msg.substring(0, nl);
        }
        msg = msg.replaceAll("'(?:[^']*[/\\\\][^']*)'", "'<" + displayName + ">");
        msg = msg.replaceAll("(?:s3://|S3://)[^\\s'\"]+", "s3://***");
        if (msg.length() > 240) {
            msg = msg.substring(0, 240) + "...";
        }
        return msg;
    }

    private void sendBatch(List<JSONObject> records, TableMeta tableMeta) {
        JSONArray arr = new JSONArray();
        arr.addAll(records);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(arr);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, tableMeta.getTable());
        flowFile.setAttribute("_filePath", filePath);
        writeRecords(flowFile);
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setLocalBasePath(String localBasePath) {
        this.localBasePath = localBasePath;
    }

    public void setMinioEndpoint(String minioEndpoint) {
        this.minioEndpoint = minioEndpoint;
    }

    public void setMinioAccessKey(String minioAccessKey) {
        this.minioAccessKey = minioAccessKey;
    }

    public void setMinioSecretKey(String minioSecretKey) {
        this.minioSecretKey = minioSecretKey;
    }

    public void setMinioBucketName(String minioBucketName) {
        this.minioBucketName = minioBucketName;
    }

    public void setMinioUrlStyle(String minioUrlStyle) {
        this.minioUrlStyle = minioUrlStyle;
    }

    public void setCsvDelimiter(String csvDelimiter) {
        this.csvDelimiter = csvDelimiter;
    }

    public void setCsvHasHeader(boolean csvHasHeader) {
        this.csvHasHeader = csvHasHeader;
    }

    public void setExcelSheet(String excelSheet) {
        this.excelSheet = excelSheet;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}