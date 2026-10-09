package com.data.metadata.impl;

import com.data.metadata.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class DuckLakeConverter implements TypeConverter {

    enum DuckLakeType {
        CHAR,
        VARCHAR,
        TEXT,
        UUID,
        BIT,
        INT,
        UINTEGER,
        USMALLINT,
        UTINYINT,
        TINYINT,
        BIGINT,
        FLOAT,
        DOUBLE,
        DECIMAL,
        BLOB,
        DATE,
        TIME,
        TIMESTAMP,
        JSON,
        BOOLEAN,
    }

    public static HashMap<String, String> commonDataType2ColumnType = new HashMap<>();

    static {
        DatabaseConverter.register(DBType.DUCKLAKE.name(), new DuckLakeConverter());

        commonDataType2ColumnType.put(AllDataType.CHAR.name(), DuckLakeType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.BPCHAR.name(), DuckLakeType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.NCHAR.name(), DuckLakeType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR.name(), DuckLakeType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR2.name(), DuckLakeType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR.name(), DuckLakeType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR2.name(), DuckLakeType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.TINYTEXT.name(), DuckLakeType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.TEXT.name(), DuckLakeType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMTEXT.name(), DuckLakeType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.LONGTEXT.name(), DuckLakeType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.CLOB.name(), DuckLakeType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.NCLOB.name(), DuckLakeType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.RAW.name(), DuckLakeType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.BIT.name(), DuckLakeType.BIT.name());
        commonDataType2ColumnType.put(AllDataType.VARBIT.name(), DuckLakeType.BIT.name()); // MySQL BIT 多位
        commonDataType2ColumnType.put(AllDataType.TINYINT.name(), DuckLakeType.TINYINT.name());
        commonDataType2ColumnType.put(AllDataType.SMALLINT.name(), DuckLakeType.INT.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMINT.name(), DuckLakeType.INT.name());
        commonDataType2ColumnType.put(AllDataType.INT.name(), DuckLakeType.INT.name());
        commonDataType2ColumnType.put(AllDataType.INTEGER.name(), DuckLakeType.INT.name());
        commonDataType2ColumnType.put(AllDataType.BIGINT.name(), DuckLakeType.BIGINT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT.name(), DuckLakeType.FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT4.name(), DuckLakeType.FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT8.name(), DuckLakeType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.DOUBLE.name(), DuckLakeType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.NUMERIC.name(), DuckLakeType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.DECIMAL.name(), DuckLakeType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.NUMBER.name(), DuckLakeType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.MONEY.name(), DuckLakeType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.BINARY.name(), DuckLakeType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.BYTEA.name(), DuckLakeType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.BINARY_DOUBLE.name(), DuckLakeType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.BINARY_FLOAT.name(), DuckLakeType.FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.VARBINARY.name(), DuckLakeType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.TINYBLOB.name(), DuckLakeType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.BLOB.name(), DuckLakeType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMBLOB.name(), DuckLakeType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.LONGBLOB.name(), DuckLakeType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.LONG.name(), DuckLakeType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.DATE.name(), DuckLakeType.DATE.name());
        commonDataType2ColumnType.put(AllDataType.DATETIME.name(), DuckLakeType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIME.name(), DuckLakeType.TIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP.name(), DuckLakeType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMPTZ.name(), DuckLakeType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIME_WITH_TIME_ZONE.name(), DuckLakeType.TIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_TIME_ZONE.name(), DuckLakeType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.name(), DuckLakeType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_6.getName(), DuckLakeType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_TIME_ZONE.getName(), DuckLakeType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.getName(), DuckLakeType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.BOOLEAN.name(), DuckLakeType.BOOLEAN.name());
        commonDataType2ColumnType.put(AllDataType.BOOL.name(), DuckLakeType.BOOLEAN.name());
        commonDataType2ColumnType.put(AllDataType.XML.name(), DuckLakeType.VARCHAR.name());
        commonDataType2ColumnType.put(AllDataType.JSON.name(), DuckLakeType.JSON.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL.name(), DuckLakeType.VARCHAR.name());
    }

    public ColumnMeta toLogicalType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType().toUpperCase();
        columnMeta.setType(commonDataType2ColumnType.get(upperType));
        return columnMeta;
    }

    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType().toUpperCase();
        // DuckDB/DuckLake 的类型名可能携带精度/长度（如 DECIMAL(18,2)），查表前去掉括号部分
        String baseType = DatabaseConverter.stripTypeParameters(upperType);
        String type = commonDataType2ColumnType.get(baseType);
        if (type == null) {
            if (baseType.startsWith("INTERVAL")) {
                type = DuckLakeType.VARCHAR.name();
                columnMeta.setLength(100);
            } else if (baseType.startsWith("INT")) {
                // PostgreSQL int2/int4/int8 等整型名称统一回退为 BIGINT
                type = DuckLakeType.BIGINT.name();
            } else {
                System.out.println("ducklake not found type:" + upperType);
                type = DuckLakeType.VARCHAR.name();
            }
        }
        if ("NUMBER".equals(baseType) && columnMeta.getScale() <= 0 && columnMeta.getPrecision() <= 20 && columnMeta.getPrecision() > 0) {
            type = DuckLakeType.BIGINT.name();
        }
        // 精度/小数位兜底：部分 JDBC 驱动只在类型名里给出 DECIMAL(18,2)，ColumnMeta 的 precision 为空
        int precision = columnMeta.getPrecision();
        int scale = columnMeta.getScale();
        if (("DECIMAL".equals(baseType) || "NUMERIC".equals(baseType))) {
            int[] precisionScale = DatabaseConverter.parseTypePrecisionScale(upperType);
            if (precisionScale != null) {
                if (precision <= 0) {
                    precision = precisionScale[0];
                }
                if (scale <= 0) {
                    scale = precisionScale[1];
                }
            } else if (precision <= 0 && columnMeta.getLength() > 0) {
                precision = columnMeta.getLength();
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
        return column;
    }

    public String generateTableDDL(TableMeta table) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE ");
        if (table.getCatalog() != null) {
            ddl.append(table.getCatalog()).append(".");
        }
        if (table.getSchema() != null) {
            ddl.append(table.getSchema()).append(".");
        }
        ddl.append(table.getTable().toLowerCase()).append(" (\n");

        List<String> primaryKeys = new ArrayList<>();

        for (ColumnMeta col : table.columns()) {
            String columnDDL = generateColumnDDL(col);
            ddl.append("  ").append(columnDDL);

            ddl.append(",\n");
            // 收集主键
            //            if (col.isPrimaryKey()) {
            //                primaryKeys.add(col.getName());
            //            }
        }

        // 添加主键约束
        if (!primaryKeys.isEmpty()) {
            ddl.append("  PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
        } else {
            ddl.deleteCharAt(ddl.lastIndexOf(",")); // 没有主键时移除最后一个逗号
        }

        ddl.append("\n)");
        // 添加表注释和存储引擎
        if (table.getComment() != null && !table.getComment().isEmpty()) {
            ddl.append(" COMMENT='").append(table.getComment().replace("'", "''")).append("'");
        }

        return ddl.toString();
    }

    private String qualifiedTableName(TableMeta tableMeta) {
        StringBuilder sb = new StringBuilder();
        if (tableMeta.getCatalog() != null && !tableMeta.getCatalog().isEmpty()) {
            sb.append(tableMeta.getCatalog()).append(".");
        }
        if (tableMeta.getSchema() != null && !tableMeta.getSchema().isEmpty()) {
            sb.append(tableMeta.getSchema()).append(".");
        }
        sb.append(tableMeta.getTable());
        return sb.toString();
    }

    // 新增：生成INSERT语句（append模式）
    public String generateInsertSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();

        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add(column.getName());
        }

        String columns = String.join(", ", columnNames);
        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));

        String tableName = qualifiedTableName(tableMeta);

        // DuckDB使用INSERT OR REPLACE语法
        return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
    }

    // 新增：生成UPSERT语句（update模式）- DuckDB使用INSERT OR REPLACE
    public String generateUpsertSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();
        List<String> primaryKeyColumns = new ArrayList<>();

        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add(column.getName());
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add(column.getName());
            }
        }

        String columns = String.join(", ", columnNames);
        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));

        String tableName = qualifiedTableName(tableMeta);

        // 如果没有主键，则回退到普通INSERT
        if (primaryKeyColumns.isEmpty()) {
            return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
        }

        // DuckDB使用INSERT OR REPLACE语法
        return String.format("INSERT OR REPLACE INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
    }

    // 新增：生成REPLACE语句（replace模式）- DuckDB使用INSERT OR REPLACE
    public String generateReplaceSQL(TableMeta tableMeta) {
        // DuckDB的REPLACE模式与UPSERT模式相同
        return generateUpsertSQL(tableMeta);
    }

    // 新增：生成DELETE语句（delete模式）- DuckDB使用WHERE条件删除
    public String generateDeleteSQL(TableMeta tableMeta) {
        List<String> primaryKeyColumns = new ArrayList<>();
        List<String> allColumns = new ArrayList<>();

        for (ColumnMeta column : tableMeta.columns()) {
            allColumns.add(column.getName());
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add(column.getName());
            }
        }

        String tableName = qualifiedTableName(tableMeta);

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

    public String generateColumnDDL(ColumnMeta column) {
        StringBuilder sb = new StringBuilder();
        // 根据不同类型处理长度定义
        String typeUpper = column.getType().toUpperCase();
        //        if (typeUpper.matches("VARCHAR|CHAR|VARBINARY|BINARY|BIT")) {
        //            // 这些类型必须明确指定长度
        //            int length = column.getLength() > 0 ? column.getLength() : getDefaultLength(typeUpper);
        //            sb.append(column.getType()).append("(").append(length).append(")");
        //        } else
        if (typeUpper.matches("DECIMAL|NUMERIC")) {
            if (column.getPrecision() > 38) {
                column.setPrecision(18);
                column.setScale(0);
            }
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
        } else if (typeUpper.matches("BIT") && column.getLength() == 1) {
            // BIT类型必须明确指定长度
            sb.append(DuckLakeType.TINYINT.name());
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
        //            sb.append(" DEFAULT '").append(column.getDefaultValue().toString()).append("'");
        //        }

        return String.format("%s %s", column.getName(), sb.toString());
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

    @Override
    public List<String> getSupportedTypes() {
        List<String> result = new ArrayList<>();
        for (DuckLakeType t : DuckLakeType.values()) {
            result.add(t.name());
        }
        return result;
    }
}