package com.data.datasource.provider.influxdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.datasource.QueryResult;
import com.data.job.DatasourceInfo;
import com.data.metadata.TableMeta;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InfluxDbConnectionTest {

    private HttpServer server;
    private int port;

    private volatile String lastWriteQuery;
    private volatile String lastWriteBody;
    private volatile String lastDeleteQuery;
    private volatile String lastDeleteBody;
    private volatile String lastInfluxQl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            if ("/ping".equals(path) || "/api/v2/write".equals(path) || "/write".equals(path) || "/api/v2/delete".equals(path)) {
                if ("/api/v2/write".equals(path) || "/write".equals(path)) {
                    lastWriteQuery = query;
                    lastWriteBody = body;
                } else if ("/api/v2/delete".equals(path)) {
                    lastDeleteQuery = query;
                    lastDeleteBody = body;
                }
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String response;
            if ("/api/v2/buckets".equals(path)) {
                response = "{\"buckets\":[{\"name\":\"bucket1\"},{\"name\":\"bucket2\"}]}";
            } else if ("/api/v2/query".equals(path)) {
                response = fluxResponse(body);
            } else if ("/query".equals(path)) {
                lastInfluxQl = param(query, "q");
                response = influxQlResponse(lastInfluxQl);
            } else if ("/health".equals(path)) {
                response = "{\"status\":\"pass\"}";
            } else {
                response = "{}";
            }

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
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

    // ------------------------------------------------------------------ 1.x (InfluxQL)

    @Test
    void influxQlListsDatabasesAsSchemas() throws Exception {
        assertEquals(List.of("mydb"), v1Connection().getSchemas());
    }

    @Test
    void influxQlListsMeasurementsAsTables() throws Exception {
        List<TableMeta> tables = v1Connection().getTables(null, null, null);
        assertEquals(1, tables.size());
        assertEquals("cpu", tables.get(0).getTable());
        assertEquals(InfluxDbDataSourceProvider.TYPE, tables.get(0).getDbType());
    }

    @Test
    void influxQlReadsFieldsAndTagsAsColumns() throws Exception {
        TableMeta table = v1Connection().getTableMeta(null, "cpu");
        assertTrue(table.columns().stream().anyMatch(c -> "value".equals(c.getName()) && "DOUBLE".equals(c.getType())));
        assertTrue(table.columns().stream().anyMatch(c -> "usage".equals(c.getName()) && "BIGINT".equals(c.getType())));
        assertTrue(table.columns().stream().anyMatch(c -> "host".equals(c.getName()) && "STRING".equals(c.getType())));
    }

    @Test
    void influxQlExecutesSelect() throws Exception {
        QueryResult result = v1Connection().executeQuery("SELECT value FROM cpu", null, 100);
        assertEquals(List.of("time", "value"), result.getColumns());
        assertEquals(2, result.getRows().size());
        assertEquals("1.5", String.valueOf(result.getRows().get(0).get("value")));
    }

    // ------------------------------------------------------------------ 2.x (Flux)

    @Test
    void fluxListsBucketsAsSchemas() throws Exception {
        assertEquals(List.of("bucket1", "bucket2"), v2Connection().getSchemas());
    }

    @Test
    void fluxListsMeasurementsAsTables() throws Exception {
        List<TableMeta> tables = v2Connection().getTables("bucket1", null, null);
        assertEquals(2, tables.size());
        assertEquals("cpu", tables.get(0).getTable());
        assertEquals("mem", tables.get(1).getTable());
    }

    @Test
    void fluxReadsFieldsAndTagsAsColumns() throws Exception {
        TableMeta table = v2Connection().getTableMeta("bucket1", "cpu");
        assertTrue(table.columns().stream().anyMatch(c -> "host".equals(c.getName()) && "STRING".equals(c.getType())));
        assertTrue(table.columns().stream().anyMatch(c -> "value".equals(c.getName()) && "DOUBLE".equals(c.getType())));
    }

    @Test
    void fluxExecutesQueryAndParsesAnnotatedCsv() throws Exception {
        QueryResult result = v2Connection().executeQuery("from(bucket:\"bucket1\") |> range(start: -1h)", null, 100);
        assertEquals(List.of("result", "table", "_time", "_value"), result.getColumns());
        assertEquals(2, result.getRows().size());
        assertEquals("1.5", String.valueOf(result.getRows().get(0).get("_value")));
    }

    @Test
    void fluxTruncatesToMaxRows() throws Exception {
        QueryResult result = v2Connection().executeQuery("from(bucket:\"bucket1\") |> range(start: -1h)", null, 1);
        assertEquals(1, result.getRows().size());
    }

    // ------------------------------------------------------------------ 通用

    @Test
    void testsConnectionViaPing() {
        assertTrue(v1Connection().testConnection());
        assertTrue(v2Connection().testConnection());
    }

    @Test
    void rejectsWrites() {
        assertThrows(UnsupportedOperationException.class, () -> v2Connection().executeUpdate("from(bucket:\"b\")", null));
    }

    @Test
    void requiresOrgForFlux() throws Exception {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(InfluxDbDataSourceProvider.TYPE);
        info.setUrl("localhost:" + port);
        Map<String, String> extra = new HashMap<>();
        extra.put("influx.version", "2");
        info.setExtraParams(extra);
        assertThrows(
            IllegalArgumentException.class,
            () -> new InfluxDbConnection(info).executeQuery("from(bucket:\"b\") |> range(start: -1h)", null, 10)
        );
    }

    // ------------------------------------------------------------------ 写入 / 删除

    @Test
    void writesPointsAsLineProtocol() throws Exception {
        List<Map<String, Object>> rows = List.of(
            row("host", "server01", "region", "us", "usage", 1.5, "_time", "2024-01-01T00:00:00Z"),
            row("host", "server02", "usage", 2, "_time", "2024-01-01T00:01:00Z")
        );
        int written = v2Connection().writePoints(
            "bucket1",
            "cpu",
            rows,
            "_time",
            "s",
            new LinkedHashSet<>(List.of("host", "region"))
        );
        assertEquals(2, written);
        assertTrue(lastWriteQuery.contains("org=myorg"));
        assertTrue(lastWriteQuery.contains("bucket=bucket1"));
        assertTrue(lastWriteQuery.contains("precision=ns"));
        assertEquals(
            "cpu,host=server01,region=us usage=1.5 1704067200000000000\n" +
            "cpu,host=server02 usage=2i 1704067260000000000",
            lastWriteBody
        );
    }

    @Test
    void writesNumericTimestampUsingConfiguredUnit() throws Exception {
        v1Connection().writePoints("mydb", "sensor", List.of(row("device", "d1", "value", 1.5, "ts", 1704067200L)), "ts", "s", List.of("device"));
        assertTrue(lastWriteQuery.contains("db=mydb"));
        assertEquals("sensor,device=d1 value=1.5 1704067200000000000", lastWriteBody);
    }

    @Test
    void quotesAndEscapesStringFieldValues() throws Exception {
        v2Connection().writePoints("bucket1", "log", List.of(row("message", "a b,c", "level", "info")), null, "s", List.of());
        // 字符串 field 加引号；空格与逗号在 field 值中无需转义（引号内）
        assertEquals("log message=\"a b,c\",level=\"info\"", lastWriteBody);
    }

    @Test
    void skipsRowsWithoutFields() throws Exception {
        int written = v2Connection().writePoints("bucket1", "cpu", List.of(row("host", "server01")), null, "s", new LinkedHashSet<>(List.of("host")));
        assertEquals(0, written);
        assertNull(lastWriteBody);
    }

    @Test
    void deletesMeasurementV2() throws Exception {
        v2Connection().deleteMeasurement("bucket1", "cpu");
        assertTrue(lastDeleteQuery.contains("org=myorg"));
        assertTrue(lastDeleteQuery.contains("bucket=bucket1"));
        assertTrue(lastDeleteBody.contains("_measurement=\\\"cpu\\\""));
    }

    @Test
    void deletesMeasurementV1WithDrop() throws Exception {
        v1Connection().deleteMeasurement("mydb", "cpu");
        assertTrue(lastInfluxQl.toUpperCase().startsWith("DROP MEASUREMENT"));
        assertTrue(lastInfluxQl.contains("\"cpu\""));
    }

    @Test
    void writePointsRequiresMeasurement() {
        assertThrows(
            IllegalArgumentException.class,
            () -> v2Connection().writePoints("bucket1", " ", List.of(row("v", 1)), null, "s", List.of())
        );
    }

    @Test
    void writePointsRequiresOrgForV2() {
        InfluxDbConnection noOrg = new InfluxDbConnection(info("2", null, "tok", "bucket1"));
        assertThrows(
            IllegalArgumentException.class,
            () -> noOrg.writePoints("bucket1", "cpu", List.of(row("v", 1)), null, "s", List.of())
        );
    }

    // ------------------------------------------------------------------ 版本推断

    @Test
    void infersVersionTwoWhenOrgOrTokenPresent() {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(InfluxDbDataSourceProvider.TYPE);
        info.setUrl("localhost:" + port);
        info.setDbschema("bucket1");
        Map<String, String> extra = new HashMap<>();
        extra.put("influx.token", "tok");
        info.setExtraParams(extra);
        assertEquals("2", new InfluxDbConnection(info).getVersion());
    }

    @Test
    void infersVersionOneWithoutOrgOrToken() {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(InfluxDbDataSourceProvider.TYPE);
        info.setUrl("localhost:" + port);
        info.setDbschema("mydb");
        info.setUsername("admin");
        info.setPassword("pw");
        info.setExtraParams(new HashMap<>());
        assertEquals("1", new InfluxDbConnection(info).getVersion());
    }

    // ------------------------------------------------------------------ fixtures

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            row.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return row;
    }

    private InfluxDbConnection v1Connection() {
        return new InfluxDbConnection(info("1", null, null, "mydb"));
    }

    private InfluxDbConnection v2Connection() {
        return new InfluxDbConnection(info("2", "myorg", "tok", "bucket1"));
    }

    private DatasourceInfo info(String version, String org, String token, String schema) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(InfluxDbDataSourceProvider.TYPE);
        info.setUrl("localhost:" + port);
        info.setDbschema(schema);
        Map<String, String> extra = new HashMap<>();
        extra.put("influx.version", version);
        if (org != null) {
            extra.put("influx.org", org);
        }
        if (token != null) {
            extra.put("influx.token", token);
        }
        info.setExtraParams(extra);
        return info;
    }

    private static String param(String query, String key) {
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0 && key.equals(part.substring(0, idx))) {
                return URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String influxQlResponse(String q) {
        String statement = q == null ? "" : q.toUpperCase();
        if (statement.startsWith("SHOW DATABASES")) {
            return "{\"results\":[{\"series\":[{\"name\":\"databases\",\"columns\":[\"name\"],\"values\":[[\"mydb\"]]}]}]}";
        }
        if (statement.startsWith("SHOW MEASUREMENTS")) {
            return "{\"results\":[{\"series\":[{\"name\":\"measurements\",\"columns\":[\"name\"],\"values\":[[\"cpu\"]]}]}]}";
        }
        if (statement.startsWith("SHOW FIELD KEYS")) {
            return "{\"results\":[{\"series\":[{\"columns\":[\"fieldKey\",\"fieldType\"],\"values\":[[\"value\",\"float\"],[\"usage\",\"integer\"]]}]}]}";
        }
        if (statement.startsWith("SHOW TAG KEYS")) {
            return "{\"results\":[{\"series\":[{\"columns\":[\"tagKey\"],\"values\":[[\"host\"]]}]}]}";
        }
        return (
            "{\"results\":[{\"series\":[{\"name\":\"cpu\",\"columns\":[\"time\",\"value\"]," +
            "\"values\":[[\"2024-01-01T00:00:00Z\",1.5],[\"2024-01-01T00:01:00Z\",2.5]]}]}]}"
        );
    }

    private static String fluxResponse(String body) {
        String flux = body == null ? "" : body;
        if (flux.contains("schema.measurements")) {
            return "#group,false,false\n#datatype,string,string\n#default,_result,\n,result,_value\n,,cpu\n,,mem\n";
        }
        if (flux.contains("measurementTagKeys")) {
            return "#group,false,false\n#datatype,string,string\n#default,_result,\n,result,_value\n,,host\n";
        }
        if (flux.contains("measurementFieldKeys")) {
            return "#group,false,false\n#datatype,string,string\n#default,_result,\n,result,_value\n,,value\n";
        }
        return (
            "#group,false,false,false,false\n" +
            "#datatype,string,long,dateTime:RFC3339,double\n" +
            "#default,_result,,,\n" +
            ",result,table,_time,_value\n" +
            ",,0,2024-01-01T00:00:00Z,1.5\n" +
            ",,0,2024-01-01T00:01:00Z,2.5\n"
        );
    }
}
