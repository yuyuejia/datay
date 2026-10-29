package com.data.datafusion.mcp.harness;

import static com.data.datafusion.mcp.harness.DatayHarnessArgs.clamp;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.intVal;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.mapVal;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.reqStr;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.str;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.freeObject;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.integer;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.object;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.properties;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.string;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.service.DataApiAccessException;
import com.data.datafusion.service.DataApiExecuteService;
import com.data.datafusion.service.DataApiService;
import com.data.datafusion.service.dto.DataApiDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 数据服务（对外地址 {@code /open-api/data/{code}}）的 Harness 工具族。
 *
 * <p>把「已在平台发布的数据服务」作为一等对象暴露给 Agent：先看有哪些服务、每个服务需要哪些参数，
 * 再按来源类型调用。调用直接复用 {@link DataApiExecuteService}，与第三方系统走 REST 时的
 * 取数逻辑、参数绑定、只读约束完全一致，不存在两条实现漂移的可能。</p>
 *
 * <p>只读与写操作刻意拆成两个工具：{@code TABLE}/{@code SQL} 来源是取数，归入只读的
 * {@code dataapi_query}；{@code API} 来源会代理请求上游系统、可能产生外部副作用，归入受
 * {@code datay.mcp.harness.allow-mutating-tools} 开关控制的 {@code dataapi_call}。
 * 这样关闭写开关时，只读取数照常可用，外部副作用仍被挡住。</p>
 */
@Configuration
public class DataApiHarnessTools {

    /**
     * 能力分组名，与工具名前缀保持一致。
     */
    static final String GROUP = "dataapi";

    /**
     * 由平台内部注入、不需要调用方传入的占位符，展示参数清单时过滤掉。
     */
    private static final Set<String> INTERNAL_PARAMETERS = Set.of("token", "access_token");

    private static final String TABLE_PARAMETER_HINT =
        "表来源：参数名与表字段同名时做等值过滤，另支持 pageNum / pageSize 分页；字段清单可用 datasource_columns 查询";

    private final DataApiService dataApiService;

    private final DataApiExecuteService dataApiExecuteService;

    public DataApiHarnessTools(DataApiService dataApiService, DataApiExecuteService dataApiExecuteService) {
        this.dataApiService = dataApiService;
        this.dataApiExecuteService = dataApiExecuteService;
    }

    @Bean
    DatayHarnessTool dataapiListTool() {
        return DatayHarnessTool.read(
            "dataapi_list",
            "分页列出当前租户已配置的数据服务（对外地址 /open-api/data/{code}），返回编码、名称、来源类型（TABLE/SQL/API）与启用状态。" +
            "只有 status=ENABLED 的服务可以被调用。",
            object(
                properties(
                    "page", integer("页码，从 0 开始，默认 0"),
                    "size", integer("每页条数，默认 20，最大 200"),
                    "search", string("可选关键字，按名称 / 编码 / 描述 / 表名 / schema 模糊过滤")
                )
            ),
            (args, context) -> {
                int pageIndex = Math.max(0, intVal(args, "page", 0));
                int size = clamp(intVal(args, "size", 20), 1, 200);
                Page<DataApiDTO> page = dataApiService.findAll(PageRequest.of(pageIndex, size), str(args, "search"));
                return DatayHarnessViews.page(page, DatayHarnessViews::dataApi);
            }
        );
    }

    @Bean
    DatayHarnessTool dataapiDescribeTool() {
        return DatayHarnessTool.read(
            "dataapi_describe",
            "按服务编码查看数据服务详情：来源类型、所需参数与 SQL 定义。SQL 来源的参数来自语句里的 ${name} 占位符；" +
            "API 来源的参数来自配置中的占位符，只返回请求方法、提取路径与参数，不暴露请求地址与凭据；" +
            "TABLE 来源参数可选，与表字段同名时做等值过滤。",
            object(properties("code", string("服务编码，例如 order_summary")), "code"),
            (args, context) -> {
                DataApiDTO dto = requireApi(reqStr(args, "code"));
                Map<String, Object> view = new LinkedHashMap<>(DatayHarnessViews.dataApi(dto));
                view.put("parameters", requiredParameters(dto));
                if (DataApi.SOURCE_TYPE_SQL.equals(dto.getSourceType())) {
                    view.put("sqlText", dto.getSqlText());
                } else if (DataApi.SOURCE_TYPE_API.equals(dto.getSourceType())) {
                    view.putAll(apiSourceSummary(dto));
                } else if (DataApi.SOURCE_TYPE_TABLE.equals(dto.getSourceType())) {
                    view.put("parameterHint", TABLE_PARAMETER_HINT);
                }
                return view;
            }
        );
    }

    @Bean
    DatayHarnessTool dataapiQueryTool() {
        return DatayHarnessTool.of(
            "dataapi_query",
            GROUP,
            "调用 TABLE / SQL 来源的数据服务取数（只读）。参数通过 params 传入，键为服务定义中的参数名；" +
            "表来源可用与字段同名的参数做等值过滤，并识别 pageNum / pageSize 分页。" +
            "API 来源的服务请改用 dataapi_call。",
            object(
                properties(
                    "code", string("服务编码"),
                    "params", freeObject("调用参数，键为服务定义中的参数名，值为参数值；也可把参数平铺在顶层传入")
                ),
                "code"
            ),
            false,
            Set.of(),
            (args, context) -> {
                String code = reqStr(args, "code");
                DataApiDTO dto = requireApi(code);
                if (DataApi.SOURCE_TYPE_API.equals(dto.getSourceType())) {
                    throw new IllegalArgumentException(
                        "服务 " + code + " 的来源类型是 API，会代理请求上游系统，请改用 dataapi_call 调用"
                    );
                }
                return invoke(code, args);
            }
        );
    }

    @Bean
    DatayHarnessTool dataapiCallTool() {
        return DatayHarnessTool.write(
            "dataapi_call",
            "调用 API 来源的数据服务：按服务配置的方法、请求头与请求体代理请求上游系统，可能产生外部副作用，" +
            "因此受服务端写操作开关控制。参数通过 params 传入，会替换配置中的 ${name} 占位符。" +
            "TABLE / SQL 来源的服务请改用只读的 dataapi_query。",
            object(
                properties(
                    "code", string("服务编码"),
                    "params", freeObject("调用参数，键为服务定义中的参数名，值为参数值；也可把参数平铺在顶层传入")
                ),
                "code"
            ),
            (args, context) -> {
                String code = reqStr(args, "code");
                DataApiDTO dto = requireApi(code);
                if (!DataApi.SOURCE_TYPE_API.equals(dto.getSourceType())) {
                    throw new IllegalArgumentException(
                        "服务 " + code + " 的来源类型是 " + dto.getSourceType() + "，取数请改用 dataapi_query"
                    );
                }
                return invoke(code, args);
            }
        );
    }

    // ---------------------------------------------------------------- helpers

    private DataApiDTO requireApi(String code) {
        return dataApiService
            .findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("数据服务不存在: " + code));
    }

    /**
     * 执行服务并把业务异常翻译成执行器能理解的两种结果：
     * 4xx 视为调用方参数问题，其余视为服务端执行失败。
     */
    private Object invoke(String code, Map<String, Object> args) {
        try {
            return dataApiExecuteService.invokeApiInCurrentTenant(code, collectParams(args));
        } catch (DataApiAccessException e) {
            if (e.getStatus() >= 400 && e.getStatus() < 500) {
                throw new IllegalArgumentException(e.getMessage());
            }
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * 合并 {@code params} 对象与顶层平铺参数，容忍模型把参数写在两处中的任意一处。
     */
    private static Map<String, Object> collectParams(Map<String, Object> args) {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Object> nested = mapVal(args, "params");
        if (nested != null) {
            params.putAll(nested);
        }
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String key = entry.getKey();
            if (key == null || "code".equals(key) || "params".equals(key) || key.startsWith("_")) {
                continue;
            }
            params.putIfAbsent(key, entry.getValue());
        }
        return params;
    }

    /**
     * 汇总服务需要的调用参数：SQL 来源取 {@code sqlText}，API 来源扫描整份 {@code apiConfig}
     * （URL / 请求头 / 请求体里的占位符都在其中）。
     */
    static List<String> requiredParameters(DataApiDTO dto) {
        List<String> names = new ArrayList<>();
        if (DataApi.SOURCE_TYPE_SQL.equals(dto.getSourceType())) {
            names.addAll(DataApiExecuteService.extractParameterNames(dto.getSqlText()));
        } else if (DataApi.SOURCE_TYPE_API.equals(dto.getSourceType())) {
            names.addAll(DataApiExecuteService.extractParameterNames(dto.getApiConfig()));
        }
        names.removeIf(INTERNAL_PARAMETERS::contains);
        return names;
    }

    /**
     * API 来源的安全摘要：只给出方法、提取路径与是否配置了前置取 Token，绝不回传请求地址与请求头。
     */
    private static Map<String, Object> apiSourceSummary(DataApiDTO dto) {
        Map<String, Object> summary = new LinkedHashMap<>();
        JSONObject config = parseConfig(dto.getApiConfig());
        if (config == null) {
            return summary;
        }
        Object method = config.get("method");
        summary.put("httpMethod", method == null ? "AUTO" : String.valueOf(method));
        summary.put("jsonPath", config.getString("jsonPath"));
        JSONObject preProcess = config.getJSONObject("preProcess");
        summary.put("preProcessEnabled", preProcess != null && preProcess.getBooleanValue("enabled", false));
        summary.put("hasRequestBody", config.getString("body") != null && !config.getString("body").isBlank());
        return summary;
    }

    private static JSONObject parseConfig(String apiConfig) {
        if (apiConfig == null || apiConfig.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(apiConfig);
        } catch (Exception e) {
            return null;
        }
    }
}
