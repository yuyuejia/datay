package com.data.datafusion.web.rest;

import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.job.DatasourceInfo;
import com.data.metadata.util.DBUtils;
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

    private static DatasourceInfo toDatasourceInfo(DataSourceDTO dto) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(dto.getType());
        info.setUrl(dto.getUrl());
        info.setUsername(dto.getUsername());
        info.setPassword(dto.getPassword());
        info.setDbschema(dto.getSchemaName());
        info.setPort(dto.getPort());
        info.setHostname(dto.getHostname());
        return info;
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
     * @param search   the optional keyword used to filter by name, hostname/IP, port, url, schema name or username.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of dataSources in body.
     */
    @GetMapping("")
    public ResponseEntity<List<DataSourceDTO>> getAllDataSources(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(value = "search", required = false) String search
    ) {
        LOG.debug("REST request to get a page of DataSources with search: {}", search);
        Page<DataSourceDTO> page = dataSourceService.findAll(pageable, search);
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
            try (Connection connection = DBUtils.getConnection(toDatasourceInfo(dataSource));) {
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
            try (Connection connection = DBUtils.getConnection(toDatasourceInfo(dataSource));) {
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
            try (Connection connection = DBUtils.getConnection(toDatasourceInfo(dataSource));) {
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

    /**
     * 根据数据源ID执行SQL查询
     * @param id 数据源ID
     * @param request 请求体，包含sql字段
     * @return 查询结果，包含columns、rows和affectedRows
     */
    @PostMapping("/{id}/query")
    public ResponseEntity<Map<String, Object>> executeQuery(
        @PathVariable("id") Long id,
        @RequestBody Map<String, String> request
    ) {
        String sql = request.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", "SQL语句不能为空");
            return ResponseEntity.badRequest().body(error);
        }

        LOG.debug("REST request to execute query on DataSource {} : {}", id, sql);
        Map<String, Object> result = new HashMap<>();

        Optional<DataSourceDTO> dataSourceDTO = dataSourceService.findOne(id);
        if (dataSourceDTO.isEmpty()) {
            result.put("message", "数据源不存在");
            return ResponseEntity.status(404).body(result);
        }

        DataSourceDTO dataSource = dataSourceDTO.orElseThrow();
        try (Connection connection = DBUtils.getConnection(toDatasourceInfo(dataSource))) {
            String trimmedSql = sql.trim();
            boolean isSelect = trimmedSql.toUpperCase().startsWith("SELECT")
                || trimmedSql.toUpperCase().startsWith("WITH")
                || trimmedSql.toUpperCase().startsWith("SHOW")
                || trimmedSql.toUpperCase().startsWith("DESCRIBE")
                || trimmedSql.toUpperCase().startsWith("DESC")
                || trimmedSql.toUpperCase().startsWith("EXPLAIN");

            if (isSelect) {
                try (java.sql.Statement stmt = connection.createStatement();
                     java.sql.ResultSet rs = stmt.executeQuery(sql)) {

                    java.sql.ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    int rowCount = 0;
                    int maxRows = 10000;
                    while (rs.next() && rowCount < maxRows) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String colName = meta.getColumnLabel(i);
                            int sqlType = meta.getColumnType(i);
                            Object value = rs.getObject(i);
                            if (value == null) {
                                row.put(colName, null);
                            } else if (value instanceof java.sql.Timestamp) {
                                row.put(colName, ((java.sql.Timestamp) value).toString());
                            } else if (value instanceof java.sql.Date) {
                                row.put(colName, ((java.sql.Date) value).toString());
                            } else if (value instanceof java.sql.Time) {
                                row.put(colName, ((java.sql.Time) value).toString());
                            } else if (value instanceof java.math.BigDecimal) {
                                row.put(colName, ((java.math.BigDecimal) value).doubleValue());
                            } else if (value instanceof byte[]) {
                                row.put(colName, "[BLOB]");
                            } else if (value instanceof java.sql.Clob) {
                                java.sql.Clob clob = (java.sql.Clob) value;
                                row.put(colName, clob.getSubString(1, (int) clob.length()));
                            } else if (value instanceof java.sql.Blob) {
                                row.put(colName, "[BLOB]");
                            } else {
                                row.put(colName, value);
                            }
                        }
                        rows.add(row);
                        rowCount++;
                    }

                    result.put("columns", columns);
                    result.put("rows", rows);
                    result.put("affectedRows", rowCount);
                }
            } else {
                try (java.sql.Statement stmt = connection.createStatement()) {
                    int affectedRows = stmt.executeUpdate(sql);
                    result.put("columns", Collections.emptyList());
                    result.put("rows", Collections.emptyList());
                    result.put("affectedRows", affectedRows);
                }
            }
        } catch (SQLException e) {
            LOG.error("SQL execution error", e);
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }

        return ResponseEntity.ok().body(result);
    }
}