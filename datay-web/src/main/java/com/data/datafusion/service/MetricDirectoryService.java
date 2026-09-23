package com.data.datafusion.service;

import com.data.datafusion.domain.MetricDirectory;
import com.data.datafusion.repository.MetricDirectoryRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.service.dto.MetricDirectoryDTO;
import com.data.datafusion.service.mapper.MetricDirectoryMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link MetricDirectory}.
 */
@Service
@Transactional
public class MetricDirectoryService {

    private static final Logger LOG = LoggerFactory.getLogger(MetricDirectoryService.class);

    private final MetricDirectoryRepository metricDirectoryRepository;
    private final MetricRepository metricRepository;
    private final MetricDirectoryMapper metricDirectoryMapper;

    public MetricDirectoryService(
        MetricDirectoryRepository metricDirectoryRepository,
        MetricRepository metricRepository,
        MetricDirectoryMapper metricDirectoryMapper
    ) {
        this.metricDirectoryRepository = metricDirectoryRepository;
        this.metricRepository = metricRepository;
        this.metricDirectoryMapper = metricDirectoryMapper;
    }

    public MetricDirectoryDTO save(MetricDirectoryDTO dto) {
        LOG.debug("Request to save MetricDirectory : {}", dto);
        MetricDirectory entity = metricDirectoryMapper.toEntity(dto);
        entity = metricDirectoryRepository.save(entity);
        return metricDirectoryMapper.toDto(entity);
    }

    public MetricDirectoryDTO update(MetricDirectoryDTO dto) {
        LOG.debug("Request to update MetricDirectory : {}", dto);
        MetricDirectory entity = metricDirectoryMapper.toEntity(dto);
        entity = metricDirectoryRepository.save(entity);
        return metricDirectoryMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<MetricDirectoryDTO> findAll() {
        LOG.debug("Request to get all MetricDirectories");
        return metricDirectoryRepository.findAll().stream().map(metricDirectoryMapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MetricDirectoryDTO> findByParentId(Long parentId) {
        LOG.debug("Request to get MetricDirectories by parentId : {}", parentId);
        if (parentId == null) {
            return metricDirectoryRepository.findByParentIdIsNullOrderBySortOrderAsc().stream()
                .map(metricDirectoryMapper::toDto)
                .collect(Collectors.toList());
        }
        return metricDirectoryRepository.findByParentIdOrderBySortOrderAsc(parentId).stream()
            .map(metricDirectoryMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<MetricDirectoryDTO> findOne(Long id) {
        LOG.debug("Request to get MetricDirectory : {}", id);
        return metricDirectoryRepository.findById(id).map(metricDirectoryMapper::toDto);
    }

    /**
     * 删除目录：子目录与目录下的指标移动到根目录，避免悬挂引用。
     */
    public void delete(Long id) {
        LOG.debug("Request to delete MetricDirectory : {}", id);
        metricDirectoryRepository.findByParentIdOrderBySortOrderAsc(id).forEach(child -> child.setParentId(null));
        metricRepository.findByDirectoryId(id).forEach(metric -> metric.setDirectoryId(null));
        metricDirectoryRepository.deleteById(id);
    }
}
