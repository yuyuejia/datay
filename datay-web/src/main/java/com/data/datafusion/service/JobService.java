package com.data.datafusion.service;

import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.JobDTO;
import com.data.datafusion.service.jobevent.EventServiceFactory;
import com.data.datafusion.service.mapper.JobMapper;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.Job}.
 */
@Service
@Transactional
public class JobService {

    private static final Logger LOG = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;

    private final JobMapper jobMapper;
    private final JobInstanceService jobInstanceService;

    public JobService(JobRepository jobRepository, JobMapper jobMapper, JobInstanceService jobInstanceService) {
        this.jobRepository = jobRepository;
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
        Job job = jobMapper.toEntity(jobDTO);
        job = jobRepository.save(job);
        return jobMapper.toDto(job);
    }

    public Job save(Job job) {
        LOG.debug("Request to save Job : {}", job);
        job = jobRepository.save(job);
        return job;
    }

    public void online(Job job, boolean executeOnce) {
        job.setStatus(TaskConstants.TASK_STATUS_ONLINE);
        jobRepository.save(job);
        EventServiceFactory.getEventService().addJob(job);
        if (executeOnce) {
            executeOnce(job);
        }
    }

    public void offline(Job job) {
        job.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
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
        jobInstance.setJobContext(job.getJobContext());
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
        Job job = jobMapper.toEntity(jobDTO);
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
     * Get one job by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<JobDTO> findOne(Long id) {
        LOG.debug("Request to get JobDTO : {}", id);
        return jobRepository.findById(id).map(jobMapper::toDto);
    }

    /**
     * Delete the job by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Job : {}", id);
        Job job = jobRepository.findById(id).get();
        jobRepository.deleteById(id);
        EventServiceFactory.getEventService().deleteJob(job);
    }

    @Transactional(readOnly = true)
    public Optional<Job> findOneJob(Long id) {
        LOG.debug("Request to get Job : {}", id);
        return jobRepository.findById(id);
    }
}
