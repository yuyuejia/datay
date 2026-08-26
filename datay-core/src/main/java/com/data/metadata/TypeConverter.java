package com.data.metadata;

public interface TypeConverter {
    // 转换为标准逻辑类型
    //    ColumnMeta toLogicalType(ColumnMeta columnMeta);

    // 转换为目标物理类型
    ColumnMeta toPhysicalType(ColumnMeta columnMeta);

    String generateColumnDDL(ColumnMeta column);

    String generateTableDDL(TableMeta tableMeta);

    // 新增：生成INSERT语句（append模式）
    String generateInsertSQL(TableMeta tableMeta);

    // 新增：生成UPSERT语句（update模式）
    String generateUpsertSQL(TableMeta tableMeta);

    // 新增：生成DELETE语句（delete模式）
    String generateDeleteSQL(TableMeta tableMeta);

    // 新增：生成REPLACE语句（replace模式）
    String generateReplaceSQL(TableMeta tableMeta);
    //    String generateSchemaDDL(TableMeta tableMeta);
    //    TableMeta getTableMetaData(Connection conn, String table) throws SQLException;
}
