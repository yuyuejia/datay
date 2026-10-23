package com.data.datafusion.web.rest;

import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.alert.AlertRecipientOptionService;
import com.data.datafusion.service.alert.AlertRuleService;
import com.data.datafusion.service.dto.AlertRecipientOptionsDTO;
import com.data.datafusion.service.dto.AlertRuleDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * 告警规则管理。
 */
@RestController
@RequestMapping("/api/alert-rules")
public class AlertRuleResource {

    private static final Logger LOG = LoggerFactory.getLogger(AlertRuleResource.class);

    private static final String ENTITY_NAME = "alertRule";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AlertRuleService alertRuleService;

    private final AlertRecipientOptionService alertRecipientOptionService;

    public AlertRuleResource(AlertRuleService alertRuleService, AlertRecipientOptionService alertRecipientOptionService) {
        this.alertRuleService = alertRuleService;
        this.alertRecipientOptionService = alertRecipientOptionService;
    }

    @GetMapping("")
    public ResponseEntity<List<AlertRuleDTO>> getAllAlertRules() {
        return ResponseEntity.ok(alertRuleService.findAll());
    }

    /**
     * 当前租户内可选的站内信接收人。告警中心对所有登录用户开放，因此不能依赖管理员用户/角色接口。
     */
    @GetMapping("/recipient-options")
    public ResponseEntity<AlertRecipientOptionsDTO> getRecipientOptions() {
        Long tenantId = SecurityUtils.getCurrentTenantId().orElse(null);
        return ResponseEntity.ok(alertRecipientOptionService.getOptions(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertRuleDTO> getAlertRule(@PathVariable("id") String id) {
        return ResponseUtil.wrapOrNotFound(alertRuleService.findOne(id));
    }

    @PostMapping("")
    public ResponseEntity<AlertRuleDTO> createAlertRule(@RequestBody AlertRuleDTO dto) throws URISyntaxException {
        if (dto.id() != null) {
            throw new BadRequestAlertException("A new alertRule cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            AlertRuleDTO result = alertRuleService.save(dto);
            return ResponseEntity.created(new URI("/api/alert-rules/" + result.id()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.id()))
                .body(result);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidAlertRule");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertRuleDTO> updateAlertRule(@PathVariable("id") String id, @RequestBody AlertRuleDTO dto) {
        if (dto.id() != null && !id.equals(dto.id())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        try {
            Optional<AlertRuleDTO> result = alertRuleService.update(id, dto);
            return ResponseUtil.wrapOrNotFound(
                result,
                HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)
            );
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidAlertRule");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlertRule(@PathVariable("id") String id) {
        LOG.debug("REST request to delete alert rule: {}", id);
        alertRuleService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id))
            .build();
    }
}
