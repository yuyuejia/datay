package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Apache Doris 数据源 Provider。
 *
 * <p>Doris 使用 MySQL 协议与驱动，库表元数据行为与 MySQL 一致，因此复用
 * {@link MysqlDataSourceProvider}，仅将类型声明为 {@link DBType#DORIS}。
 */
@DataSourceRegister("doris")
public class DorisDataSourceProvider extends MysqlDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.DORIS);
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
        props.put("characterEncoding", "UTF-8");
        return props;
    }
}
