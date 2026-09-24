package com.data.datafusion.service;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.service.dto.DataModelDTO;
import com.data.datafusion.service.mapper.DataModelMapper;
import com.data.datafusion.service.time.TimeGranularity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DataModelService {

    private static final Logger LOG = LoggerFactory.getLogger(DataModelService.class);

    private final DataModelRepository dataModelRepository;
    private final DataModelMapper dataModelMapper;

    public DataModelService(DataModelRepository dataModelRepository, DataModelMapper dataModelMapper) {
        this.dataModelRepository = dataModelRepository;
        this.dataModelMapper = dataModelMapper;
    }

    public DataModelDTO save(DataModelDTO dataModelDTO) {
        LOG.debug("Request to save DataModel : {}", dataModelDTO);
        validateTimeDimension(dataModelDTO);
        DataModel dataModel = dataModelMapper.toEntity(dataModelDTO);
        dataModel = dataModelRepository.save(dataModel);
        return dataModelMapper.toDto(dataModel);
    }

    public DataModelDTO update(DataModelDTO dataModelDTO) {
        LOG.debug("Request to update DataModel : {}", dataModelDTO);
        validateTimeDimension(dataModelDTO);
        DataModel dataModel = dataModelMapper.toEntity(dataModelDTO);
        dataModel = dataModelRepository.save(dataModel);
        return dataModelMapper.toDto(dataModel);
    }

    /**
     * 时间维度校验：至少选择一个合法粒度，日期范围合法。
     */
    private void validateTimeDimension(DataModelDTO dto) {
        if (!"DIMENSION".equalsIgnoreCase(dto.getModelType()) || !"TIME".equalsIgnoreCase(dto.getDimensionKind())) {
            return;
        }
        TimeGranularity.parse(dto.getTimeLevels());
        LocalDate start = parseDate(dto.getTimeStart());
        LocalDate end = parseDate(dto.getTimeEnd());
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("起始日期不能晚于结束日期");
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("日期格式错误，应为 yyyy-MM-dd：" + value);
        }
    }

    public Optional<DataModelDTO> partialUpdate(DataModelDTO dataModelDTO) {
        LOG.debug("Request to partially update DataModel : {}", dataModelDTO);
        return dataModelRepository
            .findById(dataModelDTO.getId())
            .map(existing -> {
                dataModelMapper.partialUpdate(existing, dataModelDTO);
                return existing;
            })
            .map(dataModelRepository::save)
            .map(dataModelMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<DataModelDTO> findAll() {
        LOG.debug("Request to get all DataModels");
        return dataModelRepository.findAll().stream()
            .map(dataModelMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DataModelDTO> findByDirectoryId(Long directoryId) {
        LOG.debug("Request to get DataModels by directoryId : {}", directoryId);
        return dataModelRepository.findByDirectoryId(directoryId).stream()
            .map(dataModelMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DataModelDTO> findByModelType(String modelType) {
        LOG.debug("Request to get DataModels by modelType : {}", modelType);
        return dataModelRepository.findByModelType(modelType).stream()
            .map(dataModelMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<DataModelDTO> findOne(Long id) {
        LOG.debug("Request to get DataModel : {}", id);
        return dataModelRepository.findById(id).map(dataModelMapper::toDto);
    }

    public void delete(Long id) {
        LOG.debug("Request to delete DataModel : {}", id);
        dataModelRepository.deleteById(id);
    }
}