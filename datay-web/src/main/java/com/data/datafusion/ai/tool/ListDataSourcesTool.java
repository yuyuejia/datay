package com.data.datafusion.ai.tool;

import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 数据源清单工具（只读）。
 *
 * <p>看板设计时，物理表数据集需要明确目标数据源，先用本工具列出可选数据源，
 * 再配合 {@code list_database_objects} / {@code preview_sql_result} 的 {@code dataSourceId}
 * 参数探查表结构与试跑 SQL。
 */
@Component
public class ListDataSourcesTool implements AiTool {

    /** 工具名常量。 */
    public static final String NAME = "list_data_sources";

    private static final int MAX_DATASOURCES = 200;

    private final DataSourceService dataSourceService;

    public ListDataSourcesTool(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            列出当前租户下已注册的数据源，返回 id、名称、类型与默认 schema。
            当需要为看板创建物理表数据集时，先用本工具选择数据源，再把其 id 作为
            list_database_objects / preview_sql_result 的 dataSourceId 入参进行探查。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (DataSourceDTO dto : dataSourceService.findAll(PageRequest.of(0, MAX_DATASOURCES)).getContent()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", dto.getId());
            item.put("name", dto.getName());
            item.put("type", dto.getType());
            if (dto.getSchemaName() != null && !dto.getSchemaName().isBlank()) {
                item.put("schema", dto.getSchemaName());
            }
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", items.size());
        result.put("dataSources", items);
        return result;
    }
}
