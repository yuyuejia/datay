package com.data.metadata.impl;

import com.data.metadata.*;
import com.data.metadata.util.DBUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * 达梦数据库（DaMeng / DM）类型转换与 DDL 生成器。
 *
 * <p>达梦在语法上高度兼容 Oracle，因此标识符使用双引号并大写，数值统一映射到
 * {@code NUMBER}，更新语句使用 {@code MERGE INTO}。
 */
public class DmConverter implements TypeConverter {

    enum DmType {
        CHAR,
        VARCHAR,
        VARCHAR2,
        NCHAR,
        NVARCHAR2,
        NUMBER,
        INT,
        BIGINT,
        TINYINT,
        SMALLINT,
        FLOAT,
        DOUBLE,
        DECIMAL,
        DATE,
        TIME,
        TIMESTAMP,
        CLOB,
        BLOB,
        VARBINARY,
        BIT,
        BOOLEAN,
    }

    public static HashMap<String, String> commonDataType2ColumnType = new HashMap<>();

    static {
        DatabaseConverter.register(DBType.DM.name(), new DmConverter());

        // 数值类型
        commonDataType2ColumnType.put(AllDataType.LARGEINT.name(), DmType.NUMBER.name());
        commonDataType2ColumnType.put(AllDataType.TINYINT.name(), DmType.TINYINT.name());
        commonDataType2ColumnType.put(AllDataType.SMALLINT.name(), DmType.SMALLINT.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMINT.name(), DmType.INT.name());
        commonDataType2ColumnType.put(AllDataType.INT.name(), DmType.INT.name());
        commonDataType2ColumnType.put(AllDataType.INTEGER.name(), DmType.INT.name());
        commonDataType2ColumnType.put(AllDataType.BIGINT.name(), DmType.BIGINT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT.name(), DmType.FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT4.name(), DmType.FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT8.name(), DmType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.DOUBLE.name(), DmType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.DECIMAL.name(), DmType.NUMBER.name());
        commonDataType2ColumnType.put(AllDataType.NUMERIC.name(), DmType.NUMBER.name());
        commonDataType2ColumnType.put(AllDataType.NUMBER.name(), DmType.NUMBER.name());
        commonDataType2ColumnType.put(AllDataType.MONEY.name(), DmType.NUMBER.name());

        // 字符类型
        commonDataType2ColumnType.put(AllDataType.STRING.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.CHAR.name(), DmType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.BPCHAR.name(), DmType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.NCHAR.name(), DmType.NCHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR2.name(), DmType.VARCHAR2.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR.name(), DmType.NVARCHAR2.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR2.name(), DmType.NVARCHAR2.name());
        commonDataType2ColumnType.put(AllDataType.TEXT.name(), DmType.CLOB.name());
        commonDataType2ColumnType.put(AllDataType.TINYTEXT.name(), DmType.CLOB.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMTEXT.name(), DmType.CLOB.name());
        commonDataType2ColumnType.put(AllDataType.LONGTEXT.name(), DmType.CLOB.name());
        commonDataType2ColumnType.put(AllDataType.CLOB.name(), DmType.CLOB.name());
        commonDataType2ColumnType.put(AllDataType.NCLOB.name(), DmType.CLOB.name());
        commonDataType2ColumnType.put(AllDataType.XML.name(), DmType.CLOB.name());
        commonDataType2ColumnType.put(AllDataType.JSON.name(), DmType.CLOB.name());
        commonDataType2ColumnType.put(AllDataType.ENUM.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.SET.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.UUID.name(), DmType.VARCHAR.name());

        // 二进制类型
        commonDataType2ColumnType.put(AllDataType.RAW.name(), DmType.VARBINARY.name());
        commonDataType2ColumnType.put(AllDataType.BINARY.name(), DmType.VARBINARY.name());
        commonDataType2ColumnType.put(AllDataType.VARBINARY.name(), DmType.VARBINARY.name());
        commonDataType2ColumnType.put(AllDataType.BYTEA.name(), DmType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.BLOB.name(), DmType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.TINYBLOB.name(), DmType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMBLOB.name(), DmType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.LONGBLOB.name(), DmType.BLOB.name());

        // 时间类型
        commonDataType2ColumnType.put(AllDataType.DATE.name(), DmType.DATE.name());
        commonDataType2ColumnType.put(AllDataType.DATETIME.name(), DmType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIME.name(), DmType.TIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP.name(), DmType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_6.getName(), DmType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMPTZ.name(), DmType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_TIME_ZONE.name(), DmType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.name(), DmType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIME_WITH_TIME_ZONE.name(), DmType.TIME.name());
        commonDataType2ColumnType.put(AllDataType.YEAR.name(), DmType.INT.name());

        // 布尔与位
        commonDataType2ColumnType.put(AllDataType.BOOLEAN.name(), DmType.BIT.name());
        commonDataType2ColumnType.put(AllDataType.BOOL.name(), DmType.BIT.name());
        commonDataType2ColumnType.put(AllDataType.BIT.name(), DmType.BIT.name());
        commonDataType2ColumnType.put(AllDataType.VARBIT.name(), DmType.BIT.name());

        commonDataType2ColumnType.put(AllDataType.INTERVAL.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_YEAR.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_YEAR_TO_MONTH.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_MONTH.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY_TO_HOUR.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY_TO_MINUTE.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY_TO_SECOND.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_HOUR.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_HOUR_TO_MINUTE.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_HOUR_TO_SECOND.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_MINUTE.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_MINUTE_TO_SECOND.name(), DmType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_SECOND.name(), DmType.VARCHAR.name());
    }

    @Override
    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType() == null ? "" : columnMeta.getType().toUpperCase().trim();
        boolean unsigned = upperType.endsWith(" UNSIGNED");
        String normalizedType = unsigned
            ? upperType.substring(0, upperType.length() - " UNSIGNED".length()).trim()
            : upperType;
        // 驱动可能返回带精度的类型名（如 DECIMAL(20,6)），查表前先去掉参数
        String baseType = DatabaseConverter.stripTypeParameters(normalizedType).toUpperCase();
        String type = commonDataType2ColumnType.get(baseType);
        if (type == null) {
            if (baseType.startsWith("INTERVAL")) {
                type = DmType.VARCHAR.name();
                columnMeta.setLength(100);
            } else if (baseType.startsWith("DECIMAL") || baseType.startsWith("NUMERIC")) {
                type = DmType.NUMBER.name();
            } else if (baseType.startsWith("INT")) {
                type = DmType.BIGINT.name();
            } else {
                System.out.println("dm not found type:" + upperType);
                type = DmType.VARCHAR.name();
            }
        }

        int precision = columnMeta.getPrecision();
        int scale = columnMeta.getScale();

        // 达梦 BIT 只能存储 0/1；MySQL BIT(n) 按达梦官方迁移规则拓宽为整数类型
        if (("BIT".equals(baseType) || "VARBIT".equals(baseType)) && !unsigned) {
            int width = columnMeta.getLength();
            if (width <= 1) {
                type = DmType.BIT.name();
            } else if (width <= 7) {
                type = DmType.TINYINT.name();
            } else if (width <= 15) {
                type = DmType.SMALLINT.name();
            } else if (width <= 31) {
                type = DmType.INT.name();
            } else if (width <= 63) {
                type = DmType.BIGINT.name();
            } else {
                type = DmType.NUMBER.name();
                precision = 20;
                scale = 0;
            }
        }

        // MySQL 无符号整数需要向上拓宽，避免超出达梦对应整型的取值范围
        if (unsigned) {
            switch (baseType) {
                case "TINYINT":
                    type = DmType.SMALLINT.name();
                    break;
                case "SMALLINT":
                case "MEDIUMINT":
                    type = DmType.INT.name();
                    break;
                case "INT":
                case "INTEGER":
                    type = DmType.BIGINT.name();
                    break;
                case "BIGINT":
                    type = DmType.NUMBER.name();
                    precision = 20;
                    scale = 0;
                    break;
                default:
                    break;
            }
        }

        if ("DECIMAL".equals(baseType) || "NUMERIC".equals(baseType)) {
            int[] parsed = DatabaseConverter.parseTypePrecisionScale(upperType);
            if (parsed != null) {
                if (precision <= 0) {
                    precision = parsed[0];
                }
                if (scale <= 0) {
                    scale = parsed[1];
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

    @Override
    public String generateTableDDL(TableMeta table) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE ");
        if (table.getSchema() != null && !table.getSchema().isBlank()) {
            ddl.append("\"").append(table.getSchema().toUpperCase()).append("\".");
        }
        ddl.append("\"").append(table.getTable().toUpperCase()).append("\" (\n");

        List<String> primaryKeys = new ArrayList<>();
        for (ColumnMeta col : table.columns()) {
            ddl.append("  ").append(generateColumnDDL(col)).append(",\n");
            if (col.isPrimaryKey()) {
                primaryKeys.add("\"" + col.getName().toUpperCase() + "\"");
            }
        }

        if (!primaryKeys.isEmpty()) {
            ddl.append("  CONSTRAINT PK_").append(table.getTable().toUpperCase()).append(" PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
        } else {
            ddl.deleteCharAt(ddl.lastIndexOf(","));
        }
        ddl.append("\n)");
        return ddl.toString();
    }

    @Override
    public String generateColumnDDL(ColumnMeta column) {
        StringBuilder sb = new StringBuilder();
        String typeUpper = column.getType().toUpperCase();

        if (typeUpper.startsWith("NUMBER") || typeUpper.startsWith("DECIMAL") || typeUpper.startsWith("NUMERIC")) {
            if (column.getPrecision() <= 0) {
                sb.append(typeUpper);
            } else {
                sb.append(typeUpper).append("(").append(column.getPrecision());
                if (column.getScale() <= 0) {
                    sb.append(")");
                } else {
                    sb.append(",").append(column.getScale()).append(")");
                }
            }
        } else if (typeUpper.matches("CHAR|NCHAR|VARCHAR|VARCHAR2|NVARCHAR2|VARBINARY")) {
            int length = column.getLength() > 0 ? column.getLength() : getDefaultLength(typeUpper);
            sb.append(typeUpper).append("(").append(length).append(")");
        } else if (("TIMESTAMP".equals(typeUpper) || "TIME".equals(typeUpper)) && column.getScale() > 0) {
            sb.append(typeUpper).append("(").append(column.getScale()).append(")");
        } else {
            sb.append(typeUpper);
        }

        if (!column.isNullable()) {
            sb.append(" NOT NULL");
        }
        return String.format("\"%s\" %s", column.getName().toUpperCase(), sb.toString());
    }

    /**
     * 达梦 BIT 只接受布尔值；MySQL BIT(n) 落到达梦整数列时，Binlog 的 BitSet
     * 或快照的 byte[] 需要先还原成对应的数值，否则驱动无法正确绑定。
     */
    @Override
    public void bindParameter(PreparedStatement statement, int index, ColumnMeta column, Object value) throws SQLException {
        if (value != null) {
            String type = column.getType() == null ? "" : column.getType().toUpperCase().trim();
            String baseType = DatabaseConverter.stripTypeParameters(type);
            if (value instanceof BigInteger) {
                statement.setBigDecimal(index, new BigDecimal((BigInteger) value));
                return;
            }
            if (value instanceof BigDecimal) {
                statement.setBigDecimal(index, (BigDecimal) value);
                return;
            }
            if ("BIT".equals(baseType)) {
                statement.setBoolean(index, toBoolean(value));
                return;
            }
            boolean integerTarget = "TINYINT".equals(baseType) || "SMALLINT".equals(baseType)
                || "INT".equals(baseType) || "INTEGER".equals(baseType) || "BIGINT".equals(baseType)
                || "NUMBER".equals(baseType) || "DECIMAL".equals(baseType) || "NUMERIC".equals(baseType);
            if (integerTarget && (value instanceof BitSet || value instanceof byte[] || value instanceof Boolean)) {
                BigDecimal number = toNumber(value);
                if (number != null) {
                    statement.setBigDecimal(index, number);
                    return;
                }
            }
        }
        Object normalized = DBUtils.normalizeParameter(column.getType(), value);
        // 部分达梦驱动版本对 JDBC 4.2 的 java.time 支持不完整，统一回退到 java.sql 时间类型
        if (normalized instanceof java.time.LocalDate) {
            statement.setDate(index, java.sql.Date.valueOf((java.time.LocalDate) normalized));
            return;
        }
        if (normalized instanceof java.time.LocalTime) {
            // java.sql.Time + setTime 会丢弃小数秒，达梦驱动可直接绑定 LocalTime
            statement.setObject(index, normalized);
            return;
        }
        if (normalized instanceof java.time.LocalDateTime) {
            statement.setTimestamp(index, java.sql.Timestamp.valueOf((java.time.LocalDateTime) normalized));
            return;
        }
        statement.setObject(index, normalized);
    }

    private static BigDecimal toNumber(Object value) {
        if (value instanceof BitSet) {
            BitSet bits = (BitSet) value;
            BigInteger number = BigInteger.ZERO;
            for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
                number = number.setBit(i);
            }
            return new BigDecimal(number);
        }
        if (value instanceof byte[]) {
            return new BigDecimal(new BigInteger(1, (byte[]) value));
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return null;
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof BitSet) {
            BitSet bits = (BitSet) value;
            return bits.length() > 0 && bits.get(0);
        }
        if (value instanceof byte[]) {
            for (byte b : (byte[]) value) {
                if (b != 0) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        String text = value.toString().trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "t".equalsIgnoreCase(text);
    }

    @Override
    public String generateInsertSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add("\"" + column.getName().toUpperCase() + "\"");
        }
        String columns = String.join(", ", columnNames);
        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));
        String tableName = tableMeta.getSchema() != null
            ? tableMeta.getSchema() + "." + tableMeta.getTable()
            : tableMeta.getTable();
        return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
    }

    @Override
    public String generateUpsertSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();
        List<String> primaryKeyColumns = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add("\"" + column.getName().toUpperCase() + "\"");
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add("\"" + column.getName().toUpperCase() + "\"");
            }
        }
        if (primaryKeyColumns.isEmpty()) {
            return generateInsertSQL(tableMeta);
        }

        String tableName = tableMeta.getSchema() != null
            ? tableMeta.getSchema() + "." + tableMeta.getTable()
            : tableMeta.getTable();

        StringBuilder mergeSQL = new StringBuilder();
        mergeSQL.append("MERGE INTO ").append(tableName).append(" t USING (SELECT ");

        List<String> selectParts = new ArrayList<>();
        for (String columnName : columnNames) {
            selectParts.add("? AS " + columnName);
        }
        mergeSQL.append(String.join(", ", selectParts)).append(" FROM dual) s ON (");

        List<String> onConditions = new ArrayList<>();
        for (String pkColumn : primaryKeyColumns) {
            onConditions.add("t." + pkColumn + " = s." + pkColumn);
        }
        mergeSQL.append(String.join(" AND ", onConditions)).append(") WHEN MATCHED THEN UPDATE SET ");

        List<String> updateParts = new ArrayList<>();
        for (String columnName : columnNames) {
            if (!primaryKeyColumns.contains(columnName)) {
                updateParts.add("t." + columnName + " = s." + columnName);
            }
        }
        mergeSQL.append(String.join(", ", updateParts));

        mergeSQL.append(" WHEN NOT MATCHED THEN INSERT (").append(String.join(", ", columnNames)).append(") VALUES (");

        List<String> insertValues = new ArrayList<>();
        for (String columnName : columnNames) {
            insertValues.add("s." + columnName);
        }
        mergeSQL.append(String.join(", ", insertValues)).append(")");
        return mergeSQL.toString();
    }

    @Override
    public String generateReplaceSQL(TableMeta tableMeta) {
        return generateUpsertSQL(tableMeta);
    }

    @Override
    public String generateDeleteSQL(TableMeta tableMeta) {
        List<String> primaryKeyColumns = new ArrayList<>();
        List<String> allColumns = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            allColumns.add("\"" + column.getName().toUpperCase() + "\"");
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add("\"" + column.getName().toUpperCase() + "\"");
            }
        }
        String tableName = tableMeta.getSchema() != null
            ? tableMeta.getSchema() + "." + tableMeta.getTable()
            : tableMeta.getTable();

        List<String> whereColumns = primaryKeyColumns.isEmpty() ? allColumns : primaryKeyColumns;
        List<String> whereConditions = new ArrayList<>();
        for (String column : whereColumns) {
            whereConditions.add(column + " = ?");
        }
        return String.format("DELETE FROM %s WHERE %s", tableName, String.join(" AND ", whereConditions));
    }

    private int getDefaultLength(String type) {
        switch (type) {
            case "VARCHAR":
            case "VARCHAR2":
            case "NVARCHAR2":
                return 255;
            case "CHAR":
            case "NCHAR":
                return 50;
            case "VARBINARY":
                return 2000;
            default:
                return 0;
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        List<String> result = new ArrayList<>();
        for (DmType t : DmType.values()) {
            result.add(t.name());
        }
        return result;
    }
}
