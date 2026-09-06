package com.data.metadata.impl;

import com.data.metadata.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class DorisConverter implements TypeConverter {

    enum DorisType {
        // 数值类型
        TINYINT,
        SMALLINT,
        INT,
        BIGINT,
        LARGEINT,
        FLOAT,
        DOUBLE,
        DECIMAL,
        
        // 字符串类型
        CHAR,
        VARCHAR,
        STRING,
        TEXT,
        
        // 日期时间类型
        DATE,
        DATETIME,
        
        // 其他类型
        BOOLEAN,
        HLL,
        BITMAP,
        JSON
    }

    public static HashMap<String, String> commonDataType2ColumnType = new HashMap<>();

    static {
        DatabaseConverter.register(DBType.DORIS.name(), new DorisConverter());

        // 数值类型映射
        commonDataType2ColumnType.put(AllDataType.TINYINT.name(), DorisType.TINYINT.name());
        commonDataType2ColumnType.put(AllDataType.SMALLINT.name(), DorisType.SMALLINT.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMINT.name(), DorisType.INT.name());
        commonDataType2ColumnType.put(AllDataType.INT.name(), DorisType.INT.name());
        commonDataType2ColumnType.put(AllDataType.INTEGER.name(), DorisType.INT.name());
        commonDataType2ColumnType.put(AllDataType.BIGINT.name(), DorisType.BIGINT.name());
        commonDataType2ColumnType.put(AllDataType.LARGEINT.name(), DorisType.LARGEINT.name());
        commonDataType2ColumnType.put(AllDataType.LONG.name(), DorisType.BIGINT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT.name(), DorisType.FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT4.name(), DorisType.FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT8.name(), DorisType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.DOUBLE.name(), DorisType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.NUMERIC.name(), DorisType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.DECIMAL.name(), DorisType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.NUMBER.name(), DorisType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.MONEY.name(), DorisType.DECIMAL.name());

        // 字符类型映射
        commonDataType2ColumnType.put(AllDataType.CHAR.name(), DorisType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.BPCHAR.name(), DorisType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.NCHAR.name(), DorisType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR2.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR2.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.TINYTEXT.name(), DorisType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.TEXT.name(), DorisType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMTEXT.name(), DorisType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.LONGTEXT.name(), DorisType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.CLOB.name(), DorisType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.NCLOB.name(), DorisType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.RAW.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.UUID.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.ROWID.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.UROWID.name(), DorisType.VARCHAR.name());

        // 二进制类型映射
        commonDataType2ColumnType.put(AllDataType.BIT.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARBIT.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.BINARY.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.BYTEA.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.BINARY_DOUBLE.name(), DorisType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.BINARY_FLOAT.name(), DorisType.FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.VARBINARY.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.TINYBLOB.name(), DorisType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.BLOB.name(), DorisType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMBLOB.name(), DorisType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.LONGBLOB.name(), DorisType.STRING.name());

        // 时间类型映射
        commonDataType2ColumnType.put(AllDataType.YEAR.name(), DorisType.INT.name());
        commonDataType2ColumnType.put(AllDataType.DATE.name(), DorisType.DATE.name());
        commonDataType2ColumnType.put(AllDataType.DATETIME.name(), DorisType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.TIME.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP.name(), DorisType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_6.getName(), DorisType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.TIME_WITH_TIME_ZONE.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMPTZ.name(), DorisType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_TIME_ZONE.getName(), DorisType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.getName(), DorisType.DATETIME.name());

        // 布尔类型映射
        commonDataType2ColumnType.put(AllDataType.BOOLEAN.name(), DorisType.BOOLEAN.name());
        commonDataType2ColumnType.put(AllDataType.BOOL.name(), DorisType.BOOLEAN.name());

        // 其他类型映射
        commonDataType2ColumnType.put(AllDataType.XML.name(), DorisType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.JSON.name(), DorisType.JSON.name());
        commonDataType2ColumnType.put(AllDataType.SET.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.ENUM.name(), DorisType.VARCHAR.name());

        // 时间间隔类型映射
        commonDataType2ColumnType.put(AllDataType.INTERVAL.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_YEAR.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_YEAR_TO_MONTH.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_MONTH.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY_TO_HOUR.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY_TO_MINUTE.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY_TO_SECOND.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_HOUR.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_HOUR_TO_MINUTE.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_HOUR_TO_SECOND.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_MINUTE.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_MINUTE_TO_SECOND.name(), DorisType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_SECOND.name(), DorisType.VARCHAR.name());
    }

    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType().toUpperCase();
        String type = commonDataType2ColumnType.get(upperType);
        if (type == null) {
            if (upperType.startsWith("INTERVAL")) {
                type = DorisType.VARCHAR.name();
                columnMeta.setLength(100);
            } else if (upperType.startsWith("DECIMAL")) {
                type = DorisType.DECIMAL.name();
            } else if (upperType.startsWith("INT")) {
                type = DorisType.BIGINT.name();
            } else {
                System.out.println("doris not found type:" + upperType);
                type = DorisType.VARCHAR.name();
            }
        }
        if ("NUMBER".equals(upperType) && columnMeta.getScale() <= 0 && columnMeta.getPrecision() <= 38 && columnMeta.getPrecision() > 0) {
            type = DorisType.BIGINT.name();
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

    public String generateTableDDL(TableMeta table) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
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
        ddl.deleteCharAt(ddl.lastIndexOf(",")); // 没有主键时移除最后一个逗号
        ddl.append(")\n");

        // Doris主键模型使用UNIQUE KEY
        if (!primaryKeys.isEmpty()) {
            ddl.append("UNIQUE KEY (").append(String.join(", ", primaryKeys)).append(")\n");
        }
        
        // Doris分布式配置
        ddl.append("DISTRIBUTED BY HASH(");
        if (!primaryKeys.isEmpty()) {
            // 如果有主键，使用第一个主键作为分布键
            ddl.append(primaryKeys.get(0).replace("`", ""));
        } else if (!table.columns().isEmpty()) {
            // 如果没有主键，使用第一个列作为分布键
            ddl.append(table.columns().get(0).getName());
        } else {
            ddl.append("id"); // 默认使用id作为分布键
        }
        ddl.append(") BUCKETS 10\n");
        
        // Doris表属性
        ddl.append("PROPERTIES (\n");
        ddl.append("    \"replication_num\" = \"1\"\n");
//        if (table.getComment() != null && !table.getComment().isEmpty()) {
//            ddl.append("  \"table_comment\" = \"").append(table.getComment().replace("\"", "\\\"")).append("\"\n");
//        }
        ddl.append(")");

        return ddl.toString();
    }

    // 生成INSERT语句
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

    // 生成UPSERT语句 - Doris使用INSERT INTO ... ON DUPLICATE KEY UPDATE
    public String generateUpsertSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();
        List<String> primaryKeyColumns = new ArrayList<>();

        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add("`" + column.getName() + "`");
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add("`" + column.getName() + "`");
            }
        }

        String columns = String.join(", ", columnNames);
        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));

        String tableName = tableMeta.getSchema() != null ?
            tableMeta.getSchema() + "." + tableMeta.getTable() : tableMeta.getTable();

        // 如果没有主键，则回退到普通INSERT
        if (primaryKeyColumns.isEmpty()) {
            return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
        }

        // 生成UPDATE部分（排除主键列）
        List<String> updateParts = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            if (!column.isPrimaryKey()) {
                updateParts.add("`" + column.getName() + "` = VALUES(`" + column.getName() + "`)");
            }
        }

        if (updateParts.isEmpty()) {
            // 如果所有列都是主键，则使用REPLACE
            return generateReplaceSQL(tableMeta);
        }

        return String.format("INSERT INTO %s (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s",
            tableName, columns, placeholders, String.join(", ", updateParts));
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

    // 生成REPLACE语句 - Doris使用REPLACE INTO
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
        
        if (typeUpper.matches("VARCHAR|CHAR")) {
            // 字符串类型必须明确指定长度
            int length = column.getLength() > 0 ? column.getLength() : getDefaultLength(typeUpper);
            sb.append(column.getType()).append("(").append(length).append(")");
        } else if (typeUpper.equals("DECIMAL")) {
            // 处理精度和小数位
            if (column.getPrecision() <= 0) {
                sb.append(column.getType());
            } else {
                sb.append(column.getType()).append("(").append(column.getPrecision());
                if (column.getScale() <= 0) {
                    sb.append(")");
                } else {
                    sb.append(",").append(column.getScale()).append(")");
                }
            }
        } else {
            // 其他类型不添加长度
            sb.append(column.getType());
        }
        
        // 处理非空约束
        if (!column.isNullable()) {
            sb.append(" NOT NULL");
        }
        
        // 处理默认值
        if (column.getDefaultValue() != null && !"NULL".equalsIgnoreCase(column.getDefaultValue())) {
            String defaultValue = column.getDefaultValue().toString();
            if (isSpecialDefaultValue(defaultValue)) {
                sb.append(" DEFAULT ").append(defaultValue);
            } else {
                sb.append(" DEFAULT '").append(defaultValue).append("'");
            }
        }

        return String.format("`%s` %s", column.getName().toLowerCase(), sb.toString());
    }

    /**
     * 检查是否为需要不加引号的特殊默认值
     * 例如：CURRENT_TIMESTAMP, NOW() 等函数调用
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
        return upperValue.matches("(?i)CURRENT_TIMESTAMP|NOW|CURDATE|CURTIME");
    }

    private int getDefaultLength(String type) {
        switch (type.toUpperCase()) {
            case "VARCHAR":
                return 255; // Doris默认varchar长度
            case "CHAR":
                return 1;
            default:
                return 0;
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        List<String> result = new ArrayList<>();
        for (DorisType t : DorisType.values()) {
            result.add(t.name());
        }
        return result;
    }
}