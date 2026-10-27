package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.data.metadata.util.JsonTableMetaUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JDBCLookup 组件 - 根据上游记录中的指定字段作为查询条件，查询另一个数据库的关联表，
 * 并将查询结果字段回填到上游记录中。
 *
 * <p>处理流程：
 * <ol>
 *   <li>从每个 FlowFile 的记录中提取 {@code localField} 作为关联键；</li>
 *   <li>按 {@code batchSize} 分批执行 {@code SELECT ... FROM schema.table WHERE lookupField IN (...)}，
 *       避免逐行查询造成大量数据库往返；</li>
 *   <li>将查询结果按关联键回填到原记录：{@code LEFT} 保留未匹配记录并补空，
 *       {@code INNER} 丢弃未匹配记录；</li>
 *   <li>把关联表字段追加到 FlowFile 的表元数据中，供下游组件识别新增字段及类型。</li>
 * </ol>
 *
 * <p>为避免重复关联键反复查询，组件默认开启结果缓存（LRU，默认最多缓存 10000 个键）。
 *
 * <p>配置示例：
 * <pre>
 * {
 *   "sourceId": "dataSourceId",
 *   "schema": "demo",
 *   "table": "users",
 *   "localField": "user_id",
 *   "lookupField": "id",
 *   "selectColumns": "name, dept_name",
 *   "joinType": "LEFT",
 *   "outputPrefix": "user_",
 *   "where": "status = 1",
 *   "cache": true,
 *   "batchSize": 500
 * }
 * </pre>
 */
@ComponentRegister(
    value = "JDBCLookup",
    name = "JDBC关联查询",
    group = "数据处理",
    desc = "根据上游记录中的指定字段，批量查询另一个数据库的表，并将查询结果字段回填到记录中。",
    order = 19
)
public class JDBCLookup extends FlowComponent {

    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int DEFAULT_CACHE_SIZE = 10000;
    private static final int MAX_BATCH_SIZE = 5000;
    private static final String DEFAULT_JOIN_TYPE = "LEFT";

    // 关联数据源（配置中的 sourceId 由引擎转换为 DatasourceInfo）
    private DatasourceInfo sourceId;

    // 关联表所在 schema
    private String schema;

    // 关联表表名
    private String table;

    // 上游记录中作为查询条件的字段
    private String localField;

    // 关联表中与 localField 对应的字段
    private String lookupField;

    // 需要回填的关联表字段，多个字段用英文逗号分隔；为空表示全部字段
    private String selectColumns;

    // 附加过滤条件（不包含 WHERE 关键字）
    private String where;

    // 关联类型：LEFT（默认，保留未匹配记录）或 INNER（仅保留匹配记录）
    private String joinType = DEFAULT_JOIN_TYPE;

    // 回填字段前缀，用于避免与上游同名字段冲突
    private String outputPrefix = "";

    // 是否缓存关联键的查询结果
    private boolean cache = true;

    // 单次 IN 查询的关联键数量
    private int batchSize = DEFAULT_BATCH_SIZE;

    // 缓存上限
    private int cacheSize = DEFAULT_CACHE_SIZE;

    // 关联键 -> 查询结果；Optional.empty() 表示已查询但未命中
    private final Map<String, Optional<JSONObject>> resultCache = Collections.synchronizedMap(
        new LinkedHashMap<String, Optional<JSONObject>>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Optional<JSONObject>> eldest) {
                return size() > effectiveCacheSize();
            }
        }
    );

    // 关联表元数据（首次查询时加载）
    private volatile TableMeta lookupTableMeta;

    public JDBCLookup() {
        setType(ComponentType.OPERATOR);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile == null || flowFile.getAttribute("_end") != null) {
            return;
        }
        validateConfig();

        JSONObject singleRecord = flowFile.getJsonObject();
        JSONArray records = flowFile.getJsonArray();
        if (singleRecord != null) {
            records = new JSONArray();
            records.add(singleRecord);
        }
        if (records == null || records.isEmpty()) {
            return;
        }

        try (Connection conn = DBUtils.getConnection(sourceId)) {
            loadLookupTableMeta(conn);

            List<Object> keyValues = collectKeyValues(records);
            Map<String, JSONObject> matchedRows = lookupRows(conn, keyValues);
            JSONArray output = enrichRecords(records, matchedRows);
            if (output.isEmpty()) {
                logInfo("本批次 " + records.size() + " 条记录均未匹配到关联表数据，跳过输出");
                return;
            }

            FlowFile outFile = new FlowFile();
            outFile.getAttributeMap().putAll(flowFile.getAttributeMap());
            if (singleRecord != null) {
                outFile.setJsonObject(output.getJSONObject(0));
            } else {
                outFile.setJsonArray(output);
            }
            updateTableMeta(outFile, flowFile, output);
            writeRecords(outFile);
            logInfo("关联查询完成，输入 " + records.size() + " 条，输出 " + output.size() + " 条");
        } catch (SQLException e) {
            throw new RuntimeException("JDBCLookup 关联查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 校验必填配置。
     */
    private void validateConfig() {
        if (sourceId == null) {
            throw new IllegalArgumentException("JDBCLookup 组件未配置数据源 sourceId");
        }
        if (table == null || table.trim().isEmpty()) {
            throw new IllegalArgumentException("JDBCLookup 组件关联表 table 不能为空");
        }
        if (localField == null || localField.trim().isEmpty()) {
            throw new IllegalArgumentException("JDBCLookup 组件上游关联字段 localField 不能为空");
        }
        if (lookupField == null || lookupField.trim().isEmpty()) {
            throw new IllegalArgumentException("JDBCLookup 组件关联表字段 lookupField 不能为空");
        }
    }

    /**
     * 加载关联表元数据；失败时不阻塞任务，后续按查询结果推断字段类型。
     */
    private void loadLookupTableMeta(Connection conn) {
        if (lookupTableMeta != null) {
            return;
        }
        synchronized (this) {
            if (lookupTableMeta != null) {
                return;
            }
            try {
                TableMeta meta = DBUtils.getTableMetaData(conn, trimToNull(schema), table.trim());
                if (meta != null) {
                    if (meta.getDbType() == null || meta.getDbType().trim().isEmpty()) {
                        meta.setDbType(resolveLookupDbType());
                    }
                    lookupTableMeta = meta;
                }
            } catch (SQLException e) {
                logWarn("获取关联表元数据失败，将根据查询结果推断字段类型: " + e.getMessage());
            }
        }
    }

    /**
     * 收集本批记录中的关联键，按规范化的字符串去重。
     */
    private List<Object> collectKeyValues(JSONArray records) {
        Set<String> seen = new LinkedHashSet<>();
        List<Object> values = new ArrayList<>();
        for (Object item : records) {
            if (!(item instanceof JSONObject)) {
                continue;
            }
            Object value = getRecordValue((JSONObject) item, localField);
            String key = canonicalKey(value);
            if (key != null && seen.add(key)) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * 查询关联键对应的记录：优先使用缓存，只查询缓存中缺失的键。
     */
    private Map<String, JSONObject> lookupRows(Connection conn, List<Object> keyValues) throws SQLException {
        Map<String, JSONObject> result = new HashMap<>();
        if (keyValues.isEmpty()) {
            return result;
        }

        List<Object> missingValues = new ArrayList<>();
        Set<String> missingKeys = new HashSet<>();
        for (Object value : keyValues) {
            String key = canonicalKey(value);
            if (key == null) {
                continue;
            }
            if (cache) {
                if (resultCache.containsKey(key)) {
                    Optional<JSONObject> cached = resultCache.get(key);
                    if (cached != null && cached.isPresent()) {
                        result.put(key, cached.get());
                    }
                    continue;
                }
            }
            if (missingKeys.add(key)) {
                missingValues.add(value);
            }
        }

        if (missingValues.isEmpty()) {
            return result;
        }

        int effectiveBatchSize = effectiveBatchSize();
        for (int start = 0; start < missingValues.size(); start += effectiveBatchSize) {
            int end = Math.min(start + effectiveBatchSize, missingValues.size());
            List<Object> chunk = missingValues.subList(start, end);
            Map<String, JSONObject> fetched = queryRows(conn, chunk);
            for (Object value : chunk) {
                String key = canonicalKey(value);
                if (key == null) {
                    continue;
                }
                JSONObject row = fetched.get(key);
                if (cache) {
                    resultCache.put(key, Optional.ofNullable(row));
                }
                if (row != null) {
                    result.put(key, row);
                }
            }
        }
        return result;
    }

    /**
     * 执行一批 IN 查询，返回 关联键 -> 查询结果。
     */
    private Map<String, JSONObject> queryRows(Connection conn, List<Object> keyValues) throws SQLException {
        String sql = buildLookupSql(keyValues.size());
        logDebug("执行关联查询 SQL: " + sql + "，关联键数量: " + keyValues.size());
        Map<String, JSONObject> rows = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < keyValues.size(); i++) {
                ps.setObject(i + 1, keyValues.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                int keyIndex = findResultColumn(metaData, actualLookupField());
                if (keyIndex < 0) {
                    throw new SQLException("查询结果中未找到关联字段: " + lookupField);
                }
                List<String> columnNames = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    String label = metaData.getColumnLabel(i);
                    columnNames.add(label == null || label.trim().isEmpty() ? metaData.getColumnName(i) : label);
                }
                boolean keySelected = isLookupFieldSelected();
                while (rs.next()) {
                    String key = canonicalKey(rs.getObject(keyIndex));
                    if (key == null) {
                        continue;
                    }
                    JSONObject row = new JSONObject();
                    for (int i = 1; i <= columnCount; i++) {
                        if (i == keyIndex && !keySelected) {
                            continue;
                        }
                        row.put(columnNames.get(i - 1), rs.getObject(i));
                    }
                    rows.putIfAbsent(key, row);
                }
            }
        }
        return rows;
    }

    /**
     * 构建关联查询 SQL，关联字段始终随查询返回，以保证能按关联键回填数据。
     */
    String buildLookupSql(int valueCount) {
        List<String> configured = parseConfiguredColumns();
        StringBuilder sql = new StringBuilder("SELECT ");
        if (configured == null) {
            sql.append("*");
        } else {
            String keyColumn = actualLookupField();
            List<String> selectColumns = new ArrayList<>();
            selectColumns.add(keyColumn);
            Set<String> seen = new HashSet<>();
            seen.add(keyColumn.toLowerCase(Locale.ROOT));
            for (String column : configured) {
                String actual = actualColumnName(column);
                if (seen.add(actual.toLowerCase(Locale.ROOT))) {
                    selectColumns.add(actual);
                }
            }
            sql.append(String.join(", ", selectColumns));
        }
        sql.append(" FROM ").append(qualifiedTable());
        sql.append(" WHERE ").append(actualLookupField()).append(" IN (");
        for (int i = 0; i < valueCount; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");
        if (where != null && !where.trim().isEmpty()) {
            sql.append(" AND (").append(where.trim()).append(")");
        }
        return sql.toString();
    }

    /**
     * 将查询结果回填到记录中。
     */
    JSONArray enrichRecords(JSONArray records, Map<String, JSONObject> matchedRows) {
        JSONArray output = new JSONArray(records.size());
        boolean innerJoin = "INNER".equalsIgnoreCase(normalizeJoinType());
        List<String> lookupColumns = resolveLookupColumns();
        for (Object item : records) {
            if (!(item instanceof JSONObject)) {
                output.add(item);
                continue;
            }
            JSONObject record = (JSONObject) item;
            String key = canonicalKey(getRecordValue(record, localField));
            JSONObject matched = key == null ? null : matchedRows.get(key);
            if (matched == null) {
                if (innerJoin) {
                    continue;
                }
                for (String column : lookupColumns) {
                    String outputName = applyPrefix(column);
                    if (!record.containsKey(outputName)) {
                        record.put(outputName, null);
                    }
                }
            } else {
                for (Map.Entry<String, Object> entry : matched.entrySet()) {
                    record.put(applyPrefix(entry.getKey()), entry.getValue());
                }
            }
            output.add(record);
        }
        return output;
    }

    /**
     * 合并上游表元数据与关联表字段元数据，供下游识别新增字段及类型。
     */
    private void updateTableMeta(FlowFile outFile, FlowFile inFile, JSONArray output) {
        Object attribute = inFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        TableMeta upstreamMeta = attribute instanceof TableMeta ? (TableMeta) attribute : null;
        String outputDbType = resolveOutputDbType(upstreamMeta);

        TableMeta outputMeta;
        if (upstreamMeta != null) {
            outputMeta = copyTableMeta(upstreamMeta);
        } else {
            outputMeta = JsonTableMetaUtils.build(output, outputDbType);
        }

        if (outputMeta == null) {
            return;
        }

        for (String column : resolveLookupColumns()) {
            String outputName = applyPrefix(column);
            ColumnMeta lookupColumn = findLookupColumn(column);
            String type = lookupColumn == null ? null : convertType(lookupColumn.getType(), outputDbType);
            if (type == null || type.trim().isEmpty()) {
                type = inferColumnType(output, outputName);
            }
            upsertColumn(outputMeta, outputName, type, lookupColumn);
        }

        // 关联表元数据不可用时，从实际查询结果中补全字段
        TableMeta inferred = JsonTableMetaUtils.build(output, outputDbType);
        if (inferred != null) {
            for (ColumnMeta column : inferred.columns()) {
                if (findColumn(outputMeta, column.getName()) == null) {
                    outputMeta.addColumn(column);
                }
            }
        }

        outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, outputMeta);
    }

    /**
     * 解析配置的输出字段；返回 null 表示输出全部字段。
     */
    List<String> parseConfiguredColumns() {
        if (selectColumns == null || selectColumns.trim().isEmpty()) {
            return null;
        }
        List<String> columns = new ArrayList<>();
        for (String column : selectColumns.split(",")) {
            String trimmed = column.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("*".equals(trimmed)) {
                return null;
            }
            columns.add(trimmed);
        }
        return columns.isEmpty() ? null : columns;
    }

    /**
     * 解析实际需要回填的关联表字段名（已按关联表元数据修正大小写）。
     */
    List<String> resolveLookupColumns() {
        List<String> configured = parseConfiguredColumns();
        if (configured == null) {
            if (lookupTableMeta == null) {
                return Collections.emptyList();
            }
            List<String> columns = new ArrayList<>();
            for (ColumnMeta column : lookupTableMeta.columns()) {
                columns.add(column.getName());
            }
            return columns;
        }
        List<String> resolved = new ArrayList<>(configured.size());
        for (String column : configured) {
            resolved.add(actualColumnName(column));
        }
        return resolved;
    }

    /**
     * 判断查询结果是否需要保留关联字段。配置为空或包含 * 时视为全选。
     */
    private boolean isLookupFieldSelected() {
        List<String> configured = parseConfiguredColumns();
        if (configured == null) {
            return true;
        }
        for (String column : configured) {
            if (column.equalsIgnoreCase(lookupField.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关联表字段的实际名称：能按元数据匹配时使用元数据中的大小写。
     */
    private String actualColumnName(String column) {
        ColumnMeta meta = findLookupColumn(column);
        return meta == null ? column.trim() : meta.getName();
    }

    private String actualLookupField() {
        return actualColumnName(lookupField.trim());
    }

    private ColumnMeta findLookupColumn(String column) {
        TableMeta meta = lookupTableMeta;
        if (meta == null || column == null) {
            return null;
        }
        for (ColumnMeta candidate : meta.columns()) {
            if (candidate.getName() != null && candidate.getName().equalsIgnoreCase(column.trim())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 在查询结果集中定位关联字段：优先按列标签，其次按列名。
     */
    private int findResultColumn(ResultSetMetaData metaData, String column) throws SQLException {
        int count = metaData.getColumnCount();
        for (int i = 1; i <= count; i++) {
            String label = metaData.getColumnLabel(i);
            if (label != null && label.equalsIgnoreCase(column)) {
                return i;
            }
        }
        for (int i = 1; i <= count; i++) {
            String name = metaData.getColumnName(i);
            if (name != null && name.equalsIgnoreCase(column)) {
                return i;
            }
        }
        return -1;
    }

    private String qualifiedTable() {
        String tableName = table.trim();
        String schemaName = trimToNull(schema);
        return schemaName == null ? tableName : schemaName + "." + tableName;
    }

    private String applyPrefix(String column) {
        return (outputPrefix == null ? "" : outputPrefix) + column;
    }

    String normalizeJoinType() {
        if (joinType == null || joinType.trim().isEmpty()) {
            return DEFAULT_JOIN_TYPE;
        }
        String normalized = joinType.trim().toUpperCase(Locale.ROOT);
        return "INNER".equals(normalized) ? "INNER" : DEFAULT_JOIN_TYPE;
    }

    private int effectiveBatchSize() {
        if (batchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    private int effectiveCacheSize() {
        return cacheSize <= 0 ? DEFAULT_CACHE_SIZE : cacheSize;
    }

    private String resolveLookupDbType() {
        if (sourceId == null) {
            return null;
        }
        if (sourceId.getType() != null && !sourceId.getType().trim().isEmpty()) {
            return sourceId.getType().trim();
        }
        return sourceId.getUrl() == null ? null : DBUtils.getDBType(sourceId.getUrl());
    }

    private String resolveOutputDbType(TableMeta upstreamMeta) {
        if (upstreamMeta != null && upstreamMeta.getDbType() != null && !upstreamMeta.getDbType().trim().isEmpty()) {
            return upstreamMeta.getDbType();
        }
        String lookupDbType = resolveLookupDbType();
        return lookupDbType == null ? "mysql" : lookupDbType;
    }

    /**
     * 将关联表字段类型转换为上游元数据的数据库类型，保证下游按统一方言建表。
     */
    private String convertType(String type, String outputDbType) {
        if (type == null || type.trim().isEmpty() || outputDbType == null) {
            return type;
        }
        String lookupDbType = resolveLookupDbType();
        if (lookupDbType == null || lookupDbType.equalsIgnoreCase(outputDbType)) {
            return type;
        }
        try {
            return DatabaseConverter.convert(lookupDbType, outputDbType, new ColumnMeta("value", type)).getType();
        } catch (Exception e) {
            logDebug("字段类型转换失败，保留原始类型 " + type + ": " + e.getMessage());
            return type;
        }
    }

    private String inferColumnType(JSONArray records, String columnName) {
        for (Object item : records) {
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject record = (JSONObject) item;
            if (record.containsKey(columnName)) {
                Object value = record.get(columnName);
                if (value != null) {
                    return JsonTableMetaUtils.inferColumnType(value);
                }
            }
        }
        return null;
    }

    private void upsertColumn(TableMeta tableMeta, String columnName, String type, ColumnMeta source) {
        ColumnMeta existing = findColumn(tableMeta, columnName);
        if (existing != null) {
            if (type != null && !type.trim().isEmpty()) {
                existing.setType(type);
            }
            return;
        }
        ColumnMeta column = new ColumnMeta(columnName, type == null || type.trim().isEmpty() ? "VARCHAR" : type);
        if (source != null) {
            column.setComment(source.getComment());
            column.setLength(source.getLength());
            column.setPrecision(source.getPrecision());
            column.setScale(source.getScale());
            column.setNullable(source.isNullable());
        }
        tableMeta.addColumn(column);
    }

    private ColumnMeta findColumn(TableMeta tableMeta, String columnName) {
        if (tableMeta == null || columnName == null) {
            return null;
        }
        for (ColumnMeta column : tableMeta.columns()) {
            if (column.getName() != null && column.getName().equalsIgnoreCase(columnName)) {
                return column;
            }
        }
        return null;
    }

    private TableMeta copyTableMeta(TableMeta source) {
        TableMeta copy = new TableMeta(source.getTable(), new ArrayList<>());
        copy.setSchema(source.getSchema());
        copy.setDbType(source.getDbType());
        copy.setCatalog(source.getCatalog());
        copy.setComment(source.getComment());
        for (ColumnMeta column : source.columns()) {
            ColumnMeta columnCopy = new ColumnMeta(column.getName(), column.getType());
            columnCopy.setComment(column.getComment());
            columnCopy.setLength(column.getLength());
            columnCopy.setPrecision(column.getPrecision());
            columnCopy.setScale(column.getScale());
            columnCopy.setNullable(column.isNullable());
            columnCopy.setDefaultValue(column.getDefaultValue());
            columnCopy.setPrimaryKey(column.isPrimaryKey());
            copy.addColumn(columnCopy);
        }
        return copy;
    }

    /**
     * 从记录中读取字段值；字段名不完全匹配时按忽略大小写查找。
     */
    private Object getRecordValue(JSONObject record, String field) {
        if (record == null || field == null) {
            return null;
        }
        if (record.containsKey(field)) {
            return record.get(field);
        }
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(field.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 关联键的规范化字符串：数值去除多余小数位，时间按毫秒值，其余按 toString。
     * 用于消除上游 Integer 与关联表 Long 等类型差异导致的匹配失败。
     */
    static String canonicalKey(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            try {
                return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
                // NaN / Infinity 等特殊数值直接使用字符串形式
            }
        }
        if (value instanceof java.util.Date) {
            return String.valueOf(((java.util.Date) value).getTime());
        }
        return value.toString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // 自动注入参数的 setters

    public void setSourceId(DatasourceInfo sourceId) {
        this.sourceId = sourceId;
    }

    public DatasourceInfo getSourceId() {
        return sourceId;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.sourceId = datasource;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getSchema() {
        return schema;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getTable() {
        return table;
    }

    public void setLocalField(String localField) {
        this.localField = localField;
    }

    public String getLocalField() {
        return localField;
    }

    public void setLookupField(String lookupField) {
        this.lookupField = lookupField;
    }

    public String getLookupField() {
        return lookupField;
    }

    public void setSelectColumns(String selectColumns) {
        this.selectColumns = selectColumns;
    }

    public String getSelectColumns() {
        return selectColumns;
    }

    public void setWhere(String where) {
        this.where = where;
    }

    public String getWhere() {
        return where;
    }

    public void setJoinType(String joinType) {
        this.joinType = joinType;
    }

    public String getJoinType() {
        return joinType;
    }

    public void setOutputPrefix(String outputPrefix) {
        this.outputPrefix = outputPrefix;
    }

    public String getOutputPrefix() {
        return outputPrefix;
    }

    public void setCache(boolean cache) {
        this.cache = cache;
    }

    public boolean isCache() {
        return cache;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public int getCacheSize() {
        return cacheSize;
    }
}
