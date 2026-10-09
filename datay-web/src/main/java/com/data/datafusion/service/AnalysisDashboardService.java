package com.data.datafusion.service;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.service.dashboard.DashboardDimensionValidator;
import com.data.datafusion.service.dashboard.DashboardSpecValidator;
import com.data.datafusion.service.dto.AnalysisDashboardDTO;
import com.data.datafusion.service.mapper.AnalysisDashboardMapper;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link AnalysisDashboard}.
 */
@Service
@Transactional
public class AnalysisDashboardService {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisDashboardService.class);

    private final AnalysisDashboardRepository repository;
    private final AnalysisDashboardMapper mapper;
    private final DashboardSpecValidator specValidator;
    private final DashboardDimensionValidator dimensionValidator;

    public AnalysisDashboardService(
        AnalysisDashboardRepository repository,
        AnalysisDashboardMapper mapper,
        DashboardSpecValidator specValidator,
        DashboardDimensionValidator dimensionValidator
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.specValidator = specValidator;
        this.dimensionValidator = dimensionValidator;
    }

    public AnalysisDashboardDTO save(AnalysisDashboardDTO dto) {
        LOG.debug("Request to save AnalysisDashboard : {}", dto);
        validate(dto);
        dto.setCode(resolveCode(dto.getCode(), null));

        ZonedDateTime now = ZonedDateTime.now();
        dto.setId(null);
        dto.setCreateTime(now);
        dto.setUpdateTime(now);
        if (dto.getStatus() == null || dto.getStatus().isBlank()) {
            dto.setStatus(AnalysisDashboard.STATUS_ENABLED);
        }
        dto.setSpec(normalizeSpec(dto.getSpec()));
        AnalysisDashboard entity = repository.save(mapper.toEntity(dto));
        return mapper.toDto(entity);
    }

    public Optional<AnalysisDashboardDTO> update(String id, AnalysisDashboardDTO dto) {
        LOG.debug("Request to update AnalysisDashboard : {}, {}", id, dto);
        validate(dto);
        return repository
            .findById(id)
            .map(existing -> {
                dto.setId(id);
                dto.setTenantId(existing.getTenantId());
                dto.setCreateTime(existing.getCreateTime());
                dto.setUpdateTime(ZonedDateTime.now());
                dto.setCode(resolveCode(dto.getCode() == null || dto.getCode().isBlank() ? existing.getCode() : dto.getCode(), id));
                if (dto.getStatus() == null || dto.getStatus().isBlank()) {
                    dto.setStatus(existing.getStatus() == null ? AnalysisDashboard.STATUS_ENABLED : existing.getStatus());
                }
                dto.setSpec(normalizeSpec(dto.getSpec()));
                AnalysisDashboard entity = repository.save(mapper.toEntity(dto));
                return mapper.toDto(entity);
            });
    }

    @Transactional(readOnly = true)
    public Page<AnalysisDashboardDTO> findAll(Pageable pageable, String search) {
        LOG.debug("Request to get all AnalysisDashboards with search: {}", search);
        Page<AnalysisDashboard> page = repository.findAll(buildSearchSpecification(search), pageable);
        return page.map(mapper::toDto).map(this::stripSpec);
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisDashboardDTO> findOne(String id) {
        LOG.debug("Request to get AnalysisDashboard : {}", id);
        return repository.findById(id).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisDashboardDTO> findByCode(String code) {
        LOG.debug("Request to get AnalysisDashboard by code : {}", code);
        return repository.findByCode(code).map(mapper::toDto);
    }

    public void delete(String id) {
        LOG.debug("Request to delete AnalysisDashboard : {}", id);
        repository.deleteById(id);
    }

    /**
     * 解析看板 spec 为对象，供数据查询使用。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> parseSpec(String id) {
        AnalysisDashboard dashboard = repository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("看板不存在：" + id));
        return specValidator.parse(dashboard.getSpec());
    }

    private AnalysisDashboardDTO stripSpec(AnalysisDashboardDTO dto) {
        dto.setSpec(null);
        return dto;
    }

    /**
     * 规范化看板定义：结构校验，并剔除引用了不存在或无数据维度的筛选器。
     */
    private String normalizeSpec(String specJson) {
        Map<String, Object> spec = specValidator.validateAndNormalize(specJson);
        dimensionValidator.sanitizeFilters(spec);
        return specValidator.toJson(spec);
    }

    private void validate(AnalysisDashboardDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("看板名称不能为空");
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            if (
                !AnalysisDashboard.STATUS_ENABLED.equals(dto.getStatus()) &&
                !AnalysisDashboard.STATUS_DISABLED.equals(dto.getStatus())
            ) {
                throw new IllegalArgumentException("状态只能是 ENABLED 或 DISABLED");
            }
        }
        if (dto.getSpec() == null || dto.getSpec().isBlank()) {
            throw new IllegalArgumentException("看板定义不能为空");
        }
    }

    private String resolveCode(String requested, String excludeId) {
        String candidate = requested;
        if (candidate == null || candidate.isBlank()) {
            candidate = "dashboard_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        if (!candidate.matches("[A-Za-z0-9_\\-]+")) {
            throw new IllegalArgumentException("看板编码只能包含字母、数字、下划线和中划线");
        }
        Optional<AnalysisDashboard> conflict = excludeId == null ? repository.findByCode(candidate) : repository.findByCodeAndIdNot(candidate, excludeId);
        if (conflict.isPresent()) {
            throw new IllegalArgumentException("看板编码已存在：" + candidate);
        }
        return candidate;
    }

    private Specification<AnalysisDashboard> buildSearchSpecification(String search) {
        return (root, query, cb) -> {
            if (search == null || search.trim().isEmpty()) {
                return cb.conjunction();
            }
            String keyword = "%" + search.trim().toLowerCase() + "%";
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            for (String field : new String[] { "name", "code", "description" }) {
                predicates.add(cb.like(cb.lower(root.get(field)), keyword));
            }
            return cb.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public List<AnalysisDashboardDTO> findAllSimple() {
        return repository.findAll().stream().map(mapper::toDto).map(this::stripSpec).collect(Collectors.toList());
    }
}
