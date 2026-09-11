package com.data.datafusion.service;

import static com.data.datafusion.job.TaskConstants.TASK_STATUS_OFFLINE;
import static com.data.datafusion.job.TaskConstants.TASK_STATUS_ONLINE;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datafusion.domain.ETLEdge;
import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.domain.Job;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.ETLEdgeRepository;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.dto.*;
import com.data.datafusion.service.mapper.ETLEdgeMapper;
import com.data.datafusion.service.mapper.ETLNodeMapper;
import com.data.datafusion.service.mapper.ETLTaskMapper;
import com.data.job.ETLFlowTask;
import com.data.job.ExceptionUtils;
import com.data.job.TaskInstance;
import com.data.job.TaskLogger;
import com.data.metadata.util.DBUtils;
import jakarta.persistence.criteria.Predicate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.ETLTask}.
 */
@Service
@Transactional
public class ETLTaskService {

    private static final Logger LOG = LoggerFactory.getLogger(ETLTaskService.class);

    // 调试运行的整体超时时间（秒）
    private static final long DEBUG_TIMEOUT_SECONDS = 60;

    /**
     * 模型写入组件标识。
     * <p>该组件仅用于 ETL 设计器：配置界面选择数据模型，任务定义阶段基于模型绑定的
     * 数据源与表信息，转换为后端 {@code StreamJdbcOutput} 组件。
     */
    private static final String MODEL_WRITE_COMPONENT = "ModelWrite";

    /**
     * 模型写入组件转换后使用的后端组件名。
     */
    private static final String STREAM_JDBC_OUTPUT_COMPONENT = "StreamJdbcOutput";

    private final ETLTaskRepository eTLTaskRepository;

    private final ETLNodeRepository eTLNodeRepository;

    private final ETLEdgeRepository etlEdgeRepository;

    private final ETLTaskMapper eTLTaskMapper;

    private final ETLNodeMapper eTLNodeMapper;

    private final ETLEdgeMapper etlEdgeMapper;

    private final JobService jobService;

    private final DataSourceService dataSourceService;

    private final DataModelService dataModelService;

    public ETLTaskService(
        ETLTaskRepository eTLTaskRepository,
        ETLNodeRepository eTLNodeRepository,
        ETLEdgeRepository etlEdgeRepository,
        ETLTaskMapper eTLTaskMapper,
        ETLNodeMapper eTLNodeMapper,
        ETLEdgeMapper etlEdgeMapper,
        JobService jobService,
        DataSourceService dataSourceService,
        DataModelService dataModelService
    ) {
        this.eTLTaskRepository = eTLTaskRepository;
        this.eTLNodeRepository = eTLNodeRepository;
        this.etlEdgeRepository = etlEdgeRepository;
        this.eTLTaskMapper = eTLTaskMapper;
        this.eTLNodeMapper = eTLNodeMapper;
        this.etlEdgeMapper = etlEdgeMapper;
        this.jobService = jobService;
        this.dataSourceService = dataSourceService;
        this.dataModelService = dataModelService;
    }

    /**
     * Save a eTLTask.
     *
     * @param eTLTaskDTO the entity to save.
     * @return the persisted entity.
     */
    public ETLTaskDTO save(ETLTaskDTO eTLTaskDTO) {
        LOG.debug("Request to save ETLTask : {}", eTLTaskDTO);
        ETLTask eTLTask = eTLTaskMapper.toEntity(eTLTaskDTO);
        updateNodesAndEdges(eTLTaskDTO);
        Job job = saveETLJob(eTLTaskDTO);
        eTLTask.setJobId(job.getId());
        eTLTask = eTLTaskRepository.save(eTLTask);
        return eTLTaskMapper.toDto(eTLTask);
    }

    /**
     * Update a eTLTask.
     *
     * @param eTLTaskDTO the entity to save.
     * @return the persisted entity.
     */
    public ETLTaskDTO update(ETLTaskDTO eTLTaskDTO) {
        LOG.debug("Request to update ETLTask : {}", eTLTaskDTO);
        ETLTask eTLTask = eTLTaskMapper.toEntity(eTLTaskDTO);
        updateNodesAndEdges(eTLTaskDTO);
        Job job = saveETLJob(eTLTaskDTO);
        eTLTask.setJobId(job.getId());
        eTLTask = eTLTaskRepository.save(eTLTask);
        return eTLTaskMapper.toDto(eTLTask);
    }

    public void updateNodesAndEdges(ETLTaskDTO eTLTaskDTO) {
        List<ETLNode> etlNodeList = eTLNodeMapper.toEntity(eTLTaskDTO.getNodes());
        List<ETLEdge> etlEdgeList = etlEdgeMapper.toEntity(eTLTaskDTO.getEdges());
        eTLNodeRepository.deleteAllByTaskId(String.valueOf(eTLTaskDTO.getId()));
        etlEdgeRepository.deleteAllByTaskId(String.valueOf(eTLTaskDTO.getId()));
        for (ETLNode etlNode : etlNodeList) {
            etlNode.setId(null);
            etlNode.setTaskId(String.valueOf(eTLTaskDTO.getId()));
        }
        for (ETLEdge etlEdge : etlEdgeList) {
            etlEdge.setId(null);
            etlEdge.setTaskId(String.valueOf(eTLTaskDTO.getId()));
        }
        eTLNodeRepository.saveAll(etlNodeList);
        etlEdgeRepository.saveAll(etlEdgeList);
    }

    public Job saveETLJob(ETLTaskDTO eTLTaskDTO) {
        Job job = new Job();
        job.setId(eTLTaskDTO.getJobId());
        job.setJobName(eTLTaskDTO.getTaskName());
        job.setType(TaskConstants.TASK_TYPE_ETL);
        job.setCron(eTLTaskDTO.getCron());
        job.setStatus(eTLTaskDTO.getStatus());
        job.setJobContext(generateETLJobJson(eTLTaskDTO));
        job.setCreateTime(eTLTaskDTO.getCreateTime());
        job.setUpdateTime(ZonedDateTime.now());
        // 设置租户ID，防止更新时merge操作覆盖tenant_id为空
        if (eTLTaskDTO.getTenantId()!= null){
            job.setTenantId(String.valueOf(eTLTaskDTO.getTenantId()));
        }else if(SecurityUtils.getCurrentTenantId().isPresent()){
            job.setTenantId(String.valueOf(SecurityUtils.getCurrentTenantId()));
        }
        job = jobService.save(job);
        return job;
    }

    public String generateETLJobJson(ETLTaskDTO etlTaskDTO) {
        // 获取源和目标数据源
        List<ETLNode> etlNodeList = eTLNodeMapper.toEntity(etlTaskDTO.getNodes());
        List<ETLEdge> etlEdgeList = etlEdgeMapper.toEntity(etlTaskDTO.getEdges());
        if (etlNodeList == null) {
            return null;
        }

        Map<String, Object> etlJobJson = new LinkedHashMap<>();
        List<Map<String, Object>> units = new ArrayList<>();
        List<Map<String, Object>> connections = new ArrayList<>();
        for (ETLNode etlNode : etlNodeList) {
            Map<String, Object> unit = new LinkedHashMap<>();
            unit.put(".id", etlNode.getCode());
            unit.put(".name", etlNode.getType());
            JSONObject config = JSONUtil.parseObj(etlNode.getConfig());
            if (MODEL_WRITE_COMPONENT.equals(etlNode.getType())) {
                // 模型写入组件：基于模型绑定的数据源和表信息，转换为 StreamJdbcOutput 任务定义
                appendModelWriteUnit(unit, config);
                units.add(unit);
                continue;
            }
            for (String key : config.keySet()) {
                unit.put(key, config.get(key));
                if (key.equals("sourceId")) {
                    Map<String, Object> sourceId = new LinkedHashMap<>();
                    String dataSourceId = config.get(key).toString();
                    Optional<DataSourceDTO> sourceOptional = dataSourceService.findOne(Long.valueOf(dataSourceId));
                    if (sourceOptional.isEmpty()) {
                        throw new IllegalArgumentException("数据源不存在: " + dataSourceId);
                    }
                    DataSourceDTO source = sourceOptional.get();
                    sourceId.put("url", source.getUrl());
                    sourceId.put("driver", DBUtils.getDriverClassName(source.getUrl()));
                    sourceId.put("username", source.getUsername());
                    sourceId.put("password", source.getPassword());
                    sourceId.put("dbschema", config.get("schema") == null ? "" : config.get("schema").toString());
                    unit.put("sourceId", sourceId);
                }
            }
            units.add(unit);
        }
        for (ETLEdge etlEdge : etlEdgeList) {
            Map<String, Object> connection = new LinkedHashMap<>();
            connection.put("sourceId", etlEdge.getSource());
            connection.put("targetId", etlEdge.getTarget());
            connection.put("sourcePort", 0);
            connections.add(connection);
        }
        // 生成最终的 JSON 结构
        etlJobJson.put("units", units);
        etlJobJson.put("connections", connections);
        etlJobJson.put("version", "1.0.0");

        return JSONUtil.toJsonStr(etlJobJson);
    }

    /**
     * 将「模型写入」组件的配置转换为 {@code StreamJdbcOutput} 任务定义。
     * <p>转换规则：
     * <ul>
     *     <li>从数据模型读取其绑定的数据源（dataSourceId）、schema（schemaName）、物理表（tableName）；</li>
     *     <li>生成 {@code sourceId} 数据源连接信息、{@code schema}、{@code table} 等 StreamJdbcOutput 参数；</li>
     *     <li>写入策略、更新字段、批处理数等参数沿用组件配置。</li>
     * </ul>
     *
     * @param unit   待填充的任务单元定义
     * @param config 模型写入组件的配置
     */
    private void appendModelWriteUnit(Map<String, Object> unit, JSONObject config) {
        Object modelIdValue = config.get("modelId");
        if (modelIdValue == null || modelIdValue.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("模型写入组件未选择数据模型");
        }
        DataModelDTO dataModel;
        try {
            dataModel = dataModelService.findOne(Long.valueOf(modelIdValue.toString().trim())).orElse(null);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("数据模型ID不合法: " + modelIdValue);
        }
        if (dataModel == null) {
            throw new IllegalArgumentException("数据模型不存在: " + modelIdValue);
        }
        if (dataModel.getDataSourceId() == null) {
            throw new IllegalArgumentException("数据模型未绑定数据源: " + dataModel.getName());
        }
        DataSourceDTO source = dataSourceService
            .findOne(dataModel.getDataSourceId())
            .orElseThrow(() -> new IllegalArgumentException("数据模型绑定的数据源不存在: " + dataModel.getDataSourceId()));

        // 使用后端 StreamJdbcOutput 组件承载实际写入
        unit.put(".name", STREAM_JDBC_OUTPUT_COMPONENT);

        Map<String, Object> sourceId = new LinkedHashMap<>();
        sourceId.put("url", source.getUrl());
        sourceId.put("driver", DBUtils.getDriverClassName(source.getUrl()));
        sourceId.put("username", source.getUsername());
        sourceId.put("password", source.getPassword());
        sourceId.put("dbschema", dataModel.getSchemaName() == null ? "" : dataModel.getSchemaName());
        unit.put("sourceId", sourceId);

        unit.put("schema", dataModel.getSchemaName() == null ? "" : dataModel.getSchemaName());
        unit.put("table", dataModel.getTableName() == null ? "" : dataModel.getTableName());
        unit.put("model", config.getStr("model", "append"));
        if (config.get("updateColumn") != null) {
            unit.put("updateColumn", config.get("updateColumn"));
        }
        if (config.get("maxRows") != null) {
            unit.put("maxRows", config.get("maxRows"));
        }
    }

    /**
     * Partially update a eTLTask.
     *
     * @param eTLTaskDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ETLTaskDTO> partialUpdate(ETLTaskDTO eTLTaskDTO) {
        LOG.debug("Request to partially update ETLTask : {}", eTLTaskDTO);

        return eTLTaskRepository
            .findById(eTLTaskDTO.getId())
            .map(existingETLTask -> {
                eTLTaskMapper.partialUpdate(existingETLTask, eTLTaskDTO);

                return existingETLTask;
            })
            .map(eTLTaskRepository::save)
            .map(eTLTaskMapper::toDto);
    }

    /**
     * Get all the eTLTasks.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<ETLTaskDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all ETLTasks");
        return eTLTaskRepository.findAll(pageable).map(eTLTaskMapper::toDto);
    }

    /**
     * Get all the eTLTasks with an optional keyword search.
     *
     * @param pageable the pagination information.
     * @param search   the optional keyword used to filter by task name or description.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<ETLTaskDTO> findAll(Pageable pageable, String search) {
        LOG.debug("Request to get all ETLTasks with search: {}", search);
        return eTLTaskRepository.findAll(buildSearchSpecification(search), pageable).map(eTLTaskMapper::toDto);
    }

    private Specification<ETLTask> buildSearchSpecification(String search) {
        if (search == null || search.trim().isEmpty()) {
            return Specification.where(null);
        }
        String keyword = search.trim().toLowerCase();
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (String field : new String[] { "taskName", "taskDesc" }) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), "%" + keyword + "%"));
            }
            return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Get one eTLTask by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ETLTaskDTO> findOne(Long id) {
        LOG.debug("Request to get ETLTask : {}", id);
        Optional<List<ETLNodeDTO>> etlNodeList = eTLNodeRepository.findAllByTaskId(String.valueOf(id)).map(eTLNodeMapper::toDto);
        Optional<List<ETLEdgeDTO>> etlEdgeList = etlEdgeRepository.findAllByTaskId(String.valueOf(id)).map(etlEdgeMapper::toDto);
        Optional<ETLTaskDTO> etlTaskDto = eTLTaskRepository.findById(id).map(eTLTaskMapper::toDto);
        etlTaskDto.get().setNodes(etlNodeList.get());
        etlTaskDto.get().setEdges(etlEdgeList.get());
        return etlTaskDto;
    }

    /**
     * Delete the eTLTask by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete ETLTask : {}", id);
        eTLTaskRepository
            .findById(id)
            .ifPresent(eTLTask -> {
                if (eTLTask.getJobId() != null) {
                    jobService.delete(eTLTask.getJobId());
                }
                eTLNodeRepository.deleteAllByTaskId(String.valueOf(id));
                etlEdgeRepository.deleteAllByTaskId(String.valueOf(id));
            });
        eTLTaskRepository.deleteById(id);
    }

    /**
     * 将ETLTask设置为在线状态，重新生成job并加入调度
     *
     * @param id ETLTask的ID
     * @return 更新后的ETLTaskDTO
     */
    public ETLTaskDTO online(Long id) {
        LOG.debug("Request to online ETLTask : {}", id);
        return eTLTaskRepository
            .findById(id)
            .map(etlTask -> {
                // 更新状态为ONLINE
                etlTask.setStatus(TASK_STATUS_ONLINE);

                // 重新生成job并加入调度
                Job job = jobService.findOneJob(etlTask.getJobId()).get();
                job.setStatus(TASK_STATUS_ONLINE);
                jobService.save(job);
                jobService.online(job, false);

                // 保存更新后的ETLTask
                etlTask = eTLTaskRepository.save(etlTask);
                return eTLTaskMapper.toDto(etlTask);
            })
            .orElseThrow(() -> new RuntimeException("ETLTask not found with id: " + id));
    }

    /**
     * 将ETLTask设置为离线状态，取消任务调度
     *
     * @param id ETLTask的ID
     * @return 更新后的ETLTaskDTO
     */
    public ETLTaskDTO offline(Long id) {
        LOG.debug("Request to offline ETLTask : {}", id);
        return eTLTaskRepository
            .findById(id)
            .map(etlTask -> {
                // 更新状态为OFFLINE
                etlTask.setStatus(TASK_STATUS_OFFLINE);

                // 取消任务调度
                if (etlTask.getJobId() != null) {
                    Job job = jobService.findOneJob(etlTask.getJobId()).get();
                    jobService.offline(job);
                }

                // 保存更新后的ETLTask
                etlTask = eTLTaskRepository.save(etlTask);
                return eTLTaskMapper.toDto(etlTask);
            })
            .orElseThrow(() -> new RuntimeException("ETLTask not found with id: " + id));
    }

    /**
     * 立即执行ETL任务一次
     *
     * @param id ETLTask的ID
     */
    public void executeOnce(Long id) {
        LOG.debug("Request to executeOnce ETLTask : {}", id);
        eTLTaskRepository
            .findById(id)
            .map(etlTask -> {
                if (etlTask.getJobId() == null) {
                    throw new RuntimeException("ETLTask has no job, please save task first: " + id);
                }
                Job job = jobService
                    .findOneJob(etlTask.getJobId())
                    .orElseThrow(() -> new RuntimeException("Job not found with id: " + etlTask.getJobId()));
                jobService.executeOnce(job);
                return etlTask;
            })
            .orElseThrow(() -> new RuntimeException("ETLTask not found with id: " + id));
    }

    /**
     * 调试运行 ETL 任务。
     * <p>
     * 调试运行时：
     * <ul>
     *     <li>源组件最多读取 rowLimit 条数据；</li>
     *     <li>所有数据输出（sink）组件只读取数据、不写入目标库；</li>
     *     <li>不加载、也不保存增量同步状态；</li>
     *     <li>当指定 targetNodeId 时，只运行该节点及其上游依赖。</li>
     * </ul>
     * 返回各组件输出的采样数据，供设计器展示上游数据以便配置下游组件。
     *
     * @param request 调试请求（任务图、采样行数、目标节点）
     * @return 各节点采样结果
     */
    public ETLDebugResultDTO debug(ETLDebugDTO request) {
        ETLDebugResultDTO result = new ETLDebugResultDTO();
        long startTime = System.currentTimeMillis();
        TaskLogger taskLogger = null;
        try {
            if (request == null || request.getTask() == null) {
                throw new IllegalArgumentException("调试请求不能为空");
            }
            String jobJson = generateETLJobJson(request.getTask());
            if (jobJson == null) {
                throw new IllegalArgumentException("任务未配置任何节点，无法调试运行");
            }

            int rowLimit = request.getRowLimit() == null || request.getRowLimit() <= 0 ? 100 : request.getRowLimit();
            String instanceCode = "debug_" + System.currentTimeMillis();

            TaskInstance taskInstance = new TaskInstance();
            taskInstance.setJobCode("debug");
            taskInstance.setInstanceCode(instanceCode);
            taskInstance.setJobContext(jobJson);
            taskInstance.setType(TaskConstants.TASK_TYPE_ETL);

            taskLogger = new TaskLogger("debug", instanceCode);
            ETLFlowTask flowTask = new ETLFlowTask(taskInstance, taskLogger);
            flowTask.getContext().setDebugMode(true);
            flowTask.getContext().setDebugRowLimit(rowLimit);
            flowTask.getContext().setDebugTargetNodeId(
                request.getTargetNodeId() == null || request.getTargetNodeId().trim().isEmpty() ? null : request.getTargetNodeId()
            );

            // 调试运行整体超时保护，避免异常源组件导致请求线程长期阻塞
            ExecutorService debugExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "etl-debug-" + instanceCode);
                thread.setDaemon(true);
                return thread;
            });
            try {
                Future<?> future = debugExecutor.submit(() -> {
                    try {
                        flowTask.runJob(jobJson);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                future.get(DEBUG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                result.setNodes(flowTask.getContext().getDebugResults());
            } catch (TimeoutException e) {
                flowTask.cancel();
                result.setError("调试运行超时（" + DEBUG_TIMEOUT_SECONDS + " 秒），已中止");
            } catch (ExecutionException e) {
                throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
            } finally {
                debugExecutor.shutdownNow();
            }
        } catch (Exception e) {
            LOG.error("ETL 调试运行失败", e);
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = ExceptionUtils.describe(e);
            }
            result.setError(message);
        } finally {
            if (taskLogger != null) {
                try {
                    taskLogger.closeLogFile();
                } catch (Exception ignore) {
                    // ignore close failure
                }
            }
            result.setElapsedMs(System.currentTimeMillis() - startTime);
        }
        return result;
    }
}