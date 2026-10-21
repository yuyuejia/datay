package com.data.datafusion.web.rest;

import com.data.datafusion.service.alert.AlertChannelService;
import com.data.datafusion.service.dto.AlertChannelDTO;
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
 * 告警通知渠道管理。
 */
@RestController
@RequestMapping("/api/alert-channels")
public class AlertChannelResource {

    private static final Logger LOG = LoggerFactory.getLogger(AlertChannelResource.class);

    private static final String ENTITY_NAME = "alertChannel";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AlertChannelService alertChannelService;

    public AlertChannelResource(AlertChannelService alertChannelService) {
        this.alertChannelService = alertChannelService;
    }

    @GetMapping("")
    public ResponseEntity<List<AlertChannelDTO>> getAllAlertChannels() {
        return ResponseEntity.ok(alertChannelService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertChannelDTO> getAlertChannel(@PathVariable("id") String id) {
        return ResponseUtil.wrapOrNotFound(alertChannelService.findOne(id));
    }

    @PostMapping("")
    public ResponseEntity<AlertChannelDTO> createAlertChannel(@RequestBody AlertChannelDTO dto) throws URISyntaxException {
        if (dto.id() != null) {
            throw new BadRequestAlertException("A new alertChannel cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            AlertChannelDTO result = alertChannelService.save(dto);
            return ResponseEntity.created(new URI("/api/alert-channels/" + result.id()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.id()))
                .body(result);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidAlertChannel");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertChannelDTO> updateAlertChannel(@PathVariable("id") String id, @RequestBody AlertChannelDTO dto) {
        if (dto.id() != null && !id.equals(dto.id())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        try {
            Optional<AlertChannelDTO> result = alertChannelService.update(id, dto);
            return ResponseUtil.wrapOrNotFound(
                result,
                HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)
            );
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalidAlertChannel");
        }
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Void> testAlertChannel(@PathVariable("id") String id) {
        LOG.debug("REST request to test alert channel: {}", id);
        try {
            alertChannelService.test(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "alertChannelTestFailed");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlertChannel(@PathVariable("id") String id) {
        alertChannelService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id))
            .build();
    }
}
