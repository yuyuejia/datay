package com.data.datafusion.service;

import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.ModelFieldDTO;
import com.data.datafusion.service.mapper.ModelFieldMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ModelFieldService {

    private static final Logger LOG = LoggerFactory.getLogger(ModelFieldService.class);

    private final ModelFieldRepository modelFieldRepository;
    private final ModelFieldMapper modelFieldMapper;

    public ModelFieldService(ModelFieldRepository modelFieldRepository, ModelFieldMapper modelFieldMapper) {
        this.modelFieldRepository = modelFieldRepository;
        this.modelFieldMapper = modelFieldMapper;
    }

    public ModelFieldDTO save(ModelFieldDTO modelFieldDTO) {
        LOG.debug("Request to save ModelField : {}", modelFieldDTO);
        ModelField modelField = modelFieldMapper.toEntity(modelFieldDTO);
        modelField = modelFieldRepository.save(modelField);
        return modelFieldMapper.toDto(modelField);
    }

    public ModelFieldDTO update(ModelFieldDTO modelFieldDTO) {
        LOG.debug("Request to update ModelField : {}", modelFieldDTO);
        ModelField modelField = modelFieldMapper.toEntity(modelFieldDTO);
        modelField = modelFieldRepository.save(modelField);
        return modelFieldMapper.toDto(modelField);
    }

    public Optional<ModelFieldDTO> partialUpdate(ModelFieldDTO modelFieldDTO) {
        LOG.debug("Request to partially update ModelField : {}", modelFieldDTO);
        return modelFieldRepository
            .findById(modelFieldDTO.getId())
            .map(existing -> {
                modelFieldMapper.partialUpdate(existing, modelFieldDTO);
                return existing;
            })
            .map(modelFieldRepository::save)
            .map(modelFieldMapper::toDto);
    }

    public List<ModelFieldDTO> saveAll(List<ModelFieldDTO> modelFieldDTOs) {
        LOG.debug("Request to save all ModelFields : {}", modelFieldDTOs);
        List<ModelField> modelFields = modelFieldMapper.toEntity(modelFieldDTOs);
        modelFields = modelFieldRepository.saveAll(modelFields);
        return modelFieldMapper.toDto(modelFields);
    }

    @Transactional(readOnly = true)
    public List<ModelFieldDTO> findAll() {
        LOG.debug("Request to get all ModelFields");
        return modelFieldRepository.findAll().stream()
            .map(modelFieldMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ModelFieldDTO> findByModelId(String modelId) {
        LOG.debug("Request to get ModelFields by modelId : {}", modelId);
        return modelFieldRepository.findByModelIdOrderBySortOrderAsc(modelId).stream()
            .map(modelFieldMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ModelFieldDTO> findOne(String id) {
        LOG.debug("Request to get ModelField : {}", id);
        return modelFieldRepository.findById(id).map(modelFieldMapper::toDto);
    }

    public void delete(String id) {
        LOG.debug("Request to delete ModelField : {}", id);
        modelFieldRepository.deleteById(id);
    }

    public void deleteByModelId(String modelId) {
        LOG.debug("Request to delete ModelFields by modelId : {}", modelId);
        modelFieldRepository.deleteByModelId(modelId);
    }
}