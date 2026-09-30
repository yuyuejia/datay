package com.data.datafusion.web.rest;

import com.data.datafusion.domain.Authority;
import com.data.datafusion.repository.AuthorityRepository;
import com.data.datafusion.repository.RoleDataScopeRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.data.datafusion.domain.Authority}.
 */
@RestController
@RequestMapping("/api/authorities")
@Transactional
public class AuthorityResource {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorityResource.class);

    private static final String ENTITY_NAME = "adminAuthority";

    /** 角色名前缀，保证与 hasAuthority/hasRole 断言一致。 */
    private static final String ROLE_PREFIX = "ROLE_";

    /** 角色名最大长度。 */
    private static final int NAME_MAX_LENGTH = 50;

    /** 内置角色，禁止删除。 */
    private static final Set<String> BUILT_IN_ROLES = Set.of("ROLE_ADMIN", "ROLE_TENANT_ADMIN", "ROLE_USER");

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AuthorityRepository authorityRepository;
    private final UserRepository userRepository;
    private final RoleDataScopeRepository roleDataScopeRepository;

    public AuthorityResource(
        AuthorityRepository authorityRepository,
        UserRepository userRepository,
        RoleDataScopeRepository roleDataScopeRepository
    ) {
        this.authorityRepository = authorityRepository;
        this.userRepository = userRepository;
        this.roleDataScopeRepository = roleDataScopeRepository;
    }

    /**
     * {@code POST  /authorities} : Create a new authority.
     *
     * @param authority the authority to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new authority, or with status {@code 400 (Bad Request)} if the authority has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<Authority> createAuthority(@Valid @RequestBody Authority authority) throws URISyntaxException {
        LOG.debug("REST request to save Authority : {}", authority);
        String name = normalizeName(authority.getName());
        if (name.isEmpty()) {
            throw new BadRequestAlertException("角色名不能为空", ENTITY_NAME, "namenull");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new BadRequestAlertException("角色名长度不能超过 " + NAME_MAX_LENGTH + " 个字符", ENTITY_NAME, "nametoolong");
        }
        if (authorityRepository.existsById(name)) {
            throw new BadRequestAlertException("角色已存在", ENTITY_NAME, "idexists");
        }
        authority.setName(name);
        authority = authorityRepository.save(authority);
        // 角色名可能包含中文等非 ASCII 字符，需编码后再构建 Location，避免 URISyntaxException
        URI location = UriComponentsBuilder.fromPath("/api/authorities/{name}").buildAndExpand(authority.getName()).encode().toUri();
        return ResponseEntity.created(location)
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, authority.getName()))
            .body(authority);
    }

    /**
     * {@code PUT  /authorities/:name} : Update the authority description.
     */
    @PutMapping("/{name}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<Authority> updateAuthority(@PathVariable("name") String name, @RequestBody Authority authority) {
        LOG.debug("REST request to update Authority : {}, {}", name, authority);
        Optional<Authority> existing = authorityRepository.findById(name);
        if (existing.isEmpty()) {
            throw new BadRequestAlertException("角色不存在", ENTITY_NAME, "idnotfound");
        }
        Authority entity = existing.get();
        entity.setDescription(authority.getDescription());
        entity = authorityRepository.save(entity);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, entity.getName()))
            .body(entity);
    }

    /**
     * {@code GET  /authorities} : get all the authorities.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of authorities in body.
     */
    @GetMapping("")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public List<Authority> getAllAuthorities() {
        LOG.debug("REST request to get all Authorities");
        return authorityRepository.findAll();
    }

    /**
     * {@code GET  /authorities/:id} : get the "id" authority.
     *
     * @param id the id of the authority to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the authority, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<Authority> getAuthority(@PathVariable("id") String id) {
        LOG.debug("REST request to get Authority : {}", id);
        Optional<Authority> authority = authorityRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(authority);
    }

    /**
     * {@code DELETE  /authorities/:id} : delete the "id" authority.
     *
     * <p>内置角色、已被用户分配的角色、以及存在数据权限规则的角色均不允许删除。
     *
     * @param id the id of the authority to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteAuthority(@PathVariable("id") String id) {
        LOG.debug("REST request to delete Authority : {}", id);
        if (BUILT_IN_ROLES.contains(id)) {
            throw new BadRequestAlertException("内置角色不可删除", ENTITY_NAME, "builtin");
        }
        long userCount = userRepository.countByAuthoritiesName(id);
        if (userCount > 0) {
            throw new BadRequestAlertException("该角色已分配给 " + userCount + " 个用户，请先解除分配", ENTITY_NAME, "inuse");
        }
        if (!roleDataScopeRepository.findByRoleName(id).isEmpty()) {
            throw new BadRequestAlertException("该角色存在数据权限规则，请先在数据权限中删除", ENTITY_NAME, "hasScope");
        }
        authorityRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    private String normalizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String name = rawName.trim();
        if (name.isEmpty()) {
            return "";
        }
        if (!name.startsWith(ROLE_PREFIX)) {
            name = ROLE_PREFIX + name;
        }
        return name.toUpperCase(java.util.Locale.ROOT);
    }
}
