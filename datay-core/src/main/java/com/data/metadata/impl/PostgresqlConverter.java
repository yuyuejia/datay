package com.data.metadata.impl;

import com.data.metadata.*;

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
    }

    @Override
    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType().toUpperCase();
        String type = commonDataType2ColumnType.get(upperType);
        if (type == null) {
            if (upperType.startsWith("INTERVAL")) {
                type = PostgresType.INTERVAL.name();
            } else {
                System.out.println("postgresql not found type:" + upperType);
                type = PostgresType.VARCHAR.name();
            }
        }
        if ("NUMBER".equals(upperType) && columnMeta.getScale() <= 0 && columnMeta.getPrecision() <= 20 && columnMeta.getPrecision() > 0) {
            type = PostgresType.BIGINT.name();
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
                if (column.getScale() <= 0) {
                    sb.append(",").append(4).append(")");
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