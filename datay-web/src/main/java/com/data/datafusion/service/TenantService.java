package com.data.datafusion.service;

import com.data.datafusion.domain.Tenant;
import com.data.datafusion.domain.User;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.dto.AdminUserDTO;
import com.data.datafusion.service.dto.TenantDTO;
import com.data.datafusion.service.mapper.TenantMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;

    private final UserRepository userRepository;

    private final TenantMapper tenantMapper;

    public TenantService(TenantRepository tenantRepository, UserRepository userRepository, TenantMapper tenantMapper) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.tenantMapper = tenantMapper;
    }

    public TenantDTO save(TenantDTO dto) {
        LOG.debug("Save Tenant : {}", dto);
        Tenant tenant = tenantMapper.toEntity(dto);
        String login = SecurityUtils.getCurrentUserLogin().orElse("system");
        Instant now = Instant.now();
        if (tenant.getId() == null) {
            tenant.setCreatedBy(login);
            tenant.setCreatedDate(now);
        }
        tenant.setLastModifiedBy(login);
        tenant.setLastModifiedDate(now);
        tenant = tenantRepository.save(tenant);
        return tenantMapper.toDto(tenant);
    }

    public Optional<TenantDTO> partialUpdate(TenantDTO dto) {
        LOG.debug("Partial update Tenant : {}", dto);
        return tenantRepository
            .findById(dto.getId())
            .map(existing -> {
                tenantMapper.partialUpdate(existing, dto);
                existing.setLastModifiedBy(SecurityUtils.getCurrentUserLogin().orElse("system"));
                existing.setLastModifiedDate(Instant.now());
                return tenantRepository.save(existing);
            })
            .map(tenantMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<TenantDTO> findAll() {
        LOG.debug("Get all Tenants");
        return tenantRepository.findAll().stream().map(tenantMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<TenantDTO> findOne(Long id) {
        LOG.debug("Get Tenant : {}", id);
        return tenantRepository.findById(id).map(tenantMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<TenantDTO> findByCode(String code) {
        return tenantRepository.findByCode(code).map(tenantMapper::toDto);
    }

    public void delete(Long id) {
        LOG.debug("Delete Tenant : {}", id);
        tenantRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<TenantDTO> findAllByCurrentUser() {
        return SecurityUtils
            .getCurrentUserId()
            .map(tenantRepository::findAllByUserId)
            .map(list -> list.stream().map(tenantMapper::toDto).toList())
            .orElse(List.of());
    }

    public void addUserToTenant(Long tenantId, Long userId) {
        LOG.debug("Add user {} to tenant {}", userId, tenantId);
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        tenant.getUsers().add(user);
        tenantRepository.save(tenant);
    }

    public void removeUserFromTenant(Long tenantId, Long userId) {
        LOG.debug("Remove user {} from tenant {}", userId, tenantId);
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        tenant.getUsers().remove(user);
        tenantRepository.save(tenant);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserDTO> getTenantUsers(Long tenantId, Pageable pageable) {
        LOG.debug("Get users of tenant {}", tenantId);
        return userRepository.findAllByTenantId(tenantId, pageable).map(AdminUserDTO::new);
    }
}