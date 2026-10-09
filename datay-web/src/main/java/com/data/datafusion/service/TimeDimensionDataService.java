package com.data.datafusion.service;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.TimeDataRequestDTO;
import com.data.datafusion.service.time.TimeGranularity;
import com.data.metadata.util.DBUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 时间维度预置数据生成。
 *
 * <p>按所选最细粒度逐行生成日期数据：每个层级输出 {@code {g}_id}/{@code {g}_name}，
 * 主键列 {@code date_key} 取最末级粒度 id，{@code hierarchy} 为各层级名称由粗到细的拼接。
 */
@Service
public class TimeDimensionDataService {

    private static final Logger LOG = LoggerFactory.getLogger(TimeDimensionDataService.class);

    private static final String DIMENSION_KIND_TIME = "TIME";
    private static final int BATCH_SIZE = 500;

    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final DataSourceService dataSourceService;

    public TimeDimensionDataService(
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        DataSourceService dataSourceService
    ) {
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.dataSourceService = dataSourceService;
    }

    /**
     * 生成并写入预置数据，返回写入行数。
     */
    public int generate(String modelId, TimeDataRequestDTO request) {
        DataModel model = dataModelRepository
            .findById(modelId)
            .orElseThrow(() -> new IllegalArgumentException("数据模型不存在：" + modelId));
        if (!"DIMENSION".equalsIgnoreCase(model.getModelType()) || !DIMENSION_KIND_TIME.equalsIgnoreCase(model.getDimensionKind())) {
            throw new IllegalArgumentException("仅时间维度支持生成预置数据");
        }
        List<TimeGranularity> levels = TimeGranularity.parse(model.getTimeLevels());

        String dataSourceId = request != null && request.getDataSourceId() != null ? request.getDataSourceId() : model.getDataSourceId();
        String schema = firstNonBlank(request == null ? null : request.getSchemaName(), model.getSchemaName());
        String table = firstNonBlank(request == null ? null : request.getTableName(), model.getTableName());
        if (dataSourceId == null || table == null || table.isBlank()) {
            throw new IllegalArgumentException("请先完成物化（指定数据源与物理表）后再生成预置数据");
        }
        String start = firstNonBlank(request == null ? null : request.getStart(), model.getTimeStart());
        String end = firstNonBlank(request == null ? null : request.getEnd(), model.getTimeEnd());
        LocalDate startDate = parseDate(start, "起始日期");
        LocalDate endDate = parseDate(end, "结束日期");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("起始日期不能晚于结束日期");
        }

        DataSourceDTO dataSource = dataSourceService
            .findOne(dataSourceId)
            .orElseThrow(() -> new IllegalArgumentException("数据源不存在：" + dataSourceId));
        List<ModelField> fields = modelFieldRepository.findByModelIdOrderBySortOrderAsc(modelId);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("时间维度未定义字段");
        }
        List<Map<String, String>> rows = buildRows(levels, startDate, endDate);

        String fullTable = schema == null || schema.isBlank() ? table : schema + "." + table;
        boolean overwrite = request != null && Boolean.TRUE.equals(request.getOverwrite());
        try (Connection connection = DBUtils.getConnection(DataSourceQueryService.toDatasourceInfo(dataSource))) {
            if (overwrite) {
                DBUtils.execute(connection, "DELETE FROM " + fullTable);
            }
            insertRows(connection, fullTable, fields, rows);
        } catch (SQLException e) {
            LOG.error("生成时间维度预置数据失败", e);
            throw new IllegalStateException("生成预置数据失败：" + e.getMessage(), e);
        }
        LOG.info("Time dimension data generated: model={}, table={}, rows={}", modelId, fullTable, rows.size());
        return rows.size();
    }

    /**
     * 计算预置数据行（纯计算，便于测试）。
     *
     * <p>按最细粒度去重：同一最细粒度只保留首个日期的取值。
     */
    public List<Map<String, String>> buildRows(List<TimeGranularity> levels, LocalDate start, LocalDate end) {
        if (levels == null || levels.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个时间粒度");
        }
        TimeGranularity finest = levels.get(levels.size() - 1);
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String finestId = finest.idOf(date);
            if (!rows.containsKey(finestId)) {
                rows.put(finestId, buildRow(levels, date));
            }
        }
        return new ArrayList<>(rows.values());
    }

    private Map<String, String> buildRow(List<TimeGranularity> levels, LocalDate date) {
        Map<String, String> row = new LinkedHashMap<>();
        StringBuilder hierarchy = new StringBuilder();
        for (TimeGranularity level : levels) {
            row.put(level.idFieldName(), level.idOf(date));
            row.put(level.nameFieldName(), level.nameOf(date));
            if (hierarchy.length() > 0) {
                hierarchy.append('/');
            }
            hierarchy.append(level.nameOf(date));
        }
        TimeGranularity finest = levels.get(levels.size() - 1);
        row.put("date_key", finest.idOf(date));
        row.put("hierarchy", hierarchy.toString());
        return row;
    }

    private void insertRows(Connection connection, String fullTable, List<ModelField> fields, List<Map<String, String>> rows)
        throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        List<String> columns = new ArrayList<>();
        for (ModelField field : fields) {
            columns.add(field.getFieldName());
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + fullTable + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int count = 0;
            for (Map<String, String> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    ps.setString(i + 1, row.get(columns.get(i)));
                }
                ps.addBatch();
                if (++count % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private static LocalDate parseDate(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "格式错误，应为 yyyy-MM-dd：" + value);
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
