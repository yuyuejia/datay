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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        jobInstance = jobInstanceRepository.save(jobInstance);
        return jobInstanceMapper.toDto(jobInstance);
    }

    public JobInstance save(JobInstance jobInstance) {
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
        LOG.debug("Request to get all JobInstances");
        return jobInstanceRepository.findAll(pageable).map(jobInstanceMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<JobInstanceDTO> findAllByJobCode(String jobCode, Pageable pageable) {
        LOG.debug("Request to get JobInstances by jobCode: {}", jobCode);
        return jobInstanceRepository.findAllByJobCode(jobCode, pageable).map(jobInstanceMapper::toDto);
    }

    /**
     * Get one jobInstance by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<JobInstanceDTO> findOne(Long id) {
        LOG.debug("Request to get JobInstance : {}", id);
        return jobInstanceRepository.findById(id).map(jobInstanceMapper::toDto);
    }

    public JobInstance findOneJobInstance(Long id) {
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
    public void delete(Long id) {
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
        //发送任务执行事件，worker会接收并执行
        JobStatusEvent startJobEvent = new JobStatusEvent();
        startJobEvent.setJobInstance(jobInstance);
        EventServiceFactory.getEventService().pushStartJobEvent(startJobEvent);
    }

    public FlowInstance buildFlowInstance(JobInstance jobInstance) {
        Flow flow = JSONObject.parseObject(jobInstance.getJobContext(), Flow.class);
        FlowInstance flowInstance = new FlowInstance();
        flowInstance.setJobDepends(flow.getJobDepends());
        for (Job job : flow.getJobs()) {
            JobInstance subJobInstance = buildJobInstance(job.getId());
            subJobInstance.setSubJob(true);
            subJobInstance.setParentInstanceCode(jobInstance.getInstanceCode());
            subJobInstance.setStatus(TaskConstants.TASK_STATUS_APPENDING);
            subJobInstance.setCreateTime(ZonedDateTime.now());
            save(subJobInstance);
            flowInstance.getJobInstances().add(subJobInstance);
        }
        return flowInstance;
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

    public JobInstance buildJobInstance(Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        JobInstance jobInstance = new JobInstance();
        jobInstance.setJobName(job.getJobName());
        jobInstance.setInstanceCode(job.getId() + "-" + System.currentTimeMillis());
        jobInstance.setType(job.getType());
        jobInstance.setJobCode(String.valueOf(job.getId()));
        if(TaskConstants.TASK_TYPE_SQL.equals(job.getType())){
            jobInstance.setJobContext(SqlTaskContextAssembler.assemble(job.getJobContext(), job.getTenantId()));
        }else {
            jobInstance.setJobContext(job.getJobContext());
        }
        //        jobInstance.setParameter();
        return jobInstance;
    }
}
