package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DorisStreamLoad 实际发起 Stream Load 请求的回归测试。
 *
 * <p>用本地 HTTP 服务模拟 Doris FE，验证：
 * <ol>
 *   <li>请求头符合预期（尤其 {@code max_filter_ratio=0}）；</li>
 *   <li>HTTP 200 + {@code Status=Fail} 会被识别为失败并抛出异常；</li>
 *   <li>HTTP 200 + {@code Status=Success} 正常返回。</li>
 * </ol>
 * </p>
 */
public class DorisStreamLoadHttpTest {

    private static final String CONTEXT_PATH = "/api/dwd/dwd_visitor_order/_stream_load";

    private HttpServer server;
    private DorisStreamLoad component;
    private TableMeta targetTable;

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<Headers> requestHeaders = new AtomicReference<>();
    private volatile String responseBody;
    private volatile int responseStatus = 200;

    @BeforeEach
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
            CONTEXT_PATH,
            exchange -> {
                requestHeaders.set(exchange.getRequestHeaders());
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = responseBody == null ? new byte[0] : responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        );
        server.start();

        component = new DorisStreamLoad();
        component.setId("doris_sink");
        component.setName("Doris写入");
        component.setTaskLogger(new TaskLogger("doris-stream-load-http-test", "doris-stream-load-http-test"));
        component.setSchema("dwd");
        component.setTable("dwd_visitor_order");

        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType("DORIS");
        datasource.setUrl("jdbc:mysql://127.0.0.1:9030/dwd");
        datasource.setUsername("root");
        datasource.setPassword("");
        Map<String, String> extraParams = new HashMap<>();
        extraParams.put("fe_endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
        datasource.setExtraParams(extraParams);
        component.setDatasource(datasource);

        targetTable = new TableMeta();
        targetTable.setSchema("dwd");
        targetTable.setTable("dwd_visitor_order");
        targetTable.addColumn(new ColumnMeta("sellid", "VARCHAR"));
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void successResponseWritesAndSendsExpectedHeaders() throws Exception {
        responseBody =
            "{\"Status\":\"Success\",\"Message\":\"OK\",\"NumberTotalRows\":2," +
            "\"NumberLoadedRows\":2,\"NumberFilteredRows\":0}";

        invokeStreamLoad(flowFile());

        String body = requestBody.get();
        assertTrue(body.contains("\"sellid\":\"S1\""), "请求体应包含记录数据: " + body);
        assertTrue(body.contains("\"sellid\":\"S2\""), "请求体应包含记录数据: " + body);

        Headers headers = requestHeaders.get();
        assertEquals("0", headers.getFirst("max_filter_ratio"));
        assertEquals("json", headers.getFirst("format"));
        assertEquals("true", headers.getFirst("strip_outer_array"));
        assertNull(headers.getFirst("fuzzy_parse"), "不能开启 fuzzy_parse，否则 NULL 列会导致后续字段错位");
    }

    @Test
    public void http200WithFailStatusThrows() {
        responseBody =
            "{\"Status\":\"Fail\",\"Message\":\"too many filtered rows\"," +
            "\"NumberTotalRows\":2,\"NumberLoadedRows\":0,\"NumberFilteredRows\":2," +
            "\"ErrorURL\":\"http://be:8040/api/_load_error_log\"}";

        InvocationTargetException error = assertThrows(
            InvocationTargetException.class,
            () -> invokeStreamLoad(flowFile())
        );
        Throwable cause = error.getCause();
        assertTrue(cause instanceof IOException, "应抛出 IOException，实际: " + cause);
        assertTrue(cause.getMessage().contains("Status=Fail"));
        assertTrue(cause.getMessage().contains("too many filtered rows"));
        assertTrue(cause.getMessage().contains("dwd.dwd_visitor_order"));
        assertTrue(cause.getMessage().contains("ErrorURL"));
    }

    @Test
    public void http500Throws() {
        responseStatus = 500;
        responseBody = "internal error";

        InvocationTargetException error = assertThrows(
            InvocationTargetException.class,
            () -> invokeStreamLoad(flowFile())
        );
        assertTrue(error.getCause() instanceof IOException);
        assertTrue(error.getCause().getMessage().contains("statusCode=500"));
    }

    private FlowFile flowFile() {
        JSONArray rows = new JSONArray();
        JSONObject first = new JSONObject();
        first.put("sellid", "S1");
        rows.add(first);
        JSONObject second = new JSONObject();
        second.put("sellid", "S2");
        rows.add(second);

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(rows);
        return flowFile;
    }

    private void invokeStreamLoad(FlowFile flowFile) throws Exception {
        Method method = DorisStreamLoad.class.getDeclaredMethod(
            "streamLoadData",
            TableMeta.class,
            FlowFile.class,
            String.class
        );
        method.setAccessible(true);
        method.invoke(component, targetTable, flowFile, "INSERT");
    }
}
