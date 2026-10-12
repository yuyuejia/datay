package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.provider.mongodb.MongoConnection;
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
 * MongoDB 数据输出组件（非 JDBC）。
 *
 * <p>将上游数据流按批写入 MongoDB 集合：目标集合优先取组件参数 {@code table}，缺省使用 FlowFile
 * 的表名属性；{@code overwrite} 模式在首批写入前删除集合。配置 {@code idField} 时以其字段值作为
 * 文档 {@code _id} 做 upsert（重复写入覆盖）。
 */
@ComponentRegister(
    value = "MongoDBOutput",
    name = "MongoDB 输出",
    group = "数据输出",
    desc = "将上游数据流按批写入 MongoDB 集合（非 JDBC 数据源）。",
    order = 12
)
public class MongoDBOutput extends FlowComponent {

    private String table; // 目标集合，缺省使用 flowfile 表名
    private String database;
    private String model; // 写入模式：overwrite / append
    private String idField; // 作为文档 _id 的字段名（可选）
    private DatasourceInfo datasource;

    private final Set<String> preparedCollections = new HashSet<>();

    public MongoDBOutput() {
        setType(ComponentType.SINK);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile.getAttribute("_end") != null) {
            return;
        }
        if (getContext() != null && getContext().isDebugMode()) {
            logInfo("调试模式：跳过写入 MongoDB");
            return;
        }
        if (datasource == null) {
            throw new IllegalArgumentException("MongoDBOutput 未配置数据源");
        }

        JSONArray records = flowFile.getJsonArray();
        if (records == null || records.isEmpty()) {
            return;
        }

        String collection = resolveCollection(flowFile);
        String db = database != null && !database.trim().isEmpty() ? database.trim() : datasource.getDbschema();

        List<Map<String, Object>> documents = new ArrayList<>(records.size());
        for (Object o : records) {
            Map<String, Object> document = new LinkedHashMap<>();
            if (o instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    document.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            } else {
                document.put("value", o);
            }
            documents.add(document);
        }

        try (DataSourceConnection connection = DataSourceProviderFactory.connect(this.datasource)) {
            if (!(connection instanceof MongoConnection mongo)) {
                throw new IllegalStateException(
                    "MongoDBOutput 需要 MongoDB 数据源，实际类型: " + (datasource.getType())
                );
            }
            if ("overwrite".equalsIgnoreCase(this.model) && preparedCollections.add(collection)) {
                mongo.dropCollection(db, collection);
            }
            int written = mongo.upsertDocuments(db, collection, documents, this.idField);
            logInfo("已写入 " + written + " 条记录到 MongoDB 集合 " + collection + "，写入模式：" + model);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logError("写入 MongoDB 失败，集合：" + collection + "，" + e.getMessage());
            throw new RuntimeException("写入 MongoDB 失败", e);
        }
    }

    private String resolveCollection(FlowFile flowFile) {
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
        throw new IllegalArgumentException("未配置目标集合，且无法从 flowfile 属性获取表名");
    }

    public void setTable(String table) {
        this.table = table;
    }

    public void setDatabase(String database) {
        this.database = database;
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
