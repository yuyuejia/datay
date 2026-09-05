package com.data.datafusion.service;

import com.data.datafusion.domain.ModelDirectory;
import com.data.datafusion.repository.ModelDirectoryRepository;
import com.data.datafusion.service.dto.ModelDirectoryDTO;
import com.data.datafusion.service.mapper.ModelDirectoryMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ModelDirectoryService {

    private static final Logger LOG = LoggerFactory.getLogger(ModelDirectoryService.class);

    private final ModelDirectoryRepository modelDirectoryRepository;
    private final ModelDirectoryMapper modelDirectoryMapper;

    public ModelDirectoryService(ModelDirectoryRepository modelDirectoryRepository, ModelDirectoryMapper modelDirectoryMapper) {
        this.modelDirectoryRepository = modelDirectoryRepository;
        this.modelDirectoryMapper = modelDirectoryMapper;
    }

    public ModelDirectoryDTO save(ModelDirectoryDTO modelDirectoryDTO) {
        LOG.debug("Request to save ModelDirectory : {}", modelDirectoryDTO);
        ModelDirectory modelDirectory = modelDirectoryMapper.toEntity(modelDirectoryDTO);
        modelDirectory = modelDirectoryRepository.save(modelDirectory);
        return modelDirectoryMapper.toDto(modelDirectory);
    }

    public ModelDirectoryDTO update(ModelDirectoryDTO modelDirectoryDTO) {
        LOG.debug("Request to update ModelDirectory : {}", modelDirectoryDTO);
        ModelDirectory modelDirectory = modelDirectoryMapper.toEntity(modelDirectoryDTO);
        modelDirectory = modelDirectoryRepository.save(modelDirectory);
        return modelDirectoryMapper.toDto(modelDirectory);
    }

    public Optional<ModelDirectoryDTO> partialUpdate(ModelDirectoryDTO modelDirectoryDTO) {
        LOG.debug("Request to partially update ModelDirectory : {}", modelDirectoryDTO);
        return modelDirectoryRepository
            .findById(modelDirectoryDTO.getId())
            .map(existing -> {
                modelDirectoryMapper.partialUpdate(existing, modelDirectoryDTO);
                return existing;
            })
            .map(modelDirectoryRepository::save)
            .map(modelDirectoryMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<ModelDirectoryDTO> findAll() {
        LOG.debug("Request to get all ModelDirectories");
        return modelDirectoryRepository.findAll().stream()
            .map(modelDirectoryMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ModelDirectoryDTO> findByParentId(Long parentId) {
        LOG.debug("Request to get ModelDirectories by parentId : {}", parentId);
        if (parentId == null) {
            return modelDirectoryRepository.findByParentIdIsNullOrderBySortOrderAsc().stream()
                .map(modelDirectoryMapper::toDto)
                .collect(Collectors.toList());
        }
        return modelDirectoryRepository.findByParentIdOrderBySortOrderAsc(parentId).stream()
            .map(modelDirectoryMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ModelDirectoryDTO> findOne(Long id) {
        LOG.debug("Request to get ModelDirectory : {}", id);
        return modelDirectoryRepository.findById(id).map(modelDirectoryMapper::toDto);
    }

    public void delete(Long id) {
        LOG.debug("Request to delete ModelDirectory : {}", id);
        modelDirectoryRepository.deleteById(id);
    }
}