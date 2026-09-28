package com.data.datafusion.web.rest;

import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.service.EtlTaskStateService;
import com.data.datafusion.service.StatusStorageConfigService;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import com.data.status.StatusStorageStrategy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for managing ETL task state (increment watermark / CDC offset).
 */
@RestController
@RequestMapping("/api/etl-task-state")
public class EtlTaskStateResource {

    private static final Logger LOG = LoggerFactory.getLogger(EtlTaskStateResource.class);

    private static final String ENTITY_NAME = "etlTaskState";

    private final EtlTaskStateService etlTaskStateService;

    public EtlTaskStateResource(EtlTaskStateService etlTaskStateService) {
        this.etlTaskStateService = etlTaskStateService;
    }

    /**
     * {@code GET /etl-task-state/config} : get status storage config.
     */
    @GetMapping("/config")
    public ResponseEntity<StatusStorageConfigService.StatusStorageConfig> getConfig() {
        return ResponseEntity.ok(etlTaskStateService.getConfig());
    }

    /**
     * {@code POST /etl-task-state/config} : update status storage config and reload strategy.
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(
        @RequestBody StatusStorageConfigService.StatusStorageConfig config
    ) {
        LOG.info("REST request to update status storage config: type={}", config.type);
        try {
            StatusStorageStrategy strategy = etlTaskStateService.updateConfig(config);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("strategy", strategy.getStrategyName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOG.error("Failed to update status storage config", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * {@code GET /etl-task-state/config/test} : test the configured status storage backend.
     */
    @GetMapping("/config/test")
    public ResponseEntity<Map<String, Object>> testConfig() {
        Map<String, Object> result = new HashMap<>();
        try {
            StatusStorageStrategy strategy = etlTaskStateService.testStrategy();
            result.put("success", true);
            result.put("strategy", strategy.getStrategyName());
        } catch (Exception e) {
            LOG.error("Failed to test status storage config", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET /etl-task-state} : list all tasks that currently have state.
     */
    @GetMapping("")
    public ResponseEntity<List<EtlTaskStateService.StateSummary>> listStates() {
        return ResponseEntity.ok(etlTaskStateService.listStates());
    }

    /**
     * {@code GET /etl-task-state/{taskId}} : get the state of a task.
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> getState(@PathVariable("taskId") Long taskId) {
        ETLTask task = requireTask(taskId);
        Map<String, Object> state = etlTaskStateService.loadState(taskId);
        return ResponseEntity.ok(buildStateResponse(task, state));
    }

    /**
     * {@code PATCH /etl-task-state/{taskId}} : merge (patch) the state of a task.
     *
     * <p>请求体为待修改的 key/value 增量；value 为 null 表示删除该 key。
     */
    @PatchMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> patchState(
        @PathVariable("taskId") Long taskId,
        @RequestBody(required = false) Map<String, Object> patch
    ) {
        ETLTask task = requireTask(taskId);
        guardRunning(task, false);
        Map<String, Object> state = etlTaskStateService.patchState(taskId, patch);
        return ResponseEntity.ok(buildStateResponse(task, state));
    }

    /**
     * {@code DELETE /etl-task-state/{taskId}} : delete the whole state of a task,
     * or a single key when {@code key} is provided.
     */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> deleteState(
        @PathVariable("taskId") Long taskId,
        @RequestParam(value = "key", required = false) String key,
        @RequestParam(value = "force", required = false, defaultValue = "false") boolean force
    ) {
        ETLTask task = requireTask(taskId);
        guardRunning(task, force);
        Map<String, Object> state = etlTaskStateService.deleteState(taskId, key);
        return ResponseEntity.ok(buildStateResponse(task, state));
    }

    private Map<String, Object> buildStateResponse(ETLTask task, Map<String, Object> state) {
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", task.getId());
        response.put("taskName", task.getTaskName());
        response.put("taskCode", task.getTaskCode());
        response.put("jobCode", etlTaskStateService.resolveJobCode(task));
        response.put("state", state);
        response.put("nodeNames", etlTaskStateService.getNodeNames(task.getId()));
        return response;
    }

    private ETLTask requireTask(Long taskId) {
        ETLTask task = etlTaskStateService.findTask(taskId).orElse(null);
        if (task == null) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        return task;
    }

    private void guardRunning(ETLTask task, boolean force) {
        if (!force && etlTaskStateService.isRunning(task.getId())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "任务正在运行中，修改状态可能被运行中的任务覆盖，请先停止任务或使用 force=true"
            );
        }
    }
}
