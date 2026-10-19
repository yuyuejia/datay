package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.Connection;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * HttpInvoke 分页循环拉取测试：使用本地 HTTP 服务模拟分页接口。
 */
public class HttpInvokePaginationTest {

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final List<Integer> requestedPages = new ArrayList<>();
    private final List<JSONObject> requestBodies = new ArrayList<>();
    private int totalRows = 7;

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/query", this::handleQuery);
        server.start();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @Timeout(60000)
    public void shouldFetchAllPagesWithBodyPlaceholders() throws Exception {
        HttpInvoke component = newComponent();
        component.setBody("{\"page_no\":${pageNo},\"page_size\":${pageSize}}");
        assertPaginationResult(component);
    }

    @Test
    @Timeout(60000)
    public void shouldFetchAllPagesWithBodyPaths() throws Exception {
        HttpInvoke component = newComponent();
        component.setBody("{\"page_no\":1,\"page_size\":1}");
        component.setPageNoPath("page_no");
        component.setPageSizePath("page_size");
        assertPaginationResult(component);
    }

    @Test
    @Timeout(60000)
    public void shouldStopAfterEmptyPageWhenTotalIsExactMultipleOfPageSize() throws Exception {
        totalRows = 6;
        HttpInvoke component = newComponent();
        component.setBody("{\"page_no\":${pageNo},\"page_size\":${pageSize}}");
        component.execute(new FlowFile());

        LinkedBlockingQueue<Object> outputQueue = (LinkedBlockingQueue<Object>) component.getContext()
            .getConnections()
            .get("http-node_sink");
        List<FlowFile> pages = new ArrayList<>();
        Object item;
        while ((item = outputQueue.poll()) != null) {
            pages.add((FlowFile) item);
        }

        assertEquals(3, requestCount.get());
        assertEquals(List.of(1, 2, 3), requestedPages);
        assertEquals(2, pages.size());
    }

    @Test
    @Timeout(60000)
    public void shouldResolveTimeRangeOnceForAllPages() throws Exception {
        HttpInvoke component = newComponent();
        component.setBody(
            "{\"page_no\":${pageNo},\"page_size\":${pageSize}," +
            "\"conditions\":[{\"start\":\"#{NOW - 1d, yyyy-MM-dd} 00:00:00\"," +
            "\"end\":\"#{NOW - 1d, yyyy-MM-dd} 23:59:59\"}]}"
        );
        assertPaginationResult(component);

        assertEquals(3, requestBodies.size());
        String start = requestBodies.get(0).getJSONArray("conditions").getJSONObject(0).getString("start");
        String end = requestBodies.get(0).getJSONArray("conditions").getJSONObject(0).getString("end");
        assertFalse(start.contains("#{"));
        assertFalse(end.contains("#{"));
        for (JSONObject body : requestBodies) {
            assertEquals(start, body.getJSONArray("conditions").getJSONObject(0).getString("start"));
            assertEquals(end, body.getJSONArray("conditions").getJSONObject(0).getString("end"));
        }
    }

    private HttpInvoke newComponent() {
        HttpInvoke component = new HttpInvoke();
        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode("test-instance");
        context.setJobCode("test-job");

        LinkedBlockingQueue<Object> outputQueue = new LinkedBlockingQueue<>();
        context.getConnections().put("http-node_sink", outputQueue);

        component.setContext(context);
        component.setTaskLogger(new TaskLogger("test-job", "test-instance"));
        component.setId("http-node");
        component.setName("HttpInvoke");
        component.setActiveThreads(new AtomicInteger(1));
        component.getOutput().add(new Connection("http-node", "sink", 0));

        component.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/query");
        component.setMethod("POST");
        component.setHeaders(new java.util.HashMap<>());
        component.setPaginationEnabled(true);
        component.setStartPage(1);
        component.setPageSize(3);
        component.setMaxPages(10);
        component.setDataPath("$.std_data.result.rows");
        return component;
    }

    private void assertPaginationResult(HttpInvoke component) throws Exception {
        component.execute(new FlowFile());

        LinkedBlockingQueue<Object> outputQueue = (LinkedBlockingQueue<Object>) component.getContext()
            .getConnections()
            .get("http-node_sink");
        List<FlowFile> pages = new ArrayList<>();
        Object item;
        while ((item = outputQueue.poll()) != null) {
            pages.add((FlowFile) item);
        }

        assertEquals(3, requestCount.get());
        assertEquals(List.of(1, 2, 3), requestedPages);
        assertEquals(3, pages.size());

        int totalRows = 0;
        for (FlowFile page : pages) {
            JSONArray rows = page.getJsonArray();
            assertNotNull(rows);
            totalRows += rows.size();
            assertNotNull(page.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA));
        }
        assertEquals(7, totalRows);
    }

    private void handleQuery(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JSONObject request = JSON.parseObject(requestBody);
        int pageNo = request.getIntValue("page_no");
        int pageSize = request.getIntValue("page_size");
        requestCount.incrementAndGet();
        requestedPages.add(pageNo);
        requestBodies.add(request);

        int total = totalRows;
        int start = (pageNo - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        JSONArray rows = new JSONArray();
        for (int i = start; i < end; i++) {
            JSONObject row = new JSONObject();
            row.put("id", i + 1);
            row.put("name", "row-" + (i + 1));
            rows.add(row);
        }

        JSONObject result = new JSONObject();
        result.put("rows", rows);

        JSONObject stdData = new JSONObject();
        stdData.put("parameter", request);
        stdData.put("result", result);

        JSONObject response = new JSONObject();
        response.put("std_data", stdData);

        byte[] bytes = response.toJSONString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
