package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.provider.mongodb.MongoConnection;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB 数据输入组件（非 JDBC）。
 *
 * <p>从指定集合读取文档（支持 filter / projection / sort / limit），按 {@code batchSize} 分批
 * 以 JSON 数组向下游传递，并携带集合名与采样得到的表元数据（供下游自动建表）。
 * 配置 {@code incrColumn} 时按该数值字段做增量同步，增量水位写入任务状态。
 */
@ComponentRegister(
    value = "MongoDBInput",
    name = "MongoDB 输入",
    group = "数据输入",
    desc = "从 MongoDB 集合读取文档（支持 filter/projection/sort/limit 与增量），分批向下游传递。",
    order = 71
)
public class MongoDBInput extends FlowComponent {

    private DatasourceInfo datasource;
    private String collection;
    private String database;
    private String filter;
    private String projection;
    private String sort;
    private Integer limit;
    private String incrColumn;
    private int batchSize = 1000;
    private int sampleSize = 100;

    public MongoDBInput() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile.getAttribute("_end") != null) {
            return;
        }
        if (datasource == null) {
            throw new IllegalArgumentException("MongoDBInput 未配置数据源");
        }
        if (collection == null || collection.trim().isEmpty()) {
            throw new IllegalArgumentException("MongoDBInput 未配置集合名");
        }
        String coll = collection.trim();
        String db = database != null && !database.trim().isEmpty() ? database.trim() : datasource.getDbschema();

        try (DataSourceConnection connection = DataSourceProviderFactory.connect(this.datasource)) {
            if (!(connection instanceof MongoConnection mongo)) {
                throw new IllegalStateException(
                    "MongoDBInput 需要 MongoDB 数据源，实际类型: " + (datasource.getType())
                );
            }

            Map<String, Object> filterMap = parseObject(filter);
            if (filterMap == null) {
                filterMap = new LinkedHashMap<>();
            }

            String incrField = incrColumn == null || incrColumn.trim().isEmpty() ? null : incrColumn.trim();
            String statusKey = incrField == null ? null : "lastIncrValue." + coll;
            long lastValue = statusKey == null ? 0 : toLong(getStatus(statusKey));
            if (incrField != null && lastValue > 0) {
                filterMap.put(incrField, Map.of("$gt", lastValue));
                logInfo("增量同步，字段: " + incrField + "，上次增量值: " + lastValue);
            }

            List<Map<String, Object>> rows = mongo.findDocuments(
                db,
                coll,
                filterMap,
                parseObject(projection),
                parseObject(sort),
                limit == null ? 0 : limit
            );
            logInfo("读取到 " + rows.size() + " 条文档，集合: " + coll);

            if (incrField != null) {
                long maxValue = lastValue;
                for (Map<String, Object> row : rows) {
                    Object value = row.get(incrField);
                    if (value instanceof Number) {
                        maxValue = Math.max(maxValue, ((Number) value).longValue());
                    }
                }
                if (maxValue > lastValue) {
                    flowFile.setStatus(getId() + "." + statusKey, String.valueOf(maxValue));
                    logInfo("更新增量状态: " + statusKey + " = " + maxValue);
                }
            }

            TableMeta tableMeta = mongo.sampleTableMeta(db, coll, sampleSize);

            int effectiveBatch = batchSize <= 0 ? 1000 : batchSize;
            int batchCount = 0;
            for (int i = 0; i < rows.size(); i += effectiveBatch) {
                List<Map<String, Object>> batch = rows.subList(i, Math.min(i + effectiveBatch, rows.size()));
                sendRecords(batch, tableMeta, flowFile);
                batchCount++;
            }
            if (rows.isEmpty()) {
                sendRecords(new ArrayList<>(), tableMeta, flowFile);
            }
            logInfo("数据发送完成，共 " + batchCount + " 批");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logError("读取 MongoDB 失败，集合：" + coll + "，" + e.getMessage());
            throw new RuntimeException("读取 MongoDB 失败", e);
        }
    }

    private void sendRecords(List<Map<String, Object>> rows, TableMeta tableMeta, FlowFile sourceFlowFile) {
        JSONArray array = new JSONArray();
        for (Map<String, Object> row : rows) {
            array.add(new JSONObject(row));
        }
        FlowFile out = new FlowFile();
        out.setJsonArray(array);
        out.setAttribute(FlowFile.ATTRIBUTE_TABLE, collection.trim());
        if (tableMeta != null) {
            out.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        }
        if (sourceFlowFile != null && !sourceFlowFile.getStatusMap().isEmpty()) {
            out.getStatusMap().putAll(sourceFlowFile.getStatusMap());
        }
        writeRecords(out);
    }

    private static Map<String, Object> parseObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        JSONObject object = JSON.parseObject(json.trim());
        return object == null ? null : new LinkedHashMap<>(object);
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public void setProjection(String projection) {
        this.projection = projection;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public void setLimit(String limit) {
        if (limit != null && !limit.trim().isEmpty()) {
            try {
                this.limit = Integer.parseInt(limit.trim());
            } catch (NumberFormatException ignored) {
                this.limit = null;
            }
        }
    }

    public void setIncrColumn(String incrColumn) {
        this.incrColumn = incrColumn;
    }

    public int getBatchSize() {
        return batchSize;
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

    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    public void setSampleSize(String sampleSize) {
        if (sampleSize != null && !sampleSize.trim().isEmpty()) {
            try {
                this.sampleSize = Integer.parseInt(sampleSize.trim());
            } catch (NumberFormatException ignored) {
                this.sampleSize = 100;
            }
        }
    }
}
