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
import com.data.metadata.util.DBUtils;
import com.data.job.DatasourceInfo;
import com.data.metadata.DatabaseConverter;
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
    public Optional<DataSyncDTO> findOne(Long id) {
        LOG.debug("Request to get DataSync : {}", id);
        Optional<DataSyncDTO> dataSyncDTO = dataSyncRepository.findById(id).map(dataSyncMapper::toDto);
        if (dataSyncDTO.isPresent()) {
            List<DataSyncTableConfig> dataSyncTableConfigs = dataSyncTableConfigRepository.findAllBySyncTask(id.toString());
            dataSyncDTO.get().setSelectedTables(dataSyncTableConfigMapper.toDto(dataSyncTableConfigs));
        }
        return dataSyncDTO;
    }

    /**
     * Delete the dataSync by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete DataSync : {}", id);
        Optional<DataSyncDTO> dataSyncDTO = findOne(id);
        if (dataSyncDTO.isPresent()) {
            dataSyncRepository.deleteById(id);
            dataSyncTableConfigRepository.deleteAllBySyncTask(id.toString());
            String jobCode = dataSyncDTO.get().getJobCode();
            if (jobCode != null && !jobCode.isEmpty()) {
                jobService.delete(Long.valueOf(jobCode));
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

    public void executeDataSyncNow(Long id) {
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
                jobService.findOneJob(Long.valueOf(jobCode)).ifPresent(jobService::executeOnce);
            }
        }
    }

    public void syncTablesDDL(DataSyncDTO dataSyncDTO) throws SQLException {
        Optional<DataSourceDTO> source = dataSourceService.findOne(Long.valueOf(dataSyncDTO.getSource()));

        Optional<DataSourceDTO> target = dataSourceService.findOne(Long.valueOf(dataSyncDTO.getTarget()));
        if (source.isPresent() && target.isPresent()) {
            DatasourceInfo sourceInfo = new DatasourceInfo();
            sourceInfo.setUrl(source.get().getUrl());
            sourceInfo.setUsername(source.get().getUsername());
            sourceInfo.setPassword(source.get().getPassword());
            sourceInfo.setDbschema(source.get().getSchemaName());
            DatasourceInfo targetInfo = new DatasourceInfo();
            targetInfo.setUrl(target.get().getUrl());
            targetInfo.setUsername(target.get().getUsername());
            targetInfo.setPassword(target.get().getPassword());
            targetInfo.setDbschema(target.get().getSchemaName());
            try (Connection sConnection = DBUtils.getConnection(sourceInfo); Connection tConnection = DBUtils.getConnection(targetInfo)) {
                List<DataSyncTableConfigDTO> selectedTables = dataSyncDTO.getSelectedTables();
                for (DataSyncTableConfigDTO tableConfigDTO : selectedTables) {
                    TableMeta srcTable = DBUtils.getTableMetaData(
                        sConnection,
                        tableConfigDTO.getSrcSchemaName(),
                        tableConfigDTO.getSrcTableName()
                    );
                    if (!DBUtils.tableExists(tConnection, tableConfigDTO.getDesSchemaName(), tableConfigDTO.getDesTableName())) {
                        // 转换并创建目标表
                        TableMeta targetTable = DatabaseConverter.convert(
                            DBUtils.getDBType(source.get().getUrl()),
                            DBUtils.getDBType(target.get().getUrl()),
                            srcTable
                        );
                        targetTable.setTable(tableConfigDTO.getDesTableName());
                        targetTable.setSchema(tableConfigDTO.getDesSchemaName());
                        String ddl = DatabaseConverter.generateTableDDL(DBUtils.getDBType(target.get().getUrl()), targetTable);
                        DBUtils.execute(tConnection, ddl);
                    }
                }
            }
        }
    }

    public Job saveETLJob(DataSyncDTO dataSyncDTO) {
        Job job = new Job();
        if (dataSyncDTO.getJobCode() != null) {
            job.setId(Long.valueOf(dataSyncDTO.getJobCode()));
        }
        job.setJobName(dataSyncDTO.getJobName());
        job.setType(TaskConstants.TASK_TYPE_ETL);
        job.setStatus(dataSyncDTO.getStatus());
        job.setCron(dataSyncDTO.getCron());
        job.setUpdateTime(ZonedDateTime.now());
        job.setCreateTime(ZonedDateTime.now());
        job.setJobContext(generateETLJobJson(dataSyncDTO));
        // 设置租户ID，防止更新时merge操作覆盖tenant_id为空
        if (dataSyncDTO.getTenantId()!= null){
            job.setTenantId(String.valueOf(dataSyncDTO.getTenantId()));
        }else if(SecurityUtils.getCurrentTenantId().isPresent()){
            job.setTenantId(String.valueOf(SecurityUtils.getCurrentTenantId()));
        }return jobService.save(job);
    }

    public String generateETLJobJson(DataSyncDTO dataSyncDTO) {
        // 获取源和目标数据源
        Optional<DataSourceDTO> sourceOptional = dataSourceService.findOne(Long.valueOf(dataSyncDTO.getSource()));
        Optional<DataSourceDTO> targetOptional = dataSourceService.findOne(Long.valueOf(dataSyncDTO.getTarget()));

        DataSourceDTO source = sourceOptional.orElse(null);
        DataSourceDTO target = targetOptional.orElse(null);

        Map<String, Object> etlJobJson = new LinkedHashMap<>();
        List<Map<String, Object>> units = new ArrayList<>();
        List<Map<String, Object>> connections = new ArrayList<>();

        List<DataSyncTableConfigDTO> selectedTables = dataSyncDTO.getSelectedTables();

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
            sourceIdInput.put("dbschema", selectedTables.get(0).getSrcSchemaName());
            jdbcInput.put("sourceId", sourceIdInput);
        }

        StringBuilder tableList = new StringBuilder();
        JSONObject incrColumn = new JSONObject();
        boolean overwrite = "FULL_SYNC".equals(dataSyncDTO.getType()) || "DATA_ONLY".equals(dataSyncDTO.getType());
        for (DataSyncTableConfigDTO tableConfigDTO : selectedTables) {
            tableList.append(tableConfigDTO.getSrcTableName()).append(",");
            // OVERWRITE 模式下不使用增量列，避免「增量输入 + OVERWRITE 输出」导致的覆盖丢失
            if (!overwrite) {
                incrColumn.put(tableConfigDTO.getSrcTableName(), tableConfigDTO.getSrcColPks());
            }
        }
        jdbcInput.put("table", tableList.substring(0, tableList.length() - 1)); // 假设 DataSyncDTO 有 getSrcTableName 方法
        jdbcInput.put("incrColumn", incrColumn);
        jdbcInput.put("maxRowsPerPartition", "10000");
        jdbcInput.put("where", ""); // 假设 DataSyncDTO 有 getWhereClause 方法
        units.add(jdbcInput);

        // 生成 JdbcOutput 组件
        Map<String, Object> jdbcOutput = new LinkedHashMap<>();
        jdbcOutput.put(".id", RandomUtil.randomString(8));
        jdbcOutput.put(".name", "StreamJdbcOutput");

        if (target != null) {
            Map<String, Object> sourceIdOutput = new LinkedHashMap<>();
            sourceIdOutput.put("url", target.getUrl());
            sourceIdOutput.put("driver", DBUtils.getDriverClassName(target.getUrl()));
            sourceIdOutput.put("username", target.getUsername());
            sourceIdOutput.put("password", target.getPassword());
            sourceIdOutput.put("dbschema", selectedTables.get(0).getDesSchemaName());
            jdbcOutput.put("sourceId", sourceIdOutput);
        }
        jdbcOutput.put("table", ""); // 假设 DataSyncDTO 有 getDesTableName 方法
        if (overwrite) {
            jdbcOutput.put("model", "overwrite");
        }
        units.add(jdbcOutput);

        // 生成连接信息
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("sourceId", jdbcInput.get(".id"));
        connection.put("sourcePort", 0);
        connection.put("targetId", jdbcOutput.get(".id"));

        connections.add(connection);
        // 生成最终的 JSON 结构
        etlJobJson.put("units", units);
        etlJobJson.put("connections", connections);
        etlJobJson.put("version", "1.0.0");

        return JSONUtil.toJsonStr(etlJobJson);
    }
}