package com.data.datafusion.service;

import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.MaterializeFieldDTO;
import com.data.datafusion.service.dto.MaterializeRequestDTO;
import com.data.datafusion.service.dto.MaterializeResponseDTO;
import com.data.datafusion.service.dto.ModelFieldDTO;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.data.job.DatasourceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DataModelMaterializeService {

    private static final Logger LOG = LoggerFactory.getLogger(DataModelMaterializeService.class);

    private final DataSourceService dataSourceService;

    private static final Map<String, String> LOGICAL_TYPE_TO_COMMON_TYPE = new HashMap<>();

    static {
        LOGICAL_TYPE_TO_COMMON_TYPE.put("STRING", "VARCHAR");
        LOGICAL_TYPE_TO_COMMON_TYPE.put("INTEGER", "INT");
        LOGICAL_TYPE_TO_COMMON_TYPE.put("LONG", "BIGINT");
        LOGICAL_TYPE_TO_COMMON_TYPE.put("DOUBLE", "DOUBLE");
        LOGICAL_TYPE_TO_COMMON_TYPE.put("DECIMAL", "DECIMAL");
        LOGICAL_TYPE_TO_COMMON_TYPE.put("DATE", "DATE");
        LOGICAL_TYPE_TO_COMMON_TYPE.put("DATETIME", "TIMESTAMP");
        LOGICAL_TYPE_TO_COMMON_TYPE.put("BOOLEAN", "BOOLEAN");
    }

    public DataModelMaterializeService(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    public String logicalTypeToCommonType(String logicalType) {
        if (logicalType == null) {
            return "VARCHAR";
        }
        return LOGICAL_TYPE_TO_COMMON_TYPE.getOrDefault(logicalType.toUpperCase(), logicalType);
    }

    public List<MaterializeFieldDTO> prepareMaterializeFields(List<ModelFieldDTO> modelFields, String targetDbType) {
        List<MaterializeFieldDTO> fields = new ArrayList<>();
        if (modelFields == null) {
            return fields;
        }

        for (ModelFieldDTO modelField : modelFields) {
            MaterializeFieldDTO field = new MaterializeFieldDTO();
            field.setFieldName(modelField.getFieldName());
            field.setLogicalType(modelField.getFieldType());
            field.setFieldLength(modelField.getFieldLength());
            field.setFieldPrecision(modelField.getFieldPrecision());
            field.setFieldScale(modelField.getFieldScale());
            field.setIsPrimaryKey(modelField.getIsPrimaryKey());
            field.setDescription(modelField.getDescription());

            String commonType = logicalTypeToCommonType(modelField.getFieldType());
            ColumnMeta columnMeta = new ColumnMeta(modelField.getFieldName(), commonType);
            columnMeta.setLength(modelField.getFieldLength() != null ? modelField.getFieldLength() : 0);
            columnMeta.setPrecision(modelField.getFieldPrecision() != null ? modelField.getFieldPrecision() : 0);
            columnMeta.setScale(modelField.getFieldScale() != null ? modelField.getFieldScale() : 0);
            columnMeta.setPrimaryKey(Boolean.TRUE.equals(modelField.getIsPrimaryKey()));
            columnMeta.setComment(modelField.getDescription());

            ColumnMeta physicalColumn = DatabaseConverter.convert("common", targetDbType, columnMeta);
            field.setPhysicalType(physicalColumn.getType());

            fields.add(field);
        }

        return fields;
    }

    public MaterializeResponseDTO checkTableExists(MaterializeRequestDTO request) {
        MaterializeResponseDTO response = new MaterializeResponseDTO();
        response.setSuccess(true);

        Optional<DataSourceDTO> dsOpt = dataSourceService.findOne(request.getDataSourceId());
        if (dsOpt.isEmpty()) {
            response.setSuccess(false);
            response.setMessage("数据源不存在");
            return response;
        }

        DataSourceDTO ds = dsOpt.get();
        try (Connection conn = DBUtils.getConnection(toDatasourceInfo(ds))) {
            String dbType = DBUtils.getDBType(ds.getUrl());
            boolean exists;
            if ("mysql".equalsIgnoreCase(dbType)) {
                exists = DBUtils.tableExists(conn, request.getSchemaName(), null, request.getTableName());
            } else {
                exists = DBUtils.tableExists(conn, null, request.getSchemaName(), request.getTableName());
            }
            response.setTableExists(exists);
            response.setMessage(exists ? "目标表已存在" : "目标表不存在，可以创建");
        } catch (SQLException e) {
            LOG.error("检查表是否存在失败", e);
            response.setSuccess(false);
            response.setMessage("检查表存在性失败：" + e.getMessage());
        }

        return response;
    }

    public MaterializeResponseDTO generateDDL(MaterializeRequestDTO request) {
        MaterializeResponseDTO response = new MaterializeResponseDTO();

        Optional<DataSourceDTO> dsOpt = dataSourceService.findOne(request.getDataSourceId());
        if (dsOpt.isEmpty()) {
            response.setSuccess(false);
            response.setMessage("数据源不存在");
            return response;
        }

        DataSourceDTO ds = dsOpt.get();
        String dbType = DBUtils.getDBType(ds.getUrl());

        List<ColumnMeta> columns = new ArrayList<>();
        for (MaterializeFieldDTO fieldDTO : request.getFields()) {
            ColumnMeta column = new ColumnMeta(fieldDTO.getFieldName(), fieldDTO.getPhysicalType());
            column.setLength(fieldDTO.getFieldLength() != null ? fieldDTO.getFieldLength() : 0);
            column.setPrecision(fieldDTO.getFieldPrecision() != null ? fieldDTO.getFieldPrecision() : 0);
            column.setScale(fieldDTO.getFieldScale() != null ? fieldDTO.getFieldScale() : 0);
            column.setPrimaryKey(Boolean.TRUE.equals(fieldDTO.getIsPrimaryKey()));
            column.setComment(fieldDTO.getDescription());
            columns.add(column);
        }

        TableMeta tableMeta = new TableMeta(request.getTableName(), columns);
        tableMeta.setDbType(dbType);
        tableMeta.setSchema(request.getSchemaName());

        String ddl = DatabaseConverter.generateTableDDL(dbType, tableMeta);
        response.setSuccess(true);
        response.setDdl(ddl);

        return response;
    }

    public MaterializeResponseDTO materialize(MaterializeRequestDTO request) {
        MaterializeResponseDTO response = new MaterializeResponseDTO();

        Optional<DataSourceDTO> dsOpt = dataSourceService.findOne(request.getDataSourceId());
        if (dsOpt.isEmpty()) {
            response.setSuccess(false);
            response.setMessage("数据源不存在");
            return response;
        }

        DataSourceDTO ds = dsOpt.get();
        String dbType = DBUtils.getDBType(ds.getUrl());

        try (Connection conn = DBUtils.getConnection(toDatasourceInfo(ds))) {
            boolean exists;
            if ("mysql".equalsIgnoreCase(dbType)) {
                exists = DBUtils.tableExists(conn, request.getSchemaName(), null, request.getTableName());
            } else {
                exists = DBUtils.tableExists(conn, null, request.getSchemaName(), request.getTableName());
            }

            if (exists && !Boolean.TRUE.equals(request.getOverwrite())) {
                response.setSuccess(false);
                response.setMessage("目标表已存在，如需覆盖请勾选覆盖选项");
                response.setTableExists(true);
                return response;
            }

            if (exists && Boolean.TRUE.equals(request.getOverwrite())) {
                String dropTableSql = DatabaseConverter.generateDropTable(dbType, buildFullTableName(dbType, request.getSchemaName(), request.getTableName()));
                LOG.info("执行 DROP TABLE: {}", dropTableSql);
                DBUtils.execute(conn, dropTableSql);
            }

            List<ColumnMeta> columns = new ArrayList<>();
            for (MaterializeFieldDTO fieldDTO : request.getFields()) {
                ColumnMeta column = new ColumnMeta(fieldDTO.getFieldName(), fieldDTO.getPhysicalType());
                column.setLength(fieldDTO.getFieldLength() != null ? fieldDTO.getFieldLength() : 0);
                column.setPrecision(fieldDTO.getFieldPrecision() != null ? fieldDTO.getFieldPrecision() : 0);
                column.setScale(fieldDTO.getFieldScale() != null ? fieldDTO.getFieldScale() : 0);
                column.setPrimaryKey(Boolean.TRUE.equals(fieldDTO.getIsPrimaryKey()));
                column.setComment(fieldDTO.getDescription());
                columns.add(column);
            }

            TableMeta tableMeta = new TableMeta(request.getTableName(), columns);
            tableMeta.setDbType(dbType);
            tableMeta.setSchema(request.getSchemaName());

            String ddl = DatabaseConverter.generateTableDDL(dbType, tableMeta);
            LOG.info("执行 CREATE TABLE: {}", ddl);
            DBUtils.execute(conn, ddl);

            response.setSuccess(true);
            response.setMessage("模型物化成功");
            response.setDdl(ddl);
        } catch (SQLException e) {
            LOG.error("模型物化失败", e);
            response.setSuccess(false);
            response.setMessage("模型物化失败：" + e.getMessage());
        }

        return response;
    }

    private String buildFullTableName(String dbType, String schema, String tableName) {
        if (schema != null && !schema.isEmpty()) {
            return schema + "." + tableName;
        }
        return tableName;
    }

    private DatasourceInfo toDatasourceInfo(DataSourceDTO dto) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(dto.getType());
        info.setUrl(dto.getUrl());
        info.setUsername(dto.getUsername());
        info.setPassword(dto.getPassword());
        info.setDbschema(dto.getSchemaName());
        info.setPort(dto.getPort());
        info.setHostname(dto.getHostname());
        return info;
    }
}