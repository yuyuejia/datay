package com.data.metadata.impl;

import com.data.metadata.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LogicConverter implements TypeConverter {

    public static final String LOGIC_CONVERTER_KEY = "common";

    public enum LogicType {
        STRING,
        TEXT,
        BINARY,
        BLOB,
        INTEGER,
        LONG,
        DOUBLE,
        DECIMAL,
        DATE,
        DATETIME,
        BOOLEAN;
    }

    public static final HashMap<String, String> LOGIC_TYPE_LABELS = new HashMap<>();

    public static final HashMap<String, String> commonDataType2ColumnType = new HashMap<>();

    public static final HashMap<String, String> logicType2CommonType = new HashMap<>();

    static {
        DatabaseConverter.register(LOGIC_CONVERTER_KEY, new LogicConverter());

        LOGIC_TYPE_LABELS.put("STRING", "字符串");
        LOGIC_TYPE_LABELS.put("TEXT", "大文本");
        LOGIC_TYPE_LABELS.put("BINARY", "二进制");
        LOGIC_TYPE_LABELS.put("BLOB", "大对象(BLOB)");
        LOGIC_TYPE_LABELS.put("INTEGER", "整数");
        LOGIC_TYPE_LABELS.put("LONG", "长整型");
        LOGIC_TYPE_LABELS.put("DOUBLE", "双精度");
        LOGIC_TYPE_LABELS.put("DECIMAL", "高精度数值");
        LOGIC_TYPE_LABELS.put("DATE", "日期");
        LOGIC_TYPE_LABELS.put("DATETIME", "日期时间");
        LOGIC_TYPE_LABELS.put("BOOLEAN", "布尔");

        logicType2CommonType.put("STRING", AllDataType.VARCHAR.name());
        logicType2CommonType.put("TEXT", AllDataType.TEXT.name());
        logicType2CommonType.put("BINARY", AllDataType.BLOB.name());
        logicType2CommonType.put("BLOB", AllDataType.BLOB.name());
        logicType2CommonType.put("INTEGER", AllDataType.INT.name());
        logicType2CommonType.put("LONG", AllDataType.BIGINT.name());
        logicType2CommonType.put("DOUBLE", AllDataType.DOUBLE.name());
        logicType2CommonType.put("DECIMAL", AllDataType.DECIMAL.name());
        logicType2CommonType.put("DATE", AllDataType.DATE.name());
        logicType2CommonType.put("DATETIME", AllDataType.TIMESTAMP.name());
        logicType2CommonType.put("BOOLEAN", AllDataType.BOOLEAN.name());

        commonDataType2ColumnType.put(AllDataType.STRING.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.CHAR.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.BPCHAR.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.NCHAR.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.VARCHAR2.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.NVARCHAR2.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.UUID.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.ROWID.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.UROWID.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.RAW.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.MONEY.name(), LogicType.STRING.name());

        commonDataType2ColumnType.put(AllDataType.TINYTEXT.name(), LogicType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.TEXT.name(), LogicType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMTEXT.name(), LogicType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.LONGTEXT.name(), LogicType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.CLOB.name(), LogicType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.NCLOB.name(), LogicType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.XML.name(), LogicType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.JSON.name(), LogicType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.SET.name(), LogicType.TEXT.name());
        commonDataType2ColumnType.put(AllDataType.ENUM.name(), LogicType.TEXT.name());

        commonDataType2ColumnType.put(AllDataType.BINARY.name(), LogicType.BINARY.name());
        commonDataType2ColumnType.put(AllDataType.VARBINARY.name(), LogicType.BINARY.name());
        commonDataType2ColumnType.put(AllDataType.BIT.name(), LogicType.BINARY.name());
        commonDataType2ColumnType.put(AllDataType.VARBIT.name(), LogicType.BINARY.name());

        commonDataType2ColumnType.put(AllDataType.BYTEA.name(), LogicType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.BLOB.name(), LogicType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.TINYBLOB.name(), LogicType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMBLOB.name(), LogicType.BLOB.name());
        commonDataType2ColumnType.put(AllDataType.LONGBLOB.name(), LogicType.BLOB.name());

        commonDataType2ColumnType.put(AllDataType.TINYINT.name(), LogicType.INTEGER.name());
        commonDataType2ColumnType.put(AllDataType.SMALLINT.name(), LogicType.INTEGER.name());
        commonDataType2ColumnType.put(AllDataType.MEDIUMINT.name(), LogicType.INTEGER.name());
        commonDataType2ColumnType.put(AllDataType.INT.name(), LogicType.INTEGER.name());
        commonDataType2ColumnType.put(AllDataType.INTEGER.name(), LogicType.INTEGER.name());

        commonDataType2ColumnType.put(AllDataType.BIGINT.name(), LogicType.LONG.name());
        commonDataType2ColumnType.put(AllDataType.LARGEINT.name(), LogicType.LONG.name());
        commonDataType2ColumnType.put(AllDataType.LONG.name(), LogicType.LONG.name());

        commonDataType2ColumnType.put(AllDataType.FLOAT.name(), LogicType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT4.name(), LogicType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.FLOAT8.name(), LogicType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.DOUBLE.name(), LogicType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.BINARY_DOUBLE.name(), LogicType.DOUBLE.name());
        commonDataType2ColumnType.put(AllDataType.BINARY_FLOAT.name(), LogicType.DOUBLE.name());

        commonDataType2ColumnType.put(AllDataType.NUMERIC.name(), LogicType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.DECIMAL.name(), LogicType.DECIMAL.name());
        commonDataType2ColumnType.put(AllDataType.NUMBER.name(), LogicType.DECIMAL.name());

        commonDataType2ColumnType.put(AllDataType.YEAR.name(), LogicType.DATE.name());
        commonDataType2ColumnType.put(AllDataType.DATE.name(), LogicType.DATE.name());

        commonDataType2ColumnType.put(AllDataType.TIME.name(), LogicType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.DATETIME.name(), LogicType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP.name(), LogicType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_6.getName(), LogicType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.TIME_WITH_TIME_ZONE.getName(), LogicType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMPTZ.name(), LogicType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_TIME_ZONE.getName(), LogicType.DATETIME.name());
        commonDataType2ColumnType.put(AllDataType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.getName(), LogicType.DATETIME.name());

        commonDataType2ColumnType.put(AllDataType.BOOLEAN.name(), LogicType.BOOLEAN.name());
        commonDataType2ColumnType.put(AllDataType.BOOL.name(), LogicType.BOOLEAN.name());

        commonDataType2ColumnType.put(AllDataType.INTERVAL.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_YEAR.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_YEAR_TO_MONTH.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_MONTH.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY_TO_HOUR.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY_TO_MINUTE.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_DAY_TO_SECOND.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_HOUR.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_HOUR_TO_MINUTE.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_HOUR_TO_SECOND.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_MINUTE.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_MINUTE_TO_SECOND.name(), LogicType.STRING.name());
        commonDataType2ColumnType.put(AllDataType.INTERVAL_SECOND.name(), LogicType.STRING.name());
    }

    @Override
    public ColumnMeta toTargetColumnType(ColumnMeta columnMeta) {
        String upperType = columnMeta.getType().toUpperCase();
        String type = commonDataType2ColumnType.get(upperType);
        if (type == null) {
            if (upperType.startsWith("INTERVAL")) {
                type = LogicType.STRING.name();
            } else {
                System.out.println("logic not found type:" + upperType);
                type = LogicType.STRING.name();
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
    public String generateColumnDDL(ColumnMeta column) {
        return "";
    }

    @Override
    public String generateTableDDL(TableMeta tableMeta) {
        return "";
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

    @Override
    public List<String> getSupportedTypes() {
        List<String> result = new ArrayList<>();
        for (LogicType t : LogicType.values()) {
            result.add(t.name());
        }
        return result;
    }
}