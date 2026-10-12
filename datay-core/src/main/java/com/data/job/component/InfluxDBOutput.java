package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.provider.influxdb.InfluxDbConnection;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * InfluxDB 数据输出组件（非 JDBC）。
 *
 * <p>将上游数据流按批转换为 InfluxDB 行协议写入（{@code POST /api/v2/write} 或 {@code /write}）。
 * 支持指定 tag 列与时间戳列；{@code overwrite} 模式下在首批写入前删除同名 measurement 的数据。
 */
@ComponentRegister(
    value = "InfluxDBOutput",
    name = "InfluxDB 输出",
    group = "数据输出",
    desc = "将上游数据流按批以行协议写入 InfluxDB（非 JDBC 数据源）。",
    order = 13
)
public class InfluxDBOutput extends FlowComponent {

    private String table;
    private String model;
    private String timeColumn;
    private String timeUnit = "s";
    private String tagColumns;
    private DatasourceInfo datasource;

    private final Set<String> preparedMeasurements = new HashSet<>();

    public InfluxDBOutput() {
        setType(ComponentType.SINK);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile.getAttribute("_end") != null) {
            return;
        }
        if (getContext() != null && getContext().isDebugMode()) {
            logInfo("调试模式：跳过写入 InfluxDB");
            return;
        }
        if (datasource == null) {
            throw new IllegalArgumentException("InfluxDBOutput 未配置数据源");
        }

        JSONArray records = flowFile.getJsonArray();
        if (records == null || records.isEmpty()) {
            return;
        }

        String measurement = resolveMeasurement(flowFile);
        String bucket = datasource.getDbschema();

        List<Map<String, Object>> rows = new ArrayList<>(records.size());
        for (Object o : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (o instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    row.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            } else {
                row.put("value", o);
            }
            rows.add(row);
        }

        try (DataSourceConnection connection = DataSourceProviderFactory.connect(this.datasource)) {
            if (!(connection instanceof InfluxDbConnection influx)) {
                throw new IllegalStateException(
                    "InfluxDBOutput 需要 InfluxDB 数据源，实际类型: " + (datasource == null ? null : datasource.getType())
                );
            }
            if ("overwrite".equalsIgnoreCase(this.model) && preparedMeasurements.add(measurement)) {
                influx.deleteMeasurement(bucket, measurement);
            }
            int written = influx.writePoints(bucket, measurement, rows, timeColumn, timeUnit, parseTagColumns());
            logInfo("已写入 " + written + " 条记录到 InfluxDB measurement " + measurement + "，写入模式：" + model);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logError("写入 InfluxDB 失败，measurement：" + measurement + "，" + e.getMessage());
            throw new RuntimeException("写入 InfluxDB 失败", e);
        }
    }

    private String resolveMeasurement(FlowFile flowFile) {
        if (this.table != null && !this.table.trim().isEmpty()) {
            return this.table.trim();
        }
        Object tableAttr = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE);
        if (tableAttr != null) {
            return tableAttr.toString();
        }
        TableMeta tableMeta = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        if (tableMeta != null && tableMeta.getTable() != null) {
            return tableMeta.getTable();
        }
        throw new IllegalArgumentException("未配置目标 measurement，且无法从 flowfile 属性获取表名");
    }

    private Set<String> parseTagColumns() {
        Set<String> tags = new LinkedHashSet<>();
        if (tagColumns == null || tagColumns.trim().isEmpty()) {
            return tags;
        }
        for (String column : tagColumns.split(",")) {
            if (column != null && !column.trim().isEmpty()) {
                tags.add(column.trim());
            }
        }
        return tags;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setTimeColumn(String timeColumn) {
        this.timeColumn = timeColumn;
    }

    public void setTimeUnit(String timeUnit) {
        if (timeUnit != null && !timeUnit.trim().isEmpty()) {
            this.timeUnit = timeUnit.trim();
        }
    }

    public void setTagColumns(String tagColumns) {
        this.tagColumns = tagColumns;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setSourceId(DatasourceInfo datasource) {
        this.datasource = datasource;
    }
}
