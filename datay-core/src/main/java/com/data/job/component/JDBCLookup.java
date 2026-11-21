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
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBCLookup 组件 - 根据上游记录中的指定字段作为查询条件，查询另一个数据库的关联表，
 * 并将查询结果字段回填到上游记录中。
 *
 * <p>组件支持在同一个数据源下配置多张关联表：{@code tables} 数组中的每一项独立配置关联字段与
 * 回填字段，按数组顺序依次执行。后面的关联表可以引用前面已经回填到记录中的字段，从而实现链式 lookup。
 *
 * <p>处理流程：
 * <ol>
 *   <li>从每个 FlowFile 的记录中提取当前关联表的 {@code localField} 作为关联键；</li>
 *   <li>按 {@code batchSize} 分批执行 {@code SELECT ... FROM schema.table WHERE lookupField IN (...)}，
 *       避免逐行查询造成大量数据库往返；</li>
 *   <li>将查询结果按关联键回填到原记录：{@code LEFT} 保留未匹配记录并补空，
 *       {@code INNER} 丢弃未匹配记录；</li>
 *   <li>把关联表字段追加到 FlowFile 的表元数据中，供下游组件识别新增字段及类型。</li>
 * </ol>
 *
 * <p>为避免重复关联键反复查询，组件默认开启结果缓存（LRU，默认最多缓存 10000 个键）。
 *
 * <p>多表配置示例：
 * <pre>
 * {
 *   "sourceId": "dataSourceId",
 *   "schema": "demo",
 *   "cache": true,
 *   "batchSize": 500,
 *   "tables": [
 *     {
 *       "table": "customers",
 *       "localField": "customer_id",
 *       "lookupField": "id",
 *       "selectColumns": "name, region_id",
 *       "joinType": "LEFT",
 *       "outputPrefix": "customer_",
 *       "where": "status = 1"
 *     },
 *     {
 *       "table": "regions",
 *       "localField": "customer_region_id",
 *       "lookupField": "id",
 *       "selectColumns": "region_name",
 *       "joinType": "LEFT",
 *       "outputPrefix": "region_"
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>旧版单表配置（顶层 {@code table}、{@code localField}、{@code lookupField} 等）仍然兼容；
 * 当 {@code tables} 为空时，组件按单表配置执行一次 lookup。
 */
@ComponentRegister(
    value = "JDBCLookup",
    name = "JDBC关联查询",
    group = "数据处理",
    desc = "根据上游记录中的指定字段，批量查询另一个数据库的表，并将查询结果字段回填到记录中。支持同一数据源下多表顺序回填。",
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

    // 旧版单表配置：关联表表名
    private String table;

    // 旧版单表配置：上游记录中作为查询条件的字段
    private String localField;

    // 旧版单表配置：关联表中与 localField 对应的字段
    private String lookupField;

    // 旧版单表配置：需要回填的关联表字段，多个字段用英文逗号分隔；为空表示全部字段
    private String selectColumns;

    // 旧版单表配置：附加过滤条件（不包含 WHERE 关键字）
    private String where;

    // 旧版单表配置：关联类型：LEFT（默认，保留未匹配记录）或 INNER（仅保留匹配记录）
    private String joinType = DEFAULT_JOIN_TYPE;

    // 旧版单表配置：回填字段前缀，用于避免与上游同名字段冲突
    private String outputPrefix = "";

    // 多表配置：元素为 {table, localField, lookupField, selectColumns, where, joinType, outputPrefix}
    private Object tables;

    // 输出表名：多个关联/查询组件接入同一个 Join 时，配置不同表名以便下游区分；留空则沿用上游表名
    private String outputTable;

    // 是否缓存关联键的查询结果
    private boolean cache = true;

    // 单次 IN 查询的关联键数量
    private int batchSize = DEFAULT_BATCH_SIZE;

    // 缓存上限
    private int cacheSize = DEFAULT_CACHE_SIZE;

    // 关联键 -> 查询结果；Optional.empty() 表示已查询但未命中。
    // key 由「关联表查询结构 + 规范化关联键」组成，避免多表之间缓存串用。
    private final Map<String, Optional<JSONObject>> resultCache = Collections.synchronizedMap(
        new LinkedHashMap<String, Optional<JSONObject>>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Optional<JSONObject>> eldest) {
                return size() > effectiveCacheSize();
            }
        }
    );

    // 关联表元数据缓存：key 为 schema.table
    private final Map<String, TableMeta> lookupTableMetaMap = new ConcurrentHashMap<>();

    /**
     * 单个关联表的查询配置。
     */
    public static class LookupSpec {

        private String table;

        private String localField;

        private String lookupField;

        private String selectColumns;

        private String where;

        private String joinType;

        private String outputPrefix;

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getLocalField() {
            return localField;
        }

        public void setLocalField(String localField) {
            this.localField = localField;
        }

        public String getLookupField() {
            return lookupField;
        }

        public void setLookupField(String lookupField) {
            this.lookupField = lookupField;
        }

        public String getSelectColumns() {
            return selectColumns;
        }

        public void setSelectColumns(String selectColumns) {
            this.selectColumns = selectColumns;
        }

        public String getWhere() {
            return where;
        }

        public void setWhere(String where) {
            this.where = where;
        }

        public String getJoinType() {
            return joinType;
        }

        public void setJoinType(String joinType) {
            this.joinType = joinType;
        }

        public String getOutputPrefix() {
            return outputPrefix;
        }

        public void setOutputPrefix(String outputPrefix) {
            this.outputPrefix = outputPrefix;
        }
    }

    public JDBCLookup() {
        setType(ComponentType.OPERATOR);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile == null || flowFile.getAttribute("_end") != null) {
            return;
        }
        List<LookupSpec> specs = resolveAndValidateSpecs();

        JSONObject singleRecord = flowFile.getJsonObject();
        JSONArray records = flowFile.getJsonArray();
        if (singleRecord != null) {
            records = new JSONArray();
            records.add(singleRecord);
        }
        if (records == null || records.isEmpty()) {
            return;
        }
        int inputSize = records.size();

        try (Connection conn = DBUtils.getConnection(sourceId)) {
            for (int i = 0; i < specs.size(); i++) {
                LookupSpec spec = specs.get(i);
                loadLookupTableMeta(conn, spec);

                List<Object> keyValues = collectKeyValues(records, spec.getLocalField());
                Map<String, JSONObject> matchedRows = lookupRows(conn, spec, keyValues);
                records = enrichRecords(records, matchedRows, spec);

                logInfo(
                    "第 " +
                    (i + 1) +
                    " 个关联表 " +
                    spec.getTable().trim() +
                    " 完成，当前记录 " +
                    records.size() +
                    " 条"
                );
                if (records.isEmpty()) {
                    logInfo("本批次 " + inputSize + " 条记录均未匹配到关联表数据，跳过输出");
                    return;
                }
            }

            FlowFile outFile = new FlowFile();
            outFile.getAttributeMap().putAll(flowFile.getAttributeMap());
            if (singleRecord != null) {
                outFile.setJsonObject(records.getJSONObject(0));
            } else {
                outFile.setJsonArray(records);
            }
            updateTableMeta(outFile, flowFile, records, specs);
            writeRecords(outFile);
            logInfo("关联查询完成，输入 " + inputSize + " 条，输出 " + records.size() + " 条");
        } catch (SQLException e) {
            throw new RuntimeException("JDBCLookup 关联查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析并校验关联表配置。
     */
    private List<LookupSpec> resolveAndValidateSpecs() {
        if (sourceId == null) {
            throw new IllegalArgumentException("JDBCLookup 组件未配置数据源 sourceId");
        }
        List<LookupSpec> specs = resolveLookupSpecs();
        boolean multi = specs.size() > 1;
        for (int i = 0; i < specs.size(); i++) {
            LookupSpec spec = specs.get(i);
            String label = multi ? "第 " + (i + 1) + " 个关联表" : "关联表";
            if (isBlank(spec.getTable())) {
                throw new IllegalArgumentException("JDBCLookup 组件" + label + " table 不能为空");
            }
            if (isBlank(spec.getLocalField())) {
                throw new IllegalArgumentException("JDBCLookup 组件" + label + " 上游关联字段 localField 不能为空");
            }
            if (isBlank(spec.getLookupField())) {
                throw new IllegalArgumentException("JDBCLookup 组件" + label + " 关联表字段 lookupField 不能为空");
            }
        }
        return specs;
    }

    /**
     * 解析实际执行的关联表配置：优先使用 tables 数组，为空时回退到旧版单表配置。
     */
    List<LookupSpec> resolveLookupSpecs() {
        List<LookupSpec> specs = parseConfiguredTables();
        if (!specs.isEmpty()) {
            return specs;
        }
        List<LookupSpec> single = new ArrayList<>(1);
        single.add(legacySpec());
        return single;
    }

    /**
     * 将 tables 配置解析为关联表列表。
     */
    private List<LookupSpec> parseConfiguredTables() {
        JSONArray array = resolveTablesArray();
        if (array == null || array.isEmpty()) {
            return Collections.emptyList();
        }
        List<LookupSpec> specs = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (item instanceof LookupSpec) {
                specs.add((LookupSpec) item);
                continue;
            }
            specs.add(toLookupSpec(toJsonObject(item, i)));
        }
        return specs;
    }

    /**
     * 将 tables 配置归一化为 JSONArray，兼容 JSONArray / List / JSON 字符串 / Map。
     */
    private JSONArray resolveTablesArray() {
        if (tables == null) {
            return null;
        }
        if (tables instanceof JSONArray) {
            return (JSONArray) tables;
        }
        if (tables instanceof String) {
            String text = ((String) tables).trim();
            return text.isEmpty() ? null : JSONArray.parseArray(text);
        }
        if (tables instanceof List) {
            JSONArray array = new JSONArray();
            array.addAll((List<?>) tables);
            return array;
        }
        if (tables instanceof Map) {
            JSONArray array = new JSONArray();
            array.add(tables);
            return array;
        }
        throw new IllegalArgumentException("JDBCLookup 组件 tables 配置格式错误: " + tables.getClass());
    }

    @SuppressWarnings("unchecked")
    private JSONObject toJsonObject(Object item, int index) {
        if (item instanceof JSONObject) {
            return (JSONObject) item;
        }
        if (item instanceof Map) {
            return new JSONObject((Map<String, Object>) item);
        }
        if (item instanceof String) {
            String text = ((String) item).trim();
            if (!text.isEmpty()) {
                return JSONObject.parseObject(text);
            }
        }
        throw new IllegalArgumentException("JDBCLookup 组件第 " + (index + 1) + " 个关联表配置格式错误: " + item);
    }

    private LookupSpec toLookupSpec(JSONObject config) {
        LookupSpec spec = new LookupSpec();
        spec.setTable(config.getString("table"));
        spec.setLocalField(config.getString("localField"));
        spec.setLookupField(config.getString("lookupField"));
        spec.setSelectColumns(config.getString("selectColumns"));
        spec.setWhere(config.getString("where"));
        spec.setJoinType(config.getString("joinType"));
        spec.setOutputPrefix(config.getString("outputPrefix"));
        return spec;
    }

    /**
     * 旧版单表配置转换为 LookupSpec。
     */
    private LookupSpec legacySpec() {
        LookupSpec spec = new LookupSpec();
        spec.setTable(table);
        spec.setLocalField(localField);
        spec.setLookupField(lookupField);
        spec.setSelectColumns(selectColumns);
        spec.setWhere(where);
        spec.setJoinType(joinType);
        spec.setOutputPrefix(outputPrefix);
        return spec;
    }

    /**
     * 加载关联表元数据；失败时不阻塞任务，后续按查询结果推断字段类型。
     */
    private void loadLookupTableMeta(Connection conn, LookupSpec spec) {
        String key = metaKey(spec);
        if (lookupTableMetaMap.containsKey(key)) {
            return;
        }
        synchronized (this) {
            if (lookupTableMetaMap.containsKey(key)) {
                return;
            }
            try {
                TableMeta meta = DBUtils.getTableMetaData(conn, trimToNull(schema), spec.getTable().trim());
                if (meta != null) {
                    if (meta.getDbType() == null || meta.getDbType().trim().isEmpty()) {
                        meta.setDbType(resolveLookupDbType());
                    }
                    lookupTableMetaMap.put(key, meta);
                }
            } catch (SQLException e) {
                logWarn("获取关联表 " + spec.getTable() + " 元数据失败，将根据查询结果推断字段类型: " + e.getMessage());
            }
        }
    }

    /**
     * 收集本批记录中的关联键，按规范化的字符串去重。
     */
    private List<Object> collectKeyValues(JSONArray records, String localField) {
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
    private Map<String, JSONObject> lookupRows(Connection conn, LookupSpec spec, List<Object> keyValues) throws SQLException {
        Map<String, JSONObject> result = new HashMap<>();
        if (keyValues.isEmpty()) {
            return result;
        }

        String cachePrefix = specQueryKey(spec) + "\u0000";
        List<Object> missingValues = new ArrayList<>();
        Set<String> missingKeys = new HashSet<>();
        for (Object value : keyValues) {
            String key = canonicalKey(value);
            if (key == null) {
                continue;
            }
            if (cache) {
                Optional<JSONObject> cached = resultCache.get(cachePrefix + key);
                if (cached != null) {
                    if (cached.isPresent()) {
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
            Map<String, JSONObject> fetched = queryRows(conn, spec, chunk);
            for (Object value : chunk) {
                String key = canonicalKey(value);
                if (key == null) {
                    continue;
                }
                JSONObject row = fetched.get(key);
                if (cache) {
                    resultCache.put(cachePrefix + key, Optional.ofNullable(row));
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
    private Map<String, JSONObject> queryRows(Connection conn, LookupSpec spec, List<Object> keyValues) throws SQLException {
        String sql = buildLookupSql(spec, keyValues.size());
        logDebug("执行关联查询 SQL: " + sql + "，关联键数量: " + keyValues.size());
        Map<String, JSONObject> rows = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < keyValues.size(); i++) {
                ps.setObject(i + 1, keyValues.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                int keyIndex = findResultColumn(metaData, actualLookupField(spec));
                if (keyIndex < 0) {
                    throw new SQLException("查询结果中未找到关联字段: " + spec.getLookupField());
                }
                List<String> columnNames = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    String label = metaData.getColumnLabel(i);
                    columnNames.add(label == null || label.trim().isEmpty() ? metaData.getColumnName(i) : label);
                }
                boolean keySelected = isLookupFieldSelected(spec);
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
        return buildLookupSql(legacySpec(), valueCount);
    }

    /**
     * 按单个关联表配置构建查询 SQL。
     */
    String buildLookupSql(LookupSpec spec, int valueCount) {
        List<String> configured = parseConfiguredColumns(spec);
        StringBuilder sql = new StringBuilder("SELECT ");
        if (configured == null) {
            sql.append("*");
        } else {
            String keyColumn = actualLookupField(spec);
            List<String> selectColumns = new ArrayList<>();
            selectColumns.add(keyColumn);
            Set<String> seen = new HashSet<>();
            seen.add(keyColumn.toLowerCase(Locale.ROOT));
            for (String column : configured) {
                String actual = actualColumnName(spec, column);
                if (seen.add(actual.toLowerCase(Locale.ROOT))) {
                    selectColumns.add(actual);
                }
            }
            sql.append(String.join(", ", selectColumns));
        }
        sql.append(" FROM ").append(qualifiedTable(spec));
        sql.append(" WHERE ").append(actualLookupField(spec)).append(" IN (");
        for (int i = 0; i < valueCount; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");
        String specWhere = spec.getWhere();
        if (specWhere != null && !specWhere.trim().isEmpty()) {
            sql.append(" AND (").append(specWhere.trim()).append(")");
        }
        return sql.toString();
    }

    /**
     * 将查询结果回填到记录中。
     */
    JSONArray enrichRecords(JSONArray records, Map<String, JSONObject> matchedRows, LookupSpec spec) {
        JSONArray output = new JSONArray(records.size());
        boolean innerJoin = "INNER".equalsIgnoreCase(normalizeJoinType(spec));
        List<String> lookupColumns = resolveLookupColumns(spec);
        for (Object item : records) {
            if (!(item instanceof JSONObject)) {
                output.add(item);
                continue;
            }
            JSONObject record = (JSONObject) item;
            String key = canonicalKey(getRecordValue(record, spec.getLocalField()));
            JSONObject matched = key == null ? null : matchedRows.get(key);
            if (matched == null) {
                if (innerJoin) {
                    continue;
                }
                for (String column : lookupColumns) {
                    String outputName = applyPrefix(spec, column);
                    if (!record.containsKey(outputName)) {
                        record.put(outputName, null);
                    }
                }
            } else {
                for (Map.Entry<String, Object> entry : matched.entrySet()) {
                    record.put(applyPrefix(spec, entry.getKey()), entry.getValue());
                }
            }
            output.add(record);
        }
        return output;
    }

    /**
     * 合并上游表元数据与所有关联表字段元数据，供下游识别新增字段及类型。
     */
    private void updateTableMeta(FlowFile outFile, FlowFile inFile, JSONArray output, List<LookupSpec> specs) {
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

        for (LookupSpec spec : specs) {
            for (String column : resolveLookupColumns(spec)) {
                String outputName = applyPrefix(spec, column);
                ColumnMeta lookupColumn = findLookupColumn(spec, column);
                String type = lookupColumn == null ? null : convertType(lookupColumn.getType(), outputDbType);
                if (type == null || type.trim().isEmpty()) {
                    type = inferColumnType(output, outputName);
                }
                upsertColumn(outputMeta, outputName, type, lookupColumn);
            }
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

        // 支持自定义输出表名：多个关联/查询组件接入同一个 Join 时配置不同表名以便区分；
        // 留空时沿用上游表名（保持原有回填语义不变）。
        String resolvedTable = (outputTable != null && !outputTable.trim().isEmpty())
            ? outputTable.trim()
            : outputMeta.getTable();
        if (resolvedTable != null && !resolvedTable.trim().isEmpty()) {
            outputMeta.setTable(resolvedTable);
            outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, resolvedTable);
        }
        outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, outputMeta);
    }

    /**
     * 解析配置的输出字段；返回 null 表示输出全部字段。
     */
    List<String> parseConfiguredColumns() {
        return parseConfiguredColumns(legacySpec());
    }

    /**
     * 解析单个关联表配置的输出字段；返回 null 表示输出全部字段。
     */
    List<String> parseConfiguredColumns(LookupSpec spec) {
        String value = spec.getSelectColumns();
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        List<String> columns = new ArrayList<>();
        for (String column : value.split(",")) {
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
        return resolveLookupColumns(legacySpec());
    }

    /**
     * 解析单个关联表实际需要回填的字段名（已按关联表元数据修正大小写）。
     */
    List<String> resolveLookupColumns(LookupSpec spec) {
        List<String> configured = parseConfiguredColumns(spec);
        if (configured == null) {
            TableMeta meta = lookupTableMeta(spec);
            if (meta == null) {
                return Collections.emptyList();
            }
            List<String> columns = new ArrayList<>();
            for (ColumnMeta column : meta.columns()) {
                columns.add(column.getName());
            }
            return columns;
        }
        List<String> resolved = new ArrayList<>(configured.size());
        for (String column : configured) {
            resolved.add(actualColumnName(spec, column));
        }
        return resolved;
    }

    /**
     * 判断查询结果是否需要保留关联字段。配置为空或包含 * 时视为全选。
     */
    private boolean isLookupFieldSelected(LookupSpec spec) {
        List<String> configured = parseConfiguredColumns(spec);
        if (configured == null) {
            return true;
        }
        for (String column : configured) {
            if (column.equalsIgnoreCase(spec.getLookupField().trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关联表字段的实际名称：能按元数据匹配时使用元数据中的大小写。
     */
    private String actualColumnName(LookupSpec spec, String column) {
        ColumnMeta meta = findLookupColumn(spec, column);
        return meta == null ? column.trim() : meta.getName();
    }

    private String actualLookupField(LookupSpec spec) {
        return actualColumnName(spec, spec.getLookupField().trim());
    }

    private ColumnMeta findLookupColumn(LookupSpec spec, String column) {
        TableMeta meta = lookupTableMeta(spec);
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

    private TableMeta lookupTableMeta(LookupSpec spec) {
        return lookupTableMetaMap.get(metaKey(spec));
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

    private String qualifiedTable(LookupSpec spec) {
        String tableName = spec.getTable().trim();
        String schemaName = trimToNull(schema);
        return schemaName == null ? tableName : schemaName + "." + tableName;
    }

    private String metaKey(LookupSpec spec) {
        String tableName = spec.getTable() == null ? "" : spec.getTable().trim().toLowerCase(Locale.ROOT);
        String schemaName = trimToNull(schema);
        return schemaName == null ? tableName : schemaName.toLowerCase(Locale.ROOT) + "." + tableName;
    }

    /**
     * 查询缓存的结构化前缀：相同查询结构的多个配置项可以共享缓存。
     */
    private String specQueryKey(LookupSpec spec) {
        return nullToEmpty(spec.getTable()).trim().toLowerCase(Locale.ROOT) +
        "|" +
        nullToEmpty(spec.getLookupField()).trim().toLowerCase(Locale.ROOT) +
        "|" +
        nullToEmpty(spec.getSelectColumns()).trim().toLowerCase(Locale.ROOT) +
        "|" +
        nullToEmpty(spec.getWhere()).trim();
    }

    private String applyPrefix(LookupSpec spec, String column) {
        return nullToEmpty(spec.getOutputPrefix()) + column;
    }

    String normalizeJoinType() {
        return normalizeJoinType(joinType);
    }

    private String normalizeJoinType(LookupSpec spec) {
        return normalizeJoinType(spec.getJoinType());
    }

    private String normalizeJoinType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_JOIN_TYPE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
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
            Object value = getRecordValue(record, columnName);
            if (value != null) {
                return JsonTableMetaUtils.inferColumnType(value);
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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

    public void setTables(Object tables) {
        this.tables = tables;
    }

    public Object getTables() {
        return tables;
    }

    public void setOutputTable(String outputTable) {
        this.outputTable = outputTable;
    }

    public String getOutputTable() {
        return outputTable;
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
