package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.provider.elasticsearch.ElasticsearchConnection;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Elasticsearch 数据输出组件（非 JDBC）。
 *
 * <p>将上游数据流按批通过 {@code _bulk} 写入 Elasticsearch 索引，用于 MySQL/PostgreSQL 等
 * JDBC 数据源到 Elasticsearch 的同步。目标索引优先取组件参数 {@code table}，
 * 缺省使用 FlowFile 的表名属性；{@code overwrite} 模式会在首批写入前重建索引。
 */
@ComponentRegister(
    value = "ElasticsearchOutput",
    name = "Elasticsearch 输出",
    group = "数据输出",
    desc = "将上游数据流按批写入 Elasticsearch 索引（非 JDBC 数据源）。",
    order = 11
)
public class ElasticsearchOutput extends FlowComponent {

    private String table; // 目标索引，缺省使用 flowfile 表名
    private String model; // 写入模式：overwrite / append
    private String idField; // 用作文档 _id 的字段名（可选）
    private DatasourceInfo datasource;

    private final Set<String> preparedIndexes = new HashSet<>();

    public ElasticsearchOutput() {
        setType(ComponentType.SINK);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (getContext() != null && getContext().isDebugMode()) {
            logInfo("调试模式：跳过写入 Elasticsearch");
            return;
        }
        JSONArray records = flowFile.getJsonArray();
        if (records == null || records.isEmpty()) {
            return;
        }

        String index = resolveIndex(flowFile);
        List<Map<String, Object>> documents = new ArrayList<>(records.size());
        for (Object o : records) {
            JSONObject record = (JSONObject) o;
            Map<String, Object> document = new LinkedHashMap<>();
            for (String key : record.keySet()) {
                document.put(key, record.get(key));
            }
            documents.add(document);
        }

        try (DataSourceConnection connection = DataSourceProviderFactory.connect(this.datasource)) {
            if (!(connection instanceof ElasticsearchConnection es)) {
                throw new IllegalStateException(
                    "ElasticsearchOutput 需要 Elasticsearch 数据源，实际类型: " + (datasource == null ? null : datasource.getType())
                );
            }
            if ("overwrite".equalsIgnoreCase(this.model) && preparedIndexes.add(index)) {
                es.recreateIndex(index);
            }
            int written = es.bulkIndex(index, documents, this.idField);
            logInfo("已写入 " + written + " 条记录到 Elasticsearch 索引 " + index + "，写入模式：" + model);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logError("写入 Elasticsearch 失败，索引：" + index + "，" + e.getMessage());
            throw new RuntimeException("写入 Elasticsearch 失败", e);
        }
    }

    private String resolveIndex(FlowFile flowFile) {
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
        throw new IllegalArgumentException("未配置目标索引，且无法从 flowfile 属性获取表名");
    }

    public void setTable(String table) {
        this.table = table;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setIdField(String idField) {
        this.idField = idField;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }
}
