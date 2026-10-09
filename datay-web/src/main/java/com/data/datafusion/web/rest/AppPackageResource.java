package com.data.datafusion.web.rest;

import com.data.datafusion.service.apppackage.AppPackageInstanceService;
import com.data.datafusion.service.apppackage.AppPackageService;
import com.data.datafusion.service.dto.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for the data service application market (数据服务应用市场).
 *
 * <p>提供资产包的浏览、导出、初始化与删除能力：
 * <ul>
 *   <li>{@code GET    /api/app-packages}                浏览市场（系统预制包 + 本租户的包）</li>
 *   <li>{@code GET    /api/app-packages/export-options} 导出向导可选资产</li>
 *   <li>{@code GET    /api/app-packages/{id}}           资产包详情（含内容）</li>
 *   <li>{@code GET    /api/app-packages/{id}/content}   下载资产包 JSON</li>
 *   <li>{@code POST   /api/app-packages/export}         按选择导出资产包</li>
 *   <li>{@code POST   /api/app-packages/import}         基于资产包 ID 或上传的 JSON 初始化数据应用</li>
 *   <li>{@code DELETE /api/app-packages/{id}}           删除本租户导出的资产包</li>
 *   <li>{@code GET    /api/app-packages/instances}      初始化历史</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/app-packages")
public class AppPackageResource {

    private static final Logger LOG = LoggerFactory.getLogger(AppPackageResource.class);

    private static final String ENTITY_NAME = "appPackage";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AppPackageService appPackageService;

    private final AppPackageInstanceService appPackageInstanceService;

    public AppPackageResource(AppPackageService appPackageService, AppPackageInstanceService appPackageInstanceService) {
        this.appPackageService = appPackageService;
        this.appPackageInstanceService = appPackageInstanceService;
    }

    /**
     * {@code GET /api/app-packages} : 浏览数据应用市场。
     *
     * @param source 过滤来源：ALL / SYSTEM / TENANT
     */
    @GetMapping("")
    public ResponseEntity<List<AppPackageDTO>> getAllAppPackages(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(value = "search", required = false) String search,
        @RequestParam(value = "source", required = false) String source,
        @RequestParam(value = "category", required = false) String category
    ) {
        LOG.debug("REST request to get a page of AppPackages: search={}, source={}, category={}", search, source, category);
        Page<AppPackageDTO> page = appPackageService.findAll(pageable, search, source, category);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /** {@code GET /api/app-packages/categories} : 市场里出现过的业务场景分类。 */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(appPackageService.findCategories());
    }

    /** {@code GET /api/app-packages/export-options} : 导出向导可选的数据资产。 */
    @GetMapping("/export-options")
    public ResponseEntity<AppPackageExportOptionsDTO> getExportOptions() {
        return ResponseEntity.ok(appPackageService.findExportOptions());
    }

    /** {@code GET /api/app-packages/instances} : 当前租户的数据应用初始化历史。 */
    @GetMapping("/instances")
    public ResponseEntity<List<AppPackageInstanceDTO>> getInstances(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        Page<AppPackageInstanceDTO> page = appPackageInstanceService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /** {@code GET /api/app-packages/:id} : 资产包详情，含完整内容。 */
    @GetMapping("/{id}")
    public ResponseEntity<AppPackageDTO> getAppPackage(@PathVariable("id") String id, @RequestParam(value = "includeContent", required = false, defaultValue = "true") boolean includeContent) {
        LOG.debug("REST request to get AppPackage : {}", id);
        Optional<AppPackageDTO> dto = appPackageService.findOne(id, includeContent);
        return ResponseUtil.wrapOrNotFound(dto);
    }

    /** {@code GET /api/app-packages/:id/content} : 下载资产包 JSON 文件。 */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> downloadContent(@PathVariable("id") String id) {
        LOG.debug("REST request to download AppPackage content : {}", id);
        AppPackageDTO dto = appPackageService.findOne(id, true).orElse(null);
        if (dto == null || dto.getContent() == null) {
            return ResponseEntity.notFound().build();
        }
        String fileName = (dto.getCode() == null ? "app-package" : dto.getCode()) + ".json";
        byte[] body = dto.getContent().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .contentType(MediaType.APPLICATION_JSON)
            .contentLength(body.length)
            .body(body);
    }

    /**
     * {@code POST /api/app-packages/export} : 把选中的数据资产导出为一个资产包。
     *
     * @param request 选择范围与资产包元信息
     * @return 生成的资产包（含内容 JSON）
     */
    @PostMapping("/export")
    public ResponseEntity<AppPackageDTO> exportAppPackage(@RequestBody AppPackageExportRequestDTO request) {
        LOG.debug("REST request to export AppPackage : {}", request == null ? null : request.name);
        try {
            return ResponseEntity.ok(appPackageService.export(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * {@code POST /api/app-packages/import} : 基于资产包初始化数据应用。
     * 传 {@code packageId} 使用市场中已有的包，或直接传 {@code content}（上传的资产包 JSON）。
     */
    @PostMapping("/import")
    public ResponseEntity<AppPackageInitResultDTO> importAppPackage(@RequestBody AppPackageInitRequestDTO request) {
        LOG.debug("REST request to initialize app from package : {}", request == null ? null : request.packageId);
        try {
            return ResponseEntity.ok(appPackageService.initialize(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** {@code POST /api/app-packages/:id/init} : 用市场上的某个资产包初始化数据应用。 */
    @PostMapping("/{id}/init")
    public ResponseEntity<AppPackageInitResultDTO> initFromPackage(@PathVariable("id") String id, @RequestBody(required = false) AppPackageInitRequestDTO request) {
        LOG.debug("REST request to initialize app from package {} ", id);
        AppPackageInitRequestDTO payload = request == null ? new AppPackageInitRequestDTO() : request;
        payload.packageId = id;
        try {
            return ResponseEntity.ok(appPackageService.initialize(payload));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** {@code DELETE /api/app-packages/:id} : 删除本租户导出的资产包（系统预制包不可删除）。 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAppPackage(@PathVariable("id") String id) {
        LOG.debug("REST request to delete AppPackage : {}", id);
        try {
            appPackageService.delete(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
