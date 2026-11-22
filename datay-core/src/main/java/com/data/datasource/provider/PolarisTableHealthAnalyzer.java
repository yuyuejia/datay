package com.data.datasource.provider;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Apache Polaris（Iceberg）表健康分析。
 *
 * <p>基于 DuckDB 的 {@code iceberg_snapshots} 与 {@code read_avro} 读取当前快照的
 * manifest list / manifest，统计数据文件、小文件、删除文件、分区、Manifest 与元数据文件大小，
 * 并给出健康分与治理建议。全部统计在同一个 DuckDB 连接内完成，不额外依赖 Iceberg Java 客户端。</p>
 */
public final class PolarisTableHealthAnalyzer {

    /** 小文件判定阈值，默认 128 MB。 */
    private static final long SMALL_FILE_THRESHOLD_BYTES = 128L * 1024 * 1024;
    /** 返回给前端的数据文件样本上限（按文件大小升序，便于定位小文件）。 */
    private static final int FILE_SAMPLE_LIMIT = 200;
    /** 返回给前端的 manifest 明细上限。 */
    private static final int MANIFEST_LIMIT = 200;
    /** 返回给前端的快照明细上限。 */
    private static final int SNAPSHOT_LIMIT = 20;
    /** 单次 read_avro 读取的 manifest 数量，避免 SQL 过长。 */
    private static final int MANIFEST_BATCH_SIZE = 64;
    /** 文件大小格式化阈值。 */
    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;
    /** Polaris catalog 在 DuckDB 中的固定名称。 */
    private static final String POLARIS_CATALOG = "polaris";

    private static final Pattern METADATA_VERSION_PATTERN = Pattern.compile("(?:^|/)([0-9]+)-[^/]*\\.metadata\\.json$");

    public static final PolarisTableHealthAnalyzer INSTANCE = new PolarisTableHealthAnalyzer();

    private PolarisTableHealthAnalyzer() {}

    public Map<String, Object> analyze(Connection conn, String catalog, String schema, String table) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supported", true);
        result.put("format", "ICEBERG");
        result.put("catalog", catalog);
        result.put("schema", schema);
        result.put("table", table);
        result.put("smallFileThresholdBytes", SMALL_FILE_THRESHOLD_BYTES);
        result.put("generatedAt", System.currentTimeMillis());

        try {
            ensureAvroExtension(conn);
        } catch (SQLException e) {
            result.put("supported", false);
            result.put("message", e.getMessage());
            return result;
        }

        String tableRef = (POLARIS_CATALOG + "." + quoteIdentifier(schema) + "." + quoteIdentifier(table));
        List<Map<String, Object>> snapshotRows;
        try {
            snapshotRows = query(
                conn,
                "SELECT sequence_number, snapshot_id, timestamp_ms, manifest_list FROM iceberg_snapshots(" +
                quoteLiteral(tableRef) +
                ") ORDER BY sequence_number DESC"
            );
        } catch (SQLException e) {
            result.put("supported", false);
            result.put("message", "无法读取 Iceberg 表元数据，当前表可能不是 Iceberg 表：" + e.getMessage());
            return result;
        }

        String manifestList = snapshotRows.isEmpty() ? null : stringValue(snapshotRows.get(0).get("manifest_list"));
        String location = resolveTableLocation(manifestList);
        result.put("location", location);
        result.put("snapshotCount", snapshotRows.size());

        long manifestListSize = 0L;
        List<Map<String, Object>> manifestRows = new ArrayList<>();
        long manifestSize = 0L;
        int dataManifests = 0;
        int deleteManifests = 0;
        List<String> manifestPaths = new ArrayList<>();
        List<Map<String, Object>> manifestInfos = new ArrayList<>();
        FileStats fileStats;
        try {
            if (manifestList != null && !manifestList.isBlank()) {
                manifestListSize = readObjectSize(conn, manifestList);
                manifestRows = readManifests(conn, manifestList);
            }
            for (Map<String, Object> row : manifestRows) {
                String path = stringValue(row.get("manifest_path"));
                long length = longValue(row.get("manifest_length"));
                long content = longValue(row.get("content"));
                manifestSize += length;
                if (content == 1L) {
                    deleteManifests++;
                } else {
                    dataManifests++;
                }
                if (path != null) {
                    manifestPaths.add(path);
                }
                if (manifestInfos.size() < MANIFEST_LIMIT) {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("path", path);
                    info.put("contentType", content == 1L ? "DELETES" : "DATA");
                    info.put("sizeBytes", length);
                    info.put("addedFiles", longValue(row.get("added_files_count")));
                    info.put("existingFiles", longValue(row.get("existing_files_count")));
                    info.put("deletedFiles", longValue(row.get("deleted_files_count")));
                    info.put("addedRows", longValue(row.get("added_rows_count")));
                    info.put("existingRows", longValue(row.get("existing_rows_count")));
                    info.put("deletedRows", longValue(row.get("deleted_rows_count")));
                    manifestInfos.add(info);
                }
            }
            fileStats = collectFileStats(conn, manifestPaths);
        } catch (SQLException e) {
            result.put("supported", false);
            result.put("message", "读取 Iceberg 文件清单失败：" + e.getMessage());
            return result;
        }
        MetadataStats metadataStats = readMetadataStats(conn, location);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dataFiles", fileStats.dataFiles);
        summary.put("deleteFiles", fileStats.deleteFiles);
        summary.put("totalFiles", fileStats.dataFiles + fileStats.deleteFiles);
        summary.put("smallFiles", fileStats.smallFiles);
        summary.put("smallFileThresholdBytes", SMALL_FILE_THRESHOLD_BYTES);
        summary.put("dataSizeBytes", fileStats.dataSizeBytes);
        summary.put("deleteSizeBytes", fileStats.deleteSizeBytes);
        summary.put("totalSizeBytes", fileStats.dataSizeBytes + fileStats.deleteSizeBytes);
        summary.put("avgFileSizeBytes", fileStats.dataFiles == 0 ? 0L : fileStats.dataSizeBytes / fileStats.dataFiles);
        summary.put("totalRecords", fileStats.totalRecords);
        summary.put("partitionCount", fileStats.partitions.size());
        summary.put("snapshotCount", snapshotRows.size());
        summary.put("dataManifests", dataManifests);
        summary.put("deleteManifests", deleteManifests);
        summary.put("manifestFileCount", manifestRows.size());
        summary.put("manifestSizeBytes", manifestSize);
        summary.put("manifestListSizeBytes", manifestListSize);
        summary.put("metadataFileCount", metadataStats.fileCount);
        summary.put("metadataSizeBytes", metadataStats.totalSizeBytes);
        summary.put("currentMetadataSizeBytes", metadataStats.currentSizeBytes);

        List<String> warnings = new ArrayList<>();
        int healthScore = computeHealthScore(summary, fileStats, warnings);
        String status = healthScore >= 85 ? "HEALTHY" : healthScore >= 60 ? "WARNING" : "CRITICAL";

        Map<String, Object> partitioning = new LinkedHashMap<>();
        partitioning.put("partitioned", !metadataStats.partitionFields.isEmpty());
        partitioning.put("defaultSpecId", metadataStats.defaultSpecId);
        partitioning.put("fields", metadataStats.partitionFields);
        partitioning.put("partitionCount", fileStats.partitions.size());

        result.put("status", status);
        result.put("healthScore", healthScore);
        result.put("warnings", warnings);
        result.put("summary", summary);
        result.put("partitioning", partitioning);
        result.put("lastUpdatedMs", metadataStats.lastUpdatedMs);
        result.put("lastOperation", metadataStats.lastOperation);
        result.put("manifests", manifestInfos);
        result.put("manifestsTruncated", manifestRows.size() > manifestInfos.size());
        result.put("files", fileStats.samples);
        result.put("filesTruncated", fileStats.dataFiles + fileStats.deleteFiles > fileStats.samples.size());
        result.put("snapshots", buildSnapshotInfos(snapshotRows, metadataStats));
        return result;
    }

    // ------------------------------------------------------------------ 统计实现

    private static FileStats collectFileStats(Connection conn, List<String> manifestPaths) throws SQLException {
        FileStats stats = new FileStats();
        for (int offset = 0; offset < manifestPaths.size(); offset += MANIFEST_BATCH_SIZE) {
            List<String> batch = manifestPaths.subList(offset, Math.min(offset + MANIFEST_BATCH_SIZE, manifestPaths.size()));
            String sql =
                "SELECT status, data_file.content AS content, data_file.file_path AS path, " +
                "data_file.file_format AS format, data_file.file_size_in_bytes AS size_bytes, " +
                "data_file.record_count AS record_count, CAST(data_file.partition AS VARCHAR) AS partition " +
                "FROM read_avro(" +
                stringListLiteral(batch) +
                ")";
            try (
                Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(sql)
            ) {
                while (rs.next()) {
                    long status = rs.getLong("status");
                    // 2 = DELETED，当前快照不再存活
                    if (status == 2L) {
                        continue;
                    }
                    long content = rs.getLong("content");
                    long size = rs.getLong("size_bytes");
                    long records = rs.getLong("record_count");
                    String path = rs.getString("path");
                    String format = rs.getString("format");
                    String partition = rs.getString("partition");
                    if (content == 0L) {
                        stats.dataFiles++;
                        stats.dataSizeBytes += size;
                        stats.totalRecords += records;
                        if (size < SMALL_FILE_THRESHOLD_BYTES) {
                            stats.smallFiles++;
                        }
                        if (partition != null && !partition.isBlank()) {
                            stats.partitions.add(partition);
                        }
                    } else {
                        stats.deleteFiles++;
                        stats.deleteSizeBytes += size;
                    }
                    stats.offerSample(path, content, format, size, records, partition);
                }
            }
        }
        List<FileSample> samples = new ArrayList<>(stats.sampleQueue);
        samples.sort(Comparator.comparingLong(sample -> sample.sizeBytes));
        for (FileSample sample : samples) {
            stats.samples.add(sample.toMap());
        }
        return stats;
    }

    private static List<Map<String, Object>> readManifests(Connection conn, String manifestList) throws SQLException {
        return query(
            conn,
            "SELECT manifest_path, manifest_length, content, added_files_count, existing_files_count, " +
            "deleted_files_count, added_rows_count, existing_rows_count, deleted_rows_count FROM read_avro(" +
            quoteLiteral(manifestList) +
            ")"
        );
    }

    private static long readObjectSize(Connection conn, String path) {
        try {
            List<Map<String, Object>> rows = query(conn, "SELECT size FROM read_blob(" + quoteLiteral(path) + ")");
            return rows.isEmpty() ? 0L : longValue(rows.get(0).get("size"));
        } catch (SQLException e) {
            return 0L;
        }
    }

    private static MetadataStats readMetadataStats(Connection conn, String location) {
        MetadataStats stats = new MetadataStats();
        if (location == null || location.isBlank()) {
            return stats;
        }
        List<Map<String, Object>> rows;
        try {
            rows = query(
                conn,
                "SELECT filename, size, content FROM read_text(" + quoteLiteral(location + "/metadata/*.metadata.json") + ")"
            );
        } catch (SQLException e) {
            return stats;
        }

        long bestVersion = -1L;
        for (Map<String, Object> row : rows) {
            String filename = stringValue(row.get("filename"));
            long size = longValue(row.get("size"));
            stats.fileCount++;
            stats.totalSizeBytes += size;
            long version = metadataVersion(filename);
            if (version > bestVersion || (version == bestVersion && size > stats.currentSizeBytes)) {
                JSONObject metadata = parseObject(stringValue(row.get("content")));
                if (metadata != null) {
                    bestVersion = version;
                    stats.currentSizeBytes = size;
                    stats.metadata = metadata;
                }
            }
        }

        JSONObject metadata = stats.metadata;
        if (metadata == null) {
            return stats;
        }
        stats.lastUpdatedMs = metadata.getLong("last-updated-ms");
        stats.defaultSpecId = metadata.getIntValue("default-spec-id", 0);
        stats.partitionFields = readPartitionFields(metadata);
        JSONArray snapshots = metadata.getJSONArray("snapshots");
        long currentSnapshotId = metadata.getLongValue("current-snapshot-id", -1L);
        if (snapshots != null) {
            for (int i = 0; i < snapshots.size(); i++) {
                JSONObject snapshot = snapshots.getJSONObject(i);
                if (snapshot != null && snapshot.getLongValue("snapshot-id", Long.MIN_VALUE) == currentSnapshotId) {
                    JSONObject snapshotSummary = snapshot.getJSONObject("summary");
                    if (snapshotSummary != null) {
                        stats.lastOperation = snapshotSummary.getString("operation");
                    }
                    if (stats.lastUpdatedMs == null) {
                        stats.lastUpdatedMs = snapshot.getLong("timestamp-ms");
                    }
                    break;
                }
            }
        }
        return stats;
    }

    private static List<Map<String, Object>> readPartitionFields(JSONObject metadata) {
        List<Map<String, Object>> fields = new ArrayList<>();
        JSONArray specs = metadata.getJSONArray("partition-specs");
        if (specs == null) {
            return fields;
        }
        for (int i = 0; i < specs.size(); i++) {
            JSONObject spec = specs.getJSONObject(i);
            if (spec == null) {
                continue;
            }
            int specId = spec.getIntValue("spec-id", i);
            JSONArray specFields = spec.getJSONArray("fields");
            if (specFields == null) {
                continue;
            }
            for (int j = 0; j < specFields.size(); j++) {
                JSONObject field = specFields.getJSONObject(j);
                if (field == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("specId", specId);
                item.put("name", field.getString("name"));
                item.put("transform", field.getString("transform"));
                item.put("sourceId", field.getIntValue("source-id", -1));
                fields.add(item);
            }
        }
        return fields;
    }

    private static List<Map<String, Object>> buildSnapshotInfos(
        List<Map<String, Object>> snapshotRows,
        MetadataStats metadataStats
    ) {
        List<Map<String, Object>> infos = new ArrayList<>();
        Map<Long, JSONObject> metadataSnapshots = new LinkedHashMap<>();
        JSONArray snapshots = metadataStats.metadata == null ? null : metadataStats.metadata.getJSONArray("snapshots");
        if (snapshots != null) {
            for (int i = 0; i < snapshots.size(); i++) {
                JSONObject snapshot = snapshots.getJSONObject(i);
                if (snapshot != null) {
                    metadataSnapshots.put(snapshot.getLongValue("snapshot-id", Long.MIN_VALUE), snapshot);
                }
            }
        }
        for (Map<String, Object> row : snapshotRows) {
            if (infos.size() >= SNAPSHOT_LIMIT) {
                break;
            }
            long snapshotId = longValue(row.get("snapshot_id"));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("snapshotId", snapshotId);
            info.put("sequenceNumber", longValue(row.get("sequence_number")));
            info.put("manifestList", stringValue(row.get("manifest_list")));
            JSONObject snapshot = metadataSnapshots.get(snapshotId);
            if (snapshot != null) {
                info.put("timestampMs", snapshot.getLong("timestamp-ms"));
                JSONObject summary = snapshot.getJSONObject("summary");
                if (summary != null) {
                    info.put("operation", summary.getString("operation"));
                    info.put("totalRecords", parseLong(summary.getString("total-records")));
                    info.put("totalDataFiles", parseLong(summary.getString("total-data-files")));
                    info.put("totalDeleteFiles", parseLong(summary.getString("total-delete-files")));
                }
            }
            infos.add(info);
        }
        return infos;
    }

    private static int computeHealthScore(Map<String, Object> summary, FileStats files, List<String> warnings) {
        int score = 100;
        long dataFiles = files.dataFiles;
        if (dataFiles > 0) {
            double smallRatio = files.smallFiles * 1.0 / dataFiles;
            score -= (int) Math.round(smallRatio * 35);
            if (smallRatio >= 0.3) {
                warnings.add(
                    String.format(
                        "存在 %d 个小文件（占数据文件 %.0f%%），建议执行 compaction 合并小文件。",
                        files.smallFiles,
                        smallRatio * 100
                    )
                );
            }
            long avgFileSize = files.dataSizeBytes / dataFiles;
            if (avgFileSize < 64 * MB) {
                score -= avgFileSize < 16 * MB ? 12 : 6;
                warnings.add("数据文件平均大小 " + formatBytes(avgFileSize) + "，低于推荐的 128 MB。");
            }
        }
        if (files.deleteFiles > 0) {
            double deleteRatio = files.deleteFiles * 1.0 / Math.max(1L, dataFiles);
            score -= Math.min(25, 5 + (int) Math.round(deleteRatio * 20));
            warnings.add(
                "存在 " + files.deleteFiles + " 个删除文件（" + formatBytes(files.deleteSizeBytes) + "），建议执行 rewrite/expire 清理。"
            );
        }
        long manifestCount = longValue(summary.get("manifestFileCount"));
        if (manifestCount > 200) {
            score -= 8;
            warnings.add("Manifest 文件数量较多（" + manifestCount + "），建议执行 rewrite manifests。");
        }
        long metadataFileCount = longValue(summary.get("metadataFileCount"));
        if (metadataFileCount > 500) {
            score -= 5;
            warnings.add("元数据版本文件较多（" + metadataFileCount + "），建议执行 expire snapshots 并清理旧元数据。");
        }
        long snapshotCount = longValue(summary.get("snapshotCount"));
        if (snapshotCount > 1000) {
            score -= 5;
            warnings.add("快照数量较多（" + snapshotCount + "），建议配置快照过期策略。");
        }
        long partitionCount = longValue(summary.get("partitionCount"));
        if (partitionCount > 1000) {
            score -= 5;
            warnings.add("分区数较多（" + partitionCount + "），注意分区粒度，避免过细分区产生大量小文件。");
        }
        return Math.max(0, Math.min(100, score));
    }

    // ------------------------------------------------------------------ SQL 辅助

    private static void ensureAvroExtension(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            try {
                statement.execute("INSTALL avro");
            } catch (SQLException ignored) {
                // 已安装或离线环境，继续尝试 LOAD
            }
            try {
                statement.execute("LOAD avro");
            } catch (SQLException e) {
                throw new SQLException("无法加载 DuckDB avro 扩展，表健康监控需要读取 Iceberg manifest: " + e.getMessage(), e);
            }
        }
    }

    private static List<Map<String, Object>> query(Connection conn, String sql) throws SQLException {
        try (
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery(sql)
        ) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private static String resolveTableLocation(String manifestList) {
        if (manifestList == null) {
            return null;
        }
        int index = manifestList.lastIndexOf("/metadata/");
        return index > 0 ? manifestList.substring(0, index) : null;
    }

    private static long metadataVersion(String filename) {
        if (filename == null) {
            return -1L;
        }
        Matcher matcher = METADATA_VERSION_PATTERN.matcher(filename);
        if (!matcher.find()) {
            return -1L;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static JSONObject parseObject(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String quoteIdentifier(String value) {
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }

    private static String quoteLiteral(String value) {
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }

    private static String stringListLiteral(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(quoteLiteral(values.get(i)));
        }
        return builder.append(']').toString();
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < KB) {
            return bytes + " B";
        }
        if (bytes < MB) {
            return String.format("%.1f KB", bytes / (double) KB);
        }
        if (bytes < GB) {
            return String.format("%.1f MB", bytes / (double) MB);
        }
        return String.format("%.2f GB", bytes / (double) GB);
    }

    // ------------------------------------------------------------------ 内部模型

    private static final class FileStats {

        private long dataFiles;
        private long deleteFiles;
        private long smallFiles;
        private long dataSizeBytes;
        private long deleteSizeBytes;
        private long totalRecords;
        private final Set<String> partitions = new HashSet<>();
        private final PriorityQueue<FileSample> sampleQueue = new PriorityQueue<>(
            FILE_SAMPLE_LIMIT,
            Comparator.comparingLong((FileSample sample) -> sample.sizeBytes).reversed()
        );
        private final List<Map<String, Object>> samples = new ArrayList<>();

        private void offerSample(String path, long content, String format, long size, long records, String partition) {
            FileSample sample = new FileSample(path, content, format, size, records, partition);
            if (sampleQueue.size() < FILE_SAMPLE_LIMIT) {
                sampleQueue.offer(sample);
            } else if (size < sampleQueue.peek().sizeBytes) {
                sampleQueue.poll();
                sampleQueue.offer(sample);
            }
        }
    }

    private static final class FileSample {

        private final String path;
        private final long content;
        private final String format;
        private final long sizeBytes;
        private final long recordCount;
        private final String partition;

        private FileSample(String path, long content, String format, long sizeBytes, long recordCount, String partition) {
            this.path = path;
            this.content = content;
            this.format = format;
            this.sizeBytes = sizeBytes;
            this.recordCount = recordCount;
            this.partition = partition;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("path", path);
            map.put("contentType", content == 0L ? "DATA" : content == 1L ? "POSITION_DELETES" : "EQUALITY_DELETES");
            map.put("format", format);
            map.put("sizeBytes", sizeBytes);
            map.put("recordCount", recordCount);
            map.put("partition", partition);
            return map;
        }
    }

    private static final class MetadataStats {

        private long fileCount;
        private long totalSizeBytes;
        private long currentSizeBytes;
        private Long lastUpdatedMs;
        private String lastOperation;
        private int defaultSpecId;
        private List<Map<String, Object>> partitionFields = new ArrayList<>();
        private JSONObject metadata;
    }
}
