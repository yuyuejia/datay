package com.data.metadata.impl;

import com.data.metadata.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MysqlConverter implements TypeConverter {

    enum MysqlType {
        CHAR,
        VARCHAR,
        TINYTEXT,
        TEXT,
        MEDIUMTEXT,
        LONGTEXT,
        BIT,
        TINYINT,
        SMALLINT,
        MEDIUMINT,
        INT,
        BIGINT,
        FLOAT,
        DOUBLE,
        DECIMAL,
        BINARY,
        VARBINARY,
        TINYBLOB,
        BLOB,
        MEDIUMBLOB,
        LONGBLOB,
        DATE,
        TIME,
        DATETIME,
        BOOLEAN,
        JSON,
    }

    public static HashMap<String, String> commonDataType2ColumnType = new HashMap<>();

    //    public static HashMap<String, String> columnType2CommonDataType = new HashMap<>();

    static {
        DatabaseConverter.register(DBType.MYSQL.name(), new MysqlConverter());

        commonDataType2ColumnType.put(CommonDataType.CHAR.name(), MysqlType.CHAR.name());
        commonDataType2ColumnType.put(CommonDataType.BPCHAR.name(), MysqlType.CHAR.name());
        commonDataType2ColumnType.put(CommonDataType.NCHAR.name(), MysqlType.CHAR.name());
        commonDataType2ColumnType.put(CommonDataType.VARCHAR.name(), MysqlType.VARCHAR.name());
        commonDataType2ColumnType.put(CommonDataType.VARCHAR2.name(), MysqlType.VARCHAR.name());
        commonDataType2ColumnType.put(CommonDataType.NVARCHAR.name(), MysqlType.VARCHAR.name());
        commonDataType2ColumnType.put(CommonDataType.NVARCHAR2.name(), MysqlType.VARCHAR.name());
        commonDataType2ColumnType.put(CommonDataType.TINYTEXT.name(), MysqlType.TINYTEXT.name());
        commonDataType2ColumnType.put(CommonDataType.TEXT.name(), MysqlType.TEXT.name());
        commonDataType2ColumnType.put(CommonDataType.MEDIUMTEXT.name(), MysqlType.MEDIUMTEXT.name());
        commonDataType2ColumnType.put(CommonDataType.LONGTEXT.name(), MysqlType.LONGTEXT.name());
        commonDataType2ColumnType.put(CommonDataType.CLOB.name(), MysqlType.LONGTEXT.name());
        commonDataType2ColumnType.put(CommonDataType.NCLOB.name(), MysqlType.LONGTEXT.name());
        commonDataType2ColumnType.put(CommonDataType.RAW.name(), MysqlType.VARCHAR.name());
        commonDataType2ColumnType.put(CommonDataType.BIT.name(), MysqlType.BIT.name());
        commonDataType2ColumnType.put(CommonDataType.VARBIT.name(), MysqlType.BIT.name()); // MySQL BIT 多位
        commonDataType2ColumnType.put(CommonDataType.TINYINT.name(), MysqlType.TINYINT.name());
        commonDataType2ColumnType.put(CommonDataType.SMALLINT.name(), MysqlType.SMALLINT.name());
        commonDataType2ColumnType.put(CommonDataType.MEDIUMINT.name(), MysqlType.MEDIUMINT.name());
        commonDataType2ColumnType.put(CommonDataType.INT.name(), MysqlType.INT.name());
        commonDataType2ColumnType.put(CommonDataType.INTEGER.name(), MysqlType.INT.name());
        commonDataType2ColumnType.put(CommonDataType.BIGINT.name(), MysqlType.BIGINT.name());
        commonDataType2ColumnType.put(CommonDataType.FLOAT.name(), MysqlType.FLOAT.name());
        commonDataType2ColumnType.put(CommonDataType.FLOAT4.name(), MysqlType.FLOAT.name());
        commonDataType2ColumnType.put(CommonDataType.FLOAT8.name(), MysqlType.DOUBLE.name());
        commonDataType2ColumnType.put(CommonDataType.LONG.name(), MysqlType.LONGTEXT.name());
        commonDataType2ColumnType.put(CommonDataType.DOUBLE.name(), MysqlType.DOUBLE.name());
        commonDataType2ColumnType.put(CommonDataType.NUMERIC.name(), MysqlType.DECIMAL.name());
        commonDataType2ColumnType.put(CommonDataType.NUMBER.name(), MysqlType.DECIMAL.name());
        commonDataType2ColumnType.put(CommonDataType.MONEY.name(), MysqlType.VARCHAR.name());
        commonDataType2ColumnType.put(CommonDataType.BINARY.name(), MysqlType.BINARY.name());
        commonDataType2ColumnType.put(CommonDataType.BYTEA.name(), MysqlType.BLOB.name());
        commonDataType2ColumnType.put(CommonDataType.BINARY_DOUBLE.name(), MysqlType.DOUBLE.name());
        commonDataType2ColumnType.put(CommonDataType.BINARY_FLOAT.name(), MysqlType.FLOAT.name());
        commonDataType2ColumnType.put(CommonDataType.VARBINARY.name(), MysqlType.VARBINARY.name());
        commonDataType2ColumnType.put(CommonDataType.TINYBLOB.name(), MysqlType.BLOB.name());
        commonDataType2ColumnType.put(CommonDataType.BLOB.name(), MysqlType.BLOB.name());
        commonDataType2ColumnType.put(CommonDataType.MEDIUMBLOB.name(), MysqlType.MEDIUMBLOB.name());
        commonDataType2ColumnType.put(CommonDataType.LONGBLOB.name(), MysqlType.LONGBLOB.name());
        commonDataType2ColumnType.put(CommonDataType.DATE.name(), MysqlType.DATE.name());
        commonDataType2ColumnType.put(CommonDataType.TIME.name(), MysqlType.TIME.name());
        commonDataType2ColumnType.put(CommonDataType.TIMESTAMP.name(), MysqlType.DATETIME.name());
        commonDataType2ColumnType.put(CommonDataType.TIMESTAMP_6.getName(), MysqlType.DATETIME.name());
        commonDataType2ColumnType.put(CommonDataType.TIME_WITH_TIME_ZONE.name(), MysqlType.TIME.name());
        commonDataType2ColumnType.put(CommonDataType.TIMESTAMPTZ.name(), MysqlType.DATETIME.name());
        commonDataType2ColumnType.put(CommonDataType.TIMESTAMP_WITH_TIME_ZONE.getName(), MysqlType.DATETIME.name());
        commonDataType2ColumnType.put(CommonDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.getName(), MysqlType.DATETIME.name());
        commonDataType2ColumnType.put(CommonDataType.BOOLEAN.name(), MysqlType.BOOLEAN.name());
        commonDataType2ColumnType.put(CommonDataType.BOOL.name(), MysqlType.BOOLEAN.name());
        commonDataType2ColumnType.put(CommonDataType.XML.name(), MysqlType.LONGTEXT.name());
        commonDataType2ColumnType.put(CommonDataType.JSON.name(), MysqlType.JSON.name());

        commonDataType2ColumnType.put(CommonDataType.INTERVAL.name(), MysqlType.VARCHAR.name());
    }

    public ColumnMeta toPhysicalType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType().toUpperCase();
        String type = commonDataType2ColumnType.get(upperType);
        if (type == null) {
            if (upperType.startsWith("INTERVAL")) {
                type = MysqlType.VARCHAR.name();
                columnMeta.setLength(100);
            } else if (upperType.startsWith("DECIMAL")) {
                type = MysqlType.DECIMAL.name();
            } else if (upperType.startsWith("INT")) {
                type = MysqlType.BIGINT.name();
            } else {
                System.out.println("mysql not found type:" + upperType);
                type = MysqlType.VARCHAR.name();
            }
        }
        if ("NUMBER".equals(upperType) && columnMeta.getScale() <= 0 && columnMeta.getPrecision() <= 38 && columnMeta.getPrecision() > 0) {
            type = MysqlType.BIGINT.name();
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

    //    public String generateColumnDDL(ColumnMeta column) {
    //        return String.format("`%s` %s%s",
    //                column.getName(),
    //                column.getType(),
    //                column.isNullable() ? "" : " NOT NULL");
    //    }

    public String generateTableDDL(TableMeta table) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE ");
        if (table.getSchema() != null) {
            ddl.append(table.getSchema()).append(".");
        }
        ddl.append(table.getTable().toLowerCase()).append(" (\n");

        List<String> primaryKeys = new ArrayList<>();

        for (ColumnMeta col : table.columns()) {
            String columnDDL = generateColumnDDL(col);
            ddl.append("  ").append(columnDDL);

            // 添加列注释
            if (col.getComment() != null && !col.getComment().isEmpty()) {
                ddl.append(" COMMENT '").append(col.getComment().replace("'", "''")).append("'");
            }

            ddl.append(",\n");

            // 收集主键
            if (col.isPrimaryKey()) {
                primaryKeys.add("`" + col.getName() + "`");
            }
        }

        // 添加主键约束
        if (!primaryKeys.isEmpty()) {
            ddl.append("  PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
        } else {
            ddl.deleteCharAt(ddl.lastIndexOf(",")); // 没有主键时移除最后一个逗号
        }

        ddl.append(")");
        // 添加表注释和存储引擎
//        ddl.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        if (table.getComment() != null && !table.getComment().isEmpty()) {
            ddl.append(" COMMENT='").append(table.getComment().replace("'", "''")).append("'");
        }

        return ddl.toString();
    }

    // 新增：生成INSERT语句（append模式）
    public String generateInsertSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add("`" + column.getName() + "`");
        }

        String columns = String.join(", ", columnNames);
        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));

        String tableName = tableMeta.getSchema() != null ? tableMeta.getSchema() + "." + tableMeta.getTable() : tableMeta.getTable();

        return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
    }

    // 新增：生成UPSERT语句（update模式）- MySQL使用ON DUPLICATE KEY UPDATE
    public String generateUpsertSQL(TableMeta tableMeta) {
        return generateReplaceSQL(tableMeta);
        //        List<String> columnNames = new ArrayList<>();
        //        List<String> primaryKeyColumns = new ArrayList<>();
        //
        //        for (ColumnMeta column : tableMeta.columns()) {
        //            columnNames.add("`" + column.getName() + "`");
        //            if (column.isPrimaryKey()) {
        //                primaryKeyColumns.add("`" + column.getName() + "`");
        //            }
        //        }
        //
        //        String columns = String.join(", ", columnNames);
        //        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));
        //
        //        String tableName = tableMeta.getSchema() != null ?
        //            tableMeta.getSchema() + "." + tableMeta.getTable() : tableMeta.getTable();
        //
        //        // 如果没有主键，则回退到普通INSERT
        //        if (primaryKeyColumns.isEmpty()) {
        //            return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
        //        }
        //
        //        // 生成UPDATE部分
        //        List<String> updateParts = new ArrayList<>();
        //        for (String columnName : columnNames) {
        //            updateParts.add(columnName + " = VALUES(" + columnName + ")");
        //        }
        //
        //        return String.format("INSERT INTO %s (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s",
        //            tableName, columns, placeholders, String.join(", ", updateParts));
    }

    @Override
    public String generateDeleteSQL(TableMeta tableMeta) {
        // 获取表名（包含schema）
        String tableName = tableMeta.getSchema() != null ? tableMeta.getSchema() + "." + tableMeta.getTable() : tableMeta.getTable();

        // 获取主键列
        List<String> primaryKeyColumns = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add("`" + column.getName() + "`");
            }
        }

        // 生成WHERE条件
        String whereClause;
        if (!primaryKeyColumns.isEmpty()) {
            // 有主键时，使用主键作为WHERE条件
            List<String> whereParts = new ArrayList<>();
            for (String columnName : primaryKeyColumns) {
                whereParts.add(columnName + " = ?");
            }
            whereClause = "WHERE " + String.join(" AND ", whereParts);
        } else {
            // 没有主键时，使用所有列作为WHERE条件
            List<String> whereParts = new ArrayList<>();
            for (ColumnMeta column : tableMeta.columns()) {
                whereParts.add("`" + column.getName() + "` = ?");
            }
            whereClause = "WHERE " + String.join(" AND ", whereParts);
        }

        return String.format("DELETE FROM %s %s", tableName, whereClause);
    }

    // 新增：生成REPLACE语句（replace模式）- MySQL使用REPLACE INTO
    public String generateReplaceSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add("`" + column.getName() + "`");
        }

        String columns = String.join(", ", columnNames);
        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));

        String tableName = tableMeta.getSchema() != null ? tableMeta.getSchema() + "." + tableMeta.getTable() : tableMeta.getTable();

        return String.format("REPLACE INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
    }

    public String generateColumnDDL(ColumnMeta column) {
        StringBuilder sb = new StringBuilder();
        // 根据不同类型处理长度定义
        String typeUpper = column.getType().toUpperCase();
        if (typeUpper.matches("VARCHAR|CHAR|VARBINARY|BINARY|BIT")) {
            // 这些类型必须明确指定长度
            int length = column.getLength() > 0 && column.getLength() <= 16383 ? column.getLength() : getDefaultLength(typeUpper);
            sb.append(column.getType()).append("(").append(length).append(")");
        } else if (typeUpper.matches("DECIMAL|NUMERIC|FLOAT|DOUBLE")) {
            // 处理精度和小数位
            if (column.getPrecision() <= 0) {
                sb.append(column.getType());
            } else {
                sb.append(column.getType()).append("(").append(column.getPrecision());
                if (column.getScale() < 0) {
                    sb.append(")");
                } else {
                    sb.append(",").append(column.getScale()).append(")");
                }
            }
        } else {
            // 其他类型不添加长度（如TEXT/BLOB等）
            sb.append(column.getType());
        }
        // 添加自增属性（仅限主键且是整数类型）
        //        if (column.isPrimaryKey() && isAutoIncrementType(typeUpper)) {
        //            sb.append(" AUTO_INCREMENT");
        //        }
        // 处理非空约束
//        if (!column.isNullable()) {
//            sb.append(" NOT NULL");
//        }
        // 处理默认值（增加引号转义）
//        if (
//            column.getDefaultValue() != null &&
//            !"NULL".equalsIgnoreCase(column.getDefaultValue()) &&
//            !"''".equalsIgnoreCase(column.getDefaultValue())
//        ) {
//            String defaultValue = column.getDefaultValue().toString();
//
//            // 检查是否为需要不加引号的特殊默认值
//            if (isSpecialDefaultValue(defaultValue)) {
//                sb.append(" DEFAULT ").append(defaultValue);
//            } else {
//                sb.append(" DEFAULT '").append(defaultValue).append("'");
//            }
//        }
        //        else{
        //            if (column.isNullable()) {
        //                sb.append(" DEFAULT NULL");
        //            }
        //        }

        return String.format("`%s` %s", column.getName().toLowerCase(), sb.toString());
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

        // 检查是否为特殊关键字
        return upperValue.matches("(?i)CURRENT_TIMESTAMP|NOW|UUID|RAND|CURDATE|CURTIME|UNIX_TIMESTAMP");
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

    private boolean isNumericType(String type) {
        return type.matches("(?i)TINYINT|SMALLINT|MEDIUMINT|INT|BIGINT|DECIMAL|NUMERIC|FLOAT|DOUBLE");
    }
}
