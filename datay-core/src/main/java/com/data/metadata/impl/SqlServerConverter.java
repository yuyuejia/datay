package com.data.metadata.impl;

import com.data.metadata.AllDataType;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.TypeConverter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Microsoft SQL Server 类型转换与 DDL 生成器。
 *
 * <p>SQL Server 标识符使用方括号（{@code [schema].[table]}），字符串类型使用
 * {@code NVARCHAR}，时间类型优先使用 {@code DATETIME2}，更新语句使用 {@code MERGE}。
 * 生成 DDL 时不带 IDENTITY / 默认值，避免 ETL 显式写入主键列时冲突。
 */
public class SqlServerConverter implements TypeConverter {

    public static HashMap<String, String> commonDataType2ColumnType = new HashMap<>();

    static {
        DatabaseConverter.register(DBType.SQLSERVER.name(), new SqlServerConverter());

        // 数值类型
        commonDataType2ColumnType.put(AllDataType.TINYINT.name(), "TINYINT");
        commonDataType2ColumnType.put(AllDataType.SMALLINT.name(), "SMALLINT");
        commonDataType2ColumnType.put(AllDataType.MEDIUMINT.name(), "INT");
        commonDataType2ColumnType.put(AllDataType.INT.name(), "INT");
        commonDataType2ColumnType.put(AllDataType.INTEGER.name(), "INT");
        commonDataType2ColumnType.put(AllDataType.BIGINT.name(), "BIGINT");
        commonDataType2ColumnType.put(AllDataType.LARGEINT.name(), "DECIMAL");
        commonDataType2ColumnType.put(AllDataType.FLOAT.name(), "REAL");
        commonDataType2ColumnType.put(AllDataType.FLOAT4.name(), "REAL");
        commonDataType2ColumnType.put(AllDataType.FLOAT8.name(), "FLOAT");
        commonDataType2ColumnType.put(AllDataType.DOUBLE.name(), "FLOAT");
        commonDataType2ColumnType.put(AllDataType.BINARY_FLOAT.name(), "REAL");
        commonDataType2ColumnType.put(AllDataType.BINARY_DOUBLE.name(), "FLOAT");
        commonDataType2ColumnType.put(AllDataType.DECIMAL.name(), "DECIMAL");
        commonDataType2ColumnType.put(AllDataType.NUMERIC.name(), "DECIMAL");
        commonDataType2ColumnType.put(AllDataType.NUMBER.name(), "DECIMAL");
        commonDataType2ColumnType.put(AllDataType.MONEY.name(), "DECIMAL");
        commonDataType2ColumnType.put(AllDataType.YEAR.name(), "INT");

        // 字符类型
        commonDataType2ColumnType.put(AllDataType.STRING.name(), "NVARCHAR");
        commonDataType2ColumnType.put(AllDataType.CHAR.name(), "NVARCHAR");
        commonDataType2ColumnType.put(AllDataType.BPCHAR.name(), "NVARCHAR");
        commonDataType2ColumnType.put(AllDataType.NCHAR.name(), "NCHAR");
        commonDataType2ColumnType.put(AllDataType.VARCHAR.name(), "NVARCHAR");
        commonDataType2ColumnType.put(AllDataType.VARCHAR2.name(), "NVARCHAR");
        commonDataType2ColumnType.put(AllDataType.NVARCHAR.name(), "NVARCHAR");
        commonDataType2ColumnType.put(AllDataType.NVARCHAR2.name(), "NVARCHAR");
        commonDataType2ColumnType.put(AllDataType.UUID.name(), "UNIQUEIDENTIFIER");
        commonDataType2ColumnType.put(AllDataType.XML.name(), "XML");
        commonDataType2ColumnType.put(AllDataType.JSON.name(), "NVARCHAR(MAX)");
        for (AllDataType textType : new AllDataType[] {
            AllDataType.TEXT,
            AllDataType.TINYTEXT,
            AllDataType.MEDIUMTEXT,
            AllDataType.LONGTEXT,
            AllDataType.CLOB,
            AllDataType.NCLOB,
            AllDataType.LONG,
        }) {
            commonDataType2ColumnType.put(textType.name(), "NVARCHAR(MAX)");
        }

        // 二进制类型
        commonDataType2ColumnType.put(AllDataType.BINARY.name(), "VARBINARY");
        commonDataType2ColumnType.put(AllDataType.VARBINARY.name(), "VARBINARY");
        commonDataType2ColumnType.put(AllDataType.RAW.name(), "VARBINARY");
        for (AllDataType blobType : new AllDataType[] {
            AllDataType.BYTEA,
            AllDataType.TINYBLOB,
            AllDataType.BLOB,
            AllDataType.MEDIUMBLOB,
            AllDataType.LONGBLOB,
        }) {
            commonDataType2ColumnType.put(blobType.name(), "VARBINARY(MAX)");
        }

        // 时间类型
        commonDataType2ColumnType.put(AllDataType.DATE.name(), "DATE");
        commonDataType2ColumnType.put(AllDataType.TIME.name(), "TIME");
        commonDataType2ColumnType.put(AllDataType.TIME_WITH_TIME_ZONE.name(), "TIME");
        commonDataType2ColumnType.put(AllDataType.DATETIME.name(), "DATETIME2");
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP.name(), "DATETIME2");
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_6.name(), "DATETIME2");
        commonDataType2ColumnType.put(AllDataType.TIMESTAMPTZ.name(), "DATETIMEOFFSET");
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_TIME_ZONE.name(), "DATETIMEOFFSET");
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.name(), "DATETIMEOFFSET");

        // 布尔与位
        commonDataType2ColumnType.put(AllDataType.BOOLEAN.name(), "BIT");
        commonDataType2ColumnType.put(AllDataType.BOOL.name(), "BIT");
        commonDataType2ColumnType.put(AllDataType.BIT.name(), "BIT");
        commonDataType2ColumnType.put(AllDataType.VARBIT.name(), "BIT");

        // 其他类型统一退化为字符串
        for (String type : new String[] {
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
            AllDataType.ROWID.name(),
            AllDataType.UROWID.name(),
        }) {
            commonDataType2ColumnType.put(type, "NVARCHAR");
        }
    }

    @Override
    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType() == null ? "" : columnMeta.getType().toUpperCase();
        String lookupType = upperType.replaceAll("\\(.*\\)", "").replace("IDENTITY", "").trim();
        String type = commonDataType2ColumnType.get(lookupType);
        if (type == null) {
            type = inferType(lookupType);
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

    private String inferType(String upperType) {
        if (upperType.startsWith("INT") || upperType.startsWith("MEDIUMINT")) {
            return "INT";
        }
        if (upperType.startsWith("DECIMAL") || upperType.startsWith("NUMERIC")) {
            return "DECIMAL";
        }
        if (upperType.startsWith("DATETIME") || upperType.startsWith("SMALLDATETIME")) {
            return "DATETIME2";
        }
        if (upperType.startsWith("TIMESTAMP")) {
            return "DATETIME2";
        }
        if (upperType.startsWith("VARBINARY") || upperType.startsWith("BINARY") || upperType.startsWith("IMAGE")) {
            return "VARBINARY";
        }
        if (upperType.startsWith("NVARCHAR") || upperType.startsWith("VARCHAR") || upperType.startsWith("NCHAR") || upperType.startsWith("CHAR")) {
            return "NVARCHAR";
        }
        if (upperType.startsWith("DECIMAL")) {
            return "DECIMAL";
        }
        if (upperType.startsWith("INTERVAL")) {
            return "NVARCHAR";
        }
        return "NVARCHAR(MAX)";
    }

    @Override
    public String generateColumnDDL(ColumnMeta column) {
        StringBuilder sb = new StringBuilder();
        String typeUpper = column.getType() == null ? "" : column.getType().toUpperCase();
        if (typeUpper.contains("(")) {
            sb.append(typeUpper);
        } else if (typeUpper.matches("DECIMAL|NUMERIC|NUMBER")) {
            sb.append(typeUpper);
            if (column.getPrecision() > 0) {
                sb.append("(").append(column.getPrecision());
                if (column.getScale() > 0) {
                    sb.append(",").append(column.getScale());
                }
                sb.append(")");
            }
        } else if (typeUpper.matches("NVARCHAR|NCHAR|VARBINARY|BINARY|CHAR|VARCHAR")) {
            int length = column.getLength();
            if (length <= 0 || length > maxLengthFor(typeUpper)) {
                if ("NVARCHAR".equals(typeUpper) || "VARBINARY".equals(typeUpper)) {
                    sb.append(typeUpper).append("(MAX)");
                } else {
                    sb.append(typeUpper).append("(").append(Math.min(Math.max(length, 1), maxLengthFor(typeUpper))).append(")");
                }
            } else {
                sb.append(typeUpper).append("(").append(length).append(")");
            }
        } else {
            sb.append(typeUpper);
        }

        if (!column.isNullable()) {
            sb.append(" NOT NULL");
        }
        return "[" + column.getName() + "] " + sb;
    }

    private int maxLengthFor(String type) {
        return "NVARCHAR".equals(type) || "NCHAR".equals(type) ? 4000 : 8000;
    }

    @Override
    public String generateTableDDL(TableMeta table) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(qualifiedName(table)).append(" (\n");
        List<String> primaryKeys = new ArrayList<>();
        for (ColumnMeta column : table.columns()) {
            ddl.append("  ").append(generateColumnDDL(column)).append(",\n");
            if (column.isPrimaryKey()) {
                primaryKeys.add("[" + column.getName() + "]");
            }
        }
        if (!primaryKeys.isEmpty()) {
            ddl
                .append("  CONSTRAINT [PK_")
                .append(table.getTable())
                .append("] PRIMARY KEY (")
                .append(String.join(", ", primaryKeys))
                .append(")");
        } else {
            ddl.deleteCharAt(ddl.lastIndexOf(","));
        }
        return ddl.append("\n)").toString();
    }

    @Override
    public String generateInsertSQL(TableMeta tableMeta) {
        List<String> columns = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            columns.add("[" + column.getName() + "]");
        }
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        return String.format(
            "INSERT INTO %s (%s) VALUES (%s)",
            qualifiedName(tableMeta),
            String.join(", ", columns),
            placeholders
        );
    }

    @Override
    public String generateUpsertSQL(TableMeta tableMeta) {
        List<String> columns = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            columns.add("[" + column.getName() + "]");
            if (column.isPrimaryKey()) {
                primaryKeys.add("[" + column.getName() + "]");
            }
        }
        if (primaryKeys.isEmpty()) {
            return generateInsertSQL(tableMeta);
        }

        List<String> updateParts = new ArrayList<>();
        for (String column : columns) {
            if (!primaryKeys.contains(column)) {
                updateParts.add("t." + column + " = s." + column);
            }
        }
        List<String> insertValues = new ArrayList<>();
        for (String column : columns) {
            insertValues.add("s." + column);
        }
        List<String> onConditions = new ArrayList<>();
        for (String primaryKey : primaryKeys) {
            onConditions.add("t." + primaryKey + " = s." + primaryKey);
        }

        String values = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String sql =
            "MERGE INTO " +
            qualifiedName(tableMeta) +
            " AS t USING (VALUES (" +
            values +
            ")) AS s (" +
            String.join(", ", columns) +
            ") ON " +
            String.join(" AND ", onConditions);
        if (!updateParts.isEmpty()) {
            sql += " WHEN MATCHED THEN UPDATE SET " + String.join(", ", updateParts);
        }
        sql +=
            " WHEN NOT MATCHED THEN INSERT (" +
            String.join(", ", columns) +
            ") VALUES (" +
            String.join(", ", insertValues) +
            ");";
        return sql;
    }

    @Override
    public String generateReplaceSQL(TableMeta tableMeta) {
        return generateUpsertSQL(tableMeta);
    }

    @Override
    public String generateDeleteSQL(TableMeta tableMeta) {
        List<String> conditions = new ArrayList<>();
        List<String> allColumns = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            allColumns.add("[" + column.getName() + "]");
            if (column.isPrimaryKey()) {
                conditions.add("[" + column.getName() + "] = ?");
            }
        }
        if (conditions.isEmpty()) {
            for (String column : allColumns) {
                conditions.add(column + " = ?");
            }
        }
        return String.format("DELETE FROM %s WHERE %s", qualifiedName(tableMeta), String.join(" AND ", conditions));
    }

    @Override
    public List<String> getSupportedTypes() {
        return List.of(
            "TINYINT",
            "SMALLINT",
            "INT",
            "BIGINT",
            "BIT",
            "REAL",
            "FLOAT",
            "DECIMAL",
            "NUMERIC",
            "MONEY",
            "CHAR",
            "NCHAR",
            "VARCHAR",
            "NVARCHAR",
            "NVARCHAR(MAX)",
            "TEXT",
            "NTEXT",
            "DATE",
            "TIME",
            "DATETIME",
            "DATETIME2",
            "DATETIMEOFFSET",
            "BINARY",
            "VARBINARY",
            "VARBINARY(MAX)",
            "UNIQUEIDENTIFIER",
            "XML"
        );
    }

    private String qualifiedName(TableMeta tableMeta) {
        String schema = tableMeta.getSchema();
        if (schema == null || schema.isBlank()) {
            schema = "dbo";
        }
        return "[" + schema + "].[" + tableMeta.getTable() + "]";
    }
}
