package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.QueryResult;
import com.data.datasource.provider.influxdb.InfluxDbConnection;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * InfluxDB 数据输入组件（非 JDBC）。
 *
 * <p>从 InfluxDB 读取数据点：默认按数据源 Schema（Bucket/Database）与 measurement 构建查询，
 * 也可直接填写 Flux（2.x）或 InfluxQL（1.x）语句。结果按 {@code batchSize} 分批以 JSON 数组
 * 向下游传递，并携带 measurement 名与表元数据（供下游自动建表）。
 */
@ComponentRegister(
    value = "InfluxDBInput",
    name = "InfluxDB 输入",
    group = "数据输入",
    desc = "从 InfluxDB 读取数据点（默认按 Bucket/measurement 构建 Flux/InfluxQL，或直接填写查询语句），分批向下游传递。",
    order = 72
)
public class InfluxDBInput extends FlowComponent {

    private DatasourceInfo datasource;
    private String measurement;
    private String query;
    private String start = "-30d";
    private String stop;
    private int batchSize = 1000;

    public InfluxDBInput() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile.getAttribute("_end") != null) {
            return;
        }
        if (datasource == null) {
            throw new IllegalArgumentException("InfluxDBInput 未配置数据源");
        }
        String bucket = datasource.getDbschema();
        String measurementName = measurement == null ? null : measurement.trim();

        try (DataSourceConnection connection = DataSourceProviderFactory.connect(this.datasource)) {
            if (!(connection instanceof InfluxDbConnection influx)) {
                throw new IllegalStateException(
                    "InfluxDBInput 需要 InfluxDB 数据源，实际类型: " + datasource.getType()
                );
            }
            String statement = buildStatement(influx, bucket, measurementName);
            logInfo("执行 InfluxDB 查询: " + statement);

            QueryResult result = influx.executeQuery(statement, null, 0);
            List<Map<String, Object>> rows = result.getRows();
            String table = resolveTableName(result, measurementName);
            logInfo("读取到 " + rows.size() + " 条数据点，measurement: " + table);

            TableMeta tableMeta = null;
            if (table != null) {
                try {
                    tableMeta = influx.getTableMeta(bucket, table);
                } catch (Exception e) {
                    logInfo("读取表元数据失败（忽略）：" + e.getMessage());
                }
            }

            int effectiveBatch = batchSize <= 0 ? 1000 : batchSize;
            for (int i = 0; i < rows.size(); i += effectiveBatch) {
                List<Map<String, Object>> batch = rows.subList(i, Math.min(i + effectiveBatch, rows.size()));
                sendRecords(batch, table, tableMeta, flowFile);
            }
            if (rows.isEmpty()) {
                sendRecords(new ArrayList<>(), table, tableMeta, flowFile);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logError("读取 InfluxDB 失败：" + e.getMessage());
            throw new RuntimeException("读取 InfluxDB 失败", e);
        }
    }

    private String buildStatement(InfluxDbConnection influx, String bucket, String measurementName) {
        if (query != null && !query.trim().isEmpty()) {
            return query.trim();
        }
        if ("1".equals(influx.getVersion())) {
            return measurementName == null || measurementName.isEmpty()
                ? "SELECT * FROM /.*/"
                : "SELECT * FROM " + influxQlIdentifier(measurementName);
        }
        if (bucket == null || bucket.trim().isEmpty()) {
            throw new IllegalArgumentException("InfluxDB 2.x 需要配置数据源 Schema（Bucket），或直接填写 Flux 查询语句");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("from(bucket: ").append(fluxString(bucket.trim())).append(")\n");
        sb.append("  |> range(start: ").append(fluxRangeValue(start)).append(")");
        if (stop != null && !stop.trim().isEmpty()) {
            sb.insert(sb.length() - 1, ", stop: " + fluxRangeValue(stop));
        }
        sb.append('\n');
        if (measurementName != null && !measurementName.isEmpty()) {
            sb.append("  |> filter(fn: (r) => r._measurement == ").append(fluxString(measurementName)).append(")\n");
        }
        return sb.toString();
    }

    private String resolveTableName(QueryResult result, String measurementName) {
        if (measurementName != null && !measurementName.isEmpty()) {
            return measurementName;
        }
        for (Map<String, Object> row : result.getRows()) {
            Object value = row.get("_measurement");
            if (value != null && !String.valueOf(value).isEmpty()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private void sendRecords(List<Map<String, Object>> rows, String table, TableMeta tableMeta, FlowFile sourceFlowFile) {
        JSONArray array = new JSONArray();
        for (Map<String, Object> row : rows) {
            array.add(new JSONObject(row));
        }
        FlowFile out = new FlowFile();
        out.setJsonArray(array);
        if (table != null) {
            out.setAttribute(FlowFile.ATTRIBUTE_TABLE, table);
        }
        if (tableMeta != null) {
            out.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        }
        if (sourceFlowFile != null && !sourceFlowFile.getStatusMap().isEmpty()) {
            out.getStatusMap().putAll(sourceFlowFile.getStatusMap());
        }
        writeRecords(out);
    }

    private static String fluxString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String fluxRangeValue(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String influxQlIdentifier(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setSourceId(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setMeasurement(String measurement) {
        this.measurement = measurement;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public void setStart(String start) {
        if (start != null && !start.trim().isEmpty()) {
            this.start = start.trim();
        }
    }

    public void setStop(String stop) {
        this.stop = stop;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setBatchSize(String batchSize) {
        if (batchSize != null && !batchSize.trim().isEmpty()) {
            try {
                this.batchSize = Integer.parseInt(batchSize.trim());
            } catch (NumberFormatException ignored) {
                this.batchSize = 1000;
            }
        }
    }
}
