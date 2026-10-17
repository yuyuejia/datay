package com.data.metadata.impl;

import com.data.metadata.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * SAP HANA 类型转换与 DDL 生成器。
 *
 * <p>HANA 的标识符使用双引号（保留大小写），字符类型推荐使用 Unicode 的
 * {@code NVARCHAR}/{@code NCLOB}，更新语句使用 HANA 原生的 {@code UPSERT ... WITH PRIMARY KEY}。
 */
public class HanaConverter implements TypeConverter {

    enum HanaType {
        TINYINT,
        SMALLINT,
        INTEGER,
        BIGINT,
        REAL,
        DOUBLE,
        DECIMAL,
        NVARCHAR,
        NCLOB,
        VARBINARY,
        BLOB,
        DATE,
        TIME,
        TIMESTAMP,
        BOOLEAN,
    }

    public static HashMap<String, String> commonDataType2ColumnType = new HashMap<>();

    static {
        DatabaseConverter.register(DBType.HANA.name(), new HanaConverter());

        // 数值类型
        commonDataType2ColumnType.put(AllDataType.TINYINT.name(), HanaType.TINYINT.name());
        commonDataType2ColumnType.put(AllDataType.SMALLINT.name(), HanaType.SMALLINT.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMINT.name(), HanaType.INTEGER.name());
        commonDataType2ColumnType.put(AllDataType.INT.name(), HanaType.INTEGER.name());
        commonDataType2ColumnType.put(AllDataType.INTEGER.name(), HanaType.INTEGER.name());
        commonDataType2ColumnType.put(AllDataType.BIGINT.name(), HanaType.BIGINT.name());
        commonDataType2ColumnType.put(AllDataType.LARGEINT.name(), HanaType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT.name(), HanaType.REAL.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT4.name(), HanaType.REAL.name());
        commonDataType2ColumnType.put(AllDataType.BINARY_FLOAT.name(), HanaType.REAL.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT8.name(), HanaType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.DOUBLE.name(), HanaType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.BINARY_DOUBLE.name(), HanaType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.DECIMAL.name(), HanaType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.NUMERIC.name(), HanaType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.NUMBER.name(), HanaType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.MONEY.name(), HanaType.DECIMAL.name());

        // 字符类型（统一使用 Unicode）
        commonDataType2ColumnType.put(AllDataType.STRING.name(), HanaType.NVARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.CHAR.name(), HanaType.NVARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.BPCHAR.name(), HanaType.NVARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.NCHAR.name(), HanaType.NVARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR.name(), HanaType.NVARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR2.name(), HanaType.NVARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR.name(), HanaType.NVARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR2.name(), HanaType.NVARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.TEXT.name(), HanaType.NCLOB.name());
        commonDataType2ColumnType.put(AllDataType.TINYTEXT.name(), HanaType.NVARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMTEXT.name(), HanaType.NCLOB.name());
        commonDataType2ColumnType.put(AllDataType.LONGTEXT.name(), HanaType.NCLOB.name());
        commonDataType2ColumnType.put(AllDataType.LONG.name(), HanaType.NCLOB.name());
        commonDataType2ColumnType.put(AllDataType.CLOB.name(), HanaType.NCLOB.name());
        commonDataType2ColumnType.put(AllDataType.NCLOB.name(), HanaType.NCLOB.name());
        commonDataType2ColumnType.put(AllDataType.JSON.name(), HanaType.NCLOB.name());
        commonDataType2ColumnType.put(AllDataType.XML.name(), HanaType.NCLOB.name());
        commonDataType2ColumnType.put(AllDataType.UUID.name(), HanaType.NVARCHAR.name());

        // 二进制类型
        commonDataType2ColumnType.put(AllDataType.BINARY.name(), HanaType.VARBINARY.name());
        commonDataType2ColumnType.put(AllDataType.VARBINARY.name(), HanaType.VARBINARY.name());
        commonDataType2ColumnType.put(AllDataType.BYTEA.name(), HanaType.VARBINARY.name());
        commonDataType2ColumnType.put(AllDataType.RAW.name(), HanaType.VARBINARY.name());
        commonDataType2ColumnType.put(AllDataType.BLOB.name(), HanaType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.TINYBLOB.name(), HanaType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMBLOB.name(), HanaType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.LONGBLOB.name(), HanaType.BLOB.name());

        // 时间类型
        commonDataType2ColumnType.put(AllDataType.DATE.name(), HanaType.DATE.name());
        commonDataType2ColumnType.put(AllDataType.DATETIME.name(), HanaType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIME.name(), HanaType.TIME.name());
        commonDataType2ColumnType.put(AllDataType.TIME_WITH_TIME_ZONE.name(), HanaType.TIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP.name(), HanaType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_6.name(), HanaType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMPTZ.name(), HanaType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_TIME_ZONE.name(), HanaType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.name(), HanaType.TIMESTAMP.name());

        // 布尔与位
        commonDataType2ColumnType.put(AllDataType.BOOLEAN.name(), HanaType.BOOLEAN.name());
        commonDataType2ColumnType.put(AllDataType.BOOL.name(), HanaType.BOOLEAN.name());
        commonDataType2ColumnType.put(AllDataType.BIT.name(), HanaType.BOOLEAN.name());
        commonDataType2ColumnType.put(AllDataType.VARBIT.name(), HanaType.VARBINARY.name());

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
            commonDataType2ColumnType.put(intervalType, HanaType.NVARCHAR.name());
        }
    }

    @Override
    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType().toUpperCase();
        String type = commonDataType2ColumnType.get(upperType);
        if (type == null) {
            if (upperType.startsWith("INTERVAL")) {
                type = HanaType.NVARCHAR.name();
                columnMeta.setLength(100);
            } else if (upperType.startsWith("DECIMAL") || upperType.startsWith("NUMERIC")) {
                type = HanaType.DECIMAL.name();
            } else if (upperType.startsWith("INT")) {
                type = HanaType.BIGINT.name();
            } else {
                System.out.println("hana not found type:" + upperType);
                type = HanaType.NVARCHAR.name();
            }
        }
        ColumnMeta column = new ColumnMeta(columnMeta.getName(), type);
        column.setLength(columnMeta.getLength());
        column.setPrecision(columnMeta.getPrecision());
        column.setScale(columnMeta.getScale());
        column.setNullable(columnMeta.isNullable());
        column.setDefaultValue(columnMeta.getDefaultValue());
        column.setComment(columnMeta.getComment());
        column.setPrimaryKey(columnMeta.isPrimaryKey());
        return column;
    }

    @Override
    public String generateTableDDL(TableMeta table) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE ");
        if (table.getSchema() != null && !table.getSchema().isBlank()) {
            ddl.append(quote(table.getSchema())).append(".");
        }
        ddl.append(quote(table.getTable())).append(" (\n");

        List<String> columns = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        for (ColumnMeta col : table.columns()) {
            columns.add("  " + generateColumnDDL(col));
            if (col.isPrimaryKey()) {
                primaryKeys.add(quote(col.getName()));
            }
        }
        if (!primaryKeys.isEmpty()) {
            columns.add("  CONSTRAINT " + quote("PK_" + table.getTable()) + " PRIMARY KEY (" + String.join(", ", primaryKeys) + ")");
        }
        ddl.append(String.join(",\n", columns));
        ddl.append("\n)");
        return ddl.toString();
    }

    @Override
    public String generateColumnDDL(ColumnMeta column) {
        StringBuilder sb = new StringBuilder();
        String typeUpper = column.getType().toUpperCase();

        if (HanaType.DECIMAL.name().equals(typeUpper) || typeUpper.startsWith("DECIMAL") || typeUpper.startsWith("NUMERIC")) {
            int precision = column.getPrecision() > 0 ? column.getPrecision() : 34;
            int scale = Math.max(column.getScale(), 0);
            if (scale > precision) {
                scale = precision;
            }
            sb.append(HanaType.DECIMAL.name()).append("(").append(precision);
            if (scale > 0) {
                sb.append(",").append(scale);
            }
            sb.append(")");
        } else if (HanaType.NVARCHAR.name().equals(typeUpper)) {
            if (column.getLength() > 5000) {
                sb.append(HanaType.NCLOB.name());
            } else {
                int length = column.getLength() > 0 ? column.getLength() : getDefaultLength(typeUpper);
                sb.append(HanaType.NVARCHAR.name()).append("(").append(length).append(")");
            }
        } else if (HanaType.VARBINARY.name().equals(typeUpper)) {
            if (column.getLength() > 5000) {
                sb.append(HanaType.BLOB.name());
            } else {
                int length = column.getLength() > 0 ? column.getLength() : getDefaultLength(typeUpper);
                sb.append(HanaType.VARBINARY.name()).append("(").append(length).append(")");
            }
        } else {
            sb.append(typeUpper);
        }

        if (!column.isNullable()) {
            sb.append(" NOT NULL");
        }
        return quote(column.getName()) + " " + sb;
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
        List<String> columnNames = new ArrayList<>();
        List<String> primaryKeyColumns = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add(quote(column.getName()));
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add(quote(column.getName()));
            }
        }
        String columns = String.join(", ", columnNames);
        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));
        String upsert = String.format("UPSERT %s (%s) VALUES (%s)", tableName(tableMeta), columns, placeholders);
        if (!primaryKeyColumns.isEmpty()) {
            upsert += " WITH PRIMARY KEY";
        }
        return upsert;
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
            allColumns.add(quote(column.getName()));
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add(quote(column.getName()));
            }
        }
        List<String> whereColumns = primaryKeyColumns.isEmpty() ? allColumns : primaryKeyColumns;
        List<String> whereConditions = new ArrayList<>();
        for (String column : whereColumns) {
            whereConditions.add(column + " = ?");
        }
        return String.format("DELETE FROM %s WHERE %s", tableName(tableMeta), String.join(" AND ", whereConditions));
    }

    private String tableName(TableMeta tableMeta) {
        if (tableMeta.getSchema() != null && !tableMeta.getSchema().isBlank()) {
            return quote(tableMeta.getSchema()) + "." + quote(tableMeta.getTable());
        }
        return quote(tableMeta.getTable());
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private int getDefaultLength(String type) {
        return 255;
    }

    @Override
    public List<String> getSupportedTypes() {
        List<String> result = new ArrayList<>();
        for (HanaType t : HanaType.values()) {
            result.add(t.name());
        }
        return result;
    }
}
