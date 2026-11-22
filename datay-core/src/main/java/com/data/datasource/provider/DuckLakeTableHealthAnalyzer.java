package com.data.datasource.provider;

import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.regex.Pattern;

/**
 * DuckLake 表健康分析。
 *
 * <p>直接读取 DuckLake 的元数据表（{@code __ducklake_metadata_<catalog>}）与
 * {@code ducklake_table_info} / {@code ducklake_settings} 等扩展函数，统计物理文件、
 * 删除文件、内联数据、分区、快照及元数据 Catalog 大小。</p>
 */
public final class DuckLakeTableHealthAnalyzer {

    /** 小文件判定阈值，默认 128 MB。 */
    private static final long SMALL_FILE_THRESHOLD_BYTES = 128L * 1024 * 1024;
    /** 返回给前端的数据文件样本上限。 */
    private static final int FILE_SAMPLE_LIMIT = 200;
    /** 返回给前端的快照明细上限。 */
    private static final int SNAPSHOT_LIMIT = 20;
    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;
    private static final String DUCKLAKE_CATALOG = "ducklake";
    private static final String METADATA_CATALOG = "__ducklake_metadata_ducklake";
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("([a-z])([A-Z])");

    public static final DuckLakeTableHealthAnalyzer INSTANCE = new DuckLakeTableHealthAnalyzer();

    private DuckLakeTableHealthAnalyzer() {}

    public Map<String, Object> analyze(Connection conn, String catalog, String schema, String table) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supported", true);
        result.put("format", "DUCKLAKE");
        result.put("catalog", catalog);
        result.put("schema", schema);
        result.put("table", table);
        result.put("smallFileThresholdBytes", SMALL_FILE_THRESHOLD_BYTES);
        result.put("generatedAt", System.currentTimeMillis());

        TableRef ref = resolveTable(conn, schema, table);
        if (ref == null) {
            result.put("supported", false);
            result.put("message", "未找到 DuckLake 表元数据：" + schema + "." + table);
            return result;
        }

        CatalogSettings settings = readCatalogSettings(conn, catalog);
        result.put("catalogType", settings.catalogType);
        result.put("metadataVersion", settings.metadataVersion);
        result.put("dataPath", settings.dataPath);
        ref.location = joinPath(settings.dataPath, ref.schemaPath, ref.tablePath);
        result.put("location", ref.location);

        TableInfo tableInfo = readTableInfo(conn, catalog, ref.tableId);
        List<DataFile> dataFiles = readDataFiles(conn, ref);
        List<DeleteFile> deleteFiles = readDeleteFiles(conn, ref);
        if (tableInfo == null) {
            tableInfo = TableInfo.from(dataFiles, deleteFiles);
        }

        PartitionInfo partitionInfo = readPartitionInfo(conn, ref.tableId);
        Map<Long, String> filePartitions = readFilePartitions(conn, ref.tableId, partitionInfo.fieldNames);
        InlinedStats inlined = readInlinedStats(conn, ref.tableId);
        SnapshotStats snapshots = readSnapshots(conn, ref, schema, table);
        MetadataSize metadataSize = readMetadataSize(conn);

        long dataFileCount = tableInfo.fileCount;
        long deleteFileCount = tableInfo.deleteFileCount;
        long dataSize = tableInfo.fileSizeBytes;
        long deleteSize = tableInfo.deleteFileSizeBytes;
        long dataRecords = dataFiles.stream().mapToLong(file -> file.recordCount).sum();
        long deleteRecords = deleteFiles.stream().mapToLong(file -> file.deleteCount).sum();
        long totalRecords = Math.max(0L, dataRecords - deleteRecords - inlined.deleteRows + inlined.dataRows);

        Set<String> partitions = new HashSet<>();
        for (DataFile file : dataFiles) {
            String partition = filePartitions.get(file.dataFileId);
            if (partition != null && !partition.isBlank()) {
                partitions.add(partition);
            }
        }

        List<Map<String, Object>> fileSamples = buildFileSamples(conn, ref, dataFiles, deleteFiles, filePartitions);
        long totalFiles = dataFileCount + deleteFileCount;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dataFiles", dataFileCount);
        summary.put("deleteFiles", deleteFileCount);
        summary.put("totalFiles", totalFiles);
        summary.put("smallFiles", countSmallFiles(dataFiles));
        summary.put("smallFileThresholdBytes", SMALL_FILE_THRESHOLD_BYTES);
        summary.put("dataSizeBytes", dataSize);
        summary.put("deleteSizeBytes", deleteSize);
        summary.put("totalSizeBytes", dataSize + deleteSize);
        summary.put("avgFileSizeBytes", dataFileCount == 0 ? 0L : dataSize / dataFileCount);
        summary.put("totalRecords", totalRecords);
        summary.put("partitionCount", partitions.size());
        summary.put("snapshotCount", snapshots.count);
        summary.put("dataManifests", 0);
        summary.put("deleteManifests", 0);
        summary.put("manifestFileCount", 0);
        summary.put("manifestSizeBytes", 0L);
        summary.put("manifestListSizeBytes", 0L);
        summary.put("metadataFileCount", metadataSize.fileCount);
        summary.put("metadataSizeBytes", metadataSize.totalBytes);
        summary.put("currentMetadataSizeBytes", metadataSize.totalBytes);
        summary.put("inlinedRows", inlined.dataRows);
        summary.put("inlinedDeleteRows", inlined.deleteRows);

        List<String> warnings = new ArrayList<>();
        int healthScore = computeHealthScore(dataFiles, deleteFiles, inlined, partitions.size(), snapshots.count, warnings);
        String status = healthScore >= 85 ? "HEALTHY" : healthScore >= 60 ? "WARNING" : "CRITICAL";

        Map<String, Object> partitioning = new LinkedHashMap<>();
        partitioning.put("partitioned", !partitionInfo.fields.isEmpty());
        partitioning.put("defaultSpecId", partitionInfo.partitionId);
        partitioning.put("fields", partitionInfo.fields);
        partitioning.put("partitionCount", partitions.size());

        result.put("status", status);
        result.put("healthScore", healthScore);
        result.put("warnings", warnings);
        result.put("summary", summary);
        result.put("partitioning", partitioning);
        result.put("lastUpdatedMs", snapshots.lastUpdatedMs);
        result.put("lastOperation", snapshots.lastOperation);
        result.put("manifests", List.of());
        result.put("manifestsTruncated", false);
        result.put("files", fileSamples);
        result.put("filesTruncated", totalFiles > fileSamples.size());
        result.put("snapshots", snapshots.items);
        return result;
    }

    // ------------------------------------------------------------------ 元数据读取

    private static TableRef resolveTable(Connection conn, String schema, String table) throws SQLException {
        String sql =
            "SELECT t.table_id, t.table_uuid, t.path AS table_path, t.path_is_relative AS table_relative, " +
            "s.path AS schema_path, s.path_is_relative AS schema_relative, s.schema_name " +
            "FROM " +
            metadataTable("ducklake_table") +
            " t JOIN " +
            metadataTable("ducklake_schema") +
            " s ON t.schema_id = s.schema_id " +
            "WHERE t.end_snapshot IS NULL AND s.end_snapshot IS NULL " +
            "AND s.schema_name = " +
            quoteLiteral(schema) +
            " AND t.table_name = " +
            quoteLiteral(table) +
            " LIMIT 1";
        List<Map<String, Object>> rows = query(conn, sql);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        TableRef ref = new TableRef();
        ref.tableId = longValue(row.get("table_id"));
        ref.tableUuid = stringValue(row.get("table_uuid"));
        ref.tablePath = stringValue(row.get("table_path"));
        ref.tablePathRelative = booleanValue(row.get("table_relative"));
        ref.schemaPath = stringValue(row.get("schema_path"));
        ref.schemaPathRelative = booleanValue(row.get("schema_relative"));
        return ref;
    }

    private static CatalogSettings readCatalogSettings(Connection conn, String catalog) {
        CatalogSettings settings = new CatalogSettings();
        try {
            List<Map<String, Object>> rows = query(
                conn,
                "SELECT catalog_type, data_path FROM ducklake_settings(" + quoteLiteral(catalog) + ")"
            );
            if (!rows.isEmpty()) {
                settings.catalogType = stringValue(rows.get(0).get("catalog_type"));
                settings.dataPath = stringValue(rows.get(0).get("data_path"));
            }
        } catch (SQLException ignored) {
            // 旧版本 DuckLake 可能没有 ducklake_settings
        }
        try {
            List<Map<String, Object>> rows = query(
                conn,
                "SELECT value FROM ducklake_options(" +
                quoteLiteral(catalog) +
                ") WHERE option_name = 'version'"
            );
            if (!rows.isEmpty()) {
                settings.metadataVersion = stringValue(rows.get(0).get("value"));
            }
        } catch (SQLException ignored) {
            // ignore
        }
        return settings;
    }

    private static TableInfo readTableInfo(Connection conn, String catalog, long tableId) {
        try {
            List<Map<String, Object>> rows = query(
                conn,
                "SELECT file_count, file_size_bytes, delete_file_count, delete_file_size_bytes " +
                "FROM ducklake_table_info(" +
                quoteLiteral(catalog) +
                ") WHERE table_id = " +
                tableId
            );
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            TableInfo info = new TableInfo();
            info.fileCount = longValue(row.get("file_count"));
            info.fileSizeBytes = longValue(row.get("file_size_bytes"));
            info.deleteFileCount = longValue(row.get("delete_file_count"));
            info.deleteFileSizeBytes = longValue(row.get("delete_file_size_bytes"));
            return info;
        } catch (SQLException e) {
            return null;
        }
    }

    private static List<DataFile> readDataFiles(Connection conn, TableRef ref) throws SQLException {
        List<DataFile> files = new ArrayList<>();
        List<Map<String, Object>> rows = query(
            conn,
            "SELECT data_file_id, path, file_format, record_count, file_size_bytes, partition_id " +
            "FROM " +
            metadataTable("ducklake_data_file") +
            " WHERE table_id = " +
            ref.tableId +
            " AND end_snapshot IS NULL ORDER BY data_file_id"
        );
        for (Map<String, Object> row : rows) {
            DataFile file = new DataFile();
            file.dataFileId = longValue(row.get("data_file_id"));
            file.path = stringValue(row.get("path"));
            file.format = stringValue(row.get("file_format"));
            file.recordCount = longValue(row.get("record_count"));
            file.sizeBytes = longValue(row.get("file_size_bytes"));
            files.add(file);
        }
        return files;
    }

    private static List<DeleteFile> readDeleteFiles(Connection conn, TableRef ref) throws SQLException {
        List<DeleteFile> files = new ArrayList<>();
        List<Map<String, Object>> rows = query(
            conn,
            "SELECT delete_file_id, data_file_id, path, format AS file_format, delete_count, file_size_bytes " +
            "FROM " +
            metadataTable("ducklake_delete_file") +
            " WHERE table_id = " +
            ref.tableId +
            " AND end_snapshot IS NULL ORDER BY delete_file_id"
        );
        for (Map<String, Object> row : rows) {
            DeleteFile file = new DeleteFile();
            file.deleteFileId = longValue(row.get("delete_file_id"));
            file.dataFileId = longValue(row.get("data_file_id"));
            file.path = stringValue(row.get("path"));
            file.format = stringValue(row.get("file_format"));
            file.deleteCount = longValue(row.get("delete_count"));
            file.sizeBytes = longValue(row.get("file_size_bytes"));
            files.add(file);
        }
        return files;
    }

    private static PartitionInfo readPartitionInfo(Connection conn, long tableId) {
        PartitionInfo info = new PartitionInfo();
        try {
            List<Map<String, Object>> specs = query(
                conn,
                "SELECT partition_id FROM " +
                metadataTable("ducklake_partition_info") +
                " WHERE table_id = " +
                tableId +
                " AND end_snapshot IS NULL ORDER BY partition_id DESC LIMIT 1"
            );
            if (specs.isEmpty()) {
                return info;
            }
            info.partitionId = longValue(specs.get(0).get("partition_id"));
            List<Map<String, Object>> fields = query(
                conn,
                "SELECT pc.partition_key_index, pc.column_id, c.column_name, pc.transform " +
                "FROM " +
                metadataTable("ducklake_partition_column") +
                " pc LEFT JOIN " +
                metadataTable("ducklake_column") +
                " c ON c.column_id = pc.column_id AND c.table_id = pc.table_id AND c.end_snapshot IS NULL " +
                "WHERE pc.table_id = " +
                tableId +
                " AND pc.partition_id = " +
                info.partitionId +
                " ORDER BY pc.partition_key_index"
            );
            for (Map<String, Object> row : fields) {
                long keyIndex = longValue(row.get("partition_key_index"));
                String columnName = stringValue(row.get("column_name"));
                info.fieldNames.put(keyIndex, columnName);
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("specId", info.partitionId);
                field.put("name", columnName);
                field.put("transform", stringValue(row.get("transform")));
                field.put("sourceId", longValue(row.get("column_id")));
                info.fields.add(field);
            }
        } catch (SQLException ignored) {
            // 旧版本或非分区表
        }
        return info;
    }

    private static Map<Long, String> readFilePartitions(
        Connection conn,
        long tableId,
        Map<Long, String> fieldNames
    ) {
        Map<Long, String> partitions = new LinkedHashMap<>();
        if (fieldNames.isEmpty()) {
            return partitions;
        }
        try {
            List<Map<String, Object>> rows = query(
                conn,
                "SELECT fpv.data_file_id, fpv.partition_key_index, fpv.partition_value " +
                "FROM " +
                metadataTable("ducklake_file_partition_value") +
                " fpv JOIN " +
                metadataTable("ducklake_data_file") +
                " df ON df.data_file_id = fpv.data_file_id " +
                "WHERE fpv.table_id = " +
                tableId +
                " AND df.end_snapshot IS NULL ORDER BY fpv.data_file_id, fpv.partition_key_index"
            );
            for (Map<String, Object> row : rows) {
                long dataFileId = longValue(row.get("data_file_id"));
                long index = longValue(row.get("partition_key_index"));
                String name = fieldNames.get(index);
                String value = stringValue(row.get("partition_value"));
                String part = name == null || name.isBlank() ? value : name + "=" + value;
                partitions.merge(dataFileId, part, (left, right) -> left + ", " + right);
            }
        } catch (SQLException ignored) {
            // ignore
        }
        return partitions;
    }

    private static InlinedStats readInlinedStats(Connection conn, long tableId) {
        InlinedStats stats = new InlinedStats();
        try {
            List<Map<String, Object>> tables = query(
                conn,
                "SELECT table_name FROM " +
                metadataTable("ducklake_inlined_data_tables") +
                " WHERE table_id = " +
                tableId
            );
            for (Map<String, Object> row : tables) {
                String tableName = stringValue(row.get("table_name"));
                if (tableName == null || tableName.isBlank()) {
                    continue;
                }
                try {
                    List<Map<String, Object>> counts = query(
                        conn,
                        "SELECT COUNT(*) AS cnt FROM " +
                        metadataTable(tableName) +
                        " WHERE end_snapshot IS NULL"
                    );
                    stats.dataRows += longValue(counts.get(0).get("cnt"));
                } catch (SQLException ignored) {
                    // ignore
                }
            }
        } catch (SQLException ignored) {
            // ignore
        }
        try {
            List<Map<String, Object>> counts = query(
                conn,
                "SELECT COUNT(*) AS cnt FROM " + metadataTable("ducklake_inlined_delete_" + tableId)
            );
            stats.deleteRows = longValue(counts.get(0).get("cnt"));
        } catch (SQLException ignored) {
            // 无内联删除表
        }
        return stats;
    }

    private static SnapshotStats readSnapshots(Connection conn, TableRef ref, String schema, String table) {
        SnapshotStats stats = new SnapshotStats();
        try {
            String tableIdRegex = "(^|[^0-9])" + ref.tableId + "($|[^0-9])";
            String tableNamePattern = "%\"" + schema + "\".\"" + table + "\"%";
            List<Map<String, Object>> rows = query(
                conn,
                "SELECT s.snapshot_id, CAST(epoch_ms(s.snapshot_time) AS BIGINT) AS timestamp_ms, " +
                "sc.changes_made FROM " +
                metadataTable("ducklake_snapshot") +
                " s LEFT JOIN " +
                metadataTable("ducklake_snapshot_changes") +
                " sc ON sc.snapshot_id = s.snapshot_id " +
                "WHERE regexp_matches(coalesce(sc.changes_made, ''), " +
                quoteLiteral(tableIdRegex) +
                ") OR coalesce(sc.changes_made, '') LIKE " +
                quoteLiteral(tableNamePattern) +
                " ORDER BY s.snapshot_id DESC"
            );
            stats.count = rows.size();
            if (!rows.isEmpty()) {
                Map<String, Object> latest = rows.get(0);
                stats.lastUpdatedMs = longValue(latest.get("timestamp_ms"));
                stats.lastOperation = describeOperation(stringValue(latest.get("changes_made")));
            }
            for (Map<String, Object> row : rows) {
                if (stats.items.size() >= SNAPSHOT_LIMIT) {
                    break;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("snapshotId", longValue(row.get("snapshot_id")));
                item.put("sequenceNumber", longValue(row.get("snapshot_id")));
                item.put("timestampMs", longValue(row.get("timestamp_ms")));
                item.put("operation", describeOperation(stringValue(row.get("changes_made"))));
                stats.items.add(item);
            }
        } catch (SQLException ignored) {
            // ignore
        }
        return stats;
    }

    private static MetadataSize readMetadataSize(Connection conn) {
        MetadataSize size = new MetadataSize();
        try {
            List<Map<String, Object>> rows = query(
                conn,
                "SELECT path FROM duckdb_databases() WHERE database_name = " + quoteLiteral(METADATA_CATALOG)
            );
            String path = rows.isEmpty() ? null : stringValue(rows.get(0).get("path"));
            if (path == null || path.isBlank() || path.contains("://") || path.startsWith("postgres:") || path.startsWith("mysql:")) {
                return size;
            }
            Path metadataFile = Path.of(path).toAbsolutePath();
            if (Files.exists(metadataFile)) {
                size.fileCount = 1;
                size.totalBytes += Files.size(metadataFile);
            }
            Path walFile = Path.of(metadataFile + ".wal");
            if (Files.exists(walFile)) {
                size.fileCount++;
                size.totalBytes += Files.size(walFile);
            }
        } catch (Exception ignored) {
            // 远程 Catalog 或无法读取文件大小时返回 0
        }
        return size;
    }

    // ------------------------------------------------------------------ 健康评分与文件样本

    private static List<Map<String, Object>> buildFileSamples(
        Connection conn,
        TableRef ref,
        List<DataFile> dataFiles,
        List<DeleteFile> deleteFiles,
        Map<Long, String> filePartitions
    ) {
        PriorityQueue<FileSample> queue = new PriorityQueue<>(
            FILE_SAMPLE_LIMIT,
            Comparator.comparingLong((FileSample sample) -> sample.sizeBytes).reversed()
        );
        for (DataFile file : dataFiles) {
            offerSample(
                queue,
                new FileSample(
                    joinPath(ref.locationPrefix(), file.path),
                    "DATA",
                    file.format,
                    file.sizeBytes,
                    file.recordCount,
                    filePartitions.get(file.dataFileId)
                )
            );
        }
        for (DeleteFile file : deleteFiles) {
            offerSample(
                queue,
                new FileSample(
                    joinPath(ref.locationPrefix(), file.path),
                    "POSITION_DELETES",
                    file.format,
                    file.sizeBytes,
                    file.deleteCount,
                    filePartitions.get(file.dataFileId)
                )
            );
        }
        List<FileSample> sorted = new ArrayList<>(queue);
        sorted.sort(Comparator.comparingLong(sample -> sample.sizeBytes));
        List<Map<String, Object>> samples = new ArrayList<>();
        for (FileSample sample : sorted) {
            samples.add(sample.toMap());
        }
        return samples;
    }

    private static void offerSample(PriorityQueue<FileSample> queue, FileSample sample) {
        if (queue.size() < FILE_SAMPLE_LIMIT) {
            queue.offer(sample);
        } else if (sample.sizeBytes < queue.peek().sizeBytes) {
            queue.poll();
            queue.offer(sample);
        }
    }

    private static long countSmallFiles(List<DataFile> dataFiles) {
        return dataFiles.stream().filter(file -> file.sizeBytes < SMALL_FILE_THRESHOLD_BYTES).count();
    }

    private static int computeHealthScore(
        List<DataFile> dataFiles,
        List<DeleteFile> deleteFiles,
        InlinedStats inlined,
        long partitionCount,
        long snapshotCount,
        List<String> warnings
    ) {
        int score = 100;
        long fileCount = dataFiles.size();
        if (fileCount > 0) {
            long smallFiles = countSmallFiles(dataFiles);
            double smallRatio = smallFiles * 1.0 / fileCount;
            score -= (int) Math.round(smallRatio * 35);
            if (smallRatio >= 0.3) {
                warnings.add(
                    String.format(
                        "存在 %d 个小文件（占数据文件 %.0f%%），建议执行 ducklake_rewrite_data_files 合并小文件。",
                        smallFiles,
                        smallRatio * 100
                    )
                );
            }
            long totalSize = dataFiles.stream().mapToLong(file -> file.sizeBytes).sum();
            long avgFileSize = totalSize / fileCount;
            if (avgFileSize < 64 * MB) {
                score -= avgFileSize < 16 * MB ? 12 : 6;
                warnings.add("数据文件平均大小 " + formatBytes(avgFileSize) + "，低于推荐的 128 MB。");
            }
        }
        if (!deleteFiles.isEmpty()) {
            long deleteSize = deleteFiles.stream().mapToLong(file -> file.sizeBytes).sum();
            double deleteRatio = deleteFiles.size() * 1.0 / Math.max(1L, fileCount);
            score -= Math.min(25, 5 + (int) Math.round(deleteRatio * 20));
            warnings.add(
                "存在 " + deleteFiles.size() + " 个删除文件（" + formatBytes(deleteSize) + "），建议执行文件重写与清理。"
            );
        }
        if (inlined.deleteRows > 0) {
            score -= Math.min(20, 3 + (int) Math.min(17, inlined.deleteRows / 100));
            warnings.add(
                "存在 " + inlined.deleteRows + " 条内联删除记录，建议执行 ducklake_rewrite_data_files 清理删除标记。"
            );
        }
        if (snapshotCount > 1000) {
            score -= 5;
            warnings.add("快照数量较多（" + snapshotCount + "），建议执行 expire snapshots。");
        }
        if (partitionCount > 1000) {
            score -= 5;
            warnings.add("分区数较多（" + partitionCount + "），注意分区粒度避免过细。");
        }
        return Math.max(0, Math.min(100, score));
    }

    // ------------------------------------------------------------------ 工具方法

    private static String metadataTable(String table) {
        return quoteIdentifier(METADATA_CATALOG) + ".main." + quoteIdentifier(table);
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

    private static String quoteIdentifier(String value) {
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }

    private static String quoteLiteral(String value) {
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }

    private static String joinPath(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String value = part.trim();
            if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                builder.setLength(0);
                builder.append(value);
                continue;
            }
            if (value.startsWith("/") && builder.length() == 0) {
                builder.append(value);
                continue;
            }
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '/') {
                builder.append('/');
            }
            if (value.startsWith("/")) {
                value = value.substring(1);
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static String describeOperation(String changesMade) {
        if (changesMade == null || changesMade.isBlank()) {
            return null;
        }
        String value = CAMEL_CASE_BOUNDARY.matcher(changesMade).replaceAll("$1_$2");
        if (value.startsWith("created_table") || value.contains("created_table")) {
            return "create";
        }
        if (value.contains("inlined_insert") || value.contains("inserted_into_table")) {
            return "insert";
        }
        if (value.contains("inlined_delete") || value.contains("deleted_from_table")) {
            return "delete";
        }
        if (value.contains("altered_table") || value.contains("set_partitioning")) {
            return "alter";
        }
        if (value.contains("dropped_table")) {
            return "drop";
        }
        return changesMade;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
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

    private static final class TableRef {

        private long tableId;
        private String tableUuid;
        private String tablePath;
        private boolean tablePathRelative;
        private String schemaPath;
        private boolean schemaPathRelative;
        private String location;

        private String locationPrefix() {
            return location;
        }
    }

    private static final class CatalogSettings {

        private String catalogType;
        private String metadataVersion;
        private String dataPath;
    }

    private static final class TableInfo {

        private long fileCount;
        private long fileSizeBytes;
        private long deleteFileCount;
        private long deleteFileSizeBytes;

        private static TableInfo from(List<DataFile> dataFiles, List<DeleteFile> deleteFiles) {
            TableInfo info = new TableInfo();
            info.fileCount = dataFiles.size();
            info.fileSizeBytes = dataFiles.stream().mapToLong(file -> file.sizeBytes).sum();
            info.deleteFileCount = deleteFiles.size();
            info.deleteFileSizeBytes = deleteFiles.stream().mapToLong(file -> file.sizeBytes).sum();
            return info;
        }
    }

    private static final class DataFile {

        private long dataFileId;
        private String path;
        private String format;
        private long recordCount;
        private long sizeBytes;
    }

    private static final class DeleteFile {

        private long deleteFileId;
        private long dataFileId;
        private String path;
        private String format;
        private long deleteCount;
        private long sizeBytes;
    }

    private static final class PartitionInfo {

        private long partitionId;
        private final List<Map<String, Object>> fields = new ArrayList<>();
        private final Map<Long, String> fieldNames = new LinkedHashMap<>();
    }

    private static final class InlinedStats {

        private long dataRows;
        private long deleteRows;
    }

    private static final class SnapshotStats {

        private long count;
        private Long lastUpdatedMs;
        private String lastOperation;
        private final List<Map<String, Object>> items = new ArrayList<>();
    }

    private static final class MetadataSize {

        private long fileCount;
        private long totalBytes;
    }

    private static final class FileSample {

        private final String path;
        private final String contentType;
        private final String format;
        private final long sizeBytes;
        private final long recordCount;
        private final String partition;

        private FileSample(
            String path,
            String contentType,
            String format,
            long sizeBytes,
            long recordCount,
            String partition
        ) {
            this.path = path;
            this.contentType = contentType;
            this.format = format;
            this.sizeBytes = sizeBytes;
            this.recordCount = recordCount;
            this.partition = partition;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("path", path);
            map.put("contentType", contentType);
            map.put("format", format);
            map.put("sizeBytes", sizeBytes);
            map.put("recordCount", recordCount);
            map.put("partition", partition);
            return map;
        }
    }
}
