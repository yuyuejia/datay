package com.data.metadata.impl;

import com.data.metadata.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.data.metadata.impl.AVROConverter.AVROType.*;

public class AVROConverter implements TypeConverter {

    enum AVROType {
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        BOOLEAN,
        STRING,
        BYTES,
    }

    public static HashMap<String, String> commonDataType2ColumnType = new HashMap<>();

    //    public static HashMap<String, String> columnType2CommonDataType = new HashMap<>();

    static {
        DatabaseConverter.register(DBType.AVRO.name(), new AVROConverter());

        commonDataType2ColumnType.put(AllDataType.CHAR.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.BPCHAR.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.NCHAR.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR2.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR2.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.TINYTEXT.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.TEXT.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMTEXT.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.LONGTEXT.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.CLOB.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.NCLOB.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.RAW.name(), BYTES.name());
        commonDataType2ColumnType.put(AllDataType.BIT.name(), BOOLEAN.name());
        commonDataType2ColumnType.put(AllDataType.VARBIT.name(), BOOLEAN.name()); // MySQL BIT 多位
        commonDataType2ColumnType.put(AllDataType.TINYINT.name(), INT.name());
        commonDataType2ColumnType.put(AllDataType.SMALLINT.name(), INT.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMINT.name(), INT.name());
        commonDataType2ColumnType.put(AllDataType.INT.name(), INT.name());
        commonDataType2ColumnType.put(AllDataType.INTEGER.name(), INT.name());
        commonDataType2ColumnType.put(AllDataType.BIGINT.name(), LONG.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT.name(), FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT4.name(), FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT8.name(), DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.DOUBLE.name(), DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.NUMERIC.name(), DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.DECIMAL.name(), DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.NUMBER.name(), DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.MONEY.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.BINARY.name(), BYTES.name());
        commonDataType2ColumnType.put(AllDataType.BYTEA.name(), BYTES.name());
        commonDataType2ColumnType.put(AllDataType.BINARY_DOUBLE.name(), DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.BINARY_FLOAT.name(), FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.VARBINARY.name(), BYTES.name());
        commonDataType2ColumnType.put(AllDataType.TINYBLOB.name(), BYTES.name());
        commonDataType2ColumnType.put(AllDataType.BLOB.name(), BYTES.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMBLOB.name(), BYTES.name());
        commonDataType2ColumnType.put(AllDataType.LONGBLOB.name(), BYTES.name());
        commonDataType2ColumnType.put(AllDataType.LONG.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.DATE.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.DATETIME.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.TIME.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMPTZ.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.TIME_WITH_TIME_ZONE.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_TIME_ZONE.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_6.getName(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_TIME_ZONE.getName(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.getName(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.BOOLEAN.name(), BOOLEAN.name());
        commonDataType2ColumnType.put(AllDataType.XML.name(), STRING.name());
        commonDataType2ColumnType.put(AllDataType.JSON.name(), STRING.name());

        commonDataType2ColumnType.put(AllDataType.INTERVAL.name(), STRING.name());
    }

    public ColumnMeta toLogicalType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType().toUpperCase();
        columnMeta.setType(commonDataType2ColumnType.get(upperType));
        return columnMeta;
    }

    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType().toUpperCase();
        String type = commonDataType2ColumnType.get(upperType);
        if (type == null) {
            if (upperType.startsWith("INTERVAL")) {
                type = STRING.name();
                columnMeta.setLength(100);
            } else {
                type = STRING.name();
            }
        }
        if ("NUMBER".equals(upperType) && columnMeta.getScale() <= 0 && columnMeta.getPrecision() <= 20 && columnMeta.getPrecision() > 0) {
            type = INT.name();
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
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(table.getTable()).append(" (\n");

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
                primaryKeys.add(col.getName());
            }
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

    @Override
    public String generateInsertSQL(TableMeta tableMeta) {
        return "";
    }

    @Override
    public String generateUpsertSQL(TableMeta tableMeta) {
        return "";
    }

    @Override
    public String generateDeleteSQL(TableMeta tableMeta) {
        return "";
    }

    @Override
    public String generateReplaceSQL(TableMeta tableMeta) {
        return "";
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
        if (
            column.getDefaultValue() != null &&
            !"NULL".equalsIgnoreCase(column.getDefaultValue()) &&
            !"''".equalsIgnoreCase(column.getDefaultValue())
        ) {
            sb.append(" DEFAULT '").append(column.getDefaultValue().toString()).append("'");
        }
        //        else{
        //            if (column.isNullable()) {
        //                sb.append(" DEFAULT NULL");
        //            }
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
        for (AVROType t : AVROType.values()) {
            result.add(t.name());
        }
        return result;
    }
}