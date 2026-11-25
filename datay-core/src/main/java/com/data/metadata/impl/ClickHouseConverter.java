package com.data.metadata.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.TypeConverter;
import com.data.metadata.util.JdbcValueNormalizer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ClickHouse 方言转换器。
 *
 * <p>ClickHouse 的类型体系与标准 JDBC 差异较大：Nullable、LowCardinality、Array、Map、Tuple、
 * DateTime64、FixedString、UInt* 等都需要按 ClickHouse 语法生成 DDL；同时 MergeTree 系引擎必须
 * 指定 ORDER BY。写入侧没有通用 UPSERT，overwrite 模式由 Provider 使用 TRUNCATE + INSERT 完成。</p>
 */
public class ClickHouseConverter implements TypeConverter {

    private static final DateTimeFormatter DATE_TIME_SPACE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSSSSS]");

    @Override
    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        return toTargetColumnType(columnMeta, null);
    }

    @Override
    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta, String sourceDbType) {
        if (columnMeta == null) {
            return null;
        }
        boolean clickHouseSource = isClickHouseSource(sourceDbType, columnMeta.getType());
        DdlType ddlType = normalizeDdlType(columnMeta.getType(), true, columnMeta, clickHouseSource);
        ColumnMeta target = new ColumnMeta(columnMeta.getName(), ddlType.type);
        target.setLength(columnMeta.getLength());
        target.setPrecision(columnMeta.getPrecision());
        target.setScale(columnMeta.getScale());
        target.setDefaultValue(columnMeta.getDefaultValue());
        target.setComment(columnMeta.getComment());
        target.setTypeDefinition(columnMeta.getTypeDefinition());
        target.setPrimaryKey(columnMeta.isPrimaryKey());

        boolean nullable = ddlType.nullable || columnMeta.isNullable();
        if (columnMeta.isPrimaryKey() || isComplexType(ddlType.type)) {
            nullable = false;
        }
        target.setNullable(nullable);
        applyTypeDetails(target);
        return target;
    }

    // ------------------------------------------------------------------ 源类型归一化

    /**
     * 将 ClickHouse 源类型归一化为其它数据库可识别的通用类型。
     *
     * <p>例如 {@code UInt8 -> SMALLINT}、{@code UInt64 -> DECIMAL(20,0)}、
     * {@code DateTime64(3) -> TIMESTAMP(3)}、{@code Array/Map/Tuple -> LONGTEXT}。
     * 复杂类型读取时会被 {@link JdbcValueNormalizer} 转成 JSON 字符串，因此 LONGTEXT 可完整保存。</p>
     */
    public static ColumnMeta normalizeSourceColumn(ColumnMeta source) {
        if (source == null) {
            return null;
        }
        UnwrappedType unwrapped = unwrapTopLevel(source.getType());
        String base = baseName(unwrapped.type);
        String portable;
        int length = source.getLength();
        int precision = source.getPrecision();
        int scale = source.getScale();

        switch (base) {
            case "UINT8":
                portable = "SMALLINT";
                break;
            case "UINT16":
                portable = "INT";
                break;
            case "UINT32":
                portable = "BIGINT";
                break;
            case "UINT64":
                portable = "DECIMAL";
                precision = 20;
                scale = 0;
                break;
            case "UINT128":
            case "UINT256":
            case "INT128":
            case "INT256":
                // 超出多数目标库的 DECIMAL 精度上限，按字符串保存避免溢出
                portable = "VARCHAR";
                length = 80;
                break;
            case "INT8":
                portable = "TINYINT";
                break;
            case "INT16":
                portable = "SMALLINT";
                break;
            case "INT32":
                portable = "INT";
                break;
            case "INT64":
                portable = "BIGINT";
                break;
            case "FLOAT32":
            case "BFLOAT16":
                // FLOAT 在部分方言中按 SQL Server 语义映射为 DOUBLE；BINARY_FLOAT 统一落到单精度
                portable = "BINARY_FLOAT";
                break;
            case "FLOAT64":
                portable = "DOUBLE";
                break;
            case "DECIMAL":
            case "DECIMAL32":
            case "DECIMAL64":
            case "DECIMAL128":
                portable = "DECIMAL";
                int[] decimal = decimalPrecisionScale(unwrapped.type, source);
                if (decimal != null) {
                    precision = decimal[0];
                    scale = decimal[1];
                } else if (precision <= 0) {
                    precision = 38;
                    scale = 10;
                }
                break;
            case "DECIMAL256":
                portable = "VARCHAR";
                length = 80;
                break;
            case "STRING":
                portable = "LONGTEXT";
                length = 0;
                break;
            case "FIXEDSTRING":
                portable = "VARCHAR";
                Integer fixedLength = intArgument(unwrapped.type, 0);
                if (fixedLength != null && fixedLength > 0) {
                    length = fixedLength;
                }
                break;
            case "ENUM8":
            case "ENUM16":
            case "ENUM":
                portable = "VARCHAR";
                length = 255;
                break;
            case "UUID":
                portable = "UUID";
                length = 36;
                break;
            case "IPV4":
            case "IPV6":
            case "INET":
                portable = "VARCHAR";
                length = 45;
                break;
            case "DATE":
            case "DATE32":
                portable = "DATE";
                break;
            case "DATETIME":
                portable = "TIMESTAMP";
                scale = 0;
                break;
            case "DATETIME64":
                portable = "TIMESTAMP";
                Integer dateTimeScale = intArgument(unwrapped.type, 0);
                // MySQL / DuckDB 的时间精度上限是微秒，DateTime64(7..9) 落库时按 6 位保留
                scale = dateTimeScale == null ? 6 : Math.min(Math.max(dateTimeScale, 0), 6);
                break;
            case "BOOL":
            case "BOOLEAN":
                portable = "BOOLEAN";
                break;
            case "ARRAY":
            case "MAP":
            case "TUPLE":
            case "NESTED":
            case "AGGREGATEFUNCTION":
            case "OBJECT":
            case "NOTHING":
            case "JSON":
            default:
                portable = "LONGTEXT";
                length = 0;
                break;
        }

        precision = Math.max(precision, 0);
        scale = Math.max(scale, 0);
        length = Math.max(length, 0);
        ColumnMeta target = new ColumnMeta(source.getName(), portable, length, precision, scale);
        target.setNullable((unwrapped.nullable || source.isNullable()) && !source.isPrimaryKey());
        target.setPrimaryKey(source.isPrimaryKey());
        target.setComment(source.getComment());
        target.setDefaultValue(source.getDefaultValue());
        target.setTypeDefinition(source.getTypeDefinition());
        return target;
    }

    /**
     * 判断是否应按 ClickHouse 类型处理。
     */
    public static boolean isClickHouseSource(String sourceDbType, String type) {
        if (sourceDbType != null && !sourceDbType.isBlank()) {
            return "CLICKHOUSE".equalsIgnoreCase(sourceDbType.trim());
        }
        // 源类型未知时按 ClickHouse 特有类型兜底；已知其它库时不能仅凭 JSON/OBJECT 等通用类型误判。
        return looksLikeClickHouseType(type);
    }

    // ------------------------------------------------------------------ DDL

    @Override
    public String generateColumnDDL(ColumnMeta column) {
        DdlType ddlType = normalizeDdlType(column.getType(), true, column, false);
        boolean nullable = ddlType.nullable || column.isNullable();
        if (column.isPrimaryKey() || isComplexType(ddlType.type)) {
            nullable = false;
        }
        String type = ddlType.type;
        if (nullable) {
            if (startsWithIgnoreCase(type, "LowCardinality(")) {
                // ClickHouse 不允许 Nullable(LowCardinality(...))，可空要写成 LowCardinality(Nullable(...))
                String inner = unwrapType(type, "LowCardinality");
                if (inner != null && !startsWithIgnoreCase(inner, "Nullable(") && !isComplexType(inner)) {
                    type = "LowCardinality(Nullable(" + inner + "))";
                }
            } else if (!isNullableForbidden(type) && !startsWithIgnoreCase(type, "Nullable(")) {
                type = "Nullable(" + type + ")";
            }
        }
        return quoteIdentifier(column.getName()) + " " + type;
    }

    @Override
    public String generateTableDDL(TableMeta tableMeta) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(qualifiedTableName(tableMeta)).append(" (\n");
        for (int i = 0; i < tableMeta.columns().size(); i++) {
            if (i > 0) {
                ddl.append(",\n");
            }
            ddl.append("  ").append(generateColumnDDL(tableMeta.columns().get(i)));
        }
        ddl.append("\n) ENGINE = MergeTree\nORDER BY ");

        List<ColumnMeta> orderByColumns = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            if (column.isPrimaryKey() && !isComplexType(column.getType())) {
                orderByColumns.add(column);
            }
        }
        if (orderByColumns.isEmpty()) {
            ddl.append("tuple()");
        } else {
            ddl.append("(");
            for (int i = 0; i < orderByColumns.size(); i++) {
                if (i > 0) {
                    ddl.append(", ");
                }
                ddl.append(quoteIdentifier(orderByColumns.get(i).getName()));
            }
            ddl.append(")");
        }
        return ddl.toString();
    }

    @Override
    public String generateInsertSQL(TableMeta tableMeta) {
        List<String> names = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            names.add(quoteIdentifier(column.getName()));
            placeholders.add("?");
        }
        return "INSERT INTO " + qualifiedTableName(tableMeta) + " (" + String.join(", ", names) + ") VALUES (" +
            String.join(", ", placeholders) + ")";
    }

    @Override
    public String generateUpsertSQL(TableMeta tableMeta) {
        throw new UnsupportedOperationException(
            "ClickHouse 不支持通用 UPSERT/UPDATE 写入，请使用 overwrite/append 模式，或目标表改用 ReplacingMergeTree 引擎"
        );
    }

    @Override
    public String generateDeleteSQL(TableMeta tableMeta) {
        List<ColumnMeta> keys = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            if (column.isPrimaryKey()) {
                keys.add(column);
            }
        }
        if (keys.isEmpty()) {
            keys.addAll(tableMeta.columns());
        }
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(qualifiedTableName(tableMeta)).append(" WHERE ");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(quoteIdentifier(keys.get(i).getName())).append(" = ?");
        }
        return sql.toString();
    }

    @Override
    public String generateReplaceSQL(TableMeta tableMeta) {
        return generateInsertSQL(tableMeta);
    }

    // ------------------------------------------------------------------ 参数绑定 / 源值归一化

    @Override
    public void bindParameter(PreparedStatement statement, int index, ColumnMeta column, Object value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
            return;
        }
        Object normalized = JdbcValueNormalizer.normalize(value);
        if (normalized == null) {
            statement.setObject(index, null);
            return;
        }
        String targetType = column == null ? "" : column.getType();
        String base = bindingBaseType(targetType);
        Object bindValue = normalized;

        if (isArrayBase(base) || isTupleBase(base)) {
            bindValue = toJsonArray(normalized);
        } else if (isMapBase(base)) {
            bindValue = toJsonObject(normalized);
        } else if ("UUID".equals(base)) {
            bindValue = toUuid(normalized);
        } else if ("IPV4".equals(base) || "IPV6".equals(base)) {
            bindValue = toInetAddress(normalized);
        } else if ("BOOL".equals(base) || "BOOLEAN".equals(base)) {
            bindValue = toBoolean(normalized);
        } else if (isDateTimeBase(base)) {
            bindValue = toTargetLocalDateTime(normalized, targetType);
        } else if ("DATE".equals(base) || "DATE32".equals(base)) {
            bindValue = toTargetLocalDate(normalized);
        } else if (isNumericBase(base)) {
            bindValue = normalizeNumeric(base, normalized);
        } else if (isStringBase(base)) {
            bindValue = toClickHouseString(normalized);
        }

        statement.setObject(index, bindValue);
    }

    @Override
    public Object normalizeSourceValue(ColumnMeta column, Object value) {
        return JdbcValueNormalizer.normalize(value);
    }

    @Override
    public List<String> getSupportedTypes() {
        return Collections.unmodifiableList(
            List.of(
                "Int8",
                "Int16",
                "Int32",
                "Int64",
                "Int128",
                "Int256",
                "UInt8",
                "UInt16",
                "UInt32",
                "UInt64",
                "UInt128",
                "UInt256",
                "Float32",
                "Float64",
                "Decimal",
                "String",
                "FixedString",
                "UUID",
                "Date",
                "Date32",
                "DateTime",
                "DateTime64",
                "Bool",
                "IPv4",
                "IPv6",
                "Enum8",
                "Enum16",
                "Array",
                "Map",
                "Tuple",
                "LowCardinality"
            )
        );
    }

    // ------------------------------------------------------------------ ClickHouse 类型解析

    private static DdlType normalizeDdlType(String rawType, boolean topLevel, ColumnMeta column, boolean clickHouseSource) {
        String type = rawType == null ? "" : rawType.trim();
        if (type.isEmpty()) {
            return new DdlType("String", column != null && column.isNullable());
        }

        String inner = unwrapType(type, "Nullable");
        if (inner != null) {
            DdlType dt = normalizeDdlType(inner, topLevel, column, clickHouseSource);
            if (isComplexType(dt.type)) {
                // ClickHouse 不允许 Nullable 包裹 Array/Map/Tuple/LowCardinality
                return new DdlType(dt.type, false);
            }
            if (topLevel) {
                return new DdlType(dt.type, true);
            }
            return new DdlType("Nullable(" + dt.type + ")", false);
        }

        inner = unwrapType(type, "LowCardinality");
        if (inner != null) {
            DdlType dt = normalizeDdlType(inner, false, column, clickHouseSource);
            if (isComplexType(dt.type)) {
                return dt;
            }
            return new DdlType("LowCardinality(" + dt.type + ")", false);
        }

        inner = unwrapType(type, "SimpleAggregateFunction");
        if (inner != null) {
            List<String> args = splitTopLevel(inner);
            if (!args.isEmpty()) {
                return normalizeDdlType(args.get(args.size() - 1), topLevel, column, clickHouseSource);
            }
        }

        inner = unwrapType(type, "Array");
        if (inner != null) {
            DdlType element = normalizeDdlType(inner, false, column, clickHouseSource);
            return new DdlType("Array(" + element.type + ")", false);
        }

        inner = unwrapType(type, "Map");
        if (inner != null) {
            List<String> args = splitTopLevel(inner);
            if (args.size() == 2) {
                DdlType key = normalizeDdlType(args.get(0), false, column, clickHouseSource);
                DdlType value = normalizeDdlType(args.get(1), false, column, clickHouseSource);
                String keyType = isComplexType(key.type) ? "String" : key.type;
                return new DdlType("Map(" + keyType + ", " + value.type + ")", false);
            }
            return new DdlType("Map(String, String)", false);
        }

        inner = unwrapType(type, "Tuple");
        if (inner != null) {
            List<String> args = splitTopLevel(inner);
            List<String> elementTypes = new ArrayList<>();
            for (String arg : args) {
                elementTypes.add(normalizeTupleArgument(arg, column, clickHouseSource));
            }
            return new DdlType("Tuple(" + String.join(", ", elementTypes) + ")", false);
        }

        String upper = type.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ARRAY<") && upper.endsWith(">")) {
            String element = type.substring(type.indexOf('<') + 1, type.lastIndexOf('>'));
            return new DdlType("Array(" + normalizeDdlType(element, false, column, clickHouseSource).type + ")", false);
        }
        if (upper.startsWith("MAP<") && upper.endsWith(">")) {
            String element = type.substring(type.indexOf('<') + 1, type.lastIndexOf('>'));
            List<String> args = splitTopLevel(element);
            if (args.size() == 2) {
                String key = normalizeDdlType(args.get(0), false, column, clickHouseSource).type;
                String value = normalizeDdlType(args.get(1), false, column, clickHouseSource).type;
                return new DdlType("Map(" + (isComplexType(key) ? "String" : key) + ", " + value + ")", false);
            }
            return new DdlType("Map(String, String)", false);
        }
        if (upper.startsWith("STRUCT<") || upper.startsWith("ROW(") || upper.startsWith("TUPLE<")) {
            return new DdlType("String", false);
        }
        if (startsWithIgnoreCase(type, "Nested(") || startsWithIgnoreCase(type, "AggregateFunction(") ||
            startsWithIgnoreCase(type, "Object(")) {
            return new DdlType("String", false);
        }

        return new DdlType(normalizeScalarType(type, column, clickHouseSource), false);
    }

    private static String normalizeTupleArgument(String arg, ColumnMeta column, boolean clickHouseSource) {
        String trimmed = arg.trim();
        int space = topLevelSpaceIndex(trimmed);
        if (space > 0) {
            String name = trimmed.substring(0, space).trim();
            String rest = trimmed.substring(space + 1).trim();
            if (isSimpleIdentifier(name) && !rest.isEmpty()) {
                return name + " " + normalizeDdlType(rest, false, column, clickHouseSource).type;
            }
        }
        return normalizeDdlType(trimmed, false, column, clickHouseSource).type;
    }

    private static String normalizeScalarType(String type, ColumnMeta column, boolean clickHouseSource) {
        String base = baseName(type);
        if (clickHouseSource || isClickHouseScalarBase(base) || isClickHouseDateTimeWithZone(type)) {
            return normalizeClickHouseScalar(type, base, column);
        }
        return mapGenericScalar(type, base, column);
    }

    private static String normalizeClickHouseScalar(String type, String base, ColumnMeta column) {
        switch (base) {
            case "INT8":
                return "Int8";
            case "INT16":
                return "Int16";
            case "INT32":
                return "Int32";
            case "INT64":
                return "Int64";
            case "INT128":
                return "Int128";
            case "INT256":
                return "Int256";
            case "UINT8":
                return "UInt8";
            case "UINT16":
                return "UInt16";
            case "UINT32":
                return "UInt32";
            case "UINT64":
                return "UInt64";
            case "UINT128":
                return "UInt128";
            case "UINT256":
                return "UInt256";
            case "FLOAT32":
                return "Float32";
            case "FLOAT64":
                return "Float64";
            case "BOOL":
            case "BOOLEAN":
                return "Bool";
            case "STRING":
                return "String";
            case "UUID":
                return "UUID";
            case "IPV4":
                return "IPv4";
            case "IPV6":
                return "IPv6";
            case "DATE":
                return "Date";
            case "DATE32":
                return "Date32";
            case "DATETIME":
                return formatDateTime(type);
            case "DATETIME64":
                return formatDateTime64(type);
            case "DECIMAL":
            case "DECIMAL32":
            case "DECIMAL64":
            case "DECIMAL128":
            case "DECIMAL256":
                return formatDecimal(type, base, column);
            case "FIXEDSTRING":
                Integer fixedLength = intArgument(type, 0);
                int length = fixedLength != null && fixedLength > 0 ? fixedLength : Math.max(column == null ? 0 : column.getLength(), 1);
                return "FixedString(" + length + ")";
            case "ENUM8":
            case "ENUM16":
                return type;
            case "JSON":
            case "NOTHING":
            case "OBJECT":
                return "String";
            default:
                return mapGenericScalar(type, base, column);
        }
    }

    private static String mapGenericScalar(String type, String base, ColumnMeta column) {
        String normalizedBase = base.replace(" UNSIGNED", "").trim();
        boolean unsigned = base.endsWith(" UNSIGNED");
        switch (normalizedBase) {
            case "TINYINT":
                return unsigned ? "UInt8" : "Int8";
            case "SMALLINT":
                return unsigned ? "UInt16" : "Int16";
            case "MEDIUMINT":
                return unsigned ? "UInt32" : "Int32";
            case "INT":
            case "INTEGER":
                return unsigned ? "UInt32" : "Int32";
            case "BIGINT":
                return unsigned ? "UInt64" : "Int64";
            case "INT8":
                return unsigned ? "UInt8" : "Int8";
            case "INT16":
                return unsigned ? "UInt16" : "Int16";
            case "INT32":
                return unsigned ? "UInt32" : "Int32";
            case "INT64":
                return unsigned ? "UInt64" : "Int64";
            case "INT128":
                return unsigned ? "UInt128" : "Int128";
            case "INT256":
                return unsigned ? "UInt256" : "Int256";
            case "UTINYINT":
                return "UInt8";
            case "USMALLINT":
                return "UInt16";
            case "UINTEGER":
                return "UInt32";
            case "UBIGINT":
                return "UInt64";
            case "HUGEINT":
                return "Int128";
            case "UHUGEINT":
                return "UInt128";
            case "UINT128":
                return "UInt128";
            case "UINT256":
                return "UInt256";
            case "FLOAT":
            case "REAL":
            case "FLOAT4":
            case "FLOAT32":
            case "BFLOAT16":
            case "BINARY_FLOAT":
                return "Float32";
            case "DOUBLE":
            case "DOUBLE PRECISION":
            case "FLOAT8":
            case "FLOAT64":
            case "BINARY_DOUBLE":
                return "Float64";
            case "DECIMAL":
            case "NUMERIC":
            case "NUMBER":
                return formatDecimal(type, normalizedBase, column);
            case "DECIMAL32":
                return formatDecimal(type, "DECIMAL32", column);
            case "DECIMAL64":
                return formatDecimal(type, "DECIMAL64", column);
            case "DECIMAL128":
                return formatDecimal(type, "DECIMAL128", column);
            case "DECIMAL256":
                return formatDecimal(type, "DECIMAL256", column);
            case "MONEY":
                return "Decimal(19,4)";
            case "SMALLMONEY":
                return "Decimal(10,4)";
            case "BOOLEAN":
            case "BOOL":
                return "Bool";
            case "DATE":
                return "Date";
            case "DATE32":
                return "Date32";
            case "DATETIME":
            case "TIMESTAMP":
                return formatTargetDateTime(type, column, false);
            case "TIMESTAMPTZ":
            case "DATETIMEOFFSET":
                return formatTargetDateTime(type, column, true);
            case "TIMESTAMP WITH TIME ZONE":
            case "TIMESTAMP WITH LOCAL TIME ZONE":
                return formatTargetDateTime(type, column, true);
            case "TIME":
            case "TIME WITH TIME ZONE":
            case "YEAR":
                return "String";
            case "UUID":
                return "UUID";
            case "IPV4":
                return "IPv4";
            case "IPV6":
                return "IPv6";
            case "INET":
                return "String";
            case "ENUM":
            case "SET":
            case "JSON":
            case "JSONB":
            case "XML":
            case "BINARY":
            case "VARBINARY":
            case "BLOB":
            case "TINYBLOB":
            case "MEDIUMBLOB":
            case "LONGBLOB":
            case "BYTEA":
            case "RAW":
            case "IMAGE":
            case "BIT":
            case "VARBIT":
            case "INTERVAL":
            case "ARRAY":
            case "MAP":
            case "TUPLE":
            case "STRUCT":
            case "ROW":
            case "OBJECT":
                return "String";
            case "VARCHAR":
            case "CHAR":
            case "NCHAR":
            case "NVARCHAR":
            case "CHARACTER":
            case "CHARACTER VARYING":
            case "TEXT":
            case "TINYTEXT":
            case "MEDIUMTEXT":
            case "LONGTEXT":
            case "CLOB":
            case "NCLOB":
            case "STRING":
                return "String";
            default:
                return "String";
        }
    }

    private static String formatDateTime(String type) {
        String inner = unwrappedInner(type);
        return inner == null || inner.isBlank() ? "DateTime" : "DateTime(" + inner + ")";
    }

    private static String formatDateTime64(String type) {
        List<String> args = typeArguments(type);
        if (args.isEmpty()) {
            return "DateTime64(3)";
        }
        String precision = args.get(0).trim();
        if (precision.isEmpty()) {
            precision = "3";
        }
        if (args.size() > 1) {
            return "DateTime64(" + precision + ", " + args.get(1).trim() + ")";
        }
        return "DateTime64(" + precision + ")";
    }

    private static String formatTargetDateTime(String type, ColumnMeta column, boolean withTimeZone) {
        int scale = column == null ? 0 : Math.max(column.getScale(), 0);
        Integer parsed = intArgument(type, 0);
        if (parsed != null) {
            scale = Math.min(Math.max(parsed, 0), 9);
        }
        if (withTimeZone) {
            return "DateTime64(" + Math.max(scale, 6) + ", 'UTC')";
        }
        return scale > 0 ? "DateTime64(" + scale + ")" : "DateTime";
    }

    private static String formatDecimal(String type, String base, ColumnMeta column) {
        int[] decimal = decimalPrecisionScale(type, column);
        if (decimal == null) {
            if ("DECIMAL32".equals(base)) {
                return "Decimal(9,0)";
            }
            if ("DECIMAL64".equals(base)) {
                return "Decimal(18,0)";
            }
            if ("DECIMAL128".equals(base)) {
                return "Decimal(38,0)";
            }
            if ("DECIMAL256".equals(base)) {
                return "Decimal(76,0)";
            }
            return "Decimal(38,10)";
        }
        int precision = Math.min(Math.max(decimal[0], 1), 76);
        int scale = Math.min(Math.max(decimal[1], 0), precision);
        return "Decimal(" + precision + "," + scale + ")";
    }

    private static int[] decimalPrecisionScale(String type, ColumnMeta column) {
        String base = baseName(type);
        List<String> args = typeArguments(type);
        Integer scaleArg = null;
        if (args.size() > 1) {
            scaleArg = parseInteger(args.get(1));
        } else if (args.size() == 1) {
            scaleArg = parseInteger(args.get(0));
        }
        if ("DECIMAL32".equals(base)) {
            return new int[] { 9, scaleArg == null ? 0 : Math.max(scaleArg, 0) };
        }
        if ("DECIMAL64".equals(base)) {
            return new int[] { 18, scaleArg == null ? 0 : Math.max(scaleArg, 0) };
        }
        if ("DECIMAL128".equals(base)) {
            return new int[] { 38, scaleArg == null ? 0 : Math.max(scaleArg, 0) };
        }
        if ("DECIMAL256".equals(base)) {
            return new int[] { 76, scaleArg == null ? 0 : Math.max(scaleArg, 0) };
        }
        int[] parsed = DatabaseConverter.parseTypePrecisionScale(type);
        if (parsed != null) {
            return parsed;
        }
        if (column != null && column.getPrecision() > 0) {
            return new int[] { column.getPrecision(), Math.max(column.getScale(), 0) };
        }
        return null;
    }

    // ------------------------------------------------------------------ 绑定值转换

    private static String bindingBaseType(String type) {
        String current = type == null ? "" : type.trim();
        while (true) {
            String inner = unwrapType(current, "Nullable");
            if (inner == null) {
                inner = unwrapType(current, "LowCardinality");
            }
            if (inner == null) {
                inner = unwrapType(current, "SimpleAggregateFunction");
            }
            if (inner == null) {
                break;
            }
            if (startsWithIgnoreCase(current, "SimpleAggregateFunction(")) {
                List<String> args = splitTopLevel(inner);
                if (args.isEmpty()) {
                    break;
                }
                current = args.get(args.size() - 1).trim();
            } else {
                current = inner;
            }
        }
        return baseName(current);
    }

    private static Object toJsonArray(Object value) {
        if (value instanceof JSONArray || value instanceof List<?> || value.getClass().isArray()) {
            return JdbcValueNormalizer.normalize(value);
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.startsWith("[")) {
                try {
                    return JSON.parseArray(text);
                } catch (RuntimeException ignored) {
                    // 非 JSON 字符串交给驱动处理
                }
            }
        }
        return value;
    }

    private static Object toJsonObject(Object value) {
        if (value instanceof JSONObject || value instanceof Map<?, ?>) {
            return JdbcValueNormalizer.normalize(value);
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.startsWith("{")) {
                try {
                    return JSON.parseObject(text);
                } catch (RuntimeException ignored) {
                    // 非 JSON 字符串交给驱动处理
                }
            }
        }
        return value;
    }

    private static Object toUuid(Object value) {
        if (value instanceof UUID) {
            return value;
        }
        if (value instanceof String) {
            try {
                return UUID.fromString((String) value);
            } catch (IllegalArgumentException ignored) {
                return value;
            }
        }
        return value;
    }

    private static Object toInetAddress(Object value) {
        if (value instanceof InetAddress) {
            return value;
        }
        if (value instanceof String) {
            try {
                return InetAddress.getByName(((String) value).trim());
            } catch (Exception ignored) {
                return value;
            }
        }
        return value;
    }

    private static Object toBoolean(Object value) {
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
        }
        return value;
    }

    private static Object toClickHouseString(Object value) {
        if (value instanceof JSONArray || value instanceof JSONObject || value instanceof Map<?, ?> || value instanceof Collection<?>) {
            return JSON.toJSONString(value);
        }
        if (value instanceof byte[]) {
            return new String((byte[]) value, StandardCharsets.UTF_8);
        }
        return value.toString();
    }

    private static Object normalizeNumeric(String base, Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        }
        boolean decimalTarget = "DECIMAL".equals(base) || "NUMERIC".equals(base) || "NUMBER".equals(base);
        if (value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof BigInteger) {
            return decimalTarget ? new BigDecimal((BigInteger) value) : value;
        }
        if (value instanceof Number) {
            // ClickHouse Decimal 驱动更稳妥地接受 BigDecimal，整数型无符号数也统一转过去
            return decimalTarget ? new BigDecimal(value.toString()) : value;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                if ("DECIMAL".equals(base) || "NUMERIC".equals(base) || "NUMBER".equals(base)) {
                    return new BigDecimal(text);
                }
                if ("FLOAT32".equals(base) || "FLOAT".equals(base) || "BFLOAT16".equals(base)) {
                    return Float.valueOf(text);
                }
                if ("FLOAT64".equals(base) || "DOUBLE".equals(base)) {
                    return Double.valueOf(text);
                }
                if ("UINT64".equals(base) || "UINT128".equals(base) || "UINT256".equals(base) || "INT128".equals(base) ||
                    "INT256".equals(base)) {
                    return new BigInteger(text);
                }
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }

    private static Object toTargetLocalDateTime(Object value, String targetType) {
        ZoneId zone = extractTimezone(targetType);
        if (value instanceof OffsetDateTime) {
            OffsetDateTime offset = (OffsetDateTime) value;
            return zone == null ? offset.toLocalDateTime() : offset.atZoneSameInstant(zone).toLocalDateTime();
        }
        if (value instanceof Instant) {
            return LocalDateTime.ofInstant((Instant) value, zone == null ? ZoneId.systemDefault() : zone);
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate().atStartOfDay();
        }
        if (value instanceof LocalDateTime) {
            return value;
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }
        if (value instanceof java.util.Date) {
            return LocalDateTime.ofInstant(((java.util.Date) value).toInstant(), zone == null ? ZoneId.systemDefault() : zone);
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                OffsetDateTime offset = OffsetDateTime.parse(text);
                return zone == null ? offset.toLocalDateTime() : offset.atZoneSameInstant(zone).toLocalDateTime();
            } catch (RuntimeException ignored) {
                // 继续尝试无时区格式
            }
            try {
                return LocalDateTime.parse(text);
            } catch (RuntimeException ignored) {
                // 继续尝试 JDBC / 空格分隔格式
            }
            try {
                return Timestamp.valueOf(text).toLocalDateTime();
            } catch (RuntimeException ignored) {
                // 继续尝试纯日期
            }
            try {
                return LocalDate.parse(text).atStartOfDay();
            } catch (RuntimeException ignored) {
                // 继续尝试空格格式
            }
            try {
                return LocalDateTime.parse(text, DATE_TIME_SPACE_FORMAT);
            } catch (RuntimeException ignored) {
                return value;
            }
        }
        return value;
    }

    private static Object toTargetLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return value;
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toLocalDate();
        }
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toLocalDate();
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return LocalDateTime.ofInstant(((java.util.Date) value).toInstant(), ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return LocalDate.parse(text);
            } catch (RuntimeException ignored) {
                // 继续尝试带时间格式
            }
            try {
                return LocalDateTime.parse(text).toLocalDate();
            } catch (RuntimeException ignored) {
                // 继续尝试 JDBC 格式
            }
            try {
                return Timestamp.valueOf(text).toLocalDateTime().toLocalDate();
            } catch (RuntimeException ignored) {
                if (text.length() >= 10) {
                    try {
                        return LocalDate.parse(text.substring(0, 10));
                    } catch (RuntimeException ignoredAgain) {
                        return value;
                    }
                }
                return value;
            }
        }
        return value;
    }

    private static ZoneId extractTimezone(String type) {
        if (type == null) {
            return null;
        }
        int first = type.indexOf('\'');
        if (first < 0) {
            return null;
        }
        int second = type.indexOf('\'', first + 1);
        if (second <= first) {
            return null;
        }
        String timezone = type.substring(first + 1, second);
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 杂项

    private static String qualifiedTableName(TableMeta tableMeta) {
        String schema = tableMeta.getSchema();
        if (schema == null || schema.isBlank()) {
            schema = tableMeta.getCatalog();
        }
        if (schema == null || schema.isBlank()) {
            return quoteIdentifier(tableMeta.getTable());
        }
        return quoteIdentifier(schema) + "." + quoteIdentifier(tableMeta.getTable());
    }

    private static String quoteIdentifier(String name) {
        return "`" + (name == null ? "" : name.replace("`", "``")) + "`";
    }

    private static boolean isArrayBase(String base) {
        return "ARRAY".equals(base);
    }

    private static boolean isMapBase(String base) {
        return "MAP".equals(base);
    }

    private static boolean isTupleBase(String base) {
        return "TUPLE".equals(base) || "NESTED".equals(base);
    }

    private static boolean isNumericBase(String base) {
        return startsWithIgnoreCase(base, "INT") || startsWithIgnoreCase(base, "UINT") || startsWithIgnoreCase(base, "FLOAT") ||
            startsWithIgnoreCase(base, "DOUBLE") || startsWithIgnoreCase(base, "DECIMAL") || startsWithIgnoreCase(base, "NUMERIC");
    }

    private static boolean isStringBase(String base) {
        return "STRING".equals(base) || "FIXEDSTRING".equals(base) || "ENUM8".equals(base) || "ENUM16".equals(base) ||
            "ENUM".equals(base) || "JSON".equals(base) || "OBJECT".equals(base) || "NOTHING".equals(base) ||
            "VARCHAR".equals(base) || "CHAR".equals(base) || "TEXT".equals(base) || "LONGTEXT".equals(base) ||
            "CLOB".equals(base) || "BINARY".equals(base) || "VARBINARY".equals(base) || "BLOB".equals(base);
    }

    private static boolean isDateTimeBase(String base) {
        return "DATETIME".equals(base) || "DATETIME64".equals(base) || "TIMESTAMP".equals(base) ||
            "TIMESTAMPTZ".equals(base) || "DATETIMEOFFSET".equals(base);
    }

    private static boolean isComplexType(String type) {
        String base = baseName(type);
        return "ARRAY".equals(base) || "MAP".equals(base) || "TUPLE".equals(base) || "NESTED".equals(base) ||
            "AGGREGATEFUNCTION".equals(base) || "OBJECT".equals(base) || "NOTHING".equals(base) ||
            startsWithIgnoreCase(type, "STRUCT<") || startsWithIgnoreCase(type, "ROW(") || startsWithIgnoreCase(type, "TUPLE<");
    }

    private static boolean isNullableForbidden(String type) {
        String base = baseName(type);
        return isComplexType(type) || "LOWCARDINALITY".equals(base) || startsWithIgnoreCase(type, "LowCardinality(");
    }

    private static boolean isClickHouseScalarBase(String base) {
        return "INT8".equals(base) || "INT16".equals(base) || "INT32".equals(base) || "INT64".equals(base) ||
            "INT128".equals(base) || "INT256".equals(base) || "UINT8".equals(base) || "UINT16".equals(base) ||
            "UINT32".equals(base) || "UINT64".equals(base) || "UINT128".equals(base) || "UINT256".equals(base) ||
            "FLOAT32".equals(base) || "FLOAT64".equals(base) || "DATETIME64".equals(base) || "DATE32".equals(base) ||
            "FIXEDSTRING".equals(base) || "ENUM8".equals(base) || "ENUM16".equals(base) || "IPV4".equals(base) ||
            "IPV6".equals(base) || "NOTHING".equals(base) || "BFLOAT16".equals(base) || "DECIMAL32".equals(base) ||
            "DECIMAL64".equals(base) || "DECIMAL128".equals(base) || "DECIMAL256".equals(base);
    }

    private static boolean isClickHouseDateTimeWithZone(String type) {
        String base = baseName(type);
        return "DATETIME".equals(base) && extractTimezone(type) != null;
    }

    private static boolean looksLikeClickHouseType(String type) {
        if (type == null) {
            return false;
        }
        String trimmed = type.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("NULLABLE(") || upper.startsWith("LOWCARDINALITY(") || upper.startsWith("SIMPLEAGGREGATEFUNCTION(") ||
            upper.startsWith("ARRAY(") || upper.startsWith("MAP(") || upper.startsWith("TUPLE(") || upper.startsWith("NESTED(") ||
            upper.startsWith("AGGREGATEFUNCTION(") || upper.startsWith("OBJECT(")) {
            return true;
        }
        String base = baseName(trimmed);
        return isClickHouseScalarBase(base) || "JSON".equals(base) || "OBJECT".equals(base) || "NOTHING".equals(base) ||
            "DATETIME".equals(base) && extractTimezone(trimmed) != null;
    }

    private static String baseName(String type) {
        String stripped = DatabaseConverter.stripTypeParameters(type == null ? "" : type.trim());
        return stripped == null ? "" : stripped.toUpperCase(Locale.ROOT).trim();
    }

    private static String unwrapType(String type, String wrapper) {
        if (type == null) {
            return null;
        }
        String trimmed = type.trim();
        if (trimmed.length() <= wrapper.length() || !trimmed.regionMatches(true, 0, wrapper, 0, wrapper.length())) {
            return null;
        }
        if (trimmed.charAt(wrapper.length()) != '(') {
            return null;
        }
        int close = matchingClose(trimmed, wrapper.length());
        if (close != trimmed.length() - 1) {
            return null;
        }
        return trimmed.substring(wrapper.length() + 1, close).trim();
    }

    private static int matchingClose(String text, int openIndex) {
        int depth = 0;
        char quote = 0;
        boolean escape = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return parts;
        }
        int depth = 0;
        char quote = 0;
        boolean escape = false;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(text.substring(start).trim());
        return parts;
    }

    private static int topLevelSpaceIndex(String text) {
        int depth = 0;
        char quote = 0;
        boolean escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && Character.isWhitespace(c)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSimpleIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    private static String unwrappedInner(String type) {
        int open = type == null ? -1 : type.indexOf('(');
        if (open < 0) {
            return null;
        }
        int close = matchingClose(type, open);
        if (close < 0) {
            return null;
        }
        return type.substring(open + 1, close).trim();
    }

    private static List<String> typeArguments(String type) {
        String inner = unwrappedInner(type);
        return inner == null ? Collections.emptyList() : splitTopLevel(inner);
    }

    private static Integer intArgument(String type, int index) {
        List<String> args = typeArguments(type);
        if (index < 0 || index >= args.size()) {
            return null;
        }
        return parseInteger(args.get(index));
    }

    private static Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static void applyTypeDetails(ColumnMeta column) {
        String type = column.getType();
        if (type == null) {
            return;
        }
        String base = baseName(type);
        if (base.startsWith("DECIMAL")) {
            int[] decimal = decimalPrecisionScale(type, column);
            if (decimal != null) {
                column.setPrecision(decimal[0]);
                column.setScale(decimal[1]);
            }
        } else if ("DATETIME64".equals(base)) {
            Integer scale = intArgument(type, 0);
            if (scale != null) {
                column.setScale(Math.min(Math.max(scale, 0), 9));
            }
        } else if ("FIXEDSTRING".equals(base)) {
            Integer length = intArgument(type, 0);
            if (length != null) {
                column.setLength(length);
            }
        } else if ("UUID".equals(base)) {
            column.setLength(36);
        }
    }

    private static UnwrappedType unwrapTopLevel(String type) {
        String current = type == null ? "" : type.trim();
        boolean nullable = false;
        while (true) {
            String inner = unwrapType(current, "Nullable");
            if (inner != null) {
                nullable = true;
                current = inner;
                continue;
            }
            inner = unwrapType(current, "LowCardinality");
            if (inner != null) {
                current = inner;
                continue;
            }
            inner = unwrapType(current, "SimpleAggregateFunction");
            if (inner != null) {
                List<String> args = splitTopLevel(inner);
                if (args.isEmpty()) {
                    break;
                }
                current = args.get(args.size() - 1).trim();
                continue;
            }
            break;
        }
        return new UnwrappedType(current, nullable);
    }

    private static final class UnwrappedType {
        private final String type;
        private final boolean nullable;

        private UnwrappedType(String type, boolean nullable) {
            this.type = type;
            this.nullable = nullable;
        }
    }

    private static final class DdlType {
        private String type;
        private final boolean nullable;

        private DdlType(String type, boolean nullable) {
            this.type = type;
            this.nullable = nullable;
        }
    }
}
