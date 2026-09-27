package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONObject;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PostgreSQL 逻辑复制 {@code pgoutput} 协议解析器（proto_version = 1）。
 *
 * <p>负责把 pgoutput 的数据变更消息解析为按列名索引的 before/after 数据，
 * 并根据 PG 物理类型做基础的值转换，产出与 MySQL Binlog 采集一致的通用 CDC 事件模型。
 *
 * <p>支持的消息类型：
 * <ul>
 *   <li>{@code R} Relation：关系（表）元数据，解析后按关系 ID 缓存；</li>
 *   <li>{@code I} Insert、{@code U} Update、{@code D} Delete：数据变更；</li>
 *   <li>{@code T} Truncate：清空表；</li>
 *   <li>{@code B} Begin、{@code C} Commit：事务边界；</li>
 *   <li>{@code Y} Type、{@code O} Origin：忽略。</li>
 * </ul>
 */
public class PgOutputDecoder {

    private static final Logger log = LoggerFactory.getLogger(PgOutputDecoder.class);

    /** 关系（表）元数据解析器：根据 schema/table 读取业务字段信息。 */
    public interface TableMetaResolver {
        TableMeta resolve(String schema, String table) throws Exception;
    }

    /** 解码回调，由采集器实现，把解析结果转成通用 CDC 事件。 */
    public interface Callback {
        void onInsert(RelationInfo relation, JSONObject after);

        void onUpdate(RelationInfo relation, JSONObject before, JSONObject after);

        void onDelete(RelationInfo relation, JSONObject before);

        void onTruncate(List<RelationInfo> relations, boolean cascade);

        void onBegin(long finalLsn);

        void onCommit(long commitLsn);
    }

    /** 关系（表）信息。 */
    public static class RelationInfo {

        private final long relationId;
        private final String schema;
        private final String table;
        private final List<ColumnInfo> columns = new ArrayList<>();
        private TableMeta tableMeta;

        public RelationInfo(long relationId, String schema, String table) {
            this.relationId = relationId;
            this.schema = schema;
            this.table = table;
        }

        public long getRelationId() {
            return relationId;
        }

        public String getSchema() {
            return schema;
        }

        public String getTable() {
            return table;
        }

        public List<ColumnInfo> getColumns() {
            return columns;
        }

        public TableMeta getTableMeta() {
            return tableMeta;
        }

        public void setTableMeta(TableMeta tableMeta) {
            this.tableMeta = tableMeta;
        }
    }

    /** 关系中的列信息。 */
    public static class ColumnInfo {

        private final String name;
        private final boolean key;
        private final int typeOid;

        public ColumnInfo(String name, boolean key, int typeOid) {
            this.name = name;
            this.key = key;
            this.typeOid = typeOid;
        }

        public String getName() {
            return name;
        }

        public boolean isKey() {
            return key;
        }

        public int getTypeOid() {
            return typeOid;
        }
    }

    private final TableMetaResolver tableMetaResolver;
    private final Map<Long, RelationInfo> relations = new ConcurrentHashMap<>();

    public PgOutputDecoder(TableMetaResolver tableMetaResolver) {
        this.tableMetaResolver = tableMetaResolver;
    }

    public RelationInfo getRelation(long relationId) {
        return relations.get(relationId);
    }

    /**
     * 解码一条 pgoutput 消息。
     *
     * @param messageType 消息类型字符（缓冲区首字节）
     * @param buffer      消息体（在类型字节之后）
     * @param callback    解码回调
     */
    public void decode(char messageType, ByteBuffer buffer, Callback callback) {
        switch (messageType) {
            case 'R':
                decodeRelation(buffer);
                break;
            case 'I':
                decodeInsert(buffer, callback);
                break;
            case 'U':
                decodeUpdate(buffer, callback);
                break;
            case 'D':
                decodeDelete(buffer, callback);
                break;
            case 'T':
                decodeTruncate(buffer, callback);
                break;
            case 'C':
                decodeCommit(buffer, callback);
                break;
            case 'B':
                decodeBegin(buffer, callback);
                break;
            case 'Y':
            case 'O':
                // Type / Origin：忽略
                break;
            default:
                log.debug("忽略未处理的 pgoutput 消息类型: {}", messageType);
        }
    }

    private void decodeRelation(ByteBuffer buffer) {
        long relationId = readInt32(buffer);
        String schema = readCString(buffer);
        String table = readCString(buffer);
        buffer.get(); // replica identity
        int columnCount = readInt16(buffer) & 0xFFFF;

        RelationInfo relation = new RelationInfo(relationId, schema, table);
        for (int i = 0; i < columnCount; i++) {
            boolean key = (buffer.get() & 0x01) != 0;
            String name = readCString(buffer);
            int typeOid = readInt32(buffer);
            readInt32(buffer); // type modifier
            relation.getColumns().add(new ColumnInfo(name, key, typeOid));
        }

        if (tableMetaResolver != null) {
            try {
                relation.setTableMeta(tableMetaResolver.resolve(schema, table));
            } catch (Exception e) {
                log.warn("加载表元数据失败 {}.{}：{}", schema, table, e.getMessage());
            }
        }
        relations.put(relationId, relation);
        log.debug("解析 Relation 消息：{}.{}，列数={}", schema, table, columnCount);
    }

    private void decodeInsert(ByteBuffer buffer, Callback callback) {
        long relationId = readInt32(buffer);
        RelationInfo relation = relations.get(relationId);
        char tupleTag = (char) (buffer.get() & 0xFF); // 'N'
        if (relation == null || tupleTag != 'N') {
            return;
        }
        JSONObject after = readTuple(buffer, relation);
        callback.onInsert(relation, after);
    }

    private void decodeUpdate(ByteBuffer buffer, Callback callback) {
        long relationId = readInt32(buffer);
        RelationInfo relation = relations.get(relationId);
        if (relation == null) {
            return;
        }
        JSONObject before = null;
        char tag = (char) (buffer.get() & 0xFF);
        if (tag == 'K' || tag == 'O') {
            before = readTuple(buffer, relation);
            tag = (char) (buffer.get() & 0xFF);
        }
        if (tag != 'N') {
            return;
        }
        JSONObject after = readTuple(buffer, relation);
        callback.onUpdate(relation, before, after);
    }

    private void decodeDelete(ByteBuffer buffer, Callback callback) {
        long relationId = readInt32(buffer);
        RelationInfo relation = relations.get(relationId);
        if (relation == null) {
            return;
        }
        char tag = (char) (buffer.get() & 0xFF);
        if (tag != 'K' && tag != 'O') {
            return;
        }
        JSONObject before = readTuple(buffer, relation);
        callback.onDelete(relation, before);
    }

    private void decodeTruncate(ByteBuffer buffer, Callback callback) {
        int relationCount = readInt32(buffer);
        byte options = buffer.get();
        List<RelationInfo> targets = new ArrayList<>();
        for (int i = 0; i < relationCount; i++) {
            RelationInfo relation = relations.get((long) readInt32(buffer));
            if (relation != null) {
                targets.add(relation);
            }
        }
        callback.onTruncate(targets, (options & 0x01) != 0);
    }

    private void decodeBegin(ByteBuffer buffer, Callback callback) {
        long finalLsn = readInt64(buffer); // 事务提交记录 LSN
        readInt64(buffer); // commit timestamp
        readInt32(buffer); // xid
        callback.onBegin(finalLsn);
    }

    private void decodeCommit(ByteBuffer buffer, Callback callback) {        buffer.get(); // flags
        long commitLsn = readInt64(buffer);
        readInt64(buffer); // end LSN
        readInt64(buffer); // commit timestamp
        callback.onCommit(commitLsn);
    }

    /**
     * 读取一个 TupleData，按关系列名生成 JSON 对象。
     */
    private JSONObject readTuple(ByteBuffer buffer, RelationInfo relation) {
        JSONObject row = new JSONObject();
        List<ColumnInfo> columns = relation.getColumns();
        int columnCount = readInt16(buffer) & 0xFFFF;
        for (int i = 0; i < columnCount; i++) {
            char tag = (char) (buffer.get() & 0xFF);
            ColumnInfo column = i < columns.size() ? columns.get(i) : null;
            String columnName = column != null ? column.getName() : ("col_" + i);

            switch (tag) {
                case 'n':
                    row.put(columnName, null);
                    break;
                case 'u':
                    // 未变更的 TOAST 字段，逻辑复制不携带值
                    log.warn("表 {}.{} 字段 {} 为未变更 TOAST，事件中不携带该值", relation.getSchema(), relation.getTable(), columnName);
                    break;
                case 't':
                case 'b':
                    int length = readInt32(buffer);
                    byte[] data = new byte[length];
                    buffer.get(data);
                    String text = new String(data, StandardCharsets.UTF_8);
                    row.put(columnName, convertValue(resolveType(relation, column, columnName), text, tag == 'b'));
                    break;
                default:
                    log.warn("未知的 TupleData 标记 '{}'，表 {}.{}", tag, relation.getSchema(), relation.getTable());
                    return row;
            }
        }
        return row;
    }

    private String resolveType(RelationInfo relation, ColumnInfo column, String columnName) {
        TableMeta tableMeta = relation.getTableMeta();
        if (tableMeta != null) {
            for (ColumnMeta meta : tableMeta.columns()) {
                if (columnName.equals(meta.getName())) {
                    return meta.getType();
                }
            }
        }
        return null;
    }

    /**
     * 根据 PG 物理类型把文本值转换为下游更易写入的目标类型。
     */
    static Object convertValue(String typeName, String text, boolean binary) {
        if (text == null) {
            return null;
        }
        String type = typeName == null ? "" : typeName.toLowerCase();
        try {
            switch (type) {
                case "bool":
                case "boolean":
                    return "t".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text) || "1".equals(text);
                case "int2":
                case "int4":
                case "int8":
                case "smallint":
                case "integer":
                case "bigint":
                case "serial":
                case "bigserial":
                    return Long.parseLong(text.trim());
                case "float4":
                case "float8":
                case "real":
                case "double precision":
                    return Double.parseDouble(text.trim());
                case "numeric":
                case "decimal":
                    return new BigDecimal(text.trim());
                case "bytea":
                    return decodeBytea(text);
                case "date":
                    return LocalDate.parse(text.trim());
                case "time":
                    return LocalTime.parse(text.trim());
                case "timetz":
                    return LocalTime.parse(stripTimeZone(text.trim()));
                case "timestamp":
                    return parseLocalDateTime(text);
                case "timestamptz":
                    return parseOffsetDateTime(text);
                default:
                    return text;
            }
        } catch (Exception e) {
            log.debug("字段类型 {} 值 [{}] 转换失败，按字符串处理：{}", type, text, e.getMessage());
            return text;
        }
    }

    private static LocalDateTime parseLocalDateTime(String text) {
        String normalized = text.trim().replace(' ', 'T');
        return LocalDateTime.parse(normalized);
    }

    private static LocalDateTime parseOffsetDateTime(String text) {
        String normalized = normalizeOffset(text.trim().replace(' ', 'T'));
        try {
            return OffsetDateTime.parse(normalized).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.parse(stripTimeZone(normalized));
        }
    }

    private static final Pattern OFFSET_PATTERN = Pattern.compile("([+-]\\d{2})(\\d{2})?$");

    private static String normalizeOffset(String value) {
        Matcher matcher = OFFSET_PATTERN.matcher(value);
        if (matcher.find()) {
            String offset = matcher.group(2) == null ? matcher.group(1) + ":00" : matcher.group(1) + ":" + matcher.group(2);
            return value.substring(0, matcher.start()) + offset;
        }
        return value;
    }

    private static String stripTimeZone(String value) {
        return value.replaceAll("[+-]\\d{2}(:?\\d{2})?$", "").trim();
    }

    private static byte[] decodeBytea(String text) {
        String value = text.trim();
        if (value.startsWith("\\x")) {
            value = value.substring(2);
        }
        int length = value.length();
        if (length % 2 != 0) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        byte[] result = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            result[i / 2] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
        }
        return result;
    }

    private static int readInt16(ByteBuffer buffer) {
        return buffer.getShort();
    }

    private static int readInt32(ByteBuffer buffer) {
        return buffer.getInt();
    }

    private static long readInt64(ByteBuffer buffer) {
        return buffer.getLong();
    }

    private static String readCString(ByteBuffer buffer) {
        int start = buffer.position();
        int length = 0;
        while (buffer.hasRemaining() && buffer.get() != 0) {
            length++;
        }
        byte[] bytes = new byte[length];
        int end = buffer.position();
        buffer.position(start);
        buffer.get(bytes);
        buffer.position(end);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
