package com.data.datafusion.web.rest;

import com.data.datafusion.service.UserAccessTokenService;
import com.data.datafusion.service.dto.CreateUserAccessTokenRequestDTO;
import com.data.datafusion.service.dto.CreatedUserAccessTokenDTO;
import com.data.datafusion.service.dto.UpdateUserAccessTokenRequestDTO;
import com.data.datafusion.service.dto.UserAccessTokenDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing the current user's access tokens.
 */
@RestController
@RequestMapping("/api/account/access-tokens")
public class UserAccessTokenResource {

    private static final Logger LOG = LoggerFactory.getLogger(UserAccessTokenResource.class);

    private static final String ENTITY_NAME = "userAccessToken";

    private final UserAccessTokenService userAccessTokenService;

    public UserAccessTokenResource(UserAccessTokenService userAccessTokenService) {
        this.userAccessTokenService = userAccessTokenService;
    }

    /**
     * {@code GET  /api/account/access-tokens} : list the current user's access tokens.
     */
    @GetMapping("")
    public List<UserAccessTokenDTO> listAccessTokens() {
        LOG.debug("REST request to list access tokens");
        return userAccessTokenService.listCurrentUserTokens();
    }

    /**
     * {@code POST  /api/account/access-tokens} : create a new access token.
     */
    @PostMapping("")
    public ResponseEntity<CreatedUserAccessTokenDTO> createAccessToken(
        @RequestBody(required = false) CreateUserAccessTokenRequestDTO request
    ) {
        LOG.debug("REST request to create access token");
        if (request == null) {
            throw new BadRequestAlertException("请输入访问令牌名称", ENTITY_NAME, "invalidAccessToken");
        }
        try {
            CreatedUserAccessTokenDTO created = userAccessTokenService.createCurrentUserToken(
                request.getName(),
                request.getDescription()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidAccessToken");
        }
    }

    /**
     * {@code PATCH  /api/account/access-tokens/:id} : update an access token's name, description or status.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<UserAccessTokenDTO> updateAccessToken(
        @PathVariable("id") String id,
        @RequestBody(required = false) UpdateUserAccessTokenRequestDTO request
    ) {
        LOG.debug("REST request to update access token {}", id);
        if (request == null) {
            throw new BadRequestAlertException("缺少访问令牌参数", ENTITY_NAME, "invalidAccessToken");
        }
        try {
            return ResponseUtil.wrapOrNotFound(
                userAccessTokenService.updateCurrentUserToken(id, request.getName(), request.getDescription(), request.getStatus())
            );
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidAccessToken");
        }
    }

    /**
     * {@code POST  /api/account/access-tokens/:id/rotate} : regenerate an access token's value.
     */
    @PostMapping("/{id}/rotate")
    public ResponseEntity<CreatedUserAccessTokenDTO> rotateAccessToken(@PathVariable("id") String id) {
        LOG.debug("REST request to rotate access token {}", id);
        return ResponseUtil.wrapOrNotFound(userAccessTokenService.rotateCurrentUserToken(id));
    }

    /**
     * {@code DELETE  /api/account/access-tokens/:id} : revoke an access token.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccessToken(@PathVariable("id") String id) {
        LOG.debug("REST request to delete access token {}", id);
        if (!userAccessTokenService.deleteCurrentUserToken(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "访问令牌不存在");
        }
        return ResponseEntity.noContent().build();
    }
}
