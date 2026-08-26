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
import com.data.datafusion.service.dto.*;
import com.data.datafusion.service.mapper.ETLEdgeMapper;
import com.data.datafusion.service.mapper.ETLNodeMapper;
import com.data.datafusion.service.mapper.ETLTaskMapper;
import com.data.metadata.util.DBUtils;
import java.time.ZonedDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.ETLTask}.
 */
@Service
@Transactional
public class ETLTaskService {

    private static final Logger LOG = LoggerFactory.getLogger(ETLTaskService.class);

    private final ETLTaskRepository eTLTaskRepository;

    private final ETLNodeRepository eTLNodeRepository;

    private final ETLEdgeRepository etlEdgeRepository;

    private final ETLTaskMapper eTLTaskMapper;

    private final ETLNodeMapper eTLNodeMapper;

    private final ETLEdgeMapper etlEdgeMapper;

    private final JobService jobService;

    private final DataSourceService dataSourceService;

    public ETLTaskService(
        ETLTaskRepository eTLTaskRepository,
        ETLNodeRepository eTLNodeRepository,
        ETLEdgeRepository etlEdgeRepository,
        ETLTaskMapper eTLTaskMapper,
        ETLNodeMapper eTLNodeMapper,
        ETLEdgeMapper etlEdgeMapper,
        JobService jobService,
        DataSourceService dataSourceService
    ) {
        this.eTLTaskRepository = eTLTaskRepository;
        this.eTLNodeRepository = eTLNodeRepository;
        this.etlEdgeRepository = etlEdgeRepository;
        this.eTLTaskMapper = eTLTaskMapper;
        this.eTLNodeMapper = eTLNodeMapper;
        this.etlEdgeMapper = etlEdgeMapper;
        this.jobService = jobService;
        this.dataSourceService = dataSourceService;
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
            for (String key : config.keySet()) {
                unit.put(key, config.get(key));
                if (key.equals("sourceId")) {
                    Map<String, Object> sourceId = new LinkedHashMap<>();
                    String dataSourceId = config.get(key).toString();
                    Optional<DataSourceDTO> sourceOptional = dataSourceService.findOne(Long.valueOf(dataSourceId));
                    DataSourceDTO source = sourceOptional.get();
                    sourceId.put("url", source.getUrl());
                    sourceId.put("driver", DBUtils.getDriverClassName(source.getUrl()));
                    sourceId.put("username", source.getUsername());
                    sourceId.put("password", source.getPassword());
                    sourceId.put("dbschema", config.get("schema").toString());
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
}
