package com.data.datafusion.mcp.harness;

import com.data.datafusion.service.dto.DataModelDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.dto.JobInstanceDTO;
import com.data.datafusion.service.dto.ModelFieldDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Harness 工具的统一出参视图。
 *
 * <p>直接回传 DTO 会带来两个问题：一是实体对象里可能夹带密码等敏感字段，
 * 二是列表接口把任务设计图（nodes / edges）整体带出来会让响应体迅速膨胀。
 * 这里集中裁剪，保证「给模型看的东西」既安全又可控。
 */
public final class DatayHarnessViews {

    private DatayHarnessViews() {}

    /**
     * 把 Spring Data 分页对象转换为模型友好的结构。
     *
     * @param page   分页结果
     * @param mapper 单条记录到出参的映射
     */
    public static <T> Map<String, Object> page(Page<T> page, Function<T, ?> mapper) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("page", page.getNumber());
        view.put("size", page.getSize());
        view.put("totalElements", page.getTotalElements());
        view.put("totalPages", page.getTotalPages());
        List<Object> items = new ArrayList<>(page.getNumberOfElements());
        page.getContent().forEach(item -> items.add(mapper.apply(item)));
        view.put("items", items);
        return view;
    }

    /**
     * 列表映射，用于非分页接口。
     */
    public static <T> List<Object> list(List<T> source, Function<T, ?> mapper) {
        List<Object> items = new ArrayList<>(source == null ? 0 : source.size());
        if (source != null) {
            source.forEach(item -> items.add(mapper.apply(item)));
        }
        return items;
    }

    /**
     * 数据源视图：刻意不包含密码字段。
     */
    public static Map<String, Object> dataSource(DataSourceDTO dto) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", dto.getId());
        view.put("name", dto.getName());
        view.put("type", dto.getType());
        view.put("url", dto.getUrl());
        view.put("hostname", dto.getHostname());
        view.put("port", dto.getPort());
        view.put("schemaName", dto.getSchemaName());
        view.put("database", dto.getDatabase());
        view.put("username", dto.getUsername());
        view.put("connectionMode", dto.getConnectionMode());
        view.put("description", dto.getDescription());
        view.put("createTime", dto.getCreateTime());
        view.put("updateTime", dto.getUpdateTime());
        return view;
    }

    /**
     * ETL 任务视图。
     *
     * @param includeGraph 是否包含节点与连线（列表接口通常不需要）
     */
    public static Map<String, Object> etlTask(ETLTaskDTO dto, boolean includeGraph) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", dto.getId());
        view.put("taskName", dto.getTaskName());
        view.put("taskCode", dto.getTaskCode());
        view.put("taskDesc", dto.getTaskDesc());
        view.put("type", dto.getType());
        view.put("dir", dto.getDir());
        view.put("cron", dto.getCron());
        view.put("status", dto.getStatus());
        view.put("lastStatus", dto.getLastStatus());
        view.put("jobId", dto.getJobId());
        view.put("project", dto.getProject());
        view.put("creater", dto.getCreater());
        view.put("createTime", dto.getCreateTime());
        view.put("updateTime", dto.getUpdateTime());
        if (includeGraph) {
            view.put("jobContext", dto.getJobContext());
            view.put("nodes", list(dto.getNodes(), DatayHarnessViews::etlNode));
            view.put("edges", list(dto.getEdges(), edge -> edge));
        }
        return view;
    }

    private static Map<String, Object> etlNode(ETLNodeDTO node) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", node.getId());
        view.put("label", node.getLabel());
        view.put("code", node.getCode());
        view.put("type", node.getType());
        view.put("status", node.getStatus());
        view.put("desc", node.getDesc());
        view.put("config", node.getConfig());
        view.put("xAxis", node.getxAxis());
        view.put("yAxis", node.getyAxis());
        return view;
    }

    public static Map<String, Object> dataModel(DataModelDTO dto) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", dto.getId());
        view.put("name", dto.getName());
        view.put("code", dto.getCode());
        view.put("description", dto.getDescription());
        view.put("directoryId", dto.getDirectoryId());
        view.put("modelType", dto.getModelType());
        view.put("dimensionKind", dto.getDimensionKind());
        view.put("levelCount", dto.getLevelCount());
        view.put("timeFieldName", dto.getTimeFieldName());
        view.put("timeLevels", dto.getTimeLevels());
        view.put("timeStart", dto.getTimeStart());
        view.put("timeEnd", dto.getTimeEnd());
        view.put("isRegistered", dto.getIsRegistered());
        view.put("project", dto.getProject());
        view.put("dataSourceId", dto.getDataSourceId());
        view.put("schemaName", dto.getSchemaName());
        view.put("tableName", dto.getTableName());
        view.put("displayFieldName", dto.getDisplayFieldName());
        view.put("createTime", dto.getCreateTime());
        view.put("updateTime", dto.getUpdateTime());
        return view;
    }

    public static Map<String, Object> modelField(ModelFieldDTO dto) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", dto.getId());
        view.put("modelId", dto.getModelId());
        view.put("fieldName", dto.getFieldName());
        view.put("fieldType", dto.getFieldType());
        view.put("fieldLength", dto.getFieldLength());
        view.put("fieldPrecision", dto.getFieldPrecision());
        view.put("fieldScale", dto.getFieldScale());
        view.put("description", dto.getDescription());
        view.put("sortOrder", dto.getSortOrder());
        view.put("isPartitionKey", dto.getIsPartitionKey());
        view.put("isPrimaryKey", dto.getIsPrimaryKey());
        view.put("dimensionModelId", dto.getDimensionModelId());
        view.put("dimensionFieldId", dto.getDimensionFieldId());
        view.put("fieldRole", dto.getFieldRole());
        view.put("levelIndex", dto.getLevelIndex());
        return view;
    }

    public static Map<String, Object> jobInstance(JobInstanceDTO dto) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", dto.getId());
        view.put("instanceCode", dto.getInstanceCode());
        view.put("jobName", dto.getJobName());
        view.put("jobCode", dto.getJobCode());
        view.put("type", dto.getType());
        view.put("status", dto.getStatus());
        view.put("jobMessage", dto.getJobMessage());
        view.put("execNode", dto.getExecNode());
        view.put("startTime", dto.getStartTime());
        view.put("endTime", dto.getEndTime());
        view.put("createTime", dto.getCreateTime());
        view.put("parentInstanceCode", dto.getParentInstanceCode());
        return view;
    }
}
