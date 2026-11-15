package com.data.datafusion.web.rest;

import com.data.datafusion.service.datamap.DataMapQueryService;
import com.data.datafusion.service.dto.datamap.DataMapAssetDetailDTO;
import com.data.datafusion.service.dto.datamap.DataMapGraphDTO;
import com.data.datafusion.service.dto.datamap.DataMapIndexStatusDTO;
import com.data.datafusion.service.dto.datamap.DataMapNodeDTO;
import com.data.datafusion.service.dto.datamap.DataMapOverviewDTO;
import com.data.datafusion.service.dto.datamap.DataMapSearchResultDTO;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 数据地图查询与索引管理接口。
 */
@RestController
@RequestMapping("/api/data-map")
public class DataMapResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataMapResource.class);

    private final DataMapQueryService queryService;

    public DataMapResource(DataMapQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/overview")
    public ResponseEntity<DataMapOverviewDTO> overview() {
        return ResponseEntity.ok(queryService.overview());
    }

    @GetMapping("/index/status")
    public ResponseEntity<DataMapIndexStatusDTO> status() {
        return ResponseEntity.ok(queryService.status());
    }

    @PostMapping("/reindex")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_TENANT_ADMIN')")
    public ResponseEntity<DataMapIndexStatusDTO> reindex() {
        LOG.info("REST request to rebuild data map index");
        return ResponseEntity.accepted().body(queryService.rebuild());
    }

    @GetMapping("/search")
    public ResponseEntity<DataMapSearchResultDTO> search(
        @RequestParam(value = "q", required = false) String query,
        @RequestParam(value = "types", required = false) String types,
        @RequestParam(value = "dataSourceId", required = false) String dataSourceId,
        @RequestParam(value = "limit", required = false) Integer limit,
        @RequestParam(value = "offset", required = false) Integer offset
    ) {
        try {
            return ResponseEntity.ok(queryService.search(query, types, dataSourceId, limit, offset));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/graph")
    public ResponseEntity<DataMapGraphDTO> graph(
        @RequestParam(value = "scope", required = false, defaultValue = "full") String scope,
        @RequestParam(value = "rootId", required = false) String rootId,
        @RequestParam(value = "direction", required = false, defaultValue = "both") String direction,
        @RequestParam(value = "depth", required = false) Integer depth,
        @RequestParam(value = "types", required = false) String types,
        @RequestParam(value = "dataSourceId", required = false) String dataSourceId,
        @RequestParam(value = "includeColumns", required = false) Boolean includeColumns,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        try {
            return ResponseEntity.ok(queryService.graph(scope, rootId, direction, depth, types, dataSourceId, includeColumns, limit));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/assets/{nodeId:.+}")
    public ResponseEntity<DataMapAssetDetailDTO> detail(@org.springframework.web.bind.annotation.PathVariable("nodeId") String nodeId) {
        try {
            return ResponseEntity.ok(queryService.detail(nodeId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @GetMapping("/children")
    public ResponseEntity<List<DataMapNodeDTO>> children(@RequestParam("parentId") String parentId) {
        return ResponseEntity.ok(queryService.children(parentId));
    }

    @GetMapping("/types")
    public ResponseEntity<Map<String, String>> types() {
        return ResponseEntity.ok(queryService.types());
    }
}
