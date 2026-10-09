package com.data.datafusion.service;

import com.data.datafusion.domain.JobDepend;
import com.data.datafusion.repository.JobDependRepository;
import com.data.datafusion.service.dto.JobDependDTO;
import com.data.datafusion.service.mapper.JobDependMapper;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.JobDepend}.
 */
@Service
@Transactional
public class JobDependService {

    private static final Logger LOG = LoggerFactory.getLogger(JobDependService.class);

    private final JobDependRepository jobDependRepository;

    private final JobDependMapper jobDependMapper;

    public JobDependService(JobDependRepository jobDependRepository, JobDependMapper jobDependMapper) {
        this.jobDependRepository = jobDependRepository;
        this.jobDependMapper = jobDependMapper;
    }

    /**
     * Save a jobDepend.
     *
     * @param jobDependDTO the entity to save.
     * @return the persisted entity.
     */
    public JobDependDTO save(JobDependDTO jobDependDTO) {
        LOG.debug("Request to save JobDepend : {}", jobDependDTO);
        JobDepend jobDepend = jobDependMapper.toEntity(jobDependDTO);
        jobDepend = jobDependRepository.save(jobDepend);
        return jobDependMapper.toDto(jobDepend);
    }

    /**
     * Update a jobDepend.
     *
     * @param jobDependDTO the entity to save.
     * @return the persisted entity.
     */
    public JobDependDTO update(JobDependDTO jobDependDTO) {
        LOG.debug("Request to update JobDepend : {}", jobDependDTO);
        JobDepend jobDepend = jobDependMapper.toEntity(jobDependDTO);
        jobDepend = jobDependRepository.save(jobDepend);
        return jobDependMapper.toDto(jobDepend);
    }

    /**
     * Partially update a jobDepend.
     *
     * @param jobDependDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<JobDependDTO> partialUpdate(JobDependDTO jobDependDTO) {
        LOG.debug("Request to partially update JobDepend : {}", jobDependDTO);

        return jobDependRepository
            .findById(jobDependDTO.getId())
            .map(existingJobDepend -> {
                jobDependMapper.partialUpdate(existingJobDepend, jobDependDTO);

                return existingJobDepend;
            })
            .map(jobDependRepository::save)
            .map(jobDependMapper::toDto);
    }

    /**
     * Get all the jobDepends.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<JobDependDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all JobDepends");
        return jobDependRepository.findAll(pageable).map(jobDependMapper::toDto);
    }

    /**
     * Get one jobDepend by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<JobDependDTO> findOne(String id) {
        LOG.debug("Request to get JobDepend : {}", id);
        return jobDependRepository.findById(id).map(jobDependMapper::toDto);
    }

    /**
     * Delete the jobDepend by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete JobDepend : {}", id);
        jobDependRepository.deleteById(id);
    }

    public List<JobDepend> findByChildJobCode(String ChildJobCode) {
        return jobDependRepository.findByChildJobCode(ChildJobCode);
    }

    public List<JobDepend> findByParentJobCode(String jobCode) {
        return jobDependRepository.findByParentJobCode(jobCode);
    }
}
