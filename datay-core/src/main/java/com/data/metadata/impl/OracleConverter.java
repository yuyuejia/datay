package com.data.metadata.impl;

import com.data.metadata.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class OracleConverter implements TypeConverter {

    enum OracleType {
        VARCHAR2,
        NVARCHAR2,
        CHAR,
        NCHAR,
        NUMBER,
        INT,
        FLOAT,
        DOUBLE,
        DATE,
        TIMESTAMP,
        TIMESTAMP_WITH_TIME_ZONE,
        TIMESTAMP_WITH_LOCAL_TZ,
        INTERVAL_YEAR_TO_MONTH,
        INTERVAL_DAY_TO_SECOND,
        BLOB,
        CLOB,
        NCLOB,
        BFILE,
        RAW,
        LONG,
        LONG_RAW,
        ROWID,
        UROWID,
        XMLTYPE,
    }

    public static HashMap<String, String> commonDataType2ColumnType = new HashMap<>();

    static {
        DatabaseConverter.register(DBType.ORACLE.name(), new OracleConverter());

        // 数值类型映射
        commonDataType2ColumnType.put(AllDataType.TINYINT.name(), OracleType.INT.name());
        commonDataType2ColumnType.put(AllDataType.SMALLINT.name(), OracleType.INT.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMINT.name(), OracleType.INT.name());
        commonDataType2ColumnType.put(AllDataType.INT.name(), OracleType.INT.name());
        commonDataType2ColumnType.put(AllDataType.INTEGER.name(), OracleType.INT.name());
        commonDataType2ColumnType.put(AllDataType.BIGINT.name(), OracleType.INT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT.name(), OracleType.FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT4.name(), OracleType.FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT8.name(), OracleType.FLOAT.name());
        commonDataType2ColumnType.put(AllDataType.DOUBLE.name(), OracleType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.DECIMAL.name(), OracleType.NUMBER.name());
        commonDataType2ColumnType.put(AllDataType.NUMERIC.name(), OracleType.NUMBER.name());
        commonDataType2ColumnType.put(AllDataType.MONEY.name(), OracleType.NUMBER.name());

        // 字符类型
        commonDataType2ColumnType.put(AllDataType.CHAR.name(), OracleType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.BPCHAR.name(), OracleType.CHAR.name());
        commonDataType2ColumnType.put(AllDataType.NCHAR.name(), OracleType.NCHAR.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR.name(), OracleType.VARCHAR2.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR2.name(), OracleType.VARCHAR2.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR.name(), OracleType.NVARCHAR2.name());
        commonDataType2ColumnType.put(AllDataType.TEXT.name(), OracleType.CLOB.name());
        commonDataType2ColumnType.put(AllDataType.LONGTEXT.name(), OracleType.CLOB.name());
        commonDataType2ColumnType.put(AllDataType.CLOB.name(), OracleType.CLOB.name());
        commonDataType2ColumnType.put(AllDataType.NCLOB.name(), OracleType.NCLOB.name());

        // 二进制类型
        commonDataType2ColumnType.put(AllDataType.BINARY.name(), OracleType.RAW.name());
        commonDataType2ColumnType.put(AllDataType.BYTEA.name(), OracleType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.VARBINARY.name(), OracleType.RAW.name());
        commonDataType2ColumnType.put(AllDataType.BLOB.name(), OracleType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.LONGBLOB.name(), OracleType.BLOB.name());

        commonDataType2ColumnType.put(AllDataType.BIT.name(), OracleType.RAW.name());

        // 时间类型
        commonDataType2ColumnType.put(AllDataType.DATE.name(), OracleType.DATE.name());
        commonDataType2ColumnType.put(AllDataType.DATETIME.name(), OracleType.DATE.name());
        commonDataType2ColumnType.put(AllDataType.TIME.name(), OracleType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP.name(), OracleType.TIMESTAMP.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMPTZ.name(), OracleType.TIMESTAMP_WITH_TIME_ZONE.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_TIME_ZONE.name(), OracleType.TIMESTAMP_WITH_TIME_ZONE.name());

        commonDataType2ColumnType.put(AllDataType.INTERVAL.name(), OracleType.VARCHAR2.name());
        // 其他类型
        commonDataType2ColumnType.put(AllDataType.BOOLEAN.name(), OracleType.NUMBER.name() + "(1)");
        commonDataType2ColumnType.put(AllDataType.XML.name(), OracleType.XMLTYPE.name());
        commonDataType2ColumnType.put(AllDataType.JSON.name(), OracleType.CLOB.name());
    }

    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType().toUpperCase();
        String type = commonDataType2ColumnType.get(upperType);
        if (type == null) {
            System.out.println("oracle not found type:" + upperType);
            type = OracleType.VARCHAR2.name();
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
        StringBuilder ddl = new StringBuilder("CREATE TABLE ")
            .append(table.getSchema())
            .append(".")
            .append(table.getTable().toUpperCase())
            .append(" (\n");

        List<String> primaryKeys = new ArrayList<>();

        for (ColumnMeta col : table.columns()) {
            String columnDDL = generateColumnDDL(col);
            ddl.append("  ").append(columnDDL);

            // Oracle列注释需要单独语句
            ddl.append(",\n");

            if (col.isPrimaryKey()) {
                primaryKeys.add(col.getName());
            }
        }

        // 添加主键约束
        if (!primaryKeys.isEmpty()) {
            ddl
                .append("  CONSTRAINT PK_")
                .append(table.getTable())
                .append(" PRIMARY KEY (")
                .append(String.join(", ", primaryKeys))
                .append(")");
        } else {
            ddl.deleteCharAt(ddl.lastIndexOf(",")); // 移除末尾逗号
        }

        ddl.append("\n)"); // 结束表定义

        // 表空间等存储参数
        ddl.append(" TABLESPACE USERS");

        return ddl.toString();
    }

    // 新增：生成INSERT语句（append模式）
    public String generateInsertSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();
        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add("\"" + column.getName().toUpperCase() + "\"");
        }

        String columns = String.join(", ", columnNames);
        String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));

        String tableName = tableMeta.getSchema() != null ? tableMeta.getSchema() + "." + tableMeta.getTable() : tableMeta.getTable();

        return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
    }

    // 新增：生成UPSERT语句（update模式）- Oracle使用MERGE INTO
    public String generateUpsertSQL(TableMeta tableMeta) {
        List<String> columnNames = new ArrayList<>();
        List<String> primaryKeyColumns = new ArrayList<>();

        for (ColumnMeta column : tableMeta.columns()) {
            columnNames.add("\"" + column.getName().toUpperCase() + "\"");
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add("\"" + column.getName().toUpperCase() + "\"");
            }
        }

        String tableName = tableMeta.getSchema() != null ? tableMeta.getSchema() + "." + tableMeta.getTable() : tableMeta.getTable();

        // 如果没有主键，则回退到普通INSERT
        if (primaryKeyColumns.isEmpty()) {
            return generateInsertSQL(tableMeta);
        }

        // 生成MERGE INTO语句
        StringBuilder mergeSQL = new StringBuilder();
        mergeSQL.append("MERGE INTO ").append(tableName).append(" t USING (SELECT ");

        // 生成SELECT部分
        List<String> selectParts = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            selectParts.add("? AS " + columnNames.get(i));
        }
        mergeSQL.append(String.join(", ", selectParts));

        mergeSQL.append(" FROM dual) s ON (");

        // 生成ON条件
        List<String> onConditions = new ArrayList<>();
        for (String pkColumn : primaryKeyColumns) {
            onConditions.add("t." + pkColumn + " = s." + pkColumn);
        }
        mergeSQL.append(String.join(" AND ", onConditions));

        mergeSQL.append(") WHEN MATCHED THEN UPDATE SET ");

        // 生成UPDATE部分（排除主键）
        List<String> updateParts = new ArrayList<>();
        for (String columnName : columnNames) {
            if (!primaryKeyColumns.contains(columnName)) {
                updateParts.add("t." + columnName + " = s." + columnName);
            }
        }
        mergeSQL.append(String.join(", ", updateParts));

        mergeSQL.append(" WHEN NOT MATCHED THEN INSERT (");
        mergeSQL.append(String.join(", ", columnNames));
        mergeSQL.append(") VALUES (");

        List<String> insertValues = new ArrayList<>();
        for (String columnName : columnNames) {
            insertValues.add("s." + columnName);
        }
        mergeSQL.append(String.join(", ", insertValues));
        mergeSQL.append(")");

        return mergeSQL.toString();
    }

    // 新增：生成REPLACE语句（replace模式）- Oracle使用MERGE INTO替代
    public String generateReplaceSQL(TableMeta tableMeta) {
        // Oracle没有直接的REPLACE语句，使用UPSERT替代
        return generateUpsertSQL(tableMeta);
    }

    // 新增：生成DELETE语句（delete模式）- Oracle使用WHERE条件删除
    public String generateDeleteSQL(TableMeta tableMeta) {
        List<String> primaryKeyColumns = new ArrayList<>();
        List<String> allColumns = new ArrayList<>();

        for (ColumnMeta column : tableMeta.columns()) {
            allColumns.add("\"" + column.getName().toUpperCase() + "\"");
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add("\"" + column.getName().toUpperCase() + "\"");
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

    public String generateColumnDDL(ColumnMeta column) {
        StringBuilder sb = new StringBuilder();
        String typeUpper = column.getType().toUpperCase();

        // 处理带有括号的类型（如NUMBER(10,2)）
        if (typeUpper.startsWith("NUMBER") || typeUpper.startsWith("DECIMAL")) {
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
        } else if (typeUpper.matches("CHAR|NCHAR|VARCHAR2|NVARCHAR2")) {
            int length = column.getLength() > 0 ? column.getLength() : getDefaultLength(typeUpper);
            sb.append(typeUpper).append("(").append(length).append(")");
        } else {
            sb.append(typeUpper);
        }
        // 非空约束
        if (!column.isNullable()) {
            sb.append(" NOT NULL");
        }

        // 默认值处理
//        if (column.getDefaultValue() != null) {
//            sb.append(" DEFAULT '").append(column.getDefaultValue().toString().replace("'", "''")).append("'");
//        }

        return String.format("\"%s\" %s", column.getName().toUpperCase(), sb.toString());
    }

    private int getDefaultLength(String type) {
        switch (type) {
            case "VARCHAR2":
            case "NVARCHAR2":
                return 255; // Oracle最大4000字节
            case "CHAR":
            case "NCHAR":
                return 50;
            default:
                return 0;
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        List<String> result = new ArrayList<>();
        for (OracleType t : OracleType.values()) {
            result.add(t.name());
        }
        return result;
    }
}