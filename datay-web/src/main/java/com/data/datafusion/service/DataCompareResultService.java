package com.data.datafusion.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datafusion.domain.DataCompareTask;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.DataCompareTaskRepository;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.service.dto.DataCompareResultDTO;
import com.data.datafusion.service.dto.DataCompareTableResultDTO;
import com.data.file.FileStorageStrategy;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据比对结果查询服务。
 *
 * <p>不再单独保存结果表：执行状态来自 {@link JobInstance}，结果文件路径、格式等元数据
 * 保存在 JobInstance.jobContext.dataCompare 中；统计信息在 master 查询时按需读取 summary 文件。</p>
 */
@Service
@Transactional
public class DataCompareResultService {

    private static final Logger LOG = LoggerFactory.getLogger(DataCompareResultService.class);

    private static final String META_KEY = "dataCompare";

    private final JobInstanceRepository jobInstanceRepository;

    private final DataCompareTaskRepository taskRepository;

    private final FileStorageStrategy fileStorageStrategy;

    public DataCompareResultService(
        JobInstanceRepository jobInstanceRepository,
        DataCompareTaskRepository taskRepository,
        FileStorageStrategy fileStorageStrategy
    ) {
        this.jobInstanceRepository = jobInstanceRepository;
        this.taskRepository = taskRepository;
        this.fileStorageStrategy = fileStorageStrategy;
    }

    @Transactional(readOnly = true)
    public Optional<DataCompareResultDTO> findByJobInstanceId(String jobInstanceId) {
        return jobInstanceRepository.findById(jobInstanceId).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<DataCompareResultDTO> findByInstanceCode(String instanceCode) {
        return jobInstanceRepository.findFirstByInstanceCode(instanceCode).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<DataCompareResultDTO> findByTaskId(String taskId, Pageable pageable) {
        Optional<DataCompareTask> task = taskRepository.findById(taskId);
        if (task.isEmpty() || task.get().getJobId() == null) {
            return Page.empty(pageable);
        }
        return jobInstanceRepository.findByJobCodeOrderByCreateTimeDesc(task.get().getJobId(), pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<DataCompareResultDTO> findLatestByTaskId(String taskId) {
        return taskRepository
            .findById(taskId)
            .map(DataCompareTask::getJobId)
            .filter(jobId -> jobId != null && !jobId.isBlank())
            .flatMap(jobInstanceRepository::findFirstByJobCodeOrderByCreateTimeDesc)
            .map(this::toDto);
    }

    /**
     * 前端结果详情以 jobInstanceId 作为结果 id。
     */
    @Transactional(readOnly = true)
    public Optional<DataCompareResultDTO> findOne(String id) {
        return findByJobInstanceId(id);
    }

    /**
     * 读取 CSV 结果文件的前 N 行用于页面预览。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(String resultId, int limit, int tableIndex) {
        JobInstance instance = jobInstanceRepository
            .findById(resultId)
            .orElseThrow(() -> new IllegalArgumentException("数据比对执行实例不存在: " + resultId));
        JSONObject metadata = metadata(instance);
        if (metadata == null) {
            throw new IllegalArgumentException("结果文件不存在");
        }
        JSONObject table = tableMetadata(metadata, tableIndex);
        String resultPath = table == null ? metadata.getStr("resultPath") : table.getStr("resultPath");
        if (resultPath == null || resultPath.isBlank()) {
            throw new IllegalArgumentException("结果文件不存在");
        }
        String format = table == null ? metadata.getStr("resultFormat") : table.getStr("resultFormat");
        if (format != null && !"CSV".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("仅 CSV 格式支持预览");
        }
        int rowLimit = Math.max(1, Math.min(limit, 500));
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        try (InputStream inputStream = fileStorageStrategy.download(resultPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return Map.of("columns", columns, "rows", rows, "truncated", false);
            }
            columns = parseCsvLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (rows.size() >= rowLimit) {
                    truncated = true;
                    break;
                }
                List<String> values = parseCsvLine(line);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    row.put(columns.get(i), i < values.size() ? values.get(i) : null);
                }
                rows.add(row);
            }
        } catch (Exception e) {
            throw new RuntimeException("读取数据比对结果失败: " + e.getMessage(), e);
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("columns", columns);
        preview.put("rows", rows);
        preview.put("truncated", truncated);
        return preview;
    }

    public DataCompareResultDTO toDto(JobInstance instance) {
        DataCompareResultDTO dto = new DataCompareResultDTO();
        dto.setId(instance.getId());
        dto.setJobId(instance.getJobCode());
        dto.setJobInstanceId(instance.getId());
        dto.setInstanceCode(instance.getInstanceCode());
        dto.setTaskName(instance.getJobName());
        dto.setStatus(instance.getStatus());
        dto.setStartTime(parseTime(instance.getStartTime()));
        dto.setEndTime(parseTime(instance.getEndTime()));
        dto.setJobMessage(instance.getJobMessage());
        dto.setCreateTime(instance.getCreateTime());
        dto.setTenantId(instance.getTenantId());
        dto.setDownloadUrl("api/data-compare-results/by-instance-id/" + instance.getId() + "/download");

        JSONObject metadata = metadata(instance);
        if (metadata != null) {
            dto.setTaskId(metadata.getStr("taskId"));
            String taskName = metadata.getStr("taskName");
            if (taskName != null && !taskName.isBlank()) {
                dto.setTaskName(taskName);
            }
            dto.setOutputDir(metadata.getStr("outputDir"));
            dto.setResultFormat(metadata.getStr("resultFormat"));
            dto.setCompareRecordCount(metadata.getBool("compareRecordCount", true));
            dto.setCompareDetail(metadata.getBool("compareDetail", true));

            List<DataCompareTableResultDTO> tables = new ArrayList<>();
            JSONArray tableArray = metadata.getJSONArray("tables");
            if (tableArray != null && !tableArray.isEmpty()) {
                for (int i = 0; i < tableArray.size(); i++) {
                    JSONObject node = tableArray.getJSONObject(i);
                    DataCompareTableResultDTO table = new DataCompareTableResultDTO();
                    table.setIndex(node.getInt("index", i));
                    table.setLeftTable(node.getStr("leftTable"));
                    table.setRightTable(node.getStr("rightTable"));
                    table.setResultPath(node.getStr("resultPath"));
                    table.setSummaryPath(node.getStr("summaryPath"));
                    table.setResultFormat(node.getStr("resultFormat", metadata.getStr("resultFormat")));
                    table.setDownloadUrl(
                        "api/data-compare-results/by-instance-id/" + instance.getId() + "/download?tableIndex=" + table.getIndex()
                    );
                    table.setSummaryUrl(
                        "api/data-compare-results/by-instance-id/" + instance.getId() + "/summary?tableIndex=" + table.getIndex()
                    );
                    enrichTableSummary(instance, table);
                    tables.add(table);
                }
            } else {
                // 兼容旧版单表元数据
                DataCompareTableResultDTO table = new DataCompareTableResultDTO();
                table.setIndex(0);
                table.setResultPath(metadata.getStr("resultPath"));
                table.setSummaryPath(metadata.getStr("summaryPath"));
                table.setResultFormat(metadata.getStr("resultFormat"));
                table.setDownloadUrl(
                    "api/data-compare-results/by-instance-id/" + instance.getId() + "/download?tableIndex=0"
                );
                table.setSummaryUrl(
                    "api/data-compare-results/by-instance-id/" + instance.getId() + "/summary?tableIndex=0"
                );
                enrichTableSummary(instance, table);
                tables.add(table);
            }
            dto.setTables(tables);
            if (!tables.isEmpty()) {
                DataCompareTableResultDTO first = tables.get(0);
                dto.setResultPath(first.getResultPath());
                dto.setSummaryPath(first.getSummaryPath());
                if (first.getResultFormat() != null) {
                    dto.setResultFormat(first.getResultFormat());
                }
                dto.setDownloadUrl(first.getDownloadUrl());
                aggregate(dto, tables);
            }
        }
        return dto;
    }

    private void enrichTableSummary(JobInstance instance, DataCompareTableResultDTO table) {
        if (!TaskConstants.TASK_STATUS_SUCCESSFUL.equals(instance.getStatus())) {
            return;
        }
        if (table.getSummaryPath() == null || table.getSummaryPath().isBlank()) {
            return;
        }
        try {
            Summary summary = readSummary(table.getSummaryPath());
            table.setLeftRowCount(summary.leftRows);
            table.setRightRowCount(summary.rightRows);
            table.setSameRowCount(summary.sameRows);
            table.setDiffRowCount(summary.diffRows);
            table.setOnlyLeftCount(summary.onlyLeftRows);
            table.setOnlyRightCount(summary.onlyRightRows);
            table.setValueDiffCount(summary.valueDiffRows);
        } catch (Exception e) {
            LOG.warn(
                "读取数据比对汇总文件失败，保留空统计: jobInstanceId={}, path={}, error={}",
                instance.getId(),
                table.getSummaryPath(),
                e.getMessage()
            );
        }
    }

    private void aggregate(DataCompareResultDTO dto, List<DataCompareTableResultDTO> tables) {
        dto.setLeftRowCount(sum(tables, DataCompareTableResultDTO::getLeftRowCount));
        dto.setRightRowCount(sum(tables, DataCompareTableResultDTO::getRightRowCount));
        dto.setSameRowCount(sum(tables, DataCompareTableResultDTO::getSameRowCount));
        dto.setDiffRowCount(sum(tables, DataCompareTableResultDTO::getDiffRowCount));
        dto.setOnlyLeftCount(sum(tables, DataCompareTableResultDTO::getOnlyLeftCount));
        dto.setOnlyRightCount(sum(tables, DataCompareTableResultDTO::getOnlyRightCount));
        dto.setValueDiffCount(sum(tables, DataCompareTableResultDTO::getValueDiffCount));
    }

    private Long sum(List<DataCompareTableResultDTO> tables, java.util.function.Function<DataCompareTableResultDTO, Long> getter) {
        Long total = null;
        for (DataCompareTableResultDTO table : tables) {
            Long value = getter.apply(table);
            if (value == null) {
                continue;
            }
            total = total == null ? value : total + value;
        }
        return total;
    }

    private JSONObject tableMetadata(JSONObject metadata, int tableIndex) {
        JSONArray tables = metadata.getJSONArray("tables");
        if (tables == null || tables.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(tableIndex, tables.size() - 1));
        return tables.getJSONObject(index);
    }

    private JSONObject metadata(JobInstance instance) {
        if (instance.getJobContext() == null || instance.getJobContext().isBlank()) {
            return null;
        }
        try {
            return JSONUtil.parseObj(instance.getJobContext()).getJSONObject(META_KEY);
        } catch (Exception e) {
            LOG.warn("解析数据比对 JobContext 元数据失败: jobInstanceId={}, error={}", instance.getId(), e.getMessage());
            return null;
        }
    }

    private Summary readSummary(String summaryPath) {
        try (InputStream inputStream = fileStorageStrategy.download(summaryPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            String valueLine = reader.readLine();
            if (headerLine == null || valueLine == null) {
                throw new IllegalStateException("数据比对汇总文件为空: " + summaryPath);
            }
            List<String> headers = parseCsvLine(headerLine);
            List<String> values = parseCsvLine(valueLine);
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                map.put(headers.get(i).toLowerCase(Locale.ROOT), i < values.size() ? values.get(i) : null);
            }
            return new Summary(
                parseLong(map.get("left_rows")),
                parseLong(map.get("right_rows")),
                parseLong(map.get("same_rows")),
                parseLong(map.get("diff_rows")),
                parseLong(map.get("only_left_rows")),
                parseLong(map.get("only_right_rows")),
                parseLong(map.get("value_diff_rows"))
            );
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("读取数据比对汇总文件失败: " + e.getMessage(), e);
        }
    }

    private static ZonedDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value.trim())).atZone(ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 简单 CSV 行解析，支持双引号包裹和双引号转义。
     */
    static List<String> parseCsvLine(String line) {
        if (line == null || line.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    private record Summary(Long leftRows, Long rightRows, Long sameRows, Long diffRows, Long onlyLeftRows, Long onlyRightRows, Long valueDiffRows) {}
}
