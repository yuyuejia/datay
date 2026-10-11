package com.data.datasource.provider.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datasource.QueryResult;
import com.data.job.DatasourceInfo;
import com.data.metadata.TableMeta;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ElasticsearchConnectionTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            if ("/".equals(path)) {
                body = "{\"name\":\"node-1\",\"cluster_name\":\"test\"}";
            } else if ("/_sql".equals(path)) {
                body = "{\"columns\":[{\"name\":\"id\",\"type\":\"long\"},{\"name\":\"name\",\"type\":\"text\"}],\"rows\":[[1,\"a\"],[2,\"b\"]]}";
            } else if ("/_cat/indices".equals(path)) {
                body = "[{\"index\":\"idx1\",\"docs.count\":\"2\",\"store.size\":\"1kb\"}]";
            } else if ("/idx1/_mapping".equals(path)) {
                body =
                    "{\"idx1\":{\"mappings\":{\"properties\":{\"id\":{\"type\":\"long\"}," +
                    "\"name\":{\"type\":\"keyword\"},\"obj\":{\"properties\":{\"x\":{\"type\":\"long\"}}}}}}}";
            } else {
                body = "{}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ElasticsearchConnection connection() {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(ElasticsearchDataSourceProvider.TYPE);
        info.setUrl("localhost:" + port);
        return new ElasticsearchConnection(info);
    }

    @Test
    void executesSqlQuery() throws Exception {
        QueryResult result = connection().executeQuery("SELECT id, name FROM idx1", null, 100);
        assertEquals(List.of("id", "name"), result.getColumns());
        assertEquals(2, result.getRows().size());
        assertEquals(1, result.getRows().get(0).get("id"));
        assertEquals("a", result.getRows().get(0).get("name"));
    }

    @Test
    void truncatesToMaxRows() throws Exception {
        QueryResult result = connection().executeQuery("SELECT id, name FROM idx1", null, 1);
        assertEquals(1, result.getRows().size());
    }

    @Test
    void listsIndicesAsTables() throws Exception {
        List<TableMeta> tables = connection().getTables(null, null, null);
        assertEquals(1, tables.size());
        assertEquals("idx1", tables.get(0).getTable());
        assertEquals(ElasticsearchDataSourceProvider.TYPE, tables.get(0).getDbType());
        assertNotNull(tables.get(0).getComment());
    }

    @Test
    void readsMappingAsColumns() throws Exception {
        TableMeta table = connection().getTableMeta(null, "idx1");
        assertTrue(table.columns().stream().anyMatch(c -> "id".equals(c.getName()) && "long".equals(c.getType())));
        assertTrue(table.columns().stream().anyMatch(c -> "obj.x".equals(c.getName())));
    }

    @Test
    void testsConnection() {
        assertTrue(connection().testConnection());
    }

    @Test
    void exposesSchemas() throws Exception {
        assertEquals(List.of("_all"), connection().getSchemas());
    }

    @Test
    void rejectsWrites() {
        assertThrows(UnsupportedOperationException.class, () -> connection().executeUpdate("DELETE FROM idx1", null));
    }
}
