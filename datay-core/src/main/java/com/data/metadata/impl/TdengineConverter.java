package com.data.metadata.impl;

import com.data.metadata.AllDataType;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.TypeConverter;
import com.data.metadata.util.DBUtils;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TDengine 类型转换与 DDL 生成器。
 *
 * <p>TDengine 是时序数据库，表结构约束与关系型数据库不同：
 * <ul>
 *   <li>每个表必须有一个 {@code TIMESTAMP} 时间列，且必须位于第一列；</li>
 *   <li>没有主键、自增、默认值等概念；</li>
 *   <li>同一时间戳重复写入会覆盖旧值，因此 UPDATE 模式直接复用 INSERT；</li>
 *   <li>字符串推荐使用 {@code NCHAR}（Unicode），超长文本使用 {@code VARCHAR}；</li>
 *   <li>超级表（STable）不能直接执行普通 INSERT，需要 {@code USING ... TAGS ...}。</li>
 * </ul>
 */
public class TdengineConverter implements TypeConverter {

    enum TdengineType {
        TIMESTAMP,
        BOOL,
        TINYINT,
        SMALLINT,
        INT,
        BIGINT,
        FLOAT,
        DOUBLE,
        NCHAR,
        VARCHAR,
        VARBINARY,
    }

    /** 通用逻辑类型 -> TDengine 物理类型。 */
    public static final Map<String, String> commonDataType2ColumnType = new HashMap<>();

    static {
        DatabaseConverter.register(DBType.TDENGINE.name(), new TdengineConverter());

        // 数值类型
        put(AllDataType.TINYINT, TdengineType.TINYINT);
        put(AllDataType.SMALLINT, TdengineType.SMALLINT);
        put(AllDataType.MEDIUMINT, TdengineType.INT);
        put(AllDataType.INT, TdengineType.INT);
        put(AllDataType.INTEGER, TdengineType.INT);
        put(AllDataType.BIGINT, TdengineType.BIGINT);
        put(AllDataType.LARGEINT, TdengineType.BIGINT);
        put(AllDataType.LONG, TdengineType.BIGINT);
        put(AllDataType.YEAR, TdengineType.SMALLINT);
        put(AllDataType.FLOAT, TdengineType.FLOAT);
        put(AllDataType.FLOAT4, TdengineType.FLOAT);
        put(AllDataType.BINARY_FLOAT, TdengineType.FLOAT);
        put(AllDataType.FLOAT8, TdengineType.DOUBLE);
        put(AllDataType.DOUBLE, TdengineType.DOUBLE);
        put(AllDataType.BINARY_DOUBLE, TdengineType.DOUBLE);
        put(AllDataType.DECIMAL, TdengineType.DOUBLE);
        put(AllDataType.NUMERIC, TdengineType.DOUBLE);
        put(AllDataType.NUMBER, TdengineType.DOUBLE);
        put(AllDataType.MONEY, TdengineType.DOUBLE);

        // 字符类型（TDengine 推荐 NCHAR 存储 Unicode 文本）
        put(AllDataType.STRING, TdengineType.NCHAR);
        put(AllDataType.CHAR, TdengineType.NCHAR);
        put(AllDataType.BPCHAR, TdengineType.NCHAR);
        put(AllDataType.NCHAR, TdengineType.NCHAR);
        put(AllDataType.VARCHAR, TdengineType.NCHAR);
        put(AllDataType.VARCHAR2, TdengineType.NCHAR);
        put(AllDataType.NVARCHAR, TdengineType.NCHAR);
        put(AllDataType.NVARCHAR2, TdengineType.NCHAR);
        put(AllDataType.TINYTEXT, TdengineType.NCHAR);
        put(AllDataType.TEXT, TdengineType.NCHAR);
        put(AllDataType.MEDIUMTEXT, TdengineType.NCHAR);
        put(AllDataType.LONGTEXT, TdengineType.NCHAR);
        put(AllDataType.CLOB, TdengineType.NCHAR);
        put(AllDataType.NCLOB, TdengineType.NCHAR);
        put(AllDataType.JSON, TdengineType.NCHAR);
        put(AllDataType.XML, TdengineType.NCHAR);
        put(AllDataType.SET, TdengineType.NCHAR);
        put(AllDataType.ENUM, TdengineType.NCHAR);
        put(AllDataType.UUID, TdengineType.NCHAR);
        put(AllDataType.ROWID, TdengineType.NCHAR);
        put(AllDataType.UROWID, TdengineType.NCHAR);

        // 二进制类型
        put(AllDataType.BINARY, TdengineType.VARBINARY);
        put(AllDataType.VARBINARY, TdengineType.VARBINARY);
        put(AllDataType.BYTEA, TdengineType.VARBINARY);
        put(AllDataType.RAW, TdengineType.VARBINARY);
        put(AllDataType.VARBIT, TdengineType.VARBINARY);
        put(AllDataType.TINYBLOB, TdengineType.VARBINARY);
        put(AllDataType.BLOB, TdengineType.VARBINARY);
        put(AllDataType.MEDIUMBLOB, TdengineType.VARBINARY);
        put(AllDataType.LONGBLOB, TdengineType.VARBINARY);

        // 时间类型
        put(AllDataType.DATE, TdengineType.TIMESTAMP);
        put(AllDataType.DATETIME, TdengineType.TIMESTAMP);
        put(AllDataType.TIMESTAMP, TdengineType.TIMESTAMP);
        put(AllDataType.TIMESTAMP_6, TdengineType.TIMESTAMP);
        put(AllDataType.TIMESTAMPTZ, TdengineType.TIMESTAMP);
        put(AllDataType.TIMESTAMP_WITH_TIME_ZONE, TdengineType.TIMESTAMP);
        put(AllDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE, TdengineType.TIMESTAMP);
        // TDengine 没有独立的 TIME 类型，降级为字符串
        put(AllDataType.TIME, TdengineType.NCHAR);
        put(AllDataType.TIME_WITH_TIME_ZONE, TdengineType.NCHAR);

        // 布尔类型
        put(AllDataType.BOOLEAN, TdengineType.BOOL);
        put(AllDataType.BOOL, TdengineType.BOOL);
        put(AllDataType.BIT, TdengineType.BOOL);

        // 区间类型降级为字符串
        for (String intervalType : new String[] {
            AllDataType.INTERVAL.name(),
            AllDataType.INTERVAL_YEAR.name(),
            AllDataType.INTERVAL_YEAR_TO_MONTH.name(),
            AllDataType.INTERVAL_MONTH.name(),
            AllDataType.INTERVAL_DAY.name(),
            AllDataType.INTERVAL_DAY_TO_HOUR.name(),
            AllDataType.INTERVAL_DAY_TO_MINUTE.name(),
            AllDataType.INTERVAL_DAY_TO_SECOND.name(),
            AllDataType.INTERVAL_HOUR.name(),
            AllDataType.INTERVAL_HOUR_TO_MINUTE.name(),
            AllDataType.INTERVAL_HOUR_TO_SECOND.name(),
            AllDataType.INTERVAL_MINUTE.name(),
            AllDataType.INTERVAL_MINUTE_TO_SECOND.name(),
            AllDataType.INTERVAL_SECOND.name(),
        }) {
            commonDataType2ColumnType.put(intervalType, TdengineType.NCHAR.name());
        }
    }

    private static void put(AllDataType source, TdengineType target) {
        commonDataType2ColumnType.put(source.name(), target.name());
    }

    @Override
    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType() == null ? "" : columnMeta.getType().toUpperCase().trim();
        String type = commonDataType2ColumnType.get(upperType);
        if (type == null) {
            if (upperType.startsWith("TIMESTAMP") || upperType.startsWith("DATETIME")) {
                type = TdengineType.TIMESTAMP.name();
            } else if (
                upperType.startsWith("DECIMAL") || upperType.startsWith("NUMERIC") || upperType.startsWith("NUMBER")
            ) {
                type = TdengineType.DOUBLE.name();
            } else if (upperType.startsWith("INT")) {
                type = TdengineType.INT.name();
            } else if (
                upperType.contains("CHAR") ||
                upperType.contains("TEXT") ||
                upperType.contains("CLOB") ||
                upperType.startsWith("JSON") ||
                upperType.startsWith("ENUM") ||
                upperType.startsWith("SET")
            ) {
                type = TdengineType.NCHAR.name();
            } else if (upperType.contains("BINARY") || upperType.contains("BLOB") || upperType.startsWith("RAW")) {
                type = TdengineType.VARBINARY.name();
            } else {
                type = TdengineType.NCHAR.name();
            }
        }

        ColumnMeta column = new ColumnMeta(columnMeta.getName(), type);
        column.setLength(columnMeta.getLength());
        column.setPrecision(columnMeta.getPrecision());
        column.setScale(columnMeta.getScale());
        column.setNullable(columnMeta.isNullable());
        column.setDefaultValue(columnMeta.getDefaultValue());
        column.setComment(columnMeta.getComment());
        // TDengine 没有主键概念，清空源库主键标记，避免 ETL 误走按主键 UPDATE/DELETE 分支
        column.setPrimaryKey(false);
        column.setTypeDefinition(columnMeta.getTypeDefinition());
        return column;
    }

    @Override
    public String generateTableDDL(TableMeta table) {
        if (table == null || table.columns() == null || table.columns().isEmpty()) {
            throw new IllegalArgumentException("TDengine 目标表没有字段");
        }
        List<ColumnMeta> columns = new ArrayList<>(table.columns());
        ColumnMeta timestampColumn = columns
            .stream()
            .filter(column -> isTimestampType(column.getType()))
            .findFirst()
            .orElse(null);
        if (timestampColumn == null) {
            throw new IllegalArgumentException(
                "TDengine 目标表必须包含 TIMESTAMP/DATETIME 时间列，当前表：" + table.getTable()
            );
        }
        columns.remove(timestampColumn);
        columns.add(0, timestampColumn);

        StringBuilder ddl = new StringBuilder("CREATE TABLE ");
        if (table.getSchema() != null && !table.getSchema().isBlank()) {
            ddl.append(quote(table.getSchema())).append(".");
        }
        ddl.append(quote(table.getTable())).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            ddl.append("  ").append(generateColumnDDL(columns.get(i)));
            if (i < columns.size() - 1) {
                ddl.append(",");
            }
            ddl.append("\n");
        }
        ddl.append(")");
        return ddl.toString();
    }

    @Override
    public String generateColumnDDL(ColumnMeta column) {
        String type = column.getType() == null ? TdengineType.NCHAR.name() : column.getType().toUpperCase();
        StringBuilder ddl = new StringBuilder(quote(column.getName())).append(" ");
        switch (type) {
            case "TIMESTAMP":
            case "BOOL":
            case "TINYINT":
            case "SMALLINT":
            case "INT":
            case "BIGINT":
            case "FLOAT":
            case "DOUBLE":
                ddl.append(type);
                break;
            case "VARBINARY":
                ddl.append("VARBINARY(").append(binaryLength(column)).append(")");
                break;
            case "VARCHAR":
                ddl.append("VARCHAR(").append(varcharLength(column)).append(")");
                break;
            case "NCHAR":
            default:
                int length = column.getLength() > 0 ? column.getLength() : 255;
                if (length > 16374) {
                    ddl.append("VARCHAR(").append(Math.min(length, 65517)).append(")");
                } else {
                    ddl.append("NCHAR(").append(length).append(")");
                }
                break;
        }
        return ddl.toString();
    }

    @Override
    public String generateInsertSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add(quote(column.getName()));
        }
        String columns = String.join(", ", columnNames);
        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));
        return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName(tableMeta), columns, placeholders);
    }

    @Override
    public String generateUpsertSQL(TableMeta tableMeta) {
        // TDengine 相同时间戳重复写入会自动覆盖，UPDATE 语义直接复用 INSERT。
        return generateInsertSQL(tableMeta);
    }

    @Override
    public String generateReplaceSQL(TableMeta tableMeta) {
        return generateInsertSQL(tableMeta);
    }

    @Override
    public String generateDeleteSQL(TableMeta tableMeta) {
        ColumnMeta timestampColumn = tableMeta
            .columns()
            .stream()
            .filter(column -> isTimestampType(column.getType()))
            .findFirst()
            .orElseThrow(() ->
                new IllegalArgumentException("TDengine 表没有时间列，无法按时间戳生成 DELETE 语句：" + tableMeta.getTable())
            );
        return String.format(
            "DELETE FROM %s WHERE %s = ?",
            tableName(tableMeta),
            quote(timestampColumn.getName())
        );
    }

    @Override
    public void bindParameter(PreparedStatement statement, int index, ColumnMeta column, Object value)
        throws SQLException {
        String type = column == null ? null : column.getType();
        Object normalized;
        if (value instanceof byte[] && isCharacterType(type)) {
            // TDengine WebSocket 驱动可能把字符列返回为 byte[]，按 UTF-8 还原
            normalized = new String((byte[]) value, StandardCharsets.UTF_8);
        } else {
            normalized = DBUtils.normalizeParameter(type, value);
        }

        if (normalized instanceof String) {
            statement.setString(index, (String) normalized);
        } else if (normalized instanceof LocalDateTime) {
            statement.setTimestamp(index, Timestamp.valueOf((LocalDateTime) normalized));
        } else if (normalized instanceof LocalDate) {
            statement.setTimestamp(index, Timestamp.valueOf(((LocalDate) normalized).atStartOfDay()));
        } else if (normalized instanceof LocalTime) {
            statement.setString(index, normalized.toString());
        } else if (normalized instanceof OffsetDateTime) {
            statement.setTimestamp(index, Timestamp.from(((OffsetDateTime) normalized).toInstant()));
        } else {
            statement.setObject(index, normalized);
        }
    }

    @Override
    public Object normalizeSourceValue(ColumnMeta column, Object value) {
        if (value instanceof byte[] && column != null && isCharacterType(column.getType())) {
            return new String((byte[]) value, StandardCharsets.UTF_8);
        }
        return value;
    }

    @Override
    public List<String> getSupportedTypes() {
        List<String> result = new ArrayList<>();
        for (TdengineType type : TdengineType.values()) {
            result.add(type.name());
        }
        return result;
    }

    private static boolean isTimestampType(String type) {
        if (type == null) {
            return false;
        }
        String upperType = DatabaseConverter.stripTypeParameters(type).toUpperCase();
        return (
            "TIMESTAMP".equals(upperType) ||
            "DATETIME".equals(upperType) ||
            "DATE".equals(upperType) ||
            upperType.startsWith("TIMESTAMP")
        );
    }

    private static boolean isCharacterType(String type) {
        if (type == null) {
            return false;
        }
        String upperType = DatabaseConverter.stripTypeParameters(type).toUpperCase();
        return (
            "NCHAR".equals(upperType) ||
            "VARCHAR".equals(upperType) ||
            "CHAR".equals(upperType) ||
            "TEXT".equals(upperType) ||
            "CLOB".equals(upperType) ||
            "JSON".equals(upperType) ||
            "UUID".equals(upperType) ||
            upperType.endsWith("TEXT") ||
            upperType.contains("CHAR")
        );
    }

    private static int binaryLength(ColumnMeta column) {
        int length = column.getLength() > 0 ? column.getLength() : 255;
        return Math.min(length, 65517);
    }

    private static int varcharLength(ColumnMeta column) {
        int length = column.getLength() > 0 ? column.getLength() : 255;
        return Math.min(length, 65517);
    }

    private static String tableName(TableMeta tableMeta) {
        if (tableMeta.getSchema() != null && !tableMeta.getSchema().isBlank()) {
            return quote(tableMeta.getSchema()) + "." + quote(tableMeta.getTable());
        }
        return quote(tableMeta.getTable());
    }

    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
