package com.data.datafusion.service;

import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.ETLTaskDirectory;
import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.job.dag.Flow;
import com.data.datafusion.job.sql.SqlTaskContextAssembler;
import com.data.datafusion.repository.ETLTaskDirectoryRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.JobDTO;
import com.data.datafusion.service.jobevent.EventServiceFactory;
import com.data.datafusion.service.mapper.JobMapper;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.Job}.
 */
@Service
@Transactional
public class JobService {

    private static final Logger LOG = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;

    private final ETLTaskDirectoryRepository directoryRepository;

    private final JobMapper jobMapper;
    private final JobInstanceService jobInstanceService;

    public JobService(
        JobRepository jobRepository,
        ETLTaskDirectoryRepository directoryRepository,
        JobMapper jobMapper,
        JobInstanceService jobInstanceService
    ) {
        this.jobRepository = jobRepository;
        this.directoryRepository = directoryRepository;
        this.jobMapper = jobMapper;
        this.jobInstanceService = jobInstanceService;
    }

    /**
     * Save a job.
     *
     * @param jobDTO the entity to save.
     * @return the persisted entity.
     */
    public JobDTO save(JobDTO jobDTO) {
        LOG.debug("Request to save Job : {}", jobDTO);
        normalizeScheduling(jobDTO);
        validateDagDirectory(jobDTO);
        validateDagNesting(jobDTO);
        Job job = jobMapper.toEntity(jobDTO);
        normalizeScheduling(job);
        job = jobRepository.save(job);
        return jobMapper.toDto(job);
    }

    public Job save(Job job) {
        LOG.debug("Request to save Job : {}", job);
        normalizeScheduling(job);
        job = jobRepository.save(job);
        return job;
    }

    public void online(Job job, boolean executeOnce) {
        normalizeScheduling(job);
        job.setStatus(TaskConstants.TASK_STATUS_ONLINE);
        job.setUpdateTime(ZonedDateTime.now());
        jobRepository.save(job);
        EventServiceFactory.getEventService().addJob(job);
        if (executeOnce) {
            executeOnce(job);
        }
    }

    public void offline(Job job) {
        job.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
        job.setUpdateTime(ZonedDateTime.now());
        jobRepository.save(job);
        EventServiceFactory.getEventService().deleteJob(job);
    }

    // 立即执行一次
    public void executeOnce(Job job) {
        JobInstance jobInstance = new JobInstance();
        jobInstance.setJobName(job.getJobName());
        jobInstance.setInstanceCode(job.getId() + "-" + System.currentTimeMillis());
        jobInstance.setType(job.getType());
        jobInstance.setJobCode(String.valueOf(job.getId()));
        jobInstance.setTenantId(job.getTenantId());
        jobInstance.setRetryCount(job.getRetryCount() == null ? 0 : job.getRetryCount());
        jobInstance.setTimeoutSeconds(job.getTimeoutSeconds() == null ? 0 : job.getTimeoutSeconds());
        jobInstance.setPriority(TaskConstants.isValidPriority(job.getPriority()) ? job.getPriority() : TaskConstants.DEFAULT_PRIORITY);
        if(TaskConstants.TASK_TYPE_SQL.equals(job.getType())){
            jobInstance.setJobContext(SqlTaskContextAssembler.assemble(job.getJobContext(), job.getTenantId()));
        }else {
            jobInstance.setJobContext(job.getJobContext());
        }
        jobInstance.setCreateTime(ZonedDateTime.now());

        jobInstanceService.startInstance(jobInstance);
    }

    /**
     * Update a job.
     *
     * @param jobDTO the entity to save.
     * @return the persisted entity.
     */
    public JobDTO update(JobDTO jobDTO) {
        LOG.debug("Request to update Job : {}", jobDTO);
        preserveSchedulingFromExisting(jobDTO);
        normalizeScheduling(jobDTO);
        validateDagDirectory(jobDTO);
        validateDagNesting(jobDTO);
        Job job = jobMapper.toEntity(jobDTO);
        normalizeScheduling(job);
        job = jobRepository.save(job);
        return jobMapper.toDto(job);
    }

    /**
     * Partially update a job.
     *
     * @param jobDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<JobDTO> partialUpdate(JobDTO jobDTO) {
        LOG.debug("Request to partially update Job : {}", jobDTO);

        return jobRepository
            .findById(jobDTO.getId())
            .map(existingJob -> {
                jobMapper.partialUpdate(existingJob, jobDTO);

                return existingJob;
            })
            .map(jobRepository::save)
            .map(jobMapper::toDto);
    }

    /**
     * Get all the jobs.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<JobDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all Jobs");
        return jobRepository.findAll(pageable).map(jobMapper::toDto);
    }

    /**
     * Get all the jobs filtered by job type and search keyword.
     *
     * @param pageable the pagination information.
     * @param search   the optional keyword used to filter by job name or type (nullable).
     * @param type     the job type to include (nullable).
     * @param typeNot  the job type to exclude (nullable).
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<JobDTO> findAll(Pageable pageable, String search, String type, String typeNot) {
        return findAll(pageable, search, type, typeNot, null);
    }

    /**
     * Get all the jobs filtered by job type, search keyword and directory.
     *
     * @param pageable    the pagination information.
     * @param search      the optional keyword used to filter by job name or type (nullable).
     * @param type        the job type to include (nullable).
     * @param typeNot     the job type to exclude (nullable).
     * @param directoryId the optional directory ID to filter by (nullable).
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<JobDTO> findAll(Pageable pageable, String search, String type, String typeNot, String directoryId) {
        LOG.debug(
            "Request to get all Jobs with search: {}, type: {}, typeNot: {}, directoryId: {}",
            search,
            type,
            typeNot,
            directoryId
        );
        Specification<Job> specification = Specification.where(null);
        if (StringUtils.hasText(type)) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("type"), type));
        }
        if (StringUtils.hasText(typeNot)) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.notEqual(root.get("type"), typeNot));
        }
        if (StringUtils.hasText(directoryId)) {
            Set<String> directoryIds = collectDirectorySubtreeIds(directoryId);
            specification = specification.and((root, query, criteriaBuilder) ->
                directoryIds.isEmpty()
                    ? criteriaBuilder.disjunction()
                    : root.get("directoryId").in(directoryIds)
            );
        }
        if (StringUtils.hasText(search)) {
            String pattern = "%" + search.trim() + "%";
            specification = specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.or(
                    criteriaBuilder.like(root.get("jobName"), pattern),
                    criteriaBuilder.like(root.get("type"), pattern)
                )
            );
        }
        return jobRepository.findAll(specification, pageable).map(jobMapper::toDto);
    }

    private void validateDagDirectory(JobDTO jobDTO) {
        if (!StringUtils.hasText(jobDTO.getDirectoryId())) {
            return;
        }
        if (!TaskConstants.TASK_TYPE_DAG.equalsIgnoreCase(jobDTO.getType())) {
            throw new IllegalArgumentException("只有任务编排支持目录: " + jobDTO.getId());
        }
        ETLTaskDirectory directory = directoryRepository
            .findById(jobDTO.getDirectoryId())
            .orElseThrow(() -> new IllegalArgumentException("目录不存在: " + jobDTO.getDirectoryId()));
        if (!TaskConstants.TASK_TYPE_DAG.equals(directory.getTaskType())) {
            throw new IllegalArgumentException("目录与任务类型不匹配: " + jobDTO.getDirectoryId());
        }
    }

    /**
     * 校验任务编排的嵌套关系，禁止直接嵌套自身、间接循环嵌套以及超过最大嵌套深度。
     */
    private void validateDagNesting(JobDTO jobDTO) {
        if (!TaskConstants.TASK_TYPE_DAG.equalsIgnoreCase(jobDTO.getType()) || !StringUtils.hasText(jobDTO.getJobContext())) {
            return;
        }
        Flow flow = parseDagFlow(jobDTO.getJobContext(), jobDTO.getId());
        if (flow == null || flow.getJobs() == null) {
            return;
        }
        Set<String> path = new LinkedHashSet<>();
        if (StringUtils.hasText(jobDTO.getId())) {
            path.add(jobDTO.getId());
        }
        Map<String, Job> jobCache = new HashMap<>();
        for (Job child : flow.getJobs()) {
            validateNestedDag(child.getId(), path, jobCache, 1);
        }
    }

    private void validateNestedDag(String jobId, Set<String> path, Map<String, Job> jobCache, int depth) {
        if (!StringUtils.hasText(jobId)) {
            return;
        }
        if (depth > TaskConstants.MAX_DAG_NESTING_DEPTH) {
            throw new IllegalArgumentException("任务编排嵌套层级不能超过 " + TaskConstants.MAX_DAG_NESTING_DEPTH + " 层");
        }
        Job job = jobCache.computeIfAbsent(jobId, id -> jobRepository.findById(id).orElse(null));
        if (job == null) {
            throw new IllegalArgumentException("编排引用的任务不存在: " + jobId);
        }
        if (!TaskConstants.TASK_TYPE_DAG.equalsIgnoreCase(job.getType())) {
            return;
        }
        if (!path.add(jobId)) {
            throw new IllegalArgumentException("任务编排存在循环嵌套: " + String.join(" -> ", path) + " -> " + jobId);
        }
        try {
            if (!StringUtils.hasText(job.getJobContext())) {
                return;
            }
            Flow nestedFlow = parseDagFlow(job.getJobContext(), jobId);
            if (nestedFlow == null || nestedFlow.getJobs() == null) {
                return;
            }
            for (Job nestedChild : nestedFlow.getJobs()) {
                validateNestedDag(nestedChild.getId(), path, jobCache, depth + 1);
            }
        } finally {
            path.remove(jobId);
        }
    }

    private Flow parseDagFlow(String jobContext, String jobId) {
        try {
            return JSONObject.parseObject(jobContext, Flow.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("编排内容解析失败: " + jobId, e);
        }
    }

    /**
     * 收集目录及其子孙目录 ID，使目录筛选包含子目录。
     */
    private Set<String> collectDirectorySubtreeIds(String directoryId) {
        Set<String> ids = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(directoryId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!ids.add(current)) {
                continue;
            }
            directoryRepository.findByParentIdOrderBySortOrderAsc(current).forEach(child -> queue.add(child.getId()));
        }
        return ids;
    }

    /**
     * 移动任务编排到指定目录；{@code directoryId} 为空表示移动到根目录。
     */
    public JobDTO moveToDirectory(String id, String directoryId) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("任务编排不存在: " + id));
        if (!TaskConstants.TASK_TYPE_DAG.equalsIgnoreCase(job.getType())) {
            throw new IllegalArgumentException("只有任务编排支持目录移动: " + id);
        }
        if (StringUtils.hasText(directoryId)) {
            ETLTaskDirectory directory = directoryRepository
                .findById(directoryId)
                .orElseThrow(() -> new IllegalArgumentException("目录不存在: " + directoryId));
            if (!TaskConstants.TASK_TYPE_DAG.equals(directory.getTaskType())) {
                throw new IllegalArgumentException("目录与任务类型不匹配: " + directoryId);
            }
        }
        job.setDirectoryId(StringUtils.hasText(directoryId) ? directoryId.trim() : null);
        job.setUpdateTime(ZonedDateTime.now());
        return jobMapper.toDto(jobRepository.save(job));
    }

    /**
     * Get one job by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<JobDTO> findOne(String id) {
        LOG.debug("Request to get JobDTO : {}", id);
        return jobRepository.findById(id).map(jobMapper::toDto);
    }

    /**
     * Delete the job by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete Job : {}", id);
        Job job = jobRepository.findById(id).get();
        jobRepository.deleteById(id);
        EventServiceFactory.getEventService().deleteJob(job);
    }

    @Transactional(readOnly = true)
    public Optional<Job> findOneJob(String id) {
        LOG.debug("Request to get Job : {}", id);
        return jobRepository.findById(id);
    }

    /**
     * 更新任务时，编辑页不再提交调度设置。对于未提交的字段沿用数据库中的现有配置，
     * 避免仅修改任务内容时把已配置的 Cron、重试次数、超时时间或优先级清空。
     */
    private void preserveSchedulingFromExisting(JobDTO jobDTO) {
        if (jobDTO.getId() == null) {
            return;
        }
        jobRepository
            .findById(jobDTO.getId())
            .ifPresent(existing -> {
                if (jobDTO.getRetryCount() == null) {
                    jobDTO.setRetryCount(existing.getRetryCount());
                }
                if (jobDTO.getTimeoutSeconds() == null) {
                    jobDTO.setTimeoutSeconds(existing.getTimeoutSeconds());
                }
                if (jobDTO.getPriority() == null) {
                    jobDTO.setPriority(existing.getPriority());
                }
                if (!StringUtils.hasText(jobDTO.getCron())) {
                    jobDTO.setCron(existing.getCron());
                }
            });
    }

    private void normalizeScheduling(JobDTO jobDTO) {
        if (jobDTO.getRetryCount() == null) {
            jobDTO.setRetryCount(0);
        }
        if (jobDTO.getTimeoutSeconds() == null) {
            jobDTO.setTimeoutSeconds(0);
        }
        if (!TaskConstants.isValidPriority(jobDTO.getPriority())) {
            jobDTO.setPriority(TaskConstants.DEFAULT_PRIORITY);
        }
    }

    private void normalizeScheduling(Job job) {
        if (job.getRetryCount() == null) {
            job.setRetryCount(0);
        }
        if (job.getTimeoutSeconds() == null) {
            job.setTimeoutSeconds(0);
        }
        if (!TaskConstants.isValidPriority(job.getPriority())) {
            job.setPriority(TaskConstants.DEFAULT_PRIORITY);
        }
    }
}
