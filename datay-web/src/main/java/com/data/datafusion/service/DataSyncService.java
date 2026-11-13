package com.data.datafusion.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.DataSync;
import com.data.datafusion.domain.DataSyncTableConfig;
import com.data.datafusion.domain.Job;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.DataSyncRepository;
import com.data.datafusion.repository.DataSyncTableConfigRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.DataSyncDTO;
import com.data.datafusion.service.dto.DataSyncTableConfigDTO;
import com.data.datafusion.service.mapper.DataSyncMapper;
import com.data.datafusion.service.mapper.DataSyncTableConfigMapper;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.metadata.util.DBUtils;
import com.data.job.DatasourceInfo;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.DataSync}.
 */
@Service
@Transactional
public class DataSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncService.class);

    private final DataSyncRepository dataSyncRepository;
    private final DataSourceService dataSourceService;

    private final DataSyncTableConfigMapper dataSyncTableConfigMapper;
    private final DataSyncMapper dataSyncMapper;

    private final DataSyncTableConfigRepository dataSyncTableConfigRepository;
    private final JobService jobService;

    public DataSyncService(
        DataSyncRepository dataSyncRepository,
        DataSourceService dataSourceService,
        DataSyncTableConfigMapper dataSyncTableConfigMapper,
        DataSyncMapper dataSyncMapper,
        DataSyncTableConfigRepository dataSyncTableConfigRepository,
        JobService jobService
    ) {
        this.dataSyncRepository = dataSyncRepository;
        this.dataSourceService = dataSourceService;
        this.dataSyncTableConfigMapper = dataSyncTableConfigMapper;
        this.dataSyncMapper = dataSyncMapper;
        this.dataSyncTableConfigRepository = dataSyncTableConfigRepository;
        this.jobService = jobService;
    }

    /**
     * Save a dataSync.
     *
     * @param dataSyncDTO the entity to save.
     * @return the persisted entity.
     */
    public DataSyncDTO save(DataSyncDTO dataSyncDTO) {
        LOG.debug("Request to save DataSync : {}", dataSyncDTO);
        DataSync dataSync = dataSyncMapper.toEntity(dataSyncDTO);
        dataSync = dataSyncRepository.save(dataSync);
        List<DataSyncTableConfigDTO> selectedTables = dataSyncDTO.getSelectedTables();
        if (selectedTables != null && !selectedTables.isEmpty()) {
            for (DataSyncTableConfigDTO tableConfigDTO : selectedTables) {
                tableConfigDTO.setSyncTask(dataSync.getId().toString());
            }
            List<DataSyncTableConfig> dataSyncTableConfigs = dataSyncTableConfigMapper.toEntity(selectedTables);
            dataSyncTableConfigRepository.saveAll(dataSyncTableConfigs);
        }
        DataSyncDTO newDataSyncDTO = dataSyncMapper.toDto(dataSync);
        newDataSyncDTO.setSelectedTables(selectedTables);
        return newDataSyncDTO;
    }

    /**
     * Update a dataSync.
     *
     * @param dataSyncDTO the entity to save.
     * @return the persisted entity.
     */
    public DataSyncDTO update(DataSyncDTO dataSyncDTO) {
        LOG.debug("Request to update DataSync : {}", dataSyncDTO);
        DataSync dataSync = dataSyncMapper.toEntity(dataSyncDTO);
        dataSync = dataSyncRepository.save(dataSync);
        dataSyncTableConfigRepository.deleteAllBySyncTask(dataSync.getId().toString());
        List<DataSyncTableConfigDTO> selectedTables = dataSyncDTO.getSelectedTables();
        if (selectedTables != null && !selectedTables.isEmpty()) {
            for (DataSyncTableConfigDTO tableConfigDTO : selectedTables) {
                tableConfigDTO.setSyncTask(dataSync.getId().toString());
            }
            List<DataSyncTableConfig> dataSyncTableConfigs = dataSyncTableConfigMapper.toEntity(selectedTables);
            dataSyncTableConfigRepository.saveAll(dataSyncTableConfigs);
        }
        DataSyncDTO newDataSyncDTO = dataSyncMapper.toDto(dataSync);
        newDataSyncDTO.setSelectedTables(selectedTables);
        return newDataSyncDTO;
    }

    /**
     * Partially update a dataSync.
     *
     * @param dataSyncDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<DataSyncDTO> partialUpdate(DataSyncDTO dataSyncDTO) {
        LOG.debug("Request to partially update DataSync : {}", dataSyncDTO);

        return dataSyncRepository
            .findById(dataSyncDTO.getId())
            .map(existingDataSync -> {
                dataSyncMapper.partialUpdate(existingDataSync, dataSyncDTO);

                return existingDataSync;
            })
            .map(dataSyncRepository::save)
            .map(dataSyncMapper::toDto);
    }

    /**
     * Get all the dataSyncs.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<DataSyncDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all DataSyncs");
        return dataSyncRepository.findAll(pageable).map(dataSyncMapper::toDto);
    }

    /**
     * Get one dataSync by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<DataSyncDTO> findOne(String id) {
        LOG.debug("Request to get DataSync : {}", id);
        Optional<DataSyncDTO> dataSyncDTO = dataSyncRepository.findById(id).map(dataSyncMapper::toDto);
        if (dataSyncDTO.isPresent()) {
            List<DataSyncTableConfig> dataSyncTableConfigs = dataSyncTableConfigRepository.findAllBySyncTask(id);
            dataSyncDTO.get().setSelectedTables(dataSyncTableConfigMapper.toDto(dataSyncTableConfigs));
        }
        return dataSyncDTO;
    }

    /**
     * Delete the dataSync by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete DataSync : {}", id);
        Optional<DataSyncDTO> dataSyncDTO = findOne(id);
        if (dataSyncDTO.isPresent()) {
            dataSyncRepository.deleteById(id);
            dataSyncTableConfigRepository.deleteAllBySyncTask(id);
            String jobCode = dataSyncDTO.get().getJobCode();
            if (jobCode != null && !jobCode.isEmpty()) {
                jobService.delete(jobCode);
            }
        }
    }

    public void createDataSyncTask(DataSyncDTO dataSyncDTO) {
        try {
            if (!("DATA_ONLY").equals(dataSyncDTO.getType())) {
                syncTablesDDL(dataSyncDTO);
            }
            if (!("SCHEMA_ONLY").equals(dataSyncDTO.getType())) {
                Job job = saveETLJob(dataSyncDTO);
                dataSyncDTO.setJobCode(job.getId().toString());
            }
            dataSyncRepository.save(dataSyncMapper.toEntity(dataSyncDTO));
        } catch (SQLException e) {
            dataSyncDTO.setStatus("FAILED");
            dataSyncRepository.save(dataSyncMapper.toEntity(dataSyncDTO));
            throw new RuntimeException(e);
        }
    }

    public void executeDataSyncNow(String id) {
        Optional<DataSyncDTO> dataSyncOpt = findOne(id);
        if (!dataSyncOpt.isPresent()) {
            throw new RuntimeException("DataSync not found: " + id);
        }
        DataSyncDTO dataSyncDTO = dataSyncOpt.get();
        if ("SCHEMA_ONLY".equals(dataSyncDTO.getType())) {
            try {
                syncTablesDDL(dataSyncDTO);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            String jobCode = dataSyncDTO.getJobCode();
            if (jobCode != null && !jobCode.isEmpty()) {
                jobService.findOneJob(jobCode).ifPresent(jobService::executeOnce);
            }
        }
    }

    public void syncTablesDDL(DataSyncDTO dataSyncDTO) throws SQLException {
        Optional<DataSourceDTO> source = dataSourceService.findOne(dataSyncDTO.getSource());

        Optional<DataSourceDTO> target = dataSourceService.findOne(dataSyncDTO.getTarget());
        if (source.isPresent() && target.isPresent()) {
            if (!isJdbcDatasource(target.get())) {
                // Elasticsearch 等非 JDBC 目标无建表概念，跳过 DDL，写入时按需创建索引
                LOG.debug("目标数据源 {} 为非 JDBC 类型，跳过建表 DDL", target.get().getType());
                return;
            }
            DatasourceInfo sourceInfo = DataSourceQueryService.toDatasourceInfo(source.get());
            DatasourceInfo targetInfo = DataSourceQueryService.toDatasourceInfo(target.get());
            boolean quackTarget = targetInfo.getUrl() != null && targetInfo.getUrl().startsWith("quack:");
            try (Connection sConnection = DBUtils.getConnection(sourceInfo); Connection tConnection = DBUtils.getConnection(targetInfo)) {
                List<DataSyncTableConfigDTO> selectedTables = dataSyncDTO.getSelectedTables();
                if (selectedTables == null || selectedTables.stream().noneMatch(table -> hasText(table.getSrcTableName()))) {
                    // 未选择表时按全库同步处理：创建源 schema 下所有表的目标表结构
                    String srcSchema = resolveSourceSchema(dataSyncDTO, source.get());
                    String desSchema = resolveTargetSchema(dataSyncDTO, target.get());
                    for (TableMeta tableMeta : DBUtils.getTableList(sConnection, srcSchema)) {
                        String tableName = tableMeta.getTable();
                        if (!hasText(tableName) || tableName.endsWith("_backup") || tableName.endsWith("_tmp")) {
                            continue;
                        }
                        String tableSchema = hasText(tableMeta.getSchema()) ? tableMeta.getSchema() : srcSchema;
                        createTargetTable(
                            sConnection,
                            tConnection,
                            source.get(),
                            target.get(),
                            tableSchema,
                            tableName,
                            desSchema,
                            tableName,
                            quackTarget
                        );
                    }
                    return;
                }
                for (DataSyncTableConfigDTO tableConfigDTO : selectedTables) {
                    if (!hasText(tableConfigDTO.getSrcTableName())) {
                        continue;
                    }
                    createTargetTable(
                        sConnection,
                        tConnection,
                        source.get(),
                        target.get(),
                        tableConfigDTO.getSrcSchemaName(),
                        tableConfigDTO.getSrcTableName(),
                        tableConfigDTO.getDesSchemaName(),
                        tableConfigDTO.getDesTableName(),
                        quackTarget
                    );
                }
            }
        }
    }

    private void createTargetTable(
        Connection sConnection,
        Connection tConnection,
        DataSourceDTO source,
        DataSourceDTO target,
        String srcSchema,
        String srcTableName,
        String desSchema,
        String desTableName,
        boolean quackTarget
    ) throws SQLException {
        String targetSchema = hasText(desSchema) ? desSchema : target.getSchemaName();
        String targetTableName = hasText(desTableName) ? desTableName : srcTableName;
        if (DBUtils.tableExists(tConnection, targetSchema, targetTableName)) {
            return;
        }
        String sourceSchema = hasText(srcSchema) ? srcSchema : source.getSchemaName();
        TableMeta srcTable = DBUtils.getTableMetaData(sConnection, sourceSchema, srcTableName);
        String targetDbType = DBUtils.getDBType(target.getType(), target.getUrl());
        // 转换并创建目标表
        TableMeta targetTable = DatabaseConverter.convert(
            DBUtils.getDBType(source.getType(), source.getUrl()),
            targetDbType,
            srcTable
        );
        targetTable.setTable(targetTableName);
        targetTable.setSchema(targetSchema);
        String targetCatalog = resolveTargetCatalog(targetDbType, quackTarget);
        if (targetCatalog != null) {
            targetTable.setCatalog(targetCatalog);
        }
        String ddl = DatabaseConverter.generateTableDDL(targetDbType, targetTable);
        DBUtils.execute(tConnection, ddl);
    }

    /**
     * 解析同步建表时目标表需要携带的 catalog。
     *
     * <p>Quack 与 DuckLake 连接底层都是本地 DuckDB 库，只有显式指定 catalog 才能把表建到远端，
     * 否则会落到本地 scratch 库。</p>
     */
    static String resolveTargetCatalog(String targetDbType, boolean quackTarget) {
        if (quackTarget) {
            return DBUtils.QUACK_CATALOG;
        }
        if (DBType.DUCKLAKE.name().equalsIgnoreCase(targetDbType)) {
            return DBUtils.DUCKLAKE_CATALOG;
        }
        return null;
    }

    /**
     * 解析源端 schema：优先取表配置中的 schema，未选表时回退到数据源配置的 schema。
     */
    private static String resolveSourceSchema(DataSyncDTO dataSyncDTO, DataSourceDTO source) {
        if (dataSyncDTO.getSelectedTables() != null) {
            return dataSyncDTO
                .getSelectedTables()
                .stream()
                .map(DataSyncTableConfigDTO::getSrcSchemaName)
                .filter(DataSyncService::hasText)
                .findFirst()
                .orElseGet(() -> source == null ? null : source.getSchemaName());
        }
        return source == null ? null : source.getSchemaName();
    }

    /**
     * 解析目标端 schema：优先取表配置中的 schema，未选表时回退到数据源配置的 schema。
     */
    private static String resolveTargetSchema(DataSyncDTO dataSyncDTO, DataSourceDTO target) {
        if (dataSyncDTO.getSelectedTables() != null) {
            return dataSyncDTO
                .getSelectedTables()
                .stream()
                .map(DataSyncTableConfigDTO::getDesSchemaName)
                .filter(DataSyncService::hasText)
                .findFirst()
                .orElseGet(() -> target == null ? null : target.getSchemaName());
        }
        return target == null ? null : target.getSchemaName();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 是否为 JDBC 数据源。缺省（无类型）按 JDBC 处理，保持历史行为。
     */
    private static boolean isJdbcDatasource(DataSourceDTO dto) {
        if (dto == null || dto.getType() == null || dto.getType().isBlank()) {
            return true;
        }
        return DataSourceProviderFactory.provider(dto.getType()) instanceof JdbcDataSourceProvider;
    }

    public Job saveETLJob(DataSyncDTO dataSyncDTO) {
        Job job = new Job();
        if (dataSyncDTO.getJobCode() != null) {
            job.setId(dataSyncDTO.getJobCode());
        }
        job.setJobName(dataSyncDTO.getJobName());
        job.setType(TaskConstants.TASK_TYPE_ETL);
        job.setStatus(dataSyncDTO.getStatus());
        job.setCron(dataSyncDTO.getCron());
        job.setUpdateTime(ZonedDateTime.now());
        job.setCreateTime(ZonedDateTime.now());
        job.setJobContext(generateETLJobJson(dataSyncDTO));
        // 设置租户ID，防止更新时merge操作覆盖tenant_id为空
        String tenantId = dataSyncDTO.getTenantId();
        job.setTenantId(
            tenantId != null && !tenantId.isBlank() ? tenantId : SecurityUtils.getCurrentTenantIdString().orElse(null)
        );
        return jobService.save(job);
    }

    public String generateETLJobJson(DataSyncDTO dataSyncDTO) {
        // 获取源和目标数据源
        Optional<DataSourceDTO> sourceOptional = dataSourceService.findOne(dataSyncDTO.getSource());
        Optional<DataSourceDTO> targetOptional = dataSourceService.findOne(dataSyncDTO.getTarget());

        DataSourceDTO source = sourceOptional.orElse(null);
        DataSourceDTO target = targetOptional.orElse(null);

        Map<String, Object> etlJobJson = new LinkedHashMap<>();
        List<Map<String, Object>> units = new ArrayList<>();
        List<Map<String, Object>> connections = new ArrayList<>();

        List<DataSyncTableConfigDTO> selectedTables = dataSyncDTO.getSelectedTables() == null
            ? Collections.emptyList()
            : dataSyncDTO.getSelectedTables();
        String srcSchema = resolveSourceSchema(dataSyncDTO, source);
        String desSchema = resolveTargetSchema(dataSyncDTO, target);

        // 生成 JdbcInput 组件
        Map<String, Object> jdbcInput = new LinkedHashMap<>();
        jdbcInput.put(".id", RandomUtil.randomString(8));
        jdbcInput.put(".name", "StreamJdbcInput");

        if (source != null) {
            Map<String, Object> sourceIdInput = new LinkedHashMap<>();
            sourceIdInput.put("url", source.getUrl());
            sourceIdInput.put("driver", DBUtils.getDriverClassName(source.getUrl()));
            sourceIdInput.put("username", source.getUsername());
            sourceIdInput.put("password", source.getPassword());
            sourceIdInput.put("dbschema", srcSchema);
            sourceIdInput.put("type", source.getType());
            sourceIdInput.put("hostname", source.getHostname());
            sourceIdInput.put("port", source.getPort());
            sourceIdInput.put("extraParams", source.getExtraParams());
            jdbcInput.put("sourceId", sourceIdInput);
        }

        StringBuilder tableList = new StringBuilder();
        JSONObject incrColumn = new JSONObject();
        Map<String, String> tableMapping = new LinkedHashMap<>();
        boolean overwrite = "FULL_SYNC".equals(dataSyncDTO.getType()) || "DATA_ONLY".equals(dataSyncDTO.getType());
        for (DataSyncTableConfigDTO tableConfigDTO : selectedTables) {
            if (!hasText(tableConfigDTO.getSrcTableName())) {
                continue;
            }
            tableList.append(tableConfigDTO.getSrcTableName()).append(",");
            // 目标表重命名：仅登记确实发生变化的映射，未配置时沿用源表名
            if (
                hasText(tableConfigDTO.getDesTableName()) &&
                !tableConfigDTO.getSrcTableName().equals(tableConfigDTO.getDesTableName())
            ) {
                tableMapping.put(tableConfigDTO.getSrcTableName(), tableConfigDTO.getDesTableName());
            }
            // OVERWRITE 模式下不使用增量列，避免「增量输入 + OVERWRITE 输出」导致的覆盖丢失
            if (!overwrite) {
                incrColumn.put(tableConfigDTO.getSrcTableName(), tableConfigDTO.getSrcColPks());
            }
        }
        // 未选择表时 table 为空，StreamJdbcInput 会自动同步 schema 下的所有表
        jdbcInput.put("schema", srcSchema);
        jdbcInput.put("table", tableList.length() > 0 ? tableList.substring(0, tableList.length() - 1) : "");
        jdbcInput.put("incrColumn", incrColumn);
        jdbcInput.put("maxRowsPerPartition", "10000");
        jdbcInput.put("where", ""); // 假设 DataSyncDTO 有 getWhereClause 方法
        units.add(jdbcInput);

        // 生成输出组件：JDBC 目标用 StreamJdbcOutput，非 JDBC 目标（如 Elasticsearch）用 ElasticsearchOutput
        boolean jdbcTarget = isJdbcDatasource(target);
        Map<String, Object> outputNode = new LinkedHashMap<>();
        outputNode.put(".id", RandomUtil.randomString(8));
        outputNode.put(".name", jdbcTarget ? "StreamJdbcOutput" : "ElasticsearchOutput");

        if (target != null) {
            Map<String, Object> sourceIdOutput = new LinkedHashMap<>();
            sourceIdOutput.put("url", target.getUrl());
            sourceIdOutput.put("driver", DBUtils.getDriverClassName(target.getUrl()));
            sourceIdOutput.put("username", target.getUsername());
            sourceIdOutput.put("password", target.getPassword());
            sourceIdOutput.put("dbschema", desSchema);
            sourceIdOutput.put("type", target.getType());
            sourceIdOutput.put("hostname", target.getHostname());
            sourceIdOutput.put("port", target.getPort());
            sourceIdOutput.put("extraParams", target.getExtraParams());
            outputNode.put("sourceId", sourceIdOutput);
        }

        DataSyncTableConfigDTO firstTable = selectedTables.isEmpty() ? null : selectedTables.get(0);
        if (jdbcTarget) {
            outputNode.put("schema", desSchema);
            outputNode.put("table", ""); // 目标表由上游 FlowFile 表名属性决定
            if (!tableMapping.isEmpty()) {
                // 目标表重命名映射：StreamJdbcOutput 按上游表名查表，未命中时沿用原表名
                outputNode.put("tableMapping", tableMapping);
            }
            if (overwrite) {
                outputNode.put("model", "overwrite");
            }
        } else {
            // 非 JDBC 目标：显式配置的目标表名作为索引名，缺省回退到上游表名
            outputNode.put(
                "table",
                firstTable == null || firstTable.getDesTableName() == null ? "" : firstTable.getDesTableName()
            );
            outputNode.put("model", overwrite ? "overwrite" : "append");
            if (firstTable != null && firstTable.getSrcColPks() != null && !firstTable.getSrcColPks().isBlank()) {
                outputNode.put("idField", firstTable.getSrcColPks().split(",")[0].trim());
            }
        }
        units.add(outputNode);

        // 生成连接信息
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("sourceId", jdbcInput.get(".id"));
        connection.put("sourcePort", 0);
        connection.put("targetId", outputNode.get(".id"));

        connections.add(connection);
        // 生成最终的 JSON 结构
        etlJobJson.put("units", units);
        etlJobJson.put("connections", connections);
        etlJobJson.put("version", "1.0.0");

        return JSONUtil.toJsonStr(etlJobJson);
    }
}