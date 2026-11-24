package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DorisStreamLoad 真实 Doris 联调测试。
 *
 * <p>默认跳过，设置 {@code DORIS_STREAM_LOAD_IT=true} 后运行，要求本机可访问
 * Doris FE（默认 {@code 127.0.0.1:9030} / {@code 127.0.0.1:8030}）。</p>
 *
 * <p>覆盖三个真实问题：</p>
 * <ol>
 *   <li>正常批次能真实写入目标表，行数与提交一致；</li>
 *   <li>数据质量导致行被过滤时，Doris 返回 HTTP 200 + {@code Status=Fail}，
 *       组件必须抛异常而不是静默成功；</li>
 *   <li>UNIQUE KEY 模型会按 key 去重，写入行数可能小于提交行数
 *       （对应线上 1600W → 280W 的一种可能原因）。</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "DORIS_STREAM_LOAD_IT", matches = "true")
class DorisStreamLoadIntegrationTest extends ComponentTestBase {

    private static final String FE_HOST = env("DORIS_FE_HOST", "127.0.0.1");
    private static final int FE_HTTP_PORT = Integer.parseInt(env("DORIS_HTTP_PORT", "8030"));
    private static final int QUERY_PORT = Integer.parseInt(env("DORIS_QUERY_PORT", "9030"));
    private static final String SCHEMA = "dwd_stream_load_it";
    private static final String JDBC_URL =
        "jdbc:mysql://" + FE_HOST + ":" + QUERY_PORT + "/?useSSL=false&allowPublicKeyRetrieval=true";

    private static Connection admin;

    @BeforeAll
    static void prepareDoris() throws Exception {
        admin = DriverManager.getConnection(JDBC_URL, "root", "");
        execute("CREATE DATABASE IF NOT EXISTS " + SCHEMA);

        execute("DROP TABLE IF EXISTS " + SCHEMA + ".t_dup");
        execute("CREATE TABLE " + SCHEMA + ".t_dup (\n" +
            "  sellid VARCHAR(64),\n" +
            "  orderid VARCHAR(64),\n" +
            "  amount INT\n" +
            ") ENGINE=OLAP DUPLICATE KEY(sellid)\n" +
            "DISTRIBUTED BY HASH(sellid) BUCKETS 1\n" +
            "PROPERTIES (\"replication_num\" = \"1\")");

        execute("DROP TABLE IF EXISTS " + SCHEMA + ".t_unique");
        execute("CREATE TABLE " + SCHEMA + ".t_unique (\n" +
            "  sellid VARCHAR(64),\n" +
            "  orderid VARCHAR(64),\n" +
            "  amount INT\n" +
            ") ENGINE=OLAP UNIQUE KEY(sellid)\n" +
            "DISTRIBUTED BY HASH(sellid) BUCKETS 1\n" +
            "PROPERTIES (\"replication_num\" = \"1\")");

        execute("DROP TABLE IF EXISTS " + SCHEMA + ".t_narrow");
        execute("CREATE TABLE " + SCHEMA + ".t_narrow (\n" +
            "  sellid VARCHAR(5),\n" +
            "  amount INT\n" +
            ") ENGINE=OLAP DUPLICATE KEY(sellid)\n" +
            "DISTRIBUTED BY HASH(sellid) BUCKETS 1\n" +
            "PROPERTIES (\"replication_num\" = \"1\")");

        execute("DROP TABLE IF EXISTS " + SCHEMA + ".t_narrow_raw");
        execute("CREATE TABLE " + SCHEMA + ".t_narrow_raw (\n" +
            "  sellid VARCHAR(5),\n" +
            "  amount INT\n" +
            ") ENGINE=OLAP DUPLICATE KEY(sellid)\n" +
            "DISTRIBUTED BY HASH(sellid) BUCKETS 1\n" +
            "PROPERTIES (\"replication_num\" = \"1\")");

        execute("DROP TABLE IF EXISTS " + SCHEMA + ".t_three");
        execute("CREATE TABLE " + SCHEMA + ".t_three (\n" +
            "  sellid VARCHAR(32),\n" +
            "  amount INT,\n" +
            "  agency VARCHAR(64)\n" +
            ") ENGINE=OLAP DUPLICATE KEY(sellid)\n" +
            "DISTRIBUTED BY HASH(sellid) BUCKETS 1\n" +
            "PROPERTIES (\"replication_num\" = \"1\")");
    }

    @AfterAll
    static void closeDoris() throws Exception {
        if (admin != null) {
            admin.close();
        }
    }

    @Test
    void writesAllRowsToDuplicateKeyTable() throws Exception {
        execute("TRUNCATE TABLE " + SCHEMA + ".t_dup");
        JSONArray rows = new JSONArray();
        for (int i = 1; i <= 6; i++) {
            JSONObject row = new JSONObject();
            row.put("sellid", "S" + i);
            row.put("orderid", "O" + i);
            row.put("amount", i * 10);
            rows.add(row);
        }

        component("t_dup").execute(flowFile(rows));

        assertEquals(6, count("t_dup"), "DUPLICATE KEY 表应保留全部 6 行");
    }

    @Test
    void writesThirdColumnValues() throws Exception {
        execute("TRUNCATE TABLE " + SCHEMA + ".t_three");
        JSONArray rows = new JSONArray();
        JSONObject first = new JSONObject();
        first.put("sellid", "S1");
        first.put("amount", 1);
        first.put("agency", "agency-1");
        rows.add(first);
        JSONObject second = new JSONObject();
        // 中间列 amount 为 null（LEFT JOIN 未匹配维表的典型形态），后一列 agency 不能丢
        second.put("sellid", "S2");
        second.put("amount", null);
        second.put("agency", "agency-2");
        rows.add(second);

        component("t_three").execute(flowFile(rows));

        try (Statement statement = admin.createStatement();
             ResultSet rs = statement.executeQuery(
                 "SELECT sellid, amount, agency FROM " + SCHEMA + ".t_three ORDER BY sellid")) {
            assertTrue(rs.next(), "应能查到写入的行");
            assertEquals("S1", rs.getString("sellid"));
            assertEquals(1, rs.getInt("amount"));
            assertEquals("agency-1", rs.getString("agency"), "第三列 agency 应保留原值");

            assertTrue(rs.next(), "应能查到第二行");
            assertEquals("S2", rs.getString("sellid"));
            rs.getInt("amount");
            assertTrue(rs.wasNull(), "中间列 amount 应为 NULL");
            assertEquals("agency-2", rs.getString("agency"),
                "中间列 NULL 时后续列不能左移或丢失");
        }
    }

    @Test
    void writesTenThousandRowBatch() throws Exception {
        execute("TRUNCATE TABLE " + SCHEMA + ".t_dup");
        JSONArray rows = new JSONArray();
        for (int i = 1; i <= 10_000; i++) {
            JSONObject row = new JSONObject();
            row.put("sellid", "T" + i);
            row.put("orderid", "O" + i);
            row.put("amount", i);
            rows.add(row);
        }

        component("t_dup").execute(flowFile(rows));

        assertEquals(10_000, count("t_dup"), "单批 10000 行应全部写入");
    }

    @Test
    void filteredRowMakesBatchFailInsteadOfSilentSuccess() throws Exception {
        execute("TRUNCATE TABLE " + SCHEMA + ".t_narrow");
        JSONArray rows = new JSONArray();
        JSONObject good = new JSONObject();
        good.put("sellid", "G1");
        good.put("amount", 1);
        rows.add(good);
        JSONObject bad = new JSONObject();
        // 超过 VARCHAR(5) 长度，Doris 会按数据质量错误过滤该行
        bad.put("sellid", "THIS-IS-A-VERY-LONG-SELLID-VALUE");
        bad.put("amount", 2);
        rows.add(bad);

        RuntimeException error = assertThrows(
            RuntimeException.class,
            () -> component("t_narrow").execute(flowFile(rows))
        );
        assertTrue(error.getCause() instanceof IOException, "根因应为 IOException，实际: " + error.getCause());
        String message = error.getCause().getMessage();
        assertTrue(message.contains("Status=Fail"), "应包含 Doris 真实失败状态: " + message);
        assertTrue(message.contains(SCHEMA + ".t_narrow"), "应包含目标表: " + message);
        assertEquals(0, count("t_narrow"), "失败批次不应有部分数据落库");
    }

    /**
     * 直接发原始 Stream Load 请求，证明 Doris 在导入失败时确实返回 HTTP 200，
     * 即修复前「只看 statusCode」的写法会把失败当成功。
     */
    @Test
    void dorisReturnsHttp200EvenWhenStreamLoadFails() throws Exception {
        JSONArray rows = new JSONArray();
        JSONObject bad = new JSONObject();
        bad.put("sellid", "THIS-IS-A-VERY-LONG-SELLID-VALUE");
        bad.put("amount", 2);
        rows.add(bad);

        String url = "http://" + FE_HOST + ":" + FE_HTTP_PORT + "/api/" + SCHEMA + "/t_narrow_raw/_stream_load";
        HttpPut put = new HttpPut(url);
        put.setHeader("Expect", "100-continue");
        put.setHeader("Authorization", basicAuth("root", ""));
        put.setHeader("format", "json");
        put.setHeader("strip_outer_array", "true");
        put.setHeader("fuzzy_parse", "true");
        put.setEntity(new StringEntity(rows.toJSONString(), StandardCharsets.UTF_8));

        try (CloseableHttpClient client = HttpClients.custom()
            .setRedirectStrategy(new DefaultRedirectStrategy() {
                @Override
                protected boolean isRedirectable(String method) {
                    return true;
                }
            })
            .build();
             CloseableHttpResponse response = client.execute(put)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            assertEquals(200, statusCode, "Doris Stream Load 失败时 HTTP 状态码仍是 200，实际响应: " + body);
            JSONObject json = JSONObject.parseObject(body);
            assertEquals("Fail", json.getString("Status"), "响应体应是失败状态: " + body);
            assertTrue(json.getLongValue("NumberFilteredRows") > 0, "应有被过滤的行: " + body);
        }
    }

    @Test
    void uniqueKeyTableDeduplicatesByKey() throws Exception {
        execute("TRUNCATE TABLE " + SCHEMA + ".t_unique");
        JSONArray rows = new JSONArray();
        // 6 行只对应 3 个 sellid，UNIQUE KEY 模型下最终只剩 3 行
        String[] sellIds = {"A", "A", "B", "B", "B", "C"};
        for (int i = 0; i < sellIds.length; i++) {
            JSONObject row = new JSONObject();
            row.put("sellid", sellIds[i]);
            row.put("orderid", "O" + i);
            row.put("amount", i);
            rows.add(row);
        }

        component("t_unique").execute(flowFile(rows));

        assertEquals(3, count("t_unique"), "UNIQUE KEY 表应按 sellid 去重为 3 行");
    }

    private DorisStreamLoad component(String table) {
        DorisStreamLoad component = new DorisStreamLoad();
        initComponent(component, "doris-stream-load-it", "Doris写入");
        component.setSchema(SCHEMA);
        component.setTable(table);

        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType("DORIS");
        datasource.setUrl("jdbc:mysql://" + FE_HOST + ":" + QUERY_PORT + "/" + SCHEMA +
            "?useSSL=false&allowPublicKeyRetrieval=true");
        datasource.setHostname(FE_HOST);
        datasource.setUsername("root");
        datasource.setPassword("");
        datasource.setDbschema(SCHEMA);
        Map<String, String> extraParams = new HashMap<>();
        extraParams.put("fe_endpoint", "http://" + FE_HOST + ":" + FE_HTTP_PORT);
        datasource.setExtraParams(extraParams);
        component.setDatasource(datasource);
        return component;
    }

    private FlowFile flowFile(JSONArray rows) {
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(rows);
        return flowFile;
    }

    private static int count(String table) throws SQLException {
        try (Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + SCHEMA + "." + table)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Statement statement = admin.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String basicAuth(String username, String password) {
        String raw = username + ":" + password;
        return "Basic " + new String(Base64.encodeBase64(raw.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
