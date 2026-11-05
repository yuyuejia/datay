package com.data.metadata.impl;

import com.data.metadata.*;
import com.data.metadata.util.DBUtils;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class PostgresqlConverter implements TypeConverter {

    enum PostgresType {
        // 数值类型（参数值全大写）
        SMALLINT("SMALLINT"),
        INTEGER("INTEGER"),
        BIGINT("BIGINT"),
        BIT("BIT"),
        SERIAL("SERIAL"),
        BIGSERIAL("BIGSERIAL"),
        REAL("REAL"),
        DOUBLE_PRECISION("DOUBLE PRECISION"), // 注意空格保留
        NUMERIC("NUMERIC"),
        MONEY("MONEY"),

        // 字符类型
        CHAR("CHAR"),
        VARCHAR("VARCHAR"),
        TEXT("TEXT"),

        // 二进制
        BYTEA("BYTEA"),

        // 时间类型
        DATE("DATE"),
        TIME("TIME"),
        TIMESTAMP("TIMESTAMP"),
        TIME_WITH_TIME_ZONE("TIME WITH TIME ZONE"),
        TIMESTAMP_WITH_TIME_ZONE("TIMESTAMP WITH TIME ZONE"),
        INTERVAL("INTERVAL"),

        // 布尔
        BOOLEAN("BOOLEAN"),

        // JSON
        JSON("JSON"),
        JSONB("JSONB"),

        // 特殊类型
        UUID("UUID"),
        XML("XML"),
        CIDR("CIDR"),
        INET("INET"),
        MACADDR("MACADDR"),
        POINT("POINT"),
        LINE("LINE"),
        LSEG("LSEG"),
        BOX("BOX"),
        PATH("PATH"),
        POLYGON("POLYGON"),
        CIRCLE("CIRCLE");

        private final String typeName;

        PostgresType(String typeName) {
            this.typeName = typeName.toUpperCase(); // 确保值大写
        }

        public String getName() {
            return typeName;
        }
    }

    public static HashMap<String, String> commonDataType2ColumnType = new HashMap<>();

    static {
        DatabaseConverter.register(DBType.POSTGRESQL.name(), new PostgresqlConverter());

        // 数值类型映射
        commonDataType2ColumnType.put(AllDataType.TINYINT.name(), PostgresType.SMALLINT.name());
        commonDataType2ColumnType.put(AllDataType.SMALLINT.name(), PostgresType.SMALLINT.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMINT.name(), PostgresType.INTEGER.name());
        commonDataType2ColumnType.put(AllDataType.INT.name(), PostgresType.INTEGER.name());
        commonDataType2ColumnType.put(AllDataType.INTEGER.name(), PostgresType.INTEGER.name());
        commonDataType2ColumnType.put(AllDataType.BIGINT.name(), PostgresType.BIGINT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT.name(), PostgresType.REAL.name());
        commonDataType2ColumnType.put(AllDataType.DOUBLE.name(), PostgresType.DOUBLE_PRECISION.getName());
        commonDataType2ColumnType.put(AllDataType.BINARY_DOUBLE.name(), PostgresType.DOUBLE_PRECISION.getName());
        commonDataType2ColumnType.put(AllDataType.BINARY_FLOAT.name(), PostgresType.REAL.name());
        commonDataType2ColumnType.put(AllDataType.DECIMAL.name(), PostgresType.NUMERIC.name());
        commonDataType2ColumnType.put(AllDataType.NUMERIC.name(), PostgresType.NUMERIC.name());
        commonDataType2ColumnType.put(AllDataType.NUMBER.name(), PostgresType.NUMERIC.name());
        commonDataType2ColumnType.put(AllDataType.MONEY.name(), PostgresType.MONEY.name());

        // 字符类型
        commonDataType2ColumnType.put(AllDataType.CHAR.name(), PostgresType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.NCHAR.name(), PostgresType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR.name(), PostgresType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR2.name(), PostgresType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR.name(), PostgresType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR2.name(), PostgresType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.TEXT.name(), PostgresType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.TINYTEXT.name(), PostgresType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMTEXT.name(), PostgresType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.LONGTEXT.name(), PostgresType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.LONG.name(), PostgresType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.CLOB.name(), PostgresType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.NCLOB.name(), PostgresType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.RAW.name(), PostgresType.VARCHAR.name());

        // 二进制类型
        commonDataType2ColumnType.put(AllDataType.BINARY.name(), PostgresType.BYTEA.name());
        commonDataType2ColumnType.put(AllDataType.VARBINARY.name(), PostgresType.BYTEA.name());
        commonDataType2ColumnType.put(AllDataType.BLOB.name(), PostgresType.BYTEA.name());
        commonDataType2ColumnType.put(AllDataType.TINYBLOB.name(), PostgresType.BYTEA.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMBLOB.name(), PostgresType.BYTEA.name());
        commonDataType2ColumnType.put(AllDataType.LONGBLOB.name(), PostgresType.BYTEA.name());

        // 时间类型
        commonDataType2ColumnType.put(AllDataType.DATE.name(), PostgresType.DATE.name());
        commonDataType2ColumnType.put(AllDataType.DATETIME.name(), PostgresType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIME.name(), PostgresType.TIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP.name(), PostgresType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIME_WITH_TIME_ZONE.getName(), PostgresType.TIME_WITH_TIME_ZONE.getName());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMPTZ.name(), PostgresType.TIMESTAMP_WITH_TIME_ZONE.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_TIME_ZONE.getName(), PostgresType.TIMESTAMP_WITH_TIME_ZONE.getName());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_6.getName(), PostgresType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.getName(), PostgresType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL.name(), PostgresType.INTERVAL.name());

        // 其他类型
        commonDataType2ColumnType.put(AllDataType.BOOLEAN.name(), PostgresType.BOOLEAN.name());
        commonDataType2ColumnType.put(AllDataType.XML.name(), PostgresType.XML.name());
        commonDataType2ColumnType.put(AllDataType.JSON.name(), PostgresType.JSONB.name());
        commonDataType2ColumnType.put(AllDataType.UUID.name(), PostgresType.UUID.name());
        commonDataType2ColumnType.put(AllDataType.BIT.name(), PostgresType.BIT.name());
        commonDataType2ColumnType.put(AllDataType.VARBIT.name(), PostgresType.BYTEA.name());
        // MySQL 专有类型：PostgreSQL 没有 YEAR/ENUM/SET，按可读值落库
        commonDataType2ColumnType.put(AllDataType.YEAR.name(), PostgresType.SMALLINT.name());
        commonDataType2ColumnType.put(AllDataType.ENUM.name(), PostgresType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.SET.name(), PostgresType.VARCHAR.name());
    }

    @Override
    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType().toUpperCase();
        // PostgreSQL 没有 UNSIGNED，先去掉后缀按基础类型查表，再按需要向上拓宽整数类型。
        String normalizedType = upperType.replace(" UNSIGNED", "").trim();
        // DuckDB 等驱动返回的类型名会带精度，如 DECIMAL(18,4)；查表前先去参数。
        String baseType = DatabaseConverter.stripTypeParameters(normalizedType);
        String type = commonDataType2ColumnType.get(baseType);
        if (type == null) {
            if (baseType.startsWith("INTERVAL")) {
                type = PostgresType.INTERVAL.name();
            } else {
                System.out.println("postgresql not found type:" + upperType);
                type = PostgresType.VARCHAR.name();
            }
        }
        if ("NUMBER".equals(baseType) && columnMeta.getScale() <= 0 && columnMeta.getPrecision() <= 20 && columnMeta.getPrecision() > 0) {
            type = PostgresType.BIGINT.name();
        }
        boolean unsigned = upperType.endsWith(" UNSIGNED");
        if (unsigned) {
            switch (baseType) {
                case "TINYINT":
                    type = PostgresType.SMALLINT.name();
                    break;
                case "SMALLINT":
                case "MEDIUMINT":
                    type = PostgresType.INTEGER.name();
                    break;
                case "INT":
                case "INTEGER":
                    type = PostgresType.BIGINT.name();
                    break;
                case "BIGINT":
                    type = PostgresType.NUMERIC.name();
                    break;
                default:
                    break;
            }
        }
        // 单 bit 在 PostgreSQL 中按布尔落库（generateColumnDDL 也按此规则生成），
        // 让下游参数绑定拿到真实的 BOOLEAN 类型，而不是 BYTE/SMALLINT。
        if (PostgresType.BIT.name().equals(type) && columnMeta.getLength() <= 1) {
            type = PostgresType.BOOLEAN.name();
        }
        int precision = columnMeta.getPrecision();
        int scale = columnMeta.getScale();
        if (unsigned && "BIGINT".equals(baseType)) {
            // BIGINT UNSIGNED 最大 18446744073709551615，共 20 位
            precision = 20;
            scale = 0;
        }
        if ("DECIMAL".equals(baseType) || "NUMERIC".equals(baseType)) {
            int[] precisionScale = DatabaseConverter.parseTypePrecisionScale(upperType);
            if (precisionScale != null) {
                if (precision <= 0) {
                    precision = precisionScale[0];
                }
                if (scale <= 0) {
                    scale = precisionScale[1];
                }
            }
        }
        ColumnMeta column = new ColumnMeta(columnMeta.getName(), type);
        column.setLength(columnMeta.getLength());
        column.setPrecision(precision);
        column.setScale(scale);
        column.setNullable(columnMeta.isNullable());
        column.setDefaultValue(columnMeta.getDefaultValue());
        column.setComment(columnMeta.getComment());
        column.setPrimaryKey(columnMeta.isPrimaryKey());
        column.setTypeDefinition(columnMeta.getTypeDefinition());
        return column;
    }

    /**
     * PostgreSQL 的 BOOLEAN / BIT / JSONB 列不接受驱动按 VARCHAR/INTEGER 推断的参数类型，
     * 需要以 {@link Types#OTHER}（未指定类型）下发文本，由服务端按目标列类型解析。
     */
    @Override
    public void bindParameter(PreparedStatement statement, int index, ColumnMeta column, Object value) throws SQLException {
        if (value != null) {
            String type = column.getType() == null ? "" : column.getType().toUpperCase().trim();
            if ("BOOLEAN".equals(type) || "BOOL".equals(type)) {
                statement.setObject(index, toBoolean(value), Types.OTHER);
                return;
            }
            if ("BIT".equals(type) || type.startsWith("BIT(") || "VARBIT".equals(type)) {
                statement.setObject(index, toBitString(value, column.getLength()), Types.OTHER);
                return;
            }
            if ("JSON".equals(type) || "JSONB".equals(type)) {
                String json;
                if (value instanceof byte[]) {
                    json = new String((byte[]) value, StandardCharsets.UTF_8);
                } else if (value instanceof java.util.Map || value instanceof java.util.List) {
                    json = com.alibaba.fastjson2.JSON.toJSONString(value);
                } else {
                    json = value.toString();
                }
                statement.setObject(index, json, Types.OTHER);
                return;
            }
        }
        statement.setObject(index, DBUtils.normalizeParameter(column.getType(), value));
    }

    @Override
    public Object normalizeSourceValue(ColumnMeta column, Object value) {
        // PostgreSQL 的 jsonb/uuid/interval 等类型会以 PGobject 返回，转为文本便于下游处理
        if (value instanceof org.postgresql.util.PGobject) {
            return ((org.postgresql.util.PGobject) value).getValue();
        }
        return value;
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof java.util.BitSet) {
            java.util.BitSet bits = (java.util.BitSet) value;
            return bits.length() > 0 && bits.get(0);
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        String text = value.toString().trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "yes".equals(text) || "t".equals(text);
    }

    /**
     * MySQL Binlog 的 BIT(n) 以 {@link java.util.BitSet}（低位在前）返回，
     * PostgreSQL bit(n) 使用从左到右的二进制字符串，这里统一转换为定长位串。
     */
    private String toBitString(Object value, int length) {
        int width = length;
        if (width <= 0) {
            if (value instanceof java.util.BitSet) {
                width = Math.max(1, ((java.util.BitSet) value).length());
            } else if (value instanceof byte[]) {
                width = Math.max(1, ((byte[]) value).length * 8);
            } else if (value instanceof Number) {
                width = Math.max(1, Long.SIZE - Long.numberOfLeadingZeros(((Number) value).longValue()));
            } else {
                width = Math.max(1, value.toString().trim().length());
            }
        }
        StringBuilder bits = new StringBuilder(width);
        if (value instanceof java.util.BitSet) {
            java.util.BitSet bitSet = (java.util.BitSet) value;
            for (int i = width - 1; i >= 0; i--) {
                bits.append(bitSet.get(i) ? '1' : '0');
            }
            return bits.toString();
        }
        if (value instanceof byte[]) {
            // 快照经 JDBC 读取 BIT(n) 时通常返回大端字节数组
            StringBuilder raw = new StringBuilder();
            for (byte b : (byte[]) value) {
                for (int i = 7; i >= 0; i--) {
                    raw.append(((b >> i) & 1) == 1 ? '1' : '0');
                }
            }
            String text = raw.toString();
            if (text.length() > width) {
                return text.substring(text.length() - width);
            }
            return "0".repeat(width - text.length()) + text;
        }
        if (value instanceof Number) {
            long number = ((Number) value).longValue();
            for (int i = width - 1; i >= 0; i--) {
                bits.append(((number >> i) & 1L) == 1L ? '1' : '0');
            }
            return bits.toString();
        }
        String text = value.toString().trim();
        if (text.matches("[01]+")) {
            int padding = width - text.length();
            for (int i = 0; i < padding; i++) {
                bits.append('0');
            }
            return padding >= 0 ? bits.append(text).toString() : text.substring(text.length() - width);
        }
        return toBoolean(value) ? "1".repeat(width) : "0".repeat(width);
    }

    @Override
    public String generateTableDDL(TableMeta table) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE ")
            .append(table.getSchema())
            .append(".")
            .append(table.getTable())
            .append(" (\n");

        List<String> columns = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();

        for (ColumnMeta col : table.columns()) {
            String columnDDL = generateColumnDDL(col);
            columns.add("  " + columnDDL);
            if (col.isPrimaryKey()) {
                primaryKeys.add("\"" + col.getName() + "\"");
            }
        }

        // 处理主键约束
        if (!primaryKeys.isEmpty()) {
            columns.add("  CONSTRAINT pk_" + table.getTable() + " PRIMARY KEY (" + String.join(", ", primaryKeys) + ")");
        }

        ddl.append(String.join(",\n", columns));
        ddl.append("\n);");

        // 表注释
        if (table.getComment() != null && !table.getComment().isEmpty()) {
            ddl
                .append("\nCOMMENT ON TABLE \"")
                .append(table.getTable())
                .append("\" IS '")
                .append(escapeComment(table.getComment()))
                .append("';");
        }

        // 列注释
        for (ColumnMeta col : table.columns()) {
            if (col.getComment() != null && !col.getComment().isEmpty()) {
                ddl
                    .append("\nCOMMENT ON COLUMN \"")
                    .append(table.getTable())
                    .append("\".\"")
                    .append(col.getName())
                    .append("\" IS '")
                    .append(escapeComment(col.getComment()))
                    .append("';");
            }
        }

        return ddl.toString();
    }

    // 生成INSERT语句（append模式）
    public String generateInsertSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add("\"" + column.getName() + "\"");
        }

        String columns = String.join(", ", columnNames);
        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));

        String tableName = tableMeta.getSchema() != null ? tableMeta.getSchema() + "." + tableMeta.getTable() : tableMeta.getTable();

        return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
    }

    // 生成UPSERT语句（update模式）- PostgreSQL使用ON CONFLICT
    public String generateUpsertSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();
        List<String> primaryKeyColumns = new ArrayList<>();

        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add("\"" + column.getName() + "\"");
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add("\"" + column.getName() + "\"");
            }
        }

        String columns = String.join(", ", columnNames);
        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));

        String tableName = tableMeta.getSchema() != null ? tableMeta.getSchema() + "." + tableMeta.getTable() : tableMeta.getTable();

        // 如果没有主键，则回退到普通INSERT
        if (primaryKeyColumns.isEmpty()) {
            return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
        }

        // 生成UPDATE部分
        List<String> updateParts = new ArrayList<>();
        for (String columnName : columnNames) {
            updateParts.add(columnName + " = EXCLUDED." + columnName);
        }

        return String.format(
            "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT (%s) DO UPDATE SET %s",
            tableName,
            columns,
            placeholders,
            String.join(", ", primaryKeyColumns),
            String.join(", ", updateParts)
        );
    }

    // 生成REPLACE语句（replace模式）- PostgreSQL使用ON CONFLICT DO NOTHING + DELETE + INSERT
    public String generateReplaceSQL(TableMeta tableMeta) {
        // PostgreSQL没有直接的REPLACE语句，使用UPSERT替代
        return generateUpsertSQL(tableMeta);
    }

    // 新增：生成DELETE语句（delete模式）- PostgreSQL使用WHERE条件删除
    public String generateDeleteSQL(TableMeta tableMeta) {
        List<String> primaryKeyColumns = new ArrayList<>();
        List<String> allColumns = new ArrayList<>();

        for (ColumnMeta column : tableMeta.columns()) {
            allColumns.add("\"" + column.getName() + "\"");
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add("\"" + column.getName() + "\"");
            }
        }

        String tableName = tableMeta.getSchema() != null ? tableMeta.getSchema() + "." + tableMeta.getTable() : tableMeta.getTable();

        // 如果有主键，使用主键作为WHERE条件
        if (!primaryKeyColumns.isEmpty()) {
            List<String> whereConditions = new ArrayList<>();
            for (String pkColumn : primaryKeyColumns) {
                whereConditions.add(pkColumn + " = ?");
            }
            return String.format("DELETE FROM %s WHERE %s", tableName, String.join(" AND ", whereConditions));
        } else {
            // 如果没有主键，使用所有列作为WHERE条件
            List<String> whereConditions = new ArrayList<>();
            for (String column : allColumns) {
                whereConditions.add(column + " = ?");
            }
            return String.format("DELETE FROM %s WHERE %s", tableName, String.join(" AND ", whereConditions));
        }
    }

    @Override
    public String generateColumnDDL(ColumnMeta column) {
        StringBuilder sb = new StringBuilder();
        String typeUpper = column.getType().toUpperCase();

        if (typeUpper.matches("VARCHAR|CHAR|VARBINARY|BINARY")) {
            // 这些类型必须明确指定长度
            int length = column.getLength() > 0 && column.getLength() <= 16383 ? column.getLength() : getDefaultLength(typeUpper);
            sb.append(column.getType()).append("(").append(length).append(")");
        } else if (typeUpper.equals("BIT")) {
            if (column.getLength() == 1) {
                sb.append(PostgresType.BOOLEAN.name());
            } else {
                sb.append(column.getType()).append("(").append(column.getLength()).append(")");
            }
        } else if (typeUpper.matches("DECIMAL|NUMERIC")) {
            // 处理精度和小数位
            if (column.getPrecision() <= 0) {
                sb.append(column.getType());
            } else {
                sb.append(column.getType()).append("(").append(column.getPrecision());
                if (column.getScale() < 0) {
                    // 未知小数位时交给 PostgreSQL 使用默认精度
                    sb.append(")");
                } else {
                    sb.append(",").append(column.getScale()).append(")");
                }
            }
        } else {
            // 其他类型不添加长度（如TEXT/BLOB等）
            sb.append(column.getType());
        }

        // 非空约束
        if (!column.isNullable()) {
            sb.append(" NOT NULL");
        }

        // 默认值处理
        if (
            column.getDefaultValue() != null &&
            !"NULL".equalsIgnoreCase(column.getDefaultValue()) &&
            !"''".equalsIgnoreCase(column.getDefaultValue())
        ) {
            String defaultValue = column.getDefaultValue().toString();

            // 检查是否为需要不加引号的特殊默认值
            if (isSpecialDefaultValue(defaultValue)) {
                sb.append(" DEFAULT ").append(defaultValue);
            } else {
                sb.append(" DEFAULT '").append(defaultValue).append("'");
            }
        }

        return String.format("%s %s", column.getName(), sb.toString());
    }

    /**
     * 检查是否为需要不加引号的特殊默认值
     * 例如：CURRENT_TIMESTAMP, NOW(), UUID(), RAND() 等函数调用
     */
    private boolean isSpecialDefaultValue(String defaultValue) {
        if (defaultValue == null) {
            return false;
        }

        String upperValue = defaultValue.toUpperCase();

        // 检查是否为函数调用（包含括号）
        if (upperValue.contains("(") && upperValue.contains(")")) {
            return true;
        }

        // 检查是否为特殊关键字（PostgreSQL支持的特殊默认值）
        return upperValue.matches("(?i)CURRENT_TIMESTAMP|NOW|CURRENT_DATE|CURRENT_TIME|LOCALTIMESTAMP|LOCALTIME|TRUE|FALSE|NULL");
    }

    private int getDefaultLength(String type) {
        switch (type.toUpperCase()) {
            case "VARCHAR":
            case "VARBINARY":
                return 255; // MySQL默认varchar长度
            case "CHAR":
            case "BINARY":
                return 1;
            case "BIT":
                return 1; // 常见bit(1)表示布尔
            default:
                return 0;
        }
    }

    private String escapeComment(String comment) {
        return comment.replace("'", "''");
    }

    private String escapeDefaultValue(String value) {
        if (value.matches("^nextval\\(.*")) { // 序列处理
            return value;
        }
        return "'" + value.replace("'", "''") + "'";
    }

    @Override
    public List<String> getSupportedTypes() {
        List<String> result = new ArrayList<>();
        for (PostgresType t : PostgresType.values()) {
            result.add(t.getName());
        }
        return result;
    }
}