package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Apache Doris 数据源 Provider。
 *
 * <p>Doris 使用 MySQL 协议与驱动，库表元数据行为与 MySQL 一致，因此复用
 * {@link MysqlDataSourceProvider}，仅将类型声明为 {@link DBType#DORIS}。
 */
@DataSourceRegister("doris")
public class DorisDataSourceProvider extends MysqlDataSourceProvider {

    /** Doris 建表语句中的 UNIQUE KEY 定义，例如 {@code UNIQUE KEY(`id`, `tenant_id`)}。 */
    private static final Pattern UNIQUE_KEY_PATTERN = Pattern.compile(
        "(?i)\\bUNIQUE\\s+KEY\\s*\\(([^)]*)\\)"
    );

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.DORIS);
    }

    /**
     * Doris 通过 MySQL 协议读取表结构时不会返回主键元数据（{@code DatabaseMetaData.getPrimaryKeys}
     * 为空），但 UNIQUE KEY 模型的主键对生成 DELETE / UPSERT 语句至关重要。这里从
     * {@code SHOW CREATE TABLE} 中解析 UNIQUE KEY 列并回填为主键标记。
     */
    @Override
    public TableMeta getTableMeta(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = super.getTableMeta(conn, schema, table);
        if (tableMeta != null && !hasPrimaryKey(tableMeta)) {
            applyUniqueKeyFromCreateTable(conn, schema, table, tableMeta);
        }
        return tableMeta;
    }

    private static boolean hasPrimaryKey(TableMeta tableMeta) {
        for (ColumnMeta column : tableMeta.columns()) {
            if (column.isPrimaryKey()) {
                return true;
            }
        }
        return false;
    }

    private static void applyUniqueKeyFromCreateTable(Connection conn, String schema, String table, TableMeta tableMeta) {
        String qualified = (schema == null || schema.isBlank() ? "" : quoteIdentifier(schema) + ".") + quoteIdentifier(table);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + qualified)) {
            if (rs.next()) {
                applyUniqueKeyFromDdl(tableMeta, rs.getString(2));
            }
        } catch (SQLException | RuntimeException e) {
            // 读取建表语句失败不影响普通字段元数据，主键只能依赖驱动返回值
        }
    }

    /**
     * 从 Doris 建表语句中解析 UNIQUE KEY 列并标记为表主键。无 UNIQUE KEY 时保持不变。
     */
    static void applyUniqueKeyFromDdl(TableMeta tableMeta, String ddl) {
        if (tableMeta == null || ddl == null || ddl.isEmpty()) {
            return;
        }
        Matcher matcher = UNIQUE_KEY_PATTERN.matcher(ddl);
        if (!matcher.find()) {
            return;
        }
        Set<String> keyNames = new HashSet<>();
        for (String part : matcher.group(1).split(",")) {
            String name = part.trim().replace("`", "").replace("\"", "").trim();
            if (!name.isEmpty()) {
                keyNames.add(name.toLowerCase(Locale.ROOT));
            }
        }
        if (keyNames.isEmpty()) {
            return;
        }
        for (ColumnMeta column : tableMeta.columns()) {
            if (column.getName() != null) {
                column.setPrimaryKey(keyNames.contains(column.getName().toLowerCase(Locale.ROOT)));
            }
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * Doris FE 的服务端预编译语句对多字节字符串参数解码异常：UTF-8 中文经
     * {@code useServerPrepStmts=true} 写入会变成 U+FFFD（EF BF BD）。因此对 Doris
     * 强制使用客户端预编译，并显式声明 UTF-8 连接字符集，保证中文等文本正确入库。
     */
    @Override
    public Map<String, String> dataSourceProperties() {
        Map<String, String> props = new LinkedHashMap<>(super.dataSourceProperties());
        props.put("useServerPrepStmts", "false");
        // Doris FE 的 MySQL 协议未实现 COM_SET_OPTION(27)。MySQL Connector/J 在
        // rewriteBatchedStatements=true 且批量非 INSERT 语句达到 4 条时，会尝试通过
        // COM_SET_OPTION 动态开启多语句，Doris 直接返回 "Unknown command(27)"，
        // 导致批量 DELETE 失败（INSERT 会被改写为多值 INSERT，不受影响）。
        // 此处开启批量改写以提升 INSERT 性能；批量 DELETE 需避免依赖驱动改写。
        props.put("rewriteBatchedStatements", "true");
        props.put("characterEncoding", "UTF-8");
        // Doris 默认关闭 Unicode 标识符支持，FeNameFormat 的列名校验只接受 ASCII，自动建表时
        // 源表的中文列名（如 `订单id`）会被拒绝：
        // "Incorrect column name '订单id'. Column regex is ..."。
        // 在每个连接建立时开启该会话变量，使中文表名/列名可以直接建表与读写。
        props.put("sessionVariables", "enable_unicode_name_support=true");
        return props;
    }

    @Override
    public boolean useTruncateForOverwrite() {
        return true;
    }
}
