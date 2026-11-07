package com.data.datafusion.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datafusion.domain.DataQualityTask;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.DataQualityTaskRepository;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.service.dto.DataQualityResultDTO;
import com.data.datafusion.service.dto.DataQualityRuleResultDTO;
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
 * 数据质量结果查询服务。
 *
 * <p>执行状态来自 {@link JobInstance}，结果文件路径、任务与规则快照保存在
 * {@code JobInstance.jobContext.dataQuality} 中，规则级统计信息按需读取 summary CSV。</p>
 */
@Service
@Transactional
public class DataQualityResultService {

    private static final Logger LOG = LoggerFactory.getLogger(DataQualityResultService.class);

    private static final String META_KEY = "dataQuality";

    private final JobInstanceRepository jobInstanceRepository;

    private final DataQualityTaskRepository taskRepository;

    private final FileStorageStrategy fileStorageStrategy;

    public DataQualityResultService(
        JobInstanceRepository jobInstanceRepository,
        DataQualityTaskRepository taskRepository,
        FileStorageStrategy fileStorageStrategy
    ) {
        this.jobInstanceRepository = jobInstanceRepository;
        this.taskRepository = taskRepository;
        this.fileStorageStrategy = fileStorageStrategy;
    }

    @Transactional(readOnly = true)
    public Optional<DataQualityResultDTO> findByJobInstanceId(String jobInstanceId) {
        return jobInstanceRepository.findById(jobInstanceId).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<DataQualityResultDTO> findByInstanceCode(String instanceCode) {
        return jobInstanceRepository.findFirstByInstanceCode(instanceCode).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<DataQualityResultDTO> findByTaskId(String taskId, Pageable pageable) {
        Optional<DataQualityTask> task = taskRepository.findById(taskId);
        if (task.isEmpty() || task.get().getJobId() == null) {
            return Page.empty(pageable);
        }
        return jobInstanceRepository.findByJobCodeOrderByCreateTimeDesc(task.get().getJobId(), pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<DataQualityResultDTO> findLatestByTaskId(String taskId) {
        return taskRepository
            .findById(taskId)
            .map(DataQualityTask::getJobId)
            .filter(jobId -> jobId != null && !jobId.isBlank())
            .flatMap(jobInstanceRepository::findFirstByJobCodeOrderByCreateTimeDesc)
            .map(this::toDto);
    }

    /**
     * 前端结果详情以 jobInstanceId 作为结果 id。
     */
    @Transactional(readOnly = true)
    public Optional<DataQualityResultDTO> findOne(String id) {
        return findByJobInstanceId(id);
    }

    /**
     * 按规则 key 查找明细文件路径。
     */
    @Transactional(readOnly = true)
    public Optional<String> findDetailPath(String resultId, String ruleKey) {
        JobInstance instance = jobInstanceRepository
            .findById(resultId)
            .orElseThrow(() -> new IllegalArgumentException("数据质量执行实例不存在: " + resultId));
        JSONObject metadata = metadata(instance);
        if (metadata == null) {
            return Optional.empty();
        }
        String detailDir = metadata.getStr("detailDir");
        if (detailDir == null || detailDir.isBlank()) {
            return Optional.empty();
        }
        String safeKey = ruleKey == null ? "" : ruleKey.replaceAll("[^\\p{L}\\p{N}_-]", "_");
        return fileStorageStrategy
            .list(detailDir)
            .stream()
            .filter(info -> !info.isDirectory())
            .filter(info -> info.name().contains("_detail_") && info.name().contains(safeKey))
            .map(FileStorageStrategy.FileInfo::path)
            .findFirst();
    }

    public DataQualityResultDTO toDto(JobInstance instance) {
        DataQualityResultDTO dto = new DataQualityResultDTO();
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
        dto.setDownloadUrl("api/data-quality-results/by-instance-id/" + instance.getId() + "/download");

        JSONObject metadata = metadata(instance);
        if (metadata != null) {
            dto.setTaskId(metadata.getStr("taskId"));
            String taskName = metadata.getStr("taskName");
            if (taskName != null && !taskName.isBlank()) {
                dto.setTaskName(taskName);
            }
            dto.setDataSourceId(metadata.getStr("dataSourceId"));
            dto.setSchemaName(metadata.getStr("schemaName"));
            dto.setTableName(metadata.getStr("tableName"));
            dto.setOutputDir(metadata.getStr("outputDir"));
            dto.setResultFormat(metadata.getStr("resultFormat"));
            dto.setSummaryPath(metadata.getStr("summaryPath"));
            List<DataQualityRuleResultDTO> rules = readSummaryFile(metadata.getStr("summaryPath"));
            if (rules.isEmpty()) {
                rules = rulesFromMetadata(metadata);
            }
            dto.setRules(rules);
            enrichSummary(dto, rules);
        } else {
            enrichSummary(dto, Collections.emptyList());
        }
        return dto;
    }

    private List<DataQualityRuleResultDTO> readSummaryFile(String summaryPath) {
        List<DataQualityRuleResultDTO> rules = new ArrayList<>();
        if (summaryPath == null || summaryPath.isBlank()) {
            return rules;
        }
        try (InputStream inputStream = fileStorageStrategy.download(summaryPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rules;
            }
            List<String> headers = parseCsvLine(headerLine).stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? values.get(i) : null);
                }
                DataQualityRuleResultDTO rule = new DataQualityRuleResultDTO();
                rule.setRuleKey(row.get("rule_key"));
                rule.setRuleName(row.get("rule_name"));
                rule.setRuleLevel(row.get("rule_level"));
                rule.setColumnName(row.get("column_name"));
                rule.setRuleType(row.get("rule_type"));
                rule.setSeverity(row.get("severity"));
                rule.setCheckedRows(parseLong(row.get("checked_rows")));
                rule.setInvalidRows(parseLong(row.get("invalid_rows")));
                rule.setInvalidRatio(parseDouble(row.get("invalid_ratio")));
                rule.setMaxInvalidRows(parseLong(row.get("max_invalid_rows")));
                rule.setMaxInvalidRatio(parseDouble(row.get("max_invalid_ratio")));
                rule.setStatus(row.get("status"));
                rule.setMessage(row.get("message"));
                rules.add(rule);
            }
        } catch (Exception e) {
            LOG.debug("读取数据质量汇总文件失败: {}", e.getMessage());
        }
        return rules;
    }

    private List<DataQualityRuleResultDTO> rulesFromMetadata(JSONObject metadata) {
        List<DataQualityRuleResultDTO> rules = new ArrayList<>();
        JSONArray array = metadata.getJSONArray("rules");
        if (array == null) {
            return rules;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject node = array.getJSONObject(i);
            if (node == null) {
                continue;
            }
            DataQualityRuleResultDTO rule = new DataQualityRuleResultDTO();
            rule.setRuleKey(node.getStr("ruleCode"));
            rule.setRuleName(node.getStr("ruleName"));
            rule.setRuleLevel(node.getStr("ruleLevel"));
            rule.setColumnName(node.getStr("column"));
            rule.setRuleType(node.getStr("ruleType"));
            rule.setSeverity(node.getStr("severity"));
            rule.setMaxInvalidRows(node.getLong("maxInvalidRows"));
            rule.setMaxInvalidRatio(node.getDouble("maxInvalidRatio"));
            rules.add(rule);
        }
        return rules;
    }

    private void enrichSummary(DataQualityResultDTO dto, List<DataQualityRuleResultDTO> rules) {
        int pass = 0;
        int warn = 0;
        int fail = 0;
        long checkedRows = 0L;
        for (DataQualityRuleResultDTO rule : rules) {
            if ("PASS".equalsIgnoreCase(rule.getStatus())) {
                pass++;
            } else if ("WARN".equalsIgnoreCase(rule.getStatus())) {
                warn++;
            } else if ("FAIL".equalsIgnoreCase(rule.getStatus())) {
                fail++;
            }
            if (rule.getCheckedRows() != null) {
                checkedRows = Math.max(checkedRows, rule.getCheckedRows());
            }
        }
        dto.setTotalRules(rules.size());
        dto.setPassRules(pass);
        dto.setWarnRules(warn);
        dto.setFailRules(fail);
        dto.setCheckedRows(checkedRows);
        if (fail > 0) {
            dto.setOverallStatus("FAIL");
        } else if (warn > 0) {
            dto.setOverallStatus("WARN");
        } else if (pass > 0) {
            dto.setOverallStatus("PASS");
        } else if (TaskConstants.TASK_STATUS_FAILED.equalsIgnoreCase(dto.getStatus())) {
            dto.setOverallStatus("FAIL");
        } else if (TaskConstants.TASK_STATUS_SUCCESSFUL.equalsIgnoreCase(dto.getStatus())) {
            dto.setOverallStatus("PASS");
        } else {
            dto.setOverallStatus(null);
        }
        if (dto.getId() != null) {
            for (DataQualityRuleResultDTO rule : rules) {
                if (rule.getRuleKey() != null) {
                    rule.setDetailUrl(
                        "api/data-quality-results/by-instance-id/" +
                        dto.getId() +
                        "/detail?ruleKey=" +
                        java.net.URLEncoder.encode(rule.getRuleKey(), StandardCharsets.UTF_8)
                    );
                }
            }
        }
    }

    private JSONObject metadata(JobInstance instance) {
        if (instance == null || instance.getJobContext() == null || instance.getJobContext().isBlank()) {
            return null;
        }
        try {
            JSONObject context = JSONUtil.parseObj(instance.getJobContext());
            return context.getJSONObject(META_KEY);
        } catch (Exception e) {
            LOG.debug("解析数据质量 JobContext 失败: {}", e.getMessage());
            return null;
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

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
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
}
