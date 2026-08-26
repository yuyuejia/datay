package com.data.datafusion.web.rest;

import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.util.DBUtils;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.data.datafusion.domain.DataSource}.
 */
@RestController
@RequestMapping("/api/data-sources")
public class DataSourceResource {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceResource.class);

    private static final String ENTITY_NAME = "dataSource";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DataSourceService dataSourceService;

    private final DataSourceRepository dataSourceRepository;

    public DataSourceResource(DataSourceService dataSourceService, DataSourceRepository dataSourceRepository) {
        this.dataSourceService = dataSourceService;
        this.dataSourceRepository = dataSourceRepository;
    }

    /**
     * {@code POST  /data-sources} : Create a new dataSource.
     *
     * @param dataSourceDTO the dataSourceDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new dataSourceDTO, or with status {@code 400 (Bad Request)} if the dataSource has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<DataSourceDTO> createDataSource(@RequestBody DataSourceDTO dataSourceDTO) throws URISyntaxException {
        LOG.debug("REST request to save DataSource : {}", dataSourceDTO);
        if (dataSourceDTO.getId() != null) {
            throw new BadRequestAlertException("A new dataSource cannot already have an ID", ENTITY_NAME, "idexists");
        }
        dataSourceDTO = dataSourceService.save(dataSourceDTO);
        return ResponseEntity.created(new URI("/api/data-sources/" + dataSourceDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, dataSourceDTO.getId().toString()))
            .body(dataSourceDTO);
    }

    /**
     * {@code PUT  /data-sources/:id} : Updates an existing dataSource.
     *
     * @param id the id of the dataSourceDTO to save.
     * @param dataSourceDTO the dataSourceDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dataSourceDTO,
     * or with status {@code 400 (Bad Request)} if the dataSourceDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the dataSourceDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<DataSourceDTO> updateDataSource(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody DataSourceDTO dataSourceDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update DataSource : {}, {}", id, dataSourceDTO);
        if (dataSourceDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dataSourceDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dataSourceRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        dataSourceDTO = dataSourceService.update(dataSourceDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dataSourceDTO.getId().toString()))
            .body(dataSourceDTO);
    }

    /**
     * {@code PATCH  /data-sources/:id} : Partial updates given fields of an existing dataSource, field will ignore if it is null
     *
     * @param id the id of the dataSourceDTO to save.
     * @param dataSourceDTO the dataSourceDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dataSourceDTO,
     * or with status {@code 400 (Bad Request)} if the dataSourceDTO is not valid,
     * or with status {@code 404 (Not Found)} if the dataSourceDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the dataSourceDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<DataSourceDTO> partialUpdateDataSource(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody DataSourceDTO dataSourceDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update DataSource partially : {}, {}", id, dataSourceDTO);
        if (dataSourceDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dataSourceDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dataSourceRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<DataSourceDTO> result = dataSourceService.partialUpdate(dataSourceDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dataSourceDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /data-sources} : get all the dataSources.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of dataSources in body.
     */
    @GetMapping("")
    public ResponseEntity<List<DataSourceDTO>> getAllDataSources(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of DataSources");
        Page<DataSourceDTO> page = dataSourceService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /data-sources/:id} : get the "id" dataSource.
     *
     * @param id the id of the dataSourceDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the dataSourceDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DataSourceDTO> getDataSource(@PathVariable("id") Long id) {
        LOG.debug("REST request to get DataSource : {}", id);
        Optional<DataSourceDTO> dataSourceDTO = dataSourceService.findOne(id);
        return ResponseUtil.wrapOrNotFound(dataSourceDTO);
    }

    /**
     * {@code DELETE  /data-sources/:id} : delete the "id" dataSource.
     *
     * @param id the id of the dataSourceDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataSource(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete DataSource : {}", id);
        dataSourceService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    //根据数据库id获取schema信息
    @GetMapping("/{id}/schemas")
    public ResponseEntity<List<String>> getSchema(@PathVariable("id") Long id) throws SQLException {
        LOG.debug("REST request to get DataSource schemas : {}", id);
        List<String> schemas = new ArrayList<>();
        Optional<DataSourceDTO> dataSourceDTO = dataSourceService.findOne(id);
        if (dataSourceDTO.isPresent()) {
            DataSourceDTO dataSource = dataSourceDTO.orElseThrow();
            try (Connection connection = DBUtils.getConnection(dataSource);) {
                schemas = DBUtils.getSchemas(connection);
            }
        }
        return ResponseEntity.ok().body(schemas);
    }

    //根据数据源id和schema获取表清单
    @GetMapping("/{id}/schemas/{schema}/tables")
    public ResponseEntity<List<TableMeta>> getTables(
        @PathVariable("id") Long id,
        @PathVariable("schema") String schema,
        @RequestParam(value = "limit", required = false) Integer limit,
        @RequestParam(value = "search", required = false) String search
    ) throws SQLException {
        LOG.debug("REST request to get DataSource tables : {}", id);
        List<TableMeta> tables = new ArrayList<>();
        Optional<DataSourceDTO> dataSourceDTO = dataSourceService.findOne(id);
        if (dataSourceDTO.isPresent()) {
            DataSourceDTO dataSource = dataSourceDTO.orElseThrow();
            try (Connection connection = DBUtils.getConnection(dataSource);) {
                tables = DBUtils.getTableList(connection, schema, limit, search);
            }
        }
        return ResponseEntity.ok().body(tables);
    }

    //根据数据源id和schema获取字段清单
    @GetMapping("/{id}/schemas/{schema}/tables/{table}/columns")
    public ResponseEntity<List<ColumnMeta>> getColumns(
        @PathVariable("id") Long id,
        @PathVariable("schema") String schema,
        @PathVariable("table") String table
    ) throws SQLException {
        LOG.debug("REST request to get DataSource columns : {}", id);
        List<ColumnMeta> columns = new ArrayList<>();
        Optional<DataSourceDTO> dataSourceDTO = dataSourceService.findOne(id);
        if (dataSourceDTO.isPresent()) {
            DataSourceDTO dataSource = dataSourceDTO.orElseThrow();
            try (Connection connection = DBUtils.getConnection(dataSource);) {
                columns = DBUtils.getTableMetaData(connection, schema, table).columns();
            }
        }
        return ResponseEntity.ok().body(columns);
    }

    /**
     * 测试数据源连接（通过数据源配置）
     * @param dataSourceDTO 数据源配置
     * @return 连接测试结果
     */
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody DataSourceDTO dataSourceDTO) {
        LOG.debug("REST request to test DataSource connection : {}", dataSourceDTO);
        boolean success = dataSourceService.testConnection(dataSourceDTO);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "连接测试成功" : "连接测试失败");
        return ResponseEntity.ok().body(result);
    }
}
