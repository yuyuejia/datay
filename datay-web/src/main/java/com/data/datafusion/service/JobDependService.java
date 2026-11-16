package com.data.datafusion.service;

import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobDepend;
import com.data.datafusion.repository.JobDependRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.JobDependDTO;
import com.data.datafusion.service.mapper.JobDependMapper;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.JobDepend}.
 */
@Service
@Transactional
public class JobDependService {

    private static final Logger LOG = LoggerFactory.getLogger(JobDependService.class);

    private final JobDependRepository jobDependRepository;

    private final JobDependMapper jobDependMapper;

    private final JobRepository jobRepository;

    public JobDependService(
        JobDependRepository jobDependRepository,
        JobDependMapper jobDependMapper,
        JobRepository jobRepository
    ) {
        this.jobDependRepository = jobDependRepository;
        this.jobDependMapper = jobDependMapper;
        this.jobRepository = jobRepository;
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

    /**
     * 查询某个任务的上游依赖（DTO），供上线弹窗回显。
     */
    @Transactional(readOnly = true)
    public List<JobDependDTO> findDtosByChildJobCode(String childJobCode) {
        return jobDependRepository.findByChildJobCode(childJobCode).stream().map(jobDependMapper::toDto).toList();
    }

    /**
     * 整体替换某个任务的上游依赖。
     *
     * <p>{@code dependencies} 为 {@code null} 时不做任何修改；空列表表示清空该任务的全部依赖。
     * 该方法会校验自依赖、重复依赖、上游任务是否存在以及是否形成循环依赖。
     *
     * @param childJobCode  当前任务（子任务）的 Job ID
     * @param dependencies  新的上游依赖列表
     */
    public void replaceChildDependencies(String childJobCode, List<JobDependDTO> dependencies) {
        if (dependencies == null) {
            return;
        }
        if (!StringUtils.hasText(childJobCode)) {
            throw new IllegalArgumentException("子任务 ID 不能为空");
        }
        String normalizedChild = childJobCode.trim();
        String childTenantId = jobRepository.findById(normalizedChild).map(Job::getTenantId).orElse(null);
        List<JobDepend> newDependencies = new ArrayList<>();
        Set<String> parentJobCodes = new LinkedHashSet<>();
        for (JobDependDTO dependency : dependencies) {
            if (dependency == null) {
                throw new IllegalArgumentException("上游依赖不能为空");
            }
            String parentJobCode = trimToNull(dependency.getParentJobCode());
            if (parentJobCode == null) {
                throw new IllegalArgumentException("上游任务不能为空");
            }
            if (parentJobCode.equals(normalizedChild)) {
                throw new IllegalArgumentException("任务不能依赖自身: " + normalizedChild);
            }
            if (!parentJobCodes.add(parentJobCode)) {
                throw new IllegalArgumentException("上游任务重复: " + parentJobCode);
            }
            if (!jobRepository.existsById(parentJobCode)) {
                throw new IllegalArgumentException("上游任务不存在: " + parentJobCode);
            }
            Long lastInterval = normalizeLastInterval(dependency.getLastInterval());
            if (hasTransitiveDependency(parentJobCode, normalizedChild)) {
                throw new IllegalArgumentException("任务依赖存在循环: " + normalizedChild + " -> " + parentJobCode);
            }

            JobDepend jobDepend = new JobDepend();
            jobDepend.setParentJobCode(parentJobCode);
            jobDepend.setChildJobCode(normalizedChild);
            jobDepend.setJobCode(normalizedChild);
            jobDepend.setLastInterval(lastInterval);
            jobDepend.setCreateTime(ZonedDateTime.now());
            jobDepend.setTenantId(childTenantId);
            newDependencies.add(jobDepend);
        }

        jobDependRepository.deleteByChildJobCode(normalizedChild);
        jobDependRepository.saveAll(newDependencies);
    }

    /**
     * 删除某个任务的全部上游依赖。
     */
    public void deleteByChildJobCode(String childJobCode) {
        if (StringUtils.hasText(childJobCode)) {
            jobDependRepository.deleteByChildJobCode(childJobCode.trim());
        }
    }

    /**
     * 删除以某个任务为上游的全部依赖。
     */
    public void deleteByParentJobCode(String parentJobCode) {
        if (StringUtils.hasText(parentJobCode)) {
            jobDependRepository.deleteByParentJobCode(parentJobCode.trim());
        }
    }

    private Long normalizeLastInterval(Long lastInterval) {
        if (lastInterval == null || lastInterval <= 0) {
            throw new IllegalArgumentException("依赖有效窗口必须大于 0 毫秒");
        }
        return lastInterval;
    }

    /**
     * 判断 {@code parentJobCode} 的依赖链中是否已经包含 {@code childJobCode}。
     * 如果包含，再把 parent 作为 child 的上游就会形成循环依赖。
     */
    private boolean hasTransitiveDependency(String parentJobCode, String childJobCode) {
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.push(parentJobCode);
        while (!queue.isEmpty()) {
            String current = queue.pop();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(childJobCode)) {
                return true;
            }
            for (JobDepend dependency : jobDependRepository.findByChildJobCode(current)) {
                String upstream = trimToNull(dependency.getParentJobCode());
                if (upstream != null) {
                    queue.push(upstream);
                }
            }
        }
        return false;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
