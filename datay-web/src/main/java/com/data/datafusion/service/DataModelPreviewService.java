package com.data.datafusion.service;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.ModelPreviewDTO;
import com.data.metadata.util.DBUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据模型物理表预览服务。
 *
 * <p>只读取模型绑定的物理表，不接受前端传入任意 SQL。</p>
 */
@Service
@Transactional(readOnly = true)
public class DataModelPreviewService {

    private static final Logger LOG = LoggerFactory.getLogger(DataModelPreviewService.class);

    private static final int DEFAULT_LIMIT = 100;

    private static final int MAX_LIMIT = 500;

    private final DataModelRepository dataModelRepository;
    private final DataSourceService dataSourceService;
    private final DataSourceQueryService dataSourceQueryService;
    private final ObjectMapper objectMapper;

    public DataModelPreviewService(
        DataModelRepository dataModelRepository,
        DataSourceService dataSourceService,
        DataSourceQueryService dataSourceQueryService,
        ObjectMapper objectMapper
    ) {
        this.dataModelRepository = dataModelRepository;
        this.dataSourceService = dataSourceService;
        this.dataSourceQueryService = dataSourceQueryService;
        this.objectMapper = objectMapper;
    }

    public ModelPreviewDTO preview(String modelId, Integer limit) {
        DataModel model = dataModelRepository
            .findById(modelId)
            .orElseThrow(() -> new IllegalArgumentException("数据模型不存在：" + modelId));
        if (model.getDataSourceId() == null || model.getDataSourceId().isBlank()) {
            throw new IllegalArgumentException("模型尚未绑定数据源，请先物化或使用注册模式");
        }
        if (model.getTableName() == null || model.getTableName().isBlank()) {
            throw new IllegalArgumentException("模型尚未绑定物理表，请先物化或使用注册模式");
        }
        DataSourceDTO dataSource = dataSourceService
            .findOne(model.getDataSourceId())
            .orElseThrow(() -> new IllegalArgumentException("数据源不存在：" + model.getDataSourceId()));

        int rowLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        String statement = buildPreviewStatement(dataSource, model, rowLimit);
        try {
            Map<String, Object> result = dataSourceQueryService.executeQuery(dataSource, statement);
            return new ModelPreviewDTO(
                asColumns(result.get("columns")),
                asRows(result.get("rows")),
                displayTable(model),
                dataSource.getId(),
                dataSource.getName(),
                rowLimit
            );
        } catch (SQLException e) {
            LOG.warn("预览模型 {} 数据失败: {}", model.getCode(), e.getMessage());
            throw new IllegalArgumentException("数据预览查询失败：" + e.getMessage(), e);
        }
    }

    private String buildPreviewStatement(DataSourceDTO dataSource, DataModel model, int limit) {
        if (isMongo(dataSource)) {
            return buildMongoStatement(model, limit);
        }
        String dbType = resolveDbType(dataSource);
        String table = qualifiedTable(dbType, model.getSchemaName(), model.getTableName());
        if ("ORACLE".equals(dbType)) {
            return "SELECT * FROM " + table + " WHERE ROWNUM <= " + limit;
        }
        if ("SQLSERVER".equals(dbType)) {
            return "SELECT TOP " + limit + " * FROM " + table;
        }
        return "SELECT * FROM " + table + " LIMIT " + limit;
    }

    private String buildMongoStatement(DataModel model, int limit) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        if (model.getSchemaName() != null && !model.getSchemaName().isBlank()) {
            envelope.put("database", model.getSchemaName());
        }
        envelope.put("collection", model.getTableName());
        envelope.put("filter", Map.of());
        envelope.put("limit", limit);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("MongoDB 预览语句生成失败：" + e.getOriginalMessage(), e);
        }
    }

    private boolean isMongo(DataSourceDTO dataSource) {
        String type = dataSource.getType() == null ? "" : dataSource.getType().toUpperCase(Locale.ROOT);
        return type.contains("MONGO") || (dataSource.getUrl() != null && dataSource.getUrl().toLowerCase(Locale.ROOT).startsWith("mongodb"));
    }

    private String resolveDbType(DataSourceDTO dataSource) {
        String dbType = dataSource.getUrl() == null ? null : DBUtils.getDBType(dataSource.getUrl());
        if (dbType == null || dbType.isBlank() || "Unknown".equalsIgnoreCase(dbType)) {
            dbType = dataSource.getType() == null ? "MYSQL" : dataSource.getType();
        }
        return dbType == null ? "MYSQL" : dbType.toUpperCase(Locale.ROOT);
    }

    private String qualifiedTable(String dbType, String schema, String table) {
        String quotedTable = quoteIdent(dbType, table);
        if (schema == null || schema.isBlank()) {
            return quotedTable;
        }
        return quoteIdent(dbType, schema.trim()) + "." + quotedTable;
    }

    private String quoteIdent(String dbType, String ident) {
        if (ident == null) {
            return "";
        }
        if (
            "MYSQL".equals(dbType) ||
            "MARIADB".equals(dbType) ||
            "STARROCKS".equals(dbType) ||
            "OCEANBASE".equals(dbType) ||
            "POLARDB".equals(dbType) ||
            "TIDB".equals(dbType) ||
            "DORIS".equals(dbType) ||
            "CLICKHOUSE".equals(dbType)
        ) {
            return "`" + ident.replace("`", "``") + "`";
        }
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    private String displayTable(DataModel model) {
        List<String> parts = new ArrayList<>();
        if (model.getSchemaName() != null && !model.getSchemaName().isBlank()) {
            parts.add(model.getSchemaName());
        }
        if (model.getTableName() != null && !model.getTableName().isBlank()) {
            parts.add(model.getTableName());
        }
        return String.join(".", parts);
    }

    @SuppressWarnings("unchecked")
    private List<String> asColumns(Object value) {
        if (value instanceof List<?> list) {
            List<String> columns = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    columns.add(String.valueOf(item));
                }
            }
            return columns;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asRows(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) map);
                }
            }
            return rows;
        }
        return List.of();
    }
}
