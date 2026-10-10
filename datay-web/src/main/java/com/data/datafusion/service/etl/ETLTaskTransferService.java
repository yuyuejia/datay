package com.data.datafusion.service.etl;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DataSource;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.ETLTaskService;
import com.data.datafusion.service.apppackage.AppPackageIdRemapper;
import com.data.datafusion.service.apppackage.AppPackageJson;
import com.data.datafusion.service.apppackage.model.AppPackageEtlTask;
import com.data.datafusion.service.dto.ETLEdgeDTO;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.dto.ETLTaskImportResultDTO;
import com.data.datafusion.service.dto.ETLTaskTransferDTO;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ETL 任务单任务导出 / 导入服务。
 *
 * <p>导出时把任务的定义（节点、连线、Cron 等）序列化为一份可移植的 JSON，并附带节点中
 * 引用的数据源 / 模型的名称与编码；导入时按名称 / 编码在本租户内复用已有资产并重写引用，
 * 匹配不到的对象保留原值并给出告警。导入的任务统一以「离线」状态创建，不自动上线。
 */
@Service
public class ETLTaskTransferService {

    private static final Logger LOG = LoggerFactory.getLogger(ETLTaskTransferService.class);

    /** 节点 config 中引用数据源的字段名。 */
    private static final String FIELD_SOURCE_ID = "sourceId";

    /** 节点 config 中引用数据模型的字段名。 */
    private static final String FIELD_MODEL_ID = "modelId";

    private final ETLTaskService eTLTaskService;

    private final ETLTaskRepository eTLTaskRepository;

    private final DataSourceRepository dataSourceRepository;

    private final DataModelRepository dataModelRepository;

    private final JobRepository jobRepository;

    public ETLTaskTransferService(
        ETLTaskService eTLTaskService,
        ETLTaskRepository eTLTaskRepository,
        DataSourceRepository dataSourceRepository,
        DataModelRepository dataModelRepository,
        JobRepository jobRepository
    ) {
        this.eTLTaskService = eTLTaskService;
        this.eTLTaskRepository = eTLTaskRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dataModelRepository = dataModelRepository;
        this.jobRepository = jobRepository;
    }

    /**
     * 导出单个 ETL 任务为传输对象。
     *
     * @param taskId ETL 任务 ID
     * @return 传输对象（含任务定义与被引用对象清单）
     */
    @Transactional(readOnly = true)
    public ETLTaskTransferDTO exportTask(String taskId) {
        ETLTaskDTO dto = eTLTaskService.findOne(taskId).orElseThrow(() -> new IllegalArgumentException("ETL 任务不存在：" + taskId));

        AppPackageEtlTask task = new AppPackageEtlTask();
        task.taskName = dto.getTaskName();
        task.taskCode = dto.getTaskCode();
        task.taskDesc = dto.getTaskDesc();
        task.dir = dto.getDir();
        task.type = dto.getType();
        task.cron = dto.getCron();
        task.project = dto.getProject();
        task.nodes =
            dto.getNodes() == null
                ? new ArrayList<>()
                : dto.getNodes().stream().map(this::toTransferNode).toList();
        task.edges =
            dto.getEdges() == null
                ? new ArrayList<>()
                : dto.getEdges().stream().map(this::toTransferEdge).toList();

        Set<String> sourceIds = new LinkedHashSet<>();
        Set<String> modelIds = new LinkedHashSet<>();
        for (ETLNodeDTO node : dto.getNodes() == null ? Collections.<ETLNodeDTO>emptyList() : dto.getNodes()) {
            collectReferences(AppPackageJson.readTreeQuietly(node.getConfig()), sourceIds, modelIds);
        }

        ETLTaskTransferDTO transfer = new ETLTaskTransferDTO();
        transfer.task = task;
        transfer.references.dataSources = buildDataSourceRefs(sourceIds);
        transfer.references.models = buildModelRefs(modelIds);
        return transfer;
    }

    /**
     * 导入 ETL 任务：按名称 / 编码复用当前租户的数据源与模型并重写节点引用，离线创建任务。
     *
     * <p>本方法刻意不声明 {@code @Transactional}：当引用的数据源 / 模型无效时，
     * {@link ETLTaskService#save} 生成 Job 会抛出异常并回滚其自身事务，若外层也在同一事务中，
     * 事务会被标记为 rollback-only 而无法继续保存。这里让两次保存各自独立成事务。
     *
     * @param transfer 传输对象
     * @return 导入结果
     */
    public ETLTaskImportResultDTO importTask(ETLTaskTransferDTO transfer) {
        if (transfer == null || transfer.task == null) {
            throw new IllegalArgumentException("导入失败：文件内容不是合法的 ETL 任务 JSON");
        }
        AppPackageEtlTask item = transfer.task;
        ETLTaskImportResultDTO result = new ETLTaskImportResultDTO();

        Map<String, String> dataSourceMap = resolveDataSources(transfer.references, result);
        Map<String, String> modelMap = resolveModels(transfer.references, result);

        Map<String, Map<String, String>> remap = new LinkedHashMap<>();
        remap.put(FIELD_SOURCE_ID, dataSourceMap);
        remap.put(FIELD_MODEL_ID, modelMap);

        ETLTaskDTO dto = new ETLTaskDTO();
        dto.setTaskName(item.taskName);
        dto.setTaskDesc(item.taskDesc);
        dto.setDir(item.dir);
        dto.setType(item.type == null || item.type.isBlank() ? TaskConstants.TASK_TYPE_ETL : item.type);
        dto.setCron(item.cron);
        dto.setProject(item.project);
        dto.setDr(0);
        dto.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
        dto.setCreateTime(ZonedDateTime.now());
        dto.setUpdateTime(ZonedDateTime.now());
        dto.setNodes(
            item.nodes == null
                ? new ArrayList<>()
                : item.nodes.stream().map(node -> toNodeDto(node, remap)).toList()
        );
        dto.setEdges(
            item.edges == null
                ? new ArrayList<>()
                : item.edges.stream().map(this::toEdgeDto).toList()
        );

        String taskCode = uniqueTaskCode(item.taskCode);
        boolean renamed = !Objects.equals(taskCode, item.taskCode);
        dto.setTaskCode(taskCode);

        String taskName = uniqueTaskName(item.taskName);
        renamed = renamed || !Objects.equals(taskName, item.taskName);
        dto.setTaskName(taskName);

        ETLTaskDTO saved;
        try {
            saved = eTLTaskService.save(dto);
        } catch (IllegalArgumentException e) {
            // 引用的数据源 / 模型未匹配导致无法生成调度 Job：仍离线落库，提示用户修复引用
            LOG.warn("Failed to generate ETL job on import, saving task without job: {}", e.getMessage());
            dto.setJobId(null);
            saved = eTLTaskService.saveWithoutJob(dto);
            result.warnings.add("任务已离线导入，但部分引用的数据源 / 模型无效，暂未生成调度 Job，请修复引用后重新保存：" + e.getMessage());
        }
        result.taskId = saved.getId();
        result.taskCode = taskCode;
        result.taskName = taskName;
        result.renamed = renamed;
        LOG.info("Imported ETL task {} as {} (id={})", item.taskCode, taskCode, saved.getId());
        return result;
    }

    // ------------------------------------------------------------------ 导出辅助

    private AppPackageEtlTask.AppPackageEtlNode toTransferNode(ETLNodeDTO node) {
        AppPackageEtlTask.AppPackageEtlNode item = new AppPackageEtlTask.AppPackageEtlNode();
        item.label = node.getLabel();
        item.code = node.getCode();
        item.desc = node.getDesc();
        item.type = node.getType();
        item.config = AppPackageJson.readTreeQuietly(node.getConfig());
        item.xAxis = node.getxAxis();
        item.yAxis = node.getyAxis();
        item.status = node.getStatus();
        return item;
    }

    private AppPackageEtlTask.AppPackageEtlEdge toTransferEdge(ETLEdgeDTO edge) {
        AppPackageEtlTask.AppPackageEtlEdge item = new AppPackageEtlTask.AppPackageEtlEdge();
        item.name = edge.getName();
        item.code = edge.getCode();
        item.source = edge.getSource();
        item.target = edge.getTarget();
        item.config = edge.getConfig();
        item.status = edge.getStatus();
        return item;
    }

    /** 递归收集节点 config 中的 sourceId / modelId。 */
    private void collectReferences(JsonNode node, Set<String> sourceIds, Set<String> modelIds) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                JsonNode value = entry.getValue();
                if (value != null && !value.isContainerNode()) {
                    if (FIELD_SOURCE_ID.equals(name)) {
                        sourceIds.add(value.asText());
                    } else if (FIELD_MODEL_ID.equals(name)) {
                        modelIds.add(value.asText());
                    }
                }
                collectReferences(value, sourceIds, modelIds);
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectReferences(child, sourceIds, modelIds));
        }
    }

    private List<ETLTaskTransferDTO.DataSourceRef> buildDataSourceRefs(Set<String> sourceIds) {
        List<ETLTaskTransferDTO.DataSourceRef> refs = new ArrayList<>();
        for (String id : sourceIds) {
            Optional<DataSource> source = dataSourceRepository.findById(id);
            if (source.isEmpty()) {
                continue;
            }
            DataSource entity = source.get();
            ETLTaskTransferDTO.DataSourceRef ref = new ETLTaskTransferDTO.DataSourceRef();
            ref.oldId = entity.getId();
            ref.name = entity.getName();
            ref.type = entity.getType();
            ref.url = entity.getUrl();
            refs.add(ref);
        }
        return refs;
    }

    private List<ETLTaskTransferDTO.ModelRef> buildModelRefs(Set<String> modelIds) {
        List<ETLTaskTransferDTO.ModelRef> refs = new ArrayList<>();
        for (String id : modelIds) {
            Optional<DataModel> model = dataModelRepository.findById(id);
            if (model.isEmpty()) {
                continue;
            }
            DataModel entity = model.get();
            ETLTaskTransferDTO.ModelRef ref = new ETLTaskTransferDTO.ModelRef();
            ref.oldId = entity.getId();
            ref.code = entity.getCode();
            ref.name = entity.getName();
            refs.add(ref);
        }
        return refs;
    }

    // ------------------------------------------------------------------ 导入辅助

    private Map<String, String> resolveDataSources(ETLTaskTransferDTO.References references, ETLTaskImportResultDTO result) {
        Map<String, String> mapping = new LinkedHashMap<>();
        if (references == null || references.dataSources == null) {
            return mapping;
        }
        for (ETLTaskTransferDTO.DataSourceRef ref : references.dataSources) {
            if (ref.oldId == null) {
                continue;
            }
            ETLTaskImportResultDTO.ReferenceMapping detail = new ETLTaskImportResultDTO.ReferenceMapping();
            detail.oldId = ref.oldId;
            detail.label = ref.name;
            Optional<DataSource> existing = dataSourceRepository.findFirstByNameAndTypeAndUrl(ref.name, ref.type, ref.url);
            if (existing.isPresent()) {
                detail.matched = true;
                detail.newId = existing.get().getId();
                mapping.put(ref.oldId, detail.newId);
            } else {
                result.warnings.add("数据源未在当前租户中找到，节点引用保留原值：" + ref.name);
            }
            result.dataSources.add(detail);
        }
        return mapping;
    }

    private Map<String, String> resolveModels(ETLTaskTransferDTO.References references, ETLTaskImportResultDTO result) {
        Map<String, String> mapping = new LinkedHashMap<>();
        if (references == null || references.models == null) {
            return mapping;
        }
        for (ETLTaskTransferDTO.ModelRef ref : references.models) {
            if (ref.oldId == null) {
                continue;
            }
            ETLTaskImportResultDTO.ReferenceMapping detail = new ETLTaskImportResultDTO.ReferenceMapping();
            detail.oldId = ref.oldId;
            detail.label = ref.code == null || ref.code.isBlank() ? ref.name : ref.code;
            Optional<DataModel> existing = ref.code == null ? Optional.empty() : dataModelRepository.findFirstByCode(ref.code);
            if (existing.isEmpty() && ref.name != null) {
                existing = dataModelRepository.findFirstByName(ref.name);
            }
            if (existing.isPresent()) {
                detail.matched = true;
                detail.newId = existing.get().getId();
                mapping.put(ref.oldId, detail.newId);
            } else {
                result.warnings.add("数据模型未在当前租户中找到，节点引用保留原值：" + detail.label);
            }
            result.models.add(detail);
        }
        return mapping;
    }

    private ETLNodeDTO toNodeDto(AppPackageEtlTask.AppPackageEtlNode node, Map<String, Map<String, String>> remap) {
        ETLNodeDTO dto = new ETLNodeDTO();
        dto.setLabel(node.label);
        dto.setCode(node.code);
        dto.setDesc(node.desc);
        dto.setType(node.type);
        dto.setxAxis(node.xAxis);
        dto.setyAxis(node.yAxis);
        dto.setStatus(node.status);
        JsonNode remapped = AppPackageIdRemapper.remapIds(node.config == null ? null : node.config.deepCopy(), remap);
        dto.setConfig(remapped == null ? null : remapped.toString());
        return dto;
    }

    private ETLEdgeDTO toEdgeDto(AppPackageEtlTask.AppPackageEtlEdge edge) {
        ETLEdgeDTO dto = new ETLEdgeDTO();
        dto.setName(edge.name);
        dto.setCode(edge.code);
        dto.setSource(edge.source);
        dto.setTarget(edge.target);
        dto.setConfig(edge.config);
        dto.setStatus(edge.status);
        return dto;
    }

    private String uniqueTaskCode(String code) {
        if (code == null || code.isBlank() || eTLTaskRepository.findFirstByTaskCode(code).isEmpty()) {
            return code;
        }
        String candidate = code + "_copy";
        int index = 2;
        while (eTLTaskRepository.findFirstByTaskCode(candidate).isPresent()) {
            candidate = code + "_copy" + index;
            index++;
        }
        return candidate;
    }

    private String uniqueTaskName(String name) {
        if (name == null || name.isBlank() || jobRepository.findFirstByJobName(name).isEmpty()) {
            return name;
        }
        String candidate = name + " (副本)";
        int index = 2;
        while (jobRepository.findFirstByJobName(candidate).isPresent()) {
            candidate = name + " (副本" + index + ")";
            index++;
        }
        return candidate;
    }
}
