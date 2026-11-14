package com.data.datafusion.service;

import com.data.datafusion.domain.ETLTaskDirectory;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.DataApiRepository;
import com.data.datafusion.repository.ETLTaskDirectoryRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.ETLTaskDirectoryDTO;
import com.data.datafusion.service.mapper.ETLTaskDirectoryMapper;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Service Implementation for managing {@link ETLTaskDirectory}.
 */
@Service
@Transactional
public class ETLTaskDirectoryService {

    private static final Logger LOG = LoggerFactory.getLogger(ETLTaskDirectoryService.class);

    private final ETLTaskDirectoryRepository directoryRepository;

    private final ETLTaskRepository etlTaskRepository;

    private final JobRepository jobRepository;

    private final DataApiRepository dataApiRepository;

    private final ETLTaskDirectoryMapper directoryMapper;

    public ETLTaskDirectoryService(
        ETLTaskDirectoryRepository directoryRepository,
        ETLTaskRepository etlTaskRepository,
        JobRepository jobRepository,
        DataApiRepository dataApiRepository,
        ETLTaskDirectoryMapper directoryMapper
    ) {
        this.directoryRepository = directoryRepository;
        this.etlTaskRepository = etlTaskRepository;
        this.jobRepository = jobRepository;
        this.dataApiRepository = dataApiRepository;
        this.directoryMapper = directoryMapper;
    }

    public ETLTaskDirectoryDTO save(ETLTaskDirectoryDTO dto) {
        LOG.debug("Request to save ETLTaskDirectory : {}", dto);
        normalizeAndValidate(dto, null);
        ETLTaskDirectory entity = directoryMapper.toEntity(dto);
        entity.setCreateTime(ZonedDateTime.now());
        entity.setUpdateTime(ZonedDateTime.now());
        entity = directoryRepository.save(entity);
        return directoryMapper.toDto(entity);
    }

    public ETLTaskDirectoryDTO update(ETLTaskDirectoryDTO dto) {
        LOG.debug("Request to update ETLTaskDirectory : {}", dto);
        if (!StringUtils.hasText(dto.getId())) {
            throw new IllegalArgumentException("目录 ID 不能为空");
        }
        ETLTaskDirectory existing = directoryRepository
            .findById(dto.getId())
            .orElseThrow(() -> new IllegalArgumentException("目录不存在: " + dto.getId()));
        normalizeAndValidate(dto, existing);
        directoryMapper.partialUpdate(existing, dto);
        existing.setUpdateTime(ZonedDateTime.now());
        return directoryMapper.toDto(directoryRepository.save(existing));
    }

    @Transactional(readOnly = true)
    public List<ETLTaskDirectoryDTO> findAll(String taskType) {
        LOG.debug("Request to get ETLTaskDirectories by taskType : {}", taskType);
        String normalizedType = normalizeTaskType(taskType);
        return directoryRepository.findByTaskTypeOrderBySortOrderAscNameAsc(normalizedType).stream()
            .map(directoryMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ETLTaskDirectoryDTO> findByParent(String taskType, String parentId) {
        LOG.debug("Request to get ETLTaskDirectories by taskType : {}, parentId : {}", taskType, parentId);
        String normalizedType = normalizeTaskType(taskType);
        List<ETLTaskDirectory> directories = StringUtils.hasText(parentId)
            ? directoryRepository.findByTaskTypeAndParentIdOrderBySortOrderAscNameAsc(normalizedType, parentId)
            : directoryRepository.findByTaskTypeAndParentIdIsNullOrderBySortOrderAscNameAsc(normalizedType);
        return directories.stream().map(directoryMapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ETLTaskDirectoryDTO> findOne(String id) {
        LOG.debug("Request to get ETLTaskDirectory : {}", id);
        return directoryRepository.findById(id).map(directoryMapper::toDto);
    }

    /**
     * 删除目录：子目录与目录下的任务、编排、数据服务移动到根目录，避免悬挂引用。
     */
    public void delete(String id) {
        LOG.debug("Request to delete ETLTaskDirectory : {}", id);
        directoryRepository.findByParentIdOrderBySortOrderAsc(id).forEach(child -> child.setParentId(null));
        etlTaskRepository.findByDirectoryId(id).forEach(task -> task.setDirectoryId(null));
        jobRepository.findByDirectoryId(id).forEach(job -> job.setDirectoryId(null));
        dataApiRepository.findByDirectoryId(id).forEach(dataApi -> dataApi.setDirectoryId(null));
        directoryRepository.deleteById(id);
    }

    /**
     * 收集目录及其所有子孙目录 ID，用于按目录树筛选任务。
     */
    @Transactional(readOnly = true)
    public Set<String> collectSubtreeIds(String directoryId) {
        Set<String> ids = new LinkedHashSet<>();
        if (!StringUtils.hasText(directoryId)) {
            return ids;
        }
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
     * 构建目录的完整路径（从根目录到当前目录）。
     */
    @Transactional(readOnly = true)
    public List<String> buildPath(String directoryId) {
        List<String> names = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        String current = directoryId;
        while (StringUtils.hasText(current) && visited.add(current)) {
            Optional<ETLTaskDirectory> directory = directoryRepository.findById(current);
            if (directory.isEmpty()) {
                break;
            }
            names.add(0, directory.get().getName());
            current = directory.get().getParentId();
        }
        return names;
    }

    /**
     * 校验任务要归属的目录存在且与任务类型一致。
     */
    @Transactional(readOnly = true)
    public void validateTaskDirectory(String directoryId, String taskType) {
        if (!StringUtils.hasText(directoryId)) {
            return;
        }
        ETLTaskDirectory directory = directoryRepository
            .findById(directoryId)
            .orElseThrow(() -> new IllegalArgumentException("目录不存在: " + directoryId));
        String normalizedType = normalizeTaskType(taskType);
        if (!normalizedType.equals(directory.getTaskType())) {
            throw new IllegalArgumentException("目录与任务类型不匹配: " + directoryId);
        }
    }

    private void normalizeAndValidate(ETLTaskDirectoryDTO dto, ETLTaskDirectory existing) {
        if (!StringUtils.hasText(dto.getName())) {
            throw new IllegalArgumentException("目录名称不能为空");
        }
        dto.setName(dto.getName().trim());
        if (dto.getName().length() > 255) {
            throw new IllegalArgumentException("目录名称不能超过 255 个字符");
        }
        dto.setTaskType(normalizeTaskType(existing == null ? dto.getTaskType() : existing.getTaskType()));
        if (dto.getSortOrder() == null) {
            dto.setSortOrder(0);
        }

        if (StringUtils.hasText(dto.getParentId())) {
            ETLTaskDirectory parent = directoryRepository
                .findById(dto.getParentId())
                .orElseThrow(() -> new IllegalArgumentException("父目录不存在: " + dto.getParentId()));
            if (!dto.getTaskType().equals(parent.getTaskType())) {
                throw new IllegalArgumentException("父目录与当前目录的任务类型不一致");
            }
            validateNotDescendant(dto.getId(), dto.getParentId());
        }

        boolean duplicated = StringUtils.hasText(dto.getParentId())
            ? directoryRepository
                .findFirstByTaskTypeAndParentIdAndName(dto.getTaskType(), dto.getParentId(), dto.getName())
                .filter(item -> !item.getId().equals(dto.getId()))
                .isPresent()
            : directoryRepository
                .findFirstByTaskTypeAndParentIdIsNullAndName(dto.getTaskType(), dto.getName())
                .filter(item -> !item.getId().equals(dto.getId()))
                .isPresent();
        if (duplicated) {
            throw new IllegalArgumentException("同一父目录下已存在同名目录: " + dto.getName());
        }
    }

    /** 防止把目录挂到自己的子孙节点下形成环。 */
    private void validateNotDescendant(String id, String parentId) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        if (id.equals(parentId)) {
            throw new IllegalArgumentException("父目录不能是当前目录自身");
        }
        if (collectSubtreeIds(id).contains(parentId)) {
            throw new IllegalArgumentException("父目录不能是当前目录的子孙目录");
        }
    }

    private String normalizeTaskType(String taskType) {
        String normalized = StringUtils.hasText(taskType) ? taskType.trim().toUpperCase(Locale.ROOT) : TaskConstants.ETL_TASK_KIND_BATCH;
        if (
            !TaskConstants.ETL_TASK_KIND_BATCH.equals(normalized) &&
            !TaskConstants.ETL_TASK_KIND_STREAM.equals(normalized) &&
            !TaskConstants.TASK_TYPE_DAG.equals(normalized) &&
            !TaskConstants.TASK_TYPE_DATA_API.equals(normalized)
        ) {
            throw new IllegalArgumentException("任务类型只能是 BATCH、STREAM、DAG 或 DATA_API: " + taskType);
        }
        return normalized;
    }
}
