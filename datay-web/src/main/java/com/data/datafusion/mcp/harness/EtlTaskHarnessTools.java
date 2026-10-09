package com.data.datafusion.mcp.harness;

import static com.data.datafusion.mcp.harness.DatayHarnessArgs.clamp;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.intVal;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.mapList;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.mapVal;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.reqStr;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.str;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.array;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.freeObject;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.integer;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.object;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.properties;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.string;

import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.service.ETLTaskService;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.service.dto.ETLDebugDTO;
import com.data.datafusion.service.dto.ETLEdgeDTO;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.dto.JobInstanceDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * ETL 任务对象的 Harness 工具族。
 *
 * <p>除了常规增删改查，还暴露了任务的生命周期动作：立即运行、上线（加入调度）、下线（取消调度）、
 * 试跑调试与实例查询，行为与 {@code /api/etl-tasks} 下的 REST 接口保持一致。
 *
 * <p>节点（nodes）与连线（edges）以设计器画布的结构透传，内部用 Jackson 转换为 DTO，
 * 因此 Agent 既可以从 {@code etl_task_get} 拿到画布结构，也可以原样修改后回写。
 */
@Configuration
public class EtlTaskHarnessTools {

    private final ETLTaskService etlTaskService;
    private final JobInstanceService jobInstanceService;
    private final ETLTaskRepository etlTaskRepository;
    private final ObjectMapper objectMapper;

    public EtlTaskHarnessTools(
        ETLTaskService etlTaskService,
        JobInstanceService jobInstanceService,
        ETLTaskRepository etlTaskRepository,
        ObjectMapper objectMapper
    ) {
        this.etlTaskService = etlTaskService;
        this.jobInstanceService = jobInstanceService;
        this.etlTaskRepository = etlTaskRepository;
        this.objectMapper = objectMapper;
    }

    @Bean
    DatayHarnessTool etlTaskListTool() {
        return DatayHarnessTool.read(
            "etl_task_list",
            "分页列出当前租户下的 ETL 任务，返回任务名、编码、调度表达式与运行状态。",
            object(properties("page", integer("页码，从 0 开始，默认 0"), "size", integer("每页条数，默认 20，最大 200"), "search", string("可选关键字，按任务名或描述模糊过滤"))),
            (args, context) -> {
                int pageIndex = Math.max(0, intVal(args, "page", 0));
                int size = clamp(intVal(args, "size", 20), 1, 200);
                Page<ETLTaskDTO> page = etlTaskService.findAll(PageRequest.of(pageIndex, size), str(args, "search"));
                return DatayHarnessViews.page(page, task -> DatayHarnessViews.etlTask(task, false));
            }
        );
    }

    @Bean
    DatayHarnessTool etlTaskGetTool() {
        return DatayHarnessTool.read(
            "etl_task_get",
            "按 ID 查询 ETL 任务详情，包含节点与连线组成的任务设计图。",
            object(properties("id", string("ETL 任务 ID")), "id"),
            (args, context) -> DatayHarnessViews.etlTask(requireTask(reqStr(args, "id")), true)
        );
    }

    @Bean
    DatayHarnessTool etlTaskCreateTool() {
        return DatayHarnessTool.write(
            "etl_task_create",
            "创建一个 ETL 任务。nodes / edges 使用设计器画布结构；创建后任务为 OFFLINE 状态，可用 etl_task_online 加入调度。",
            object(
                properties(
                    "taskName", string("任务名称"),
                    "taskCode", string("任务编码（可选，建议唯一）"),
                    "taskDesc", string("任务描述（可选）"),
                    "type", string("任务类型（可选，缺省 ETL）"),
                    "dir", string("所属目录（可选）"),
                    "cron", string("调度表达式（可选，上线后按此调度）"),
                    "project", string("项目标识（可选）"),
                    "nodes", array(freeObject("节点：label / code / type / config / xAxis / yAxis"), "节点列表（可选）"),
                    "edges", array(freeObject("连线：source / target / code / name"), "连线列表（可选）")
                ),
                "taskName"
            ),
            (args, context) -> {
                ETLTaskDTO dto = new ETLTaskDTO();
                dto.setTaskName(DatayHarnessArgs.reqStr(args, "taskName"));
                dto.setTaskCode(str(args, "taskCode"));
                dto.setTaskDesc(str(args, "taskDesc"));
                dto.setType(str(args, "type") == null ? TaskConstants.TASK_TYPE_ETL : str(args, "type"));
                dto.setDir(str(args, "dir"));
                dto.setCron(str(args, "cron"));
                dto.setProject(str(args, "project"));
                String creater = str(args, "creater");
                dto.setCreater(creater == null ? context.getLogin() : creater);
                dto.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
                dto.setCreateTime(ZonedDateTime.now());
                dto.setUpdateTime(ZonedDateTime.now());
                dto.setNodes(toNodes(mapList(args, "nodes")));
                dto.setEdges(toEdges(mapList(args, "edges")));
                ETLTaskDTO saved = etlTaskService.save(dto);
                return DatayHarnessViews.etlTask(requireTask(saved.getId()), true);
            }
        );
    }

    @Bean
    DatayHarnessTool etlTaskUpdateTool() {
        return DatayHarnessTool.write(
            "etl_task_update",
            "按 ID 更新 ETL 任务。只提交需要修改的字段：仅改名称/调度等元信息时不会触碰任务设计图；提交 nodes 或 edges 时会整体替换设计图并重新生成执行计划。",
            object(
                properties(
                    "id", string("ETL 任务 ID"),
                    "taskName", string("任务名称"),
                    "taskCode", string("任务编码"),
                    "taskDesc", string("任务描述"),
                    "type", string("任务类型"),
                    "dir", string("所属目录"),
                    "cron", string("调度表达式"),
                    "project", string("项目标识"),
                    "nodes", array(freeObject("节点结构"), "新的节点列表；提交后整体替换"),
                    "edges", array(freeObject("连线结构"), "新的连线列表；提交后整体替换")
                ),
                "id"
            ),
            (args, context) -> {
                String id = reqStr(args, "id");
                ETLTaskDTO existing = requireTask(id);
                List<Map<String, Object>> rawNodes = mapList(args, "nodes");
                List<Map<String, Object>> rawEdges = mapList(args, "edges");

                if (rawNodes == null && rawEdges == null) {
                    ETLTaskDTO patch = new ETLTaskDTO();
                    patch.setId(id);
                    patch.setTaskName(str(args, "taskName"));
                    patch.setTaskCode(str(args, "taskCode"));
                    patch.setTaskDesc(str(args, "taskDesc"));
                    patch.setType(str(args, "type"));
                    patch.setDir(str(args, "dir"));
                    patch.setCron(str(args, "cron"));
                    patch.setProject(str(args, "project"));
                    patch.setUpdateTime(ZonedDateTime.now());
                    etlTaskService
                        .partialUpdate(patch)
                        .orElseThrow(() -> new IllegalArgumentException("ETL 任务不存在: " + id));
                    return DatayHarnessViews.etlTask(requireTask(id), true);
                }

                applyScalarPatch(existing, args);
                existing.setNodes(rawNodes == null ? existing.getNodes() : toNodes(rawNodes));
                existing.setEdges(rawEdges == null ? existing.getEdges() : toEdges(rawEdges));
                existing.setUpdateTime(ZonedDateTime.now());
                etlTaskService.update(existing);
                return DatayHarnessViews.etlTask(requireTask(id), true);
            }
        );
    }

    @Bean
    DatayHarnessTool etlTaskDeleteTool() {
        return DatayHarnessTool.write(
            "etl_task_delete",
            "按 ID 删除 ETL 任务，同时删除其调度 Job 与任务设计图。",
            object(properties("id", string("ETL 任务 ID")), "id"),
            (args, context) -> {
                String id = reqStr(args, "id");
                ETLTaskDTO existing = requireTask(id);
                etlTaskService.delete(id);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("deletedId", id);
                result.put("taskName", existing.getTaskName());
                return result;
            }
        );
    }

    @Bean
    DatayHarnessTool etlTaskRunTool() {
        return DatayHarnessTool.write(
            "etl_task_run",
            "立即触发一次 ETL 任务执行（不等同于上线调度）。可用 etl_task_instances 查询执行结果。",
            object(properties("id", string("ETL 任务 ID")), "id"),
            (args, context) -> {
                String id = reqStr(args, "id");
                requireTask(id);
                etlTaskService.executeOnce(id);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("taskId", id);
                result.put("message", "已提交执行，可通过 etl_task_instances 查看执行状态");
                return result;
            }
        );
    }

    @Bean
    DatayHarnessTool etlTaskOnlineTool() {
        return DatayHarnessTool.write(
            "etl_task_online",
            "把 ETL 任务置为 ONLINE 并按 cron 加入调度。任务需已生成执行计划（jobId 非空）。",
            object(properties("id", string("ETL 任务 ID")), "id"),
            (args, context) -> {
                String id = reqStr(args, "id");
                ETLTaskDTO existing = requireTask(id);
                if (existing.getJobId() == null) {
                    throw new IllegalArgumentException("任务尚未生成执行计划（jobId 为空），无法上线");
                }
                return DatayHarnessViews.etlTask(etlTaskService.online(id), true);
            }
        );
    }

    @Bean
    DatayHarnessTool etlTaskOfflineTool() {
        return DatayHarnessTool.write(
            "etl_task_offline",
            "把 ETL 任务置为 OFFLINE 并取消调度。",
            object(properties("id", string("ETL 任务 ID")), "id"),
            (args, context) -> {
                String id = reqStr(args, "id");
                requireTask(id);
                return DatayHarnessViews.etlTask(etlTaskService.offline(id), true);
            }
        );
    }

    @Bean
    DatayHarnessTool etlTaskDebugTool() {
        return DatayHarnessTool.read(
            "etl_task_debug",
            "试跑 ETL 任务并返回各节点的采样数据：源组件最多读取 rowLimit 条，sink 组件只读不写，不影响目标库与增量状态。",
            object(
                properties(
                    "id", string("ETL 任务 ID（与 task 二选一，推荐用已保存的任务）"),
                    "task", freeObject("尚未保存的任务定义，结构同 etl_task_create 的入参"),
                    "rowLimit", integer("每个源组件的采样行数，默认 100"),
                    "targetNodeId", string("只运行到该节点及其上游（可选）")
                )
            ),
            (args, context) -> {
                ETLDebugDTO request = new ETLDebugDTO();
                String id = DatayHarnessArgs.str(args, "id");
                if (id != null) {
                    request.setTask(requireTask(id));
                } else {
                    Map<String, Object> taskArgs = mapVal(args, "task");
                    if (taskArgs == null) {
                        throw new IllegalArgumentException("需要提供 id 或 task 之一");
                    }
                    request.setTask(objectMapper.convertValue(taskArgs, ETLTaskDTO.class));
                }
                request.setRowLimit(clamp(intVal(args, "rowLimit", 100), 1, 10000));
                request.setTargetNodeId(str(args, "targetNodeId"));
                return etlTaskService.debug(request);
            }
        );
    }

    @Bean
    DatayHarnessTool etlTaskInstancesTool() {
        return DatayHarnessTool.read(
            "etl_task_instances",
            "分页查询 ETL 任务的执行实例（运行历史与状态）。",
            object(properties("id", string("ETL 任务 ID"), "page", integer("页码，从 0 开始，默认 0"), "size", integer("每页条数，默认 20，最大 200")), "id"),
            (args, context) -> {
                ETLTaskDTO task = requireTask(reqStr(args, "id"));
                if (task.getJobId() == null) {
                    return DatayHarnessViews.page(Page.<JobInstanceDTO>empty(), DatayHarnessViews::jobInstance);
                }
                int pageIndex = Math.max(0, intVal(args, "page", 0));
                int size = clamp(intVal(args, "size", 20), 1, 200);
                Page<JobInstanceDTO> page = jobInstanceService.findAllByJobCode(String.valueOf(task.getJobId()), PageRequest.of(pageIndex, size));
                return DatayHarnessViews.page(page, DatayHarnessViews::jobInstance);
            }
        );
    }

    @Bean
    DatayHarnessTool etlTaskJobPreviewTool() {
        return DatayHarnessTool.read(
            "etl_task_job_preview",
            "预览 ETL 任务编译出的执行计划 JSON（引擎视角的 units / connections），便于排查配置问题。",
            object(properties("id", string("ETL 任务 ID")), "id"),
            (args, context) -> {
                ETLTaskDTO task = requireTask(reqStr(args, "id"));
                String jobJson = etlTaskService.generateETLJobJson(task);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("taskId", task.getId());
                result.put("jobId", task.getJobId());
                result.put("jobJson", jobJson);
                return result;
            }
        );
    }

    /**
     * 仅覆盖入参中显式给出的标量字段，避免「部分更新」把未提交字段清空。
     */
    private static void applyScalarPatch(ETLTaskDTO target, Map<String, Object> args) {
        putIfPresent(target::setTaskName, str(args, "taskName"));
        putIfPresent(target::setTaskCode, str(args, "taskCode"));
        putIfPresent(target::setTaskDesc, str(args, "taskDesc"));
        putIfPresent(target::setType, str(args, "type"));
        putIfPresent(target::setDir, str(args, "dir"));
        putIfPresent(target::setCron, str(args, "cron"));
        putIfPresent(target::setProject, str(args, "project"));
    }

    private static void putIfPresent(Consumer<String> setter, String value) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private ETLTaskDTO requireTask(String id) {
        if (!etlTaskRepository.existsById(id)) {
            throw new IllegalArgumentException("ETL 任务不存在: " + id);
        }
        return etlTaskService
            .findOne(id)
            .orElseThrow(() -> new IllegalArgumentException("ETL 任务不存在: " + id));
    }

    private List<ETLNodeDTO> toNodes(List<Map<String, Object>> raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        List<ETLNodeDTO> nodes = new ArrayList<>(raw.size());
        raw.forEach(item -> nodes.add(objectMapper.convertValue(item, ETLNodeDTO.class)));
        return nodes;
    }

    private List<ETLEdgeDTO> toEdges(List<Map<String, Object>> raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        List<ETLEdgeDTO> edges = new ArrayList<>(raw.size());
        raw.forEach(item -> edges.add(objectMapper.convertValue(item, ETLEdgeDTO.class)));
        return edges;
    }
}
