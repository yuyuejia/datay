package com.data.datafusion.service.datamap;

import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvRow;
import com.data.datafusion.domain.MetadataCollectTask;
import com.data.file.FileStorageStrategy;
import com.data.file.FileStorageStrategy.FileInfo;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 读取元数据采集任务输出的最新快照。
 *
 * <p>优先读取 {@code latest/tables.csv}；不存在时回退到最近一次带 tables.csv 的运行目录。
 * 同时兼容本地文件与 MinIO 文件存储。</p>
 */
@Component
public class MetadataSnapshotLoader {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataSnapshotLoader.class);

    private static final String DEFAULT_OUTPUT_DIR = "metadata";

    private final FileStorageStrategy fileStorageStrategy;

    public MetadataSnapshotLoader(FileStorageStrategy fileStorageStrategy) {
        this.fileStorageStrategy = fileStorageStrategy;
    }

    public Optional<Snapshot> load(String tenantCode, MetadataCollectTask task) {
        if (task == null || task.getDataSourceId() == null || task.getDataSourceId().isBlank()) {
            return Optional.empty();
        }
        // 文件存储策略会按当前租户补 tenants/<code> 前缀，这里使用租户内相对路径。
        String prefix = joinPath(normalizeDir(task.getOutputDir()), task.getDataSourceId());
        String latest = joinPath(prefix, "latest");
        String tablesPath = joinPath(latest, "tables.csv");
        if (!fileStorageStrategy.exists(tablesPath)) {
            Optional<String> fallback = findLatestTables(prefix);
            if (fallback.isEmpty()) {
                return Optional.empty();
            }
            tablesPath = fallback.get();
        }
        String columnsPath = joinPath(parent(tablesPath), "columns.csv");
        try {
            List<TableSnapshot> tables = readTables(tablesPath);
            List<ColumnSnapshot> columns = fileStorageStrategy.exists(columnsPath)
                ? readColumns(columnsPath)
                : List.of();
            return Optional.of(new Snapshot(tablesPath, columnsPath, tables, columns));
        } catch (Exception e) {
            LOG.warn("读取元数据快照失败 {}: {}", tablesPath, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> findLatestTables(String prefix) {
        try {
            List<FileInfo> candidates = new ArrayList<>();
            for (FileInfo entry : fileStorageStrategy.list(prefix)) {
                if (!entry.isDirectory() || "latest".equalsIgnoreCase(entry.name())) {
                    continue;
                }
                for (FileInfo file : fileStorageStrategy.list(entry.path())) {
                    if (!file.isDirectory() && file.name() != null && file.name().equalsIgnoreCase("tables.csv")) {
                        candidates.add(file);
                    }
                }
            }
            return candidates.stream().max(Comparator.comparingLong(FileInfo::lastModified)).map(FileInfo::path);
        } catch (Exception e) {
            LOG.debug("列出元数据快照目录失败 {}: {}", prefix, e.getMessage());
            return Optional.empty();
        }
    }

    private List<TableSnapshot> readTables(String path) {
        List<TableSnapshot> result = new ArrayList<>();
        for (Map<String, String> row : readCsv(path)) {
            String table = value(row, "table_name");
            if (table == null) {
                continue;
            }
            result.add(
                new TableSnapshot(
                    value(row, "catalog_name"),
                    value(row, "schema_name"),
                    table,
                    value(row, "table_comment"),
                    value(row, "collected_at")
                )
            );
        }
        return result;
    }

    private List<ColumnSnapshot> readColumns(String path) {
        List<ColumnSnapshot> result = new ArrayList<>();
        for (Map<String, String> row : readCsv(path)) {
            String table = value(row, "table_name");
            String column = value(row, "column_name");
            if (table == null || column == null) {
                continue;
            }
            result.add(
                new ColumnSnapshot(
                    value(row, "catalog_name"),
                    value(row, "schema_name"),
                    table,
                    column,
                    intValue(row, "ordinal_position"),
                    value(row, "data_type"),
                    intValue(row, "column_length"),
                    intValue(row, "numeric_precision"),
                    intValue(row, "numeric_scale"),
                    booleanValue(row, "nullable"),
                    booleanValue(row, "primary_key"),
                    value(row, "default_value"),
                    value(row, "column_comment"),
                    value(row, "collected_at")
                )
            );
        }
        return result;
    }

    private List<Map<String, String>> readCsv(String path) {
        CsvReadConfig config = CsvReadConfig
            .defaultConfig()
            .setContainsHeader(true)
            .setSkipEmptyRows(true)
            .setErrorOnDifferentFieldCount(false)
            .setTrimField(true);
        List<Map<String, String>> result = new ArrayList<>();
        try (InputStream inputStream = fileStorageStrategy.download(path); CsvReader reader = new CsvReader(config)) {
            CsvData data = reader.read(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            for (CsvRow row : data.getRows()) {
                Map<String, String> values = new LinkedHashMap<>();
                row
                    .getFieldMap()
                    .forEach((key, value) -> values.put(normalizeKey(key), value));
                result.add(values);
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取 CSV 失败: " + e.getMessage(), e);
        }
        return result;
    }

    private String value(Map<String, String> row, String key) {
        String value = row.get(normalizeKey(key));
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int intValue(Map<String, String> row, String key) {
        String value = value(row, key);
        if (value == null) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean booleanValue(Map<String, String> row, String key) {
        String value = value(row, key);
        return value != null && ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value));
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDir(String value) {
        String dir = value == null || value.isBlank() ? DEFAULT_OUTPUT_DIR : value.trim().replace('\\', '/');
        while (dir.startsWith("/")) {
            dir = dir.substring(1);
        }
        while (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        return dir.isBlank() ? DEFAULT_OUTPUT_DIR : dir;
    }

    private String parent(String path) {
        int index = path == null ? -1 : path.lastIndexOf('/');
        return index <= 0 ? "" : path.substring(0, index);
    }

    private String joinPath(String... parts) {
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

    public record Snapshot(
        String tablesPath,
        String columnsPath,
        List<TableSnapshot> tables,
        List<ColumnSnapshot> columns
    ) {}

    public record TableSnapshot(String catalog, String schema, String table, String comment, String collectedAt) {}

    public record ColumnSnapshot(
        String catalog,
        String schema,
        String table,
        String column,
        int ordinal,
        String dataType,
        int length,
        int precision,
        int scale,
        boolean nullable,
        boolean primaryKey,
        String defaultValue,
        String comment,
        String collectedAt
    ) {}
}
