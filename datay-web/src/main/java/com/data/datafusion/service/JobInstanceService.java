package com.data.datafusion.service;

import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobDepend;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.job.dag.Flow;
import com.data.datafusion.job.dag.FlowInstance;
import com.data.datafusion.job.sql.SqlTaskContextAssembler;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.JobInstanceDTO;
import com.data.datafusion.service.jobevent.EventServiceFactory;
import com.data.datafusion.service.jobevent.JobStatusEvent;
import com.data.datafusion.service.mapper.JobInstanceMapper;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.JobInstance}.
 */
@Service
@Transactional
public class JobInstanceService {

    private static final Logger LOG = LoggerFactory.getLogger(JobInstanceService.class);

    public static ConcurrentHashMap<String, JobInstance> waitingJob = new ConcurrentHashMap<String, JobInstance>();

    private final JobInstanceRepository jobInstanceRepository;

    @Autowired
    JobDependService jobDependService;

    private final JobRepository jobRepository;

    private final JobInstanceMapper jobInstanceMapper;

    public JobInstanceService(
        JobInstanceRepository jobInstanceRepository,
        JobRepository jobRepository,
        JobInstanceMapper jobInstanceMapper
    ) {
        this.jobInstanceRepository = jobInstanceRepository;
        this.jobRepository = jobRepository;
        this.jobInstanceMapper = jobInstanceMapper;
    }


    /**
     * Save a jobInstance.
     *
     * @param jobInstanceDTO the entity to save.
     * @return the persisted entity.
     */
    public JobInstanceDTO save(JobInstanceDTO jobInstanceDTO) {
        LOG.debug("Request to save JobInstance : {}", jobInstanceDTO);
        JobInstance jobInstance = jobInstanceMapper.toEntity(jobInstanceDTO);
        normalizeScheduling(jobInstance);
        jobInstance = jobInstanceRepository.save(jobInstance);
        return jobInstanceMapper.toDto(jobInstance);
    }

    public JobInstance save(JobInstance jobInstance) {
        normalizeScheduling(jobInstance);
        jobInstance = jobInstanceRepository.save(jobInstance);
        return jobInstance;
    }

    /**
     * Update a jobInstance.
     *
     * @param jobInstanceDTO the entity to save.
     * @return the persisted entity.
     */
    public JobInstanceDTO update(JobInstanceDTO jobInstanceDTO) {
        LOG.debug("Request to update JobInstance : {}", jobInstanceDTO);
        JobInstance jobInstance = jobInstanceMapper.toEntity(jobInstanceDTO);
        normalizeScheduling(jobInstance);
        jobInstance = jobInstanceRepository.save(jobInstance);
        return jobInstanceMapper.toDto(jobInstance);
    }

    /**
     * Partially update a jobInstance.
     *
     * @param jobInstanceDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<JobInstanceDTO> partialUpdate(JobInstanceDTO jobInstanceDTO) {
        LOG.debug("Request to partially update JobInstance : {}", jobInstanceDTO);

        return jobInstanceRepository
            .findById(jobInstanceDTO.getId())
            .map(existingJobInstance -> {
                jobInstanceMapper.partialUpdate(existingJobInstance, jobInstanceDTO);

                return existingJobInstance;
            })
            .map(jobInstanceRepository::save)
            .map(jobInstanceMapper::toDto);
    }

    /**
     * Get all the jobInstances.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<JobInstanceDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all JobInstances (exclude DAG sub tasks)");
        return jobInstanceRepository.findByParentInstanceCodeIsNull(pageable).map(jobInstanceMapper::toDto);
    }

    /**
     * Get all the jobInstances filtered by status.
     *
     * @param pageable the pagination information.
     * @param status   the optional status to filter by (nullable).
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<JobInstanceDTO> findAll(Pageable pageable, String status) {
        if (!StringUtils.hasText(status)) {
            return findAll(pageable);
        }
        LOG.debug("Request to get all JobInstances (exclude DAG sub tasks) with status: {}", status);
        return jobInstanceRepository
            .findByParentInstanceCodeIsNullAndStatus(status.trim(), pageable)
            .map(jobInstanceMapper::toDto);
    }

    /**
     * 查询某个 DAG 编排实例下的子任务实例列表。
     *
     * @param parentInstanceCode DAG 顶层实例代码
     * @return 子任务实例列表
     */
    @Transactional(readOnly = true)
    public List<JobInstanceDTO> findSubInstances(String parentInstanceCode) {
        return findSubInstances(parentInstanceCode, false);
    }

    /**
     * 查询某个 DAG 编排实例下的子任务实例列表。
     *
     * @param parentInstanceCode DAG 顶层实例代码
     * @param recursive          是否递归返回所有后代实例
     * @return 子任务实例列表
     */
    @Transactional(readOnly = true)
    public List<JobInstanceDTO> findSubInstances(String parentInstanceCode, boolean recursive) {
        LOG.debug("Request to get sub JobInstances by parentInstanceCode: {}, recursive: {}", parentInstanceCode, recursive);
        if (!recursive) {
            return jobInstanceRepository
                .findByParentInstanceCodeOrderByIdAsc(parentInstanceCode)
                .stream()
                .map(jobInstanceMapper::toDto)
                .collect(Collectors.toList());
        }
        List<JobInstance> descendants = new ArrayList<>();
        collectSubInstances(parentInstanceCode, descendants, new HashSet<>());
        return descendants.stream().map(jobInstanceMapper::toDto).collect(Collectors.toList());
    }

    private void collectSubInstances(String parentInstanceCode, List<JobInstance> descendants, Set<String> visited) {
        for (JobInstance child : jobInstanceRepository.findByParentInstanceCodeOrderByIdAsc(parentInstanceCode)) {
            if (StringUtils.hasText(child.getInstanceCode()) && !visited.add(child.getInstanceCode())) {
                continue;
            }
            descendants.add(child);
            if (StringUtils.hasText(child.getInstanceCode())) {
                collectSubInstances(child.getInstanceCode(), descendants, visited);
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<JobInstanceDTO> findAllByJobCode(String jobCode, Pageable pageable) {
        LOG.debug("Request to get JobInstances by jobCode: {}", jobCode);
        return jobInstanceRepository.findAllByJobCode(jobCode, pageable).map(this::toDtoWithParentJobName);
    }

    /**
     * 转换为 DTO，并补充父任务名称（DAG 编排产生的子任务实例）。
     */
    private JobInstanceDTO toDtoWithParentJobName(JobInstance jobInstance) {
        JobInstanceDTO dto = jobInstanceMapper.toDto(jobInstance);
        if (StringUtils.hasText(jobInstance.getParentInstanceCode())) {
            jobInstanceRepository
                .findByInstanceCode(jobInstance.getParentInstanceCode())
                .ifPresent(parent -> dto.setParentJobName(parent.getJobName()));
        }
        return dto;
    }

    /**
     * Get one jobInstance by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<JobInstanceDTO> findOne(String id) {
        LOG.debug("Request to get JobInstance : {}", id);
        return jobInstanceRepository.findById(id).map(jobInstanceMapper::toDto);
    }

    public JobInstance findOneJobInstance(String id) {
        return jobInstanceRepository.findById(id).orElse(null);
    }

    public JobInstance findOneJobInstanceByInstanceCode(String instanceCode) {
        return jobInstanceRepository.findByInstanceCode(instanceCode).orElse(null);
    }

    /**
     * Delete the jobInstance by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete JobInstance : {}", id);
        jobInstanceRepository.deleteById(id);
    }

    public JobInstance findLastInstance(String parentJobCode) {
        return jobInstanceRepository.findLastInstance(parentJobCode, TaskConstants.TASK_STATUS_SUCCESSFUL);
    }

    public List<JobInstance> findByStatus(String status) {
        return jobInstanceRepository.findByStatus(status);
    }

    /**
     * 根据任务代码和状态查询任务实例
     *
     * @param jobCode 任务代码
     * @param status  任务状态
     * @return 任务实例列表
     */
    public List<JobInstance> findByJobCodeAndStatus(String jobCode, String status) {
        return jobInstanceRepository.findByJobCodeAndStatus(jobCode, status);
    }

    public void startInstance(JobInstance jobInstance) {
        //设置当前任务实例状态为appending，并保存
        jobInstance.setStatus(TaskConstants.TASK_STATUS_APPENDING);
        save(jobInstance);
        //如果任务类型是DAG，获取DAG里面的任务节点，并生成子任务的实例并保存.
        //TODO 切换到任务编排里面取生成具体任务定义
        if (jobInstance.getType().equals(TaskConstants.TASK_TYPE_DAG)) {
            FlowInstance flowInstance = buildFlowInstance(jobInstance);
            jobInstance.setJobContext(JSONObject.toJSONString(flowInstance));
        }
        //发送任务执行事件，worker会接收并执行。
        //必须在事务提交后再投递启动事件：否则执行 worker 会在实例落库前就变更状态，
        //而状态事件处理线程按 instanceCode 回查数据库时读不到该实例，导致运行状态丢失，实例一直停留在 APPENDING。
        JobStatusEvent startJobEvent = new JobStatusEvent();
        startJobEvent.setJobInstance(jobInstance);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        EventServiceFactory.getEventService().pushStartJobEvent(startJobEvent);
                    }
                }
            );
        } else {
            EventServiceFactory.getEventService().pushStartJobEvent(startJobEvent);
        }
    }

    public FlowInstance buildFlowInstance(JobInstance jobInstance) {
        Set<String> dagPath = new LinkedHashSet<>();
        if (StringUtils.hasText(jobInstance.getJobCode())) {
            dagPath.add(jobInstance.getJobCode());
        }
        return buildFlowInstance(jobInstance, dagPath, 0);
    }

    private FlowInstance buildFlowInstance(JobInstance jobInstance, Set<String> dagPath, int depth) {
        Flow flow = parseDagFlow(jobInstance);
        FlowInstance flowInstance = new FlowInstance();
        flowInstance.setJobDepends(flow.getJobDepends());
        int parentPriority = normalizePriority(jobInstance.getPriority());
        for (Job job : flow.getJobs()) {
            JobInstance subJobInstance = buildJobInstance(job.getId());
            subJobInstance.setSubJob(true);
            subJobInstance.setParentInstanceCode(jobInstance.getInstanceCode());
            // DAG 以父任务优先级为准，子任务统一继承，避免节点优先级影响整体编排调度。
            subJobInstance.setPriority(parentPriority);
            subJobInstance.setStatus(TaskConstants.TASK_STATUS_APPENDING);
            subJobInstance.setCreateTime(ZonedDateTime.now());

            // 子编排需要递归展开，把自身的任务实例树写入 jobContext，供 DAGTask 执行时直接使用。
            if (TaskConstants.TASK_TYPE_DAG.equalsIgnoreCase(subJobInstance.getType())) {
                int nestedDepth = depth + 1;
                if (nestedDepth > TaskConstants.MAX_DAG_NESTING_DEPTH) {
                    throw new IllegalStateException(
                        "任务编排嵌套层级不能超过 " + TaskConstants.MAX_DAG_NESTING_DEPTH + " 层: " + subJobInstance.getJobCode()
                    );
                }
                if (!dagPath.add(subJobInstance.getJobCode())) {
                    throw new IllegalStateException(
                        "任务编排存在循环嵌套: " + String.join(" -> ", dagPath) + " -> " + subJobInstance.getJobCode()
                    );
                }
                try {
                    FlowInstance nestedFlowInstance = buildFlowInstance(subJobInstance, dagPath, nestedDepth);
                    subJobInstance.setJobContext(JSONObject.toJSONString(nestedFlowInstance));
                } finally {
                    dagPath.remove(subJobInstance.getJobCode());
                }
            }

            save(subJobInstance);
            flowInstance.getJobInstances().add(subJobInstance);
        }
        return flowInstance;
    }

    private Flow parseDagFlow(JobInstance jobInstance) {
        try {
            Flow flow = JSONObject.parseObject(jobInstance.getJobContext(), Flow.class);
            if (flow == null || flow.getJobs() == null) {
                throw new IllegalStateException("任务编排没有可执行的子任务: " + jobInstance.getJobCode());
            }
            return flow;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("编排内容解析失败: " + jobInstance.getJobCode(), e);
        }
    }

    public void waitingInstance(JobInstance jobInstance) {
        //如果存在多个同一个任务的等待实例，设置为之前的任务为超时状态
        List<JobInstance> lastJobInstances = jobInstanceRepository.findByJobCodeAndStatus(
            jobInstance.getJobCode(),
            TaskConstants.TASK_STATUS_WAITING
        );
        for (JobInstance lastJobInstance : lastJobInstances) {
            lastJobInstance.setStartTime(String.valueOf(System.currentTimeMillis()));
            lastJobInstance.setEndTime(String.valueOf(System.currentTimeMillis()));
            lastJobInstance.setStatus(TaskConstants.TASK_STATUS_TIMEOUT);
            save(lastJobInstance);
        }
        jobInstance.setStatus(TaskConstants.TASK_STATUS_WAITING);
        save(jobInstance);
        //        waitingJob.put(jobInstance.getJobCode(), jobInstance);
    }

    public boolean checkDependIsNullOrSuccess(JobInstance currentJobInstance) {
        List<JobDepend> jobDependList = jobDependService.findByChildJobCode(currentJobInstance.getJobCode());
        if (jobDependList.isEmpty()) {
            return true;
        }
        boolean isDependSuccess = true;
        for (JobDepend jobDepend : jobDependList) {
            JobInstance jobInstance = findLastInstance(jobDepend.getParentJobCode());
            if (jobInstance == null) {
                isDependSuccess = false;
                break;
            }
            if (
                currentJobInstance.getCreateTime().toInstant().toEpochMilli() - jobInstance.getCreateTime().toInstant().toEpochMilli() >
                jobDepend.getLastInterval()
            ) {
                isDependSuccess = false;
                break;
            }
        }
        return isDependSuccess;
    }

    public JobInstance buildJobInstance(String jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        JobInstance jobInstance = new JobInstance();
        jobInstance.setJobName(job.getJobName());
        jobInstance.setInstanceCode(buildInstanceCode(job.getId()));
        jobInstance.setType(job.getType());
        jobInstance.setJobCode(String.valueOf(job.getId()));
        jobInstance.setTenantId(job.getTenantId());
        jobInstance.setRetryCount(job.getRetryCount() == null ? 0 : job.getRetryCount());
        jobInstance.setTimeoutSeconds(job.getTimeoutSeconds() == null ? 0 : job.getTimeoutSeconds());
        jobInstance.setPriority(normalizePriority(job.getPriority()));
        if(TaskConstants.TASK_TYPE_SQL.equals(job.getType())){
            jobInstance.setJobContext(SqlTaskContextAssembler.assemble(job.getJobContext(), job.getTenantId()));
        }else {
            jobInstance.setJobContext(job.getJobContext());
        }
        //        jobInstance.setParameter();
        return jobInstance;
    }

    /**
     * 生成任务实例编码。追加随机后缀，避免同一毫秒内构建多个实例（尤其是嵌套编排复用同一子编排）时编码重复。
     */
    private String buildInstanceCode(String jobId) {
        return jobId + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void normalizeScheduling(JobInstance jobInstance) {
        if (jobInstance.getRetryCount() == null) {
            jobInstance.setRetryCount(0);
        }
        if (jobInstance.getTimeoutSeconds() == null) {
            jobInstance.setTimeoutSeconds(0);
        }
        jobInstance.setPriority(normalizePriority(jobInstance.getPriority()));
    }

    private int normalizePriority(Integer priority) {
        return TaskConstants.isValidPriority(priority) ? priority : TaskConstants.DEFAULT_PRIORITY;
    }
}
