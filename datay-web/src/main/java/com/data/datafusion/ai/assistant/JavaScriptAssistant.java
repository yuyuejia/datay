package com.data.datafusion.ai.assistant;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.data.datafusion.ai.JavaCodeExtractor;
import com.data.datafusion.ai.tool.ValidateJavaScriptTool;
import com.data.job.component.javascript.JavaScriptComponentExample;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Java 脚本助手。
 *
 * <p>面向 {@code JavaScriptComponent}（展示名「JAVA脚本」）的配置界面，把用户的自然语言需求
 * 翻译成符合组件契约的完整 Java 脚本。提示词内置脚本规范与官方示例，模型可调用
 * {@link ValidateJavaScriptTool} 在给出最终答案前完成编译自检。
 */
@Component
@Order(20)
public class JavaScriptAssistant implements AiAssistant {

    public static final String ID = "javascript";

    /** 组件运行时要求脚本必须声明的类名。 */
    public static final String SCRIPT_CLASS_NAME = "UserScript";

    private static final List<String> SAMPLES = List.of(
        "给 JSON 数组的每条记录增加 processed 标记和处理时间戳",
        "把金额字段 amount 乘以 1.13 作为含税金额 tax_amount",
        "过滤掉 status 为 DELETED 的记录并输出其余记录",
        "处理 CDC 的 UPDATE/DELETE 事件，保留变更前数据 __before",
        "把 CSV 文本按逗号拆分后转成 JSON 数组输出"
    );

    private static final String FINALIZE_INSTRUCTION = """
        你已获得足够信息，请立即给出最终 Java 脚本。不要再请求任何工具调用。
        输出格式要求：
        1. 用 ```java 代码块给出**完整**的 UserScript 类，必须包含所需的 import 与 process 方法；
        2. 代码块之后用简体中文简要说明处理逻辑，并指出需要用户按实际字段调整的位置。
        """;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Java 脚本助手";
    }

    @Override
    public String description() {
        return "用一句话描述数据处理需求，我来生成 JavaScriptComponent 可直接使用的 Java 脚本";
    }

    @Override
    public List<String> samplePrompts() {
        return SAMPLES;
    }

    /**
     * 脚本助手可使用编译校验工具，用于在给出最终脚本前自检语法。
     */
    @Override
    public Set<String> toolNames() {
        return Set.of(ValidateJavaScriptTool.NAME);
    }

    /**
     * 从模型回复中抽取最终 Java 脚本，覆盖默认的 SQL 抽取逻辑。
     */
    @Override
    public String extractSql(String content) {
        return JavaCodeExtractor.extract(content);
    }

    @Override
    public String finalizeInstruction() {
        return FINALIZE_INSTRUCTION;
    }

    @Override
    public String buildSystemPrompt(AiAssistantContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 DataY 数据平台内置的「Java 脚本」编写助手，服务于 ETL 任务中的 JavaScriptComponent（展示名：JAVA脚本）。\n");
        sb.append("你的职责：把用户的自然语言需求，翻译成可直接粘贴到组件配置中的 Java 脚本。\n\n");

        sb.append("【脚本契约（必须严格遵守）】\n");
        sb.append("1. 不得出现 package 声明；必须声明 public class ").append(SCRIPT_CLASS_NAME).append("。\n");
        sb.append("2. 必须实现方法：public FlowFile process(FlowFile flowFile, Map<String, Object> context)，且必须返回 FlowFile。\n");
        sb.append("3. 必须显式导入所用类型，常用导入：\n");
        sb.append("   import com.data.job.FlowFile;\n");
        sb.append("   import com.data.job.component.javascript.ScriptContext.LogFunction;\n");
        sb.append("   import com.alibaba.fastjson2.JSONArray;\n");
        sb.append("   import com.alibaba.fastjson2.JSONObject;\n");
        sb.append("   import java.util.Map;\n");
        sb.append("4. 日志：LogFunction log = (LogFunction) context.get(\"log\"); 可用 log.info/warn/error(String)。\n");
        sb.append("5. 上下文 context 还包含 componentId、componentName、executionContext（可能为 null）。\n");
        sb.append("6. FlowFile 常用 API：\n");
        sb.append("   - JSONArray getJsonArray() / void setJsonArray(JSONArray)\n");
        sb.append("   - JSONObject getJsonObject()\n");
        sb.append("   - String getCsvData() / String getTextData() / Object getData()\n");
        sb.append("   - FlowFile.DataFormat getDataFormat()（取值 JSON_ARRAY / JSON_OBJECT / CSV / TEXT / BINARY）\n");
        sb.append("   - void setAttribute(String, Object) / Object getAttribute(String)\n");
        sb.append("7. 表元数据同步（新增/删除/重命名字段后必须调用，否则下游组件无法识别变更）：\n");
        sb.append("   - 新增或修改字段：flowFile.upsertColumnMeta(\"字段名\", \"类型\");\n");
        sb.append("   - 删除字段：flowFile.removeColumnMeta(\"字段名\");\n");
        sb.append("   - 重命名字段：flowFile.renameColumnMeta(\"旧名\", \"新名\");\n");
        sb.append("   注意：这些方法仅在 FlowFile 已携带表元数据（_tableMetadata）时生效；缺失元数据时下游会自行推断，无需处理。\n\n");

        sb.append("【代码规范】\n");
        sb.append("- 只处理自己关心的数据格式，其它格式一律原样 return flowFile 透传，避免破坏数据。\n");
        sb.append("- 处理已有字段时，字段名必须严格来自上游调试元数据或用户明确给出的字段，禁止臆造、猜测或擅自改名。\n");
        sb.append("- 对数据新增/删除/重命名字段时，必须在处理记录的同时同步调用对应的表元数据方法，保持数据与元数据一致。\n");
        sb.append("- 遍历 JSON 数组时先构造结果集再 setJsonArray，不要边遍历边改动原集合结构。\n");
        sb.append("- CDC 场景：UPDATE/DELETE 事件记录可能带 __before 变更前数据，需要一并处理。\n");
        sb.append("- 对可能为 null 的对象判空；异常时用 log.error 记录并返回 flowFile，避免中断任务。\n");
        sb.append("- 变量与字段命名使用小驼峰；脚本运行在 JDK 17，不要使用未命名类、模式匹配 switch 等更高版本语法。\n");
        sb.append("- 脚本中不得访问网络、文件系统或数据库，只做纯数据处理。\n\n");

        sb.append("【参考示例】\n");
        sb.append("示例1：简单的属性处理脚本\n");
        sb.append("```java\n").append(JavaScriptComponentExample.simpleAttributeScript()).append("\n```\n\n");
        sb.append("示例2：JSON 数据处理脚本\n");
        sb.append("```java\n").append(JavaScriptComponentExample.jsonDataProcessingScript()).append("\n```\n\n");
        sb.append("示例3：按数据格式分发的复杂转换脚本\n");
        sb.append("```java\n").append(JavaScriptComponentExample.complexTransformationScript()).append("\n```\n\n");
        sb.append("示例4：新增字段并同步表元数据\n");
        sb.append("```java\n").append(JavaScriptComponentExample.addFieldWithMetadataScript()).append("\n```\n\n");

        sb.append(renderUpstreamData(context.getContextData()));

        sb.append("【工作方式】\n");
        sb.append("1. 若需求信息不足（例如未说明字段名），基于通用做法给出可运行骨架，并用注释标出需要用户调整的位置。\n");
        sb.append("2. 生成脚本后调用 validate_javascript 编译校验；若报错，依据诊断信息修正后重试。\n");
        sb.append("3. 最终答案先用 ```java 代码块给出完整脚本，再用简体中文简要说明处理逻辑与需确认的字段。\n");
        return sb.toString();
    }

    /**
     * 渲染上游组件的调试采样数据。没有采样时返回空串，避免提示词里出现空章节。
     */
    private static String renderUpstreamData(Map<String, Object> contextData) {
        if (contextData == null) {
            return "";
        }
        Object upstream = contextData.get("upstream");
        if (!(upstream instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        return "【上游输入调试数据】\n" +
        "以下是本组件上游的真实调试采样，包含字段名、字段类型与样例数据。\n" +
        "若需求涉及已有字段，必须严格在此元数据范围内识别并选择正确字段，不得臆造、猜测或擅自改名；" +
        "用户提到的字段若不在其中，不要臆造，应在最终说明中提示该字段不存在并列出可用字段。\n" +
        "若脚本对这些数据新增字段，记得同步调用 flowFile.upsertColumnMeta(字段名, 类型) 更新表元数据。\n" +
        JSON.toJSONString(upstream, JSONWriter.Feature.PrettyFormat) +
        "\n\n";
    }
}
