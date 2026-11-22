package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.expression.ParameterUtil;
import com.data.job.ComponentField;
import com.data.job.ComponentFieldType;
import com.data.job.ComponentFieldValueFormat;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.DebugLimitReachedException;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.component.arrow.ArrowRecordConverter;
import com.data.job.component.arrow.DorisArrowFlightClient;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Doris Arrow Flight SQL 输入组件。
 *
 * <p>通过 Doris 2.1+ 提供的 Arrow Flight SQL 服务流式读取数据：先由 FE 执行 SQL 获取
 * {@code FlightInfo}，再按 endpoint 中的 BE 地址建立 Flight 连接，逐批消费 Arrow
 * RecordBatch 并下发 FlowFile。</p>
 *
 * <p>表结构等元数据仍通过 Doris 的 MySQL 协议读取，保证下游自动建表、类型转换等逻辑
 * 与现有 JDBC 输入组件一致。调试模式下只读取采样上限（默认 100）条记录，并把
 * {@code LIMIT} 下推到 Doris。</p>
 */
@ComponentRegister(
    value = "DorisArrowFlightInput",
    name = "Doris读取",
    group = "数据输入",
    desc = "通过 Arrow Flight SQL 从 Apache Doris 流式读取数据，支持多表、增量同步与调试采样。",
    order = 15
)
public class DorisArrowFlightInput extends FlowComponent {

    private static final int DEFAULT_BATCH_SIZE = 10000;
    private static final int DEFAULT_ARROW_FLIGHT_PORT = 8070;
    private static final int DEFAULT_ARROW_MEMORY_MB = 512;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @ComponentField(
        key = "sourceId",
        label = "Doris 数据源",
        type = ComponentFieldType.DATASOURCE,
        required = true,
        dataSourceType = "DORIS",
        schemaField = "schema",
        group = "基础配置",
        order = 10,
        help = "选择 Doris 数据源，并在选择弹窗中确认 Schema；表元数据通过该数据源的 MySQL 查询端口读取"
    )
    private DatasourceInfo sourceId;

    /**
     * Doris 数据库名。由数据源选择弹窗在选定 Schema 后自动回写，不再单独作为表单字段展示；
     * 保留该属性用于兼容已有任务配置，未配置时回退到数据源的 dbschema。
     */
    private String schema;

    @ComponentField(
        key = "table",
        label = "表名",
        type = ComponentFieldType.TABLE,
        multiple = true,
        valueFormat = ComponentFieldValueFormat.COMMA_STRING,
        dataSourceField = "sourceId",
        schemaField = "schema",
        group = "基础配置",
        order = 30,
        help = "可多选；不选择任何表时同步该 schema 下的所有表"
    )
    private String table;

    @ComponentField(
        key = "where",
        label = "过滤条件",
        type = ComponentFieldType.TEXTAREA,
        group = "高级配置",
        order = 10,
        placeholder = "例如 status = 'PAID'",
        help = "不需要写 WHERE；支持 ${attr:属性名}、${属性名}、#{...} 等动态参数，字符串和日期需自行加引号"
    )
    private String where;

    @ComponentField(
        key = "incrColumn",
        label = "增量字段",
        type = ComponentFieldType.TEXTAREA,
        group = "高级配置",
        order = 20,
        placeholder = "单表填字段名；多表填 {\"t1\":\"id\",\"t2\":\"update_time\"}",
        help = "留空表示全量读取；增量水位会随 FlowFile 传递并在下游落库时保存"
    )
    private String incrColumn;

    @ComponentField(
        key = "arrowFlightHost",
        label = "Arrow Flight 主机",
        type = ComponentFieldType.INPUT,
        group = "Arrow Flight",
        order = 10,
        placeholder = "默认取数据源主机",
        help = "Doris FE 地址；留空时使用数据源 hostname/URL 中的主机"
    )
    private String arrowFlightHost;

    @ComponentField(
        key = "arrowFlightPort",
        label = "Arrow Flight 端口",
        type = ComponentFieldType.NUMBER,
        defaultValue = "8070",
        min = 1,
        max = 65535,
        step = 1,
        group = "Arrow Flight",
        order = 20,
        help = "Doris FE 的 arrow_flight_sql_port，官方示例默认 8070"
    )
    private Integer arrowFlightPort;

    @ComponentField(
        key = "useEncryption",
        label = "使用 TLS",
        type = ComponentFieldType.SWITCH,
        defaultValue = "false",
        group = "Arrow Flight",
        order = 30,
        help = "Arrow Flight SQL 是否使用 TLS 加密连接"
    )
    private Boolean useEncryption;

    @ComponentField(
        key = "batchSize",
        label = "批大小",
        type = ComponentFieldType.NUMBER,
        defaultValue = "10000",
        min = 1,
        group = "高级配置",
        order = 30,
        help = "每个 FlowFile 承载的记录数，默认 10000"
    )
    private Integer batchSize;

    @ComponentField(
        key = "arrowMaxMemoryMb",
        label = "Arrow 内存上限(MB)",
        type = ComponentFieldType.NUMBER,
        defaultValue = "512",
        min = 64,
        group = "高级配置",
        order = 40,
        help = "Arrow 内存分配器上限，防止大结果集导致内存溢出"
    )
    private Integer arrowMaxMemoryMb;

    private final Map<String, Object> lastIncrValues = new HashMap<>();

    private volatile boolean stopped = false;

    private volatile DorisArrowFlightClient activeClient;

    // 调试模式下当前组件已读取的行数，保证多表合计不超过采样上限
    private int debugRowsEmitted = 0;

    public DorisArrowFlightInput() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 结束信号只用于通知，不重复读取
        if (flowFile != null && flowFile.getAttribute("_end") != null) {
            return;
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("Doris 数据源不能为空");
        }
        if (sourceId.getUsername() == null || sourceId.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Doris 用户名不能为空");
        }

        stopped = false;
        debugRowsEmitted = 0;
        try (Connection metaConnection = DBUtils.getConnection(sourceId)) {
            String dbType = DBUtils.getDBType(sourceId.getType(), sourceId.getUrl());
            List<String> tables = resolveTables(metaConnection);
            Map<String, String> incrColumns = resolveIncrColumns(tables);
            loadIncrStatus(incrColumns);

            for (String tableName : tables) {
                if (stopped) {
                    break;
                }
                if (debugRowLimit() > 0 && debugRowsEmitted >= debugRowLimit()) {
                    break;
                }
                TableMeta srcTable = DBUtils.getTableMetaData(metaConnection, resolveSchema(), tableName);
                if (srcTable == null) {
                    throw new IllegalArgumentException("Doris 表不存在或无法读取元数据: " + tableName);
                }
                srcTable.setDbType(dbType);
                migrateTable(srcTable, incrColumns.get(tableName), flowFile);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Doris 元数据读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取单表数据并按批下发。
     */
    void migrateTable(TableMeta srcTable, String tableIncrColumn, FlowFile flowFile) {
        String query = buildQuery(srcTable, tableIncrColumn, flowFile);
        logInfo("执行 Arrow Flight SQL: " + query);

        int debugLimit = debugRowLimit();
        int remainingDebugRows = debugLimit > 0 ? Math.max(0, debugLimit - debugRowsEmitted) : Integer.MAX_VALUE;
        int effectiveBatchSize = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        List<JSONObject> batchRecords = new ArrayList<>(Math.min(effectiveBatchSize, 1024));
        int[] total = { 0 };

        try (DorisArrowFlightClient client = createArrowClient()) {
            activeClient = client;
            client.query(query, root -> {
                int rows = root.getRowCount();
                for (int row = 0; row < rows; row++) {
                    if (stopped || total[0] >= remainingDebugRows) {
                        return false;
                    }
                    JSONObject record = ArrowRecordConverter.toRecord(root, row);
                    updateIncrValue(srcTable, tableIncrColumn, record);
                    batchRecords.add(record);
                    total[0]++;
                    debugRowsEmitted++;

                    if (batchRecords.size() >= effectiveBatchSize) {
                        emitBatch(batchRecords, srcTable);
                        batchRecords.clear();
                    }
                    if (debugLimit > 0 && debugRowsEmitted >= debugLimit) {
                        logInfo("调试模式：达到采样行数上限 " + debugLimit);
                        return false;
                    }
                }
                return true;
            });

            if (!stopped && (!batchRecords.isEmpty() || (total[0] == 0 && emitEmptyBatch()))) {
                emitBatch(batchRecords, srcTable);
            }
            logInfo("表 " + srcTable.getTable() + " 读取完成，共处理 " + total[0] + " 条记录");
        } catch (DebugLimitReachedException e) {
            throw e;
        } catch (Exception e) {
            if (stopped) {
                logWarn("组件已停止，忽略 Arrow Flight 读取异常: " + e.getMessage());
                return;
            }
            throw new RuntimeException("Doris Arrow Flight 读取失败: " + e.getMessage(), e);
        } finally {
            activeClient = null;
        }
    }

    /**
     * 构建查询 SQL。调试模式下追加 LIMIT，把采样上限下推到 Doris。
     */
    String buildQuery(TableMeta srcTable, String tableIncrColumn, FlowFile flowFile) {
        StringBuilder query = new StringBuilder("SELECT * FROM ");
        String tableSchema = srcTable.getSchema();
        if (tableSchema == null || tableSchema.trim().isEmpty()) {
            tableSchema = resolveSchema();
        }
        if (tableSchema != null && !tableSchema.trim().isEmpty()) {
            query.append(quoteIdentifier(tableSchema)).append('.');
        }
        query.append(quoteIdentifier(srcTable.getTable()));

        List<String> conditions = new ArrayList<>();
        String processedWhere = resolveWhere(where, flowFile);
        if (processedWhere != null && !processedWhere.trim().isEmpty()) {
            conditions.add("(" + processedWhere.trim() + ")");
        }

        boolean hasIncrCondition = false;
        if (tableIncrColumn != null && !tableIncrColumn.trim().isEmpty()) {
            Object lastIncrValue = lastIncrValues.get(srcTable.getTable());
            if (lastIncrValue != null) {
                conditions.add(quoteIdentifier(tableIncrColumn) + " > " + formatIncrLiteral(lastIncrValue));
                hasIncrCondition = true;
            }
        }

        if (!conditions.isEmpty()) {
            query.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (hasIncrCondition) {
            query.append(" ORDER BY ").append(quoteIdentifier(tableIncrColumn)).append(" ASC");
        }

        int debugLimit = debugRowLimit();
        if (debugLimit > 0) {
            int remaining = Math.max(0, debugLimit - debugRowsEmitted);
            query.append(" LIMIT ").append(remaining);
        }
        return query.toString();
    }

    /**
     * 解析本次要读取的表。table 为空时读取 schema 下的全部普通表。
     */
    List<String> resolveTables(Connection metaConnection) throws SQLException {
        String tableSchema = resolveSchema();
        if (table == null || table.trim().isEmpty()) {
            if (tableSchema == null || tableSchema.trim().isEmpty()) {
                throw new IllegalArgumentException("table 和 schema 不能同时为空");
            }
            List<TableMeta> tableList = DBUtils.getTableList(metaConnection, tableSchema);
            if (tableList.isEmpty()) {
                throw new IllegalArgumentException("schema " + tableSchema + " 下没有找到任何表");
            }
            List<String> tables = new ArrayList<>(tableList.size());
            for (TableMeta tableMeta : tableList) {
                String tableName = tableMeta.getTable();
                if (tableName == null || tableName.trim().isEmpty()) {
                    continue;
                }
                // 备份表、临时表不参与同步
                if (tableName.endsWith("_backup") || tableName.endsWith("_tmp")) {
                    continue;
                }
                tables.add(tableName.trim());
            }
            logInfo("获取 schema " + tableSchema + " 下的所有表，共 " + tables.size() + " 个表");
            return tables;
        }

        List<String> tables = new ArrayList<>();
        for (String tableName : table.split(",")) {
            String trimmed = tableName.trim();
            if (!trimmed.isEmpty()) {
                tables.add(trimmed);
            }
        }
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("table 参数格式错误，应包含至少一个表名");
        }
        return tables;
    }

    /**
     * 解析增量字段配置（JSON 格式，兼容单表纯字段名）。
     */
    Map<String, String> resolveIncrColumns(List<String> tables) {
        Map<String, String> incrColumns = new LinkedHashMap<>();
        if (incrColumn == null || incrColumn.trim().isEmpty()) {
            return incrColumns;
        }
        String config = incrColumn.trim();
        try {
            JSONObject json = JSONObject.parseObject(config);
            for (String key : json.keySet()) {
                incrColumns.put(key, json.getString(key));
            }
        } catch (Exception e) {
            if (tables.size() == 1) {
                incrColumns.put(tables.get(0), config);
            } else {
                logWarn("多表同步时 incrColumn 应为 JSON 格式，当前配置已忽略: " + config);
            }
        }
        return incrColumns;
    }

    private void loadIncrStatus(Map<String, String> incrColumns) {
        for (String tableName : incrColumns.keySet()) {
            Object lastValue = getStatus("lastIncrValue." + tableName);
            if (lastValue != null) {
                lastIncrValues.put(tableName, lastValue);
                logInfo("加载表 " + tableName + " 的上次增量值: " + lastValue);
            }
        }
    }

    private void updateIncrValue(TableMeta srcTable, String tableIncrColumn, JSONObject record) {
        if (tableIncrColumn == null || tableIncrColumn.trim().isEmpty()) {
            return;
        }
        Object value = findIgnoreCase(record, tableIncrColumn.trim());
        if (value == null) {
            return;
        }
        Object current = lastIncrValues.get(srcTable.getTable());
        if (current == null || compareIncrValue(value, current) > 0) {
            lastIncrValues.put(srcTable.getTable(), value);
        }
    }

    private Object findIgnoreCase(JSONObject record, String key) {
        if (record.containsKey(key)) {
            return record.get(key);
        }
        for (String candidate : record.keySet()) {
            if (candidate.equalsIgnoreCase(key)) {
                return record.get(candidate);
            }
        }
        return null;
    }

    /**
     * 下发一批记录。增量水位随 FlowFile 的 status 传递，由下游 sink 落库时统一保存。
     */
    void emitBatch(List<JSONObject> batchRecords, TableMeta srcTable) {
        JSONArray jsonRecords = new JSONArray();
        jsonRecords.addAll(batchRecords);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(jsonRecords);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, srcTable);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, srcTable.getTable());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, srcTable.getSchema());

        Object lastValue = lastIncrValues.get(srcTable.getTable());
        if (lastValue != null) {
            flowFile.setStatus(getId() + ".lastIncrValue." + srcTable.getTable(), formatIncrStatus(lastValue));
        }
        writeRecords(flowFile);
        if (!batchRecords.isEmpty()) {
            logInfo("已下发 " + batchRecords.size() + " 条记录, table: " + srcTable.getTable());
        }
    }

    private boolean emitEmptyBatch() {
        return true;
    }

    private DorisArrowFlightClient createArrowClient() throws Exception {
        String host = resolveArrowFlightHost();
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("无法解析 Doris Arrow Flight 主机，请配置 arrowFlightHost 或检查数据源主机");
        }
        int port = resolveArrowFlightPort();
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("非法的 Doris Arrow Flight 端口: " + port);
        }
        long memoryBytes = resolveArrowMaxMemoryBytes();
        boolean tls = Boolean.TRUE.equals(useEncryption);
        return DorisArrowFlightClient.connect(
            host.trim(),
            port,
            sourceId.getUsername(),
            sourceId.getPassword(),
            tls,
            memoryBytes
        );
    }

    private String resolveArrowFlightHost() {
        if (arrowFlightHost != null && !arrowFlightHost.trim().isEmpty()) {
            return arrowFlightHost.trim();
        }
        if (sourceId.getHostname() != null && !sourceId.getHostname().trim().isEmpty()) {
            return sourceId.getHostname().trim();
        }
        try {
            return DBUtils.parseConnectionUrl(sourceId).getHostname();
        } catch (Exception ignored) {
            return null;
        }
    }

    private int resolveArrowFlightPort() {
        if (arrowFlightPort != null && arrowFlightPort > 0) {
            return arrowFlightPort;
        }
        Map<String, String> extraParams = sourceId.getExtraParams();
        if (extraParams != null) {
            for (String key : new String[] { "arrow_flight_sql_port", "arrowFlightSqlPort", "arrow_flight_port", "arrowFlightPort" }) {
                String value = extraParams.get(key);
                if (value != null && !value.trim().isEmpty()) {
                    try {
                        return Integer.parseInt(value.trim());
                    } catch (NumberFormatException ignored) {
                        // 忽略非法扩展参数，继续使用默认端口
                    }
                }
            }
        }
        return DEFAULT_ARROW_FLIGHT_PORT;
    }

    private long resolveArrowMaxMemoryBytes() {
        int mb = arrowMaxMemoryMb == null || arrowMaxMemoryMb <= 0 ? DEFAULT_ARROW_MEMORY_MB : arrowMaxMemoryMb;
        return (long) mb * 1024 * 1024;
    }

    private int debugRowLimit() {
        if (getContext() == null || !getContext().isDebugMode()) {
            return 0;
        }
        return getContext().getDebugRowLimit();
    }

    String resolveSchema() {
        if (schema != null && !schema.trim().isEmpty()) {
            return schema.trim();
        }
        return sourceId == null ? null : sourceId.getDbschema();
    }

    private String resolveWhere(String where, FlowFile flowFile) {
        if (where == null || where.trim().isEmpty()) {
            return where;
        }
        try {
            ParameterUtil.ReplacementResult result = ParameterUtil.replaceParametersStrict(where, flowFile, null);
            if (result.hasUnresolved()) {
                logWarn("where 条件中存在未解析的动态参数，将按原样拼接: " + result.getUnresolvedParameters());
            }
            return result.getText();
        } catch (Exception e) {
            logWarn("where 条件动态参数替换失败，按原条件执行: " + e.getMessage());
            return where;
        }
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private String formatIncrLiteral(Object value) {
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return "'" + DATE_TIME_FORMATTER.format(dateTime) + "'";
        }
        if (value instanceof LocalDate date) {
            return "'" + DATE_FORMATTER.format(date) + "'";
        }
        if (value instanceof LocalTime time) {
            return "'" + TIME_FORMATTER.format(time) + "'";
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return "'" + offsetDateTime.toLocalDateTime().format(DATE_TIME_FORMATTER) + "'";
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return "'" + zonedDateTime.toLocalDateTime().format(DATE_TIME_FORMATTER) + "'";
        }
        if (value instanceof java.sql.Date sqlDate) {
            return "'" + sqlDate.toLocalDate().format(DATE_FORMATTER) + "'";
        }
        if (value instanceof java.sql.Time sqlTime) {
            return "'" + sqlTime.toLocalTime().format(TIME_FORMATTER) + "'";
        }
        if (value instanceof java.util.Date date) {
            return "'" + DATE_TIME_FORMATTER.format(
                date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            ) + "'";
        }
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }

    /**
     * 把增量水位格式化为 Doris 可解析的字符串，避免 LocalDateTime 的 T 分隔符被持久化到状态。
     */
    private String formatIncrStatus(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return DATE_TIME_FORMATTER.format(dateTime);
        }
        if (value instanceof LocalDate date) {
            return DATE_FORMATTER.format(date);
        }
        if (value instanceof LocalTime time) {
            return TIME_FORMATTER.format(time);
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime().format(DATE_TIME_FORMATTER);
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toLocalDateTime().format(DATE_TIME_FORMATTER);
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().format(DATE_FORMATTER);
        }
        if (value instanceof java.sql.Time sqlTime) {
            return sqlTime.toLocalTime().format(TIME_FORMATTER);
        }
        if (value instanceof java.util.Date date) {
            return DATE_TIME_FORMATTER.format(
                date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            );
        }
        return String.valueOf(value);
    }

    /**
     * 按数值、时间、字符串的优先级比较增量值。
     */
    int compareIncrValue(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Number && right instanceof Number) {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
        }
        BigDecimal leftNumber = toNumber(left);
        BigDecimal rightNumber = toNumber(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber);
        }
        Long leftEpoch = toEpochMillis(left);
        Long rightEpoch = toEpochMillis(right);
        if (leftEpoch != null && rightEpoch != null) {
            return Long.compare(leftEpoch, rightEpoch);
        }
        if (left instanceof Boolean leftBool && right instanceof Boolean rightBool) {
            return Boolean.compare(leftBool, rightBool);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private BigDecimal toNumber(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof CharSequence text) {
            String trimmed = text.toString().trim();
            if (trimmed.matches("[-+]?\\d+(\\.\\d+)?")) {
                try {
                    return new BigDecimal(trimmed);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Long toEpochMillis(Object value) {
        if (value instanceof java.util.Date date) {
            return date.getTime();
        }
        if (value instanceof Instant instant) {
            return instant.toEpochMilli();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().toEpochMilli();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant().toEpochMilli();
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof LocalTime time) {
            return time.toNanoOfDay() / 1_000_000L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence text) {
            String trimmed = text.toString().trim();
            try {
                return Timestamp.valueOf(trimmed).getTime();
            } catch (IllegalArgumentException ignored) {
                // 继续尝试 ISO 格式
            }
            try {
                return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            } catch (Exception ignored) {
                // 非日期格式
            }
            try {
                return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            } catch (Exception ignored) {
                // 非日期格式
            }
        }
        return null;
    }

    @Override
    public void stop() {
        stopped = true;
        DorisArrowFlightClient client = activeClient;
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
                // 关闭异常不影响停止流程
            }
        }
        super.stop();
    }

    // 自动注入参数的 setters

    public void setSourceId(DatasourceInfo sourceId) {
        this.sourceId = sourceId;
    }

    public DatasourceInfo getSourceId() {
        return sourceId;
    }

    /**
     * 兼容直接使用 {@code datasource} 配置 key 的直接引擎调用。
     */
    public void setDatasource(DatasourceInfo datasource) {
        this.sourceId = datasource;
    }

    public DatasourceInfo getDatasource() {
        return sourceId;
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

    public void setWhere(String where) {
        this.where = where;
    }

    public String getWhere() {
        return where;
    }

    public void setIncrColumn(String incrColumn) {
        this.incrColumn = incrColumn;
    }

    public String getIncrColumn() {
        return incrColumn;
    }

    public void setArrowFlightHost(String arrowFlightHost) {
        this.arrowFlightHost = arrowFlightHost;
    }

    public String getArrowFlightHost() {
        return arrowFlightHost;
    }

    public void setArrowFlightPort(Integer arrowFlightPort) {
        this.arrowFlightPort = arrowFlightPort;
    }

    public Integer getArrowFlightPort() {
        return arrowFlightPort;
    }

    public void setUseEncryption(Boolean useEncryption) {
        this.useEncryption = useEncryption;
    }

    public Boolean getUseEncryption() {
        return useEncryption;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setArrowMaxMemoryMb(Integer arrowMaxMemoryMb) {
        this.arrowMaxMemoryMb = arrowMaxMemoryMb;
    }

    public Integer getArrowMaxMemoryMb() {
        return arrowMaxMemoryMb;
    }

    /**
     * 设置上次增量值（用于测试或手动设置）。
     */
    void setLastIncrValue(String tableName, Object lastIncrValue) {
        this.lastIncrValues.put(tableName, lastIncrValue);
    }

    Object getLastIncrValue(String tableName) {
        return this.lastIncrValues.get(tableName);
    }
}
