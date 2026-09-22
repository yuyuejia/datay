package com.data.job.component.javascript;

import com.data.job.ComponentRegister;
import com.data.job.ExceptionUtils;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;

/**
 * JavaScript组件 - 支持用户自定义Java代码处理flowfile的数据和属性
 * 在Spring Boot RestartClassLoader环境下工作
 */
@ComponentRegister(
    value = "JavaScriptComponent",
    name = "JAVA脚本",
    group = "数据处理",
    desc = "使用用户自定义 Java 脚本处理 FlowFile 的数据和属性，支持运行时动态编译执行。",
    order = 19
)
public class JavaScriptComponent extends FlowComponent {

    private String scriptCode;
    private String className;
    private final JavaScriptEngine engine;

    public JavaScriptComponent() {
        setType(ComponentType.OPERATOR);
        this.engine = new JavaScriptEngine();
        // 生成唯一的类名，避免类名冲突
        //        this.className = "UserScript_" + UUID.randomUUID().toString().replace("-", "");
        this.className = "UserScript";
    }

    public void execute(FlowFile flowFile) {
        if (flowFile.getAttribute("_end") != null) {
            writeRecords(flowFile);
            return;
        }

        if (scriptCode == null || scriptCode.trim().isEmpty()) {
            scriptCode = getDefaultScript();
        }

        // 创建脚本执行上下文
        ScriptContext scriptContext = new ScriptContext(flowFile, this);

        try {
            // 执行用户自定义Java代码
            FlowFile result = engine.executeScript(className, scriptCode, flowFile, scriptContext);

            // 将处理后的结果写入输出
            writeRecords(result);
        } catch (Exception e) {
            logError("Java脚本执行错误: " + ExceptionUtils.describe(e));
            throw new RuntimeException("Java脚本执行错误: " + ExceptionUtils.describe(e), e);
        }
    }

    /**
     * 获取默认脚本模板
     */
    private String getDefaultScript() {
        return (
                "import com.data.job.FlowFile;\n" +
                        "import com.data.job.component.javascript.ScriptContext.LogFunction;\n" +
                        "import com.alibaba.fastjson2.JSONArray;\n" +
                        "import com.alibaba.fastjson2.JSONObject;\n" +
                        "import java.util.Map;\n\n" +
                        "public class UserScript {\n" +
                        "    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {\n" +
                        "        LogFunction log = (LogFunction) context.get(\"log\");\n\n" +
                        "        // 1. 处理 JSON 数组：逐条读取、修改并输出\n" +
                        "        if (flowFile.getJsonArray() != null) {\n" +
                        "            JSONArray records = flowFile.getJsonArray();\n" +
                        "            JSONArray result = new JSONArray();\n" +
                        "            for (Object item : records) {\n" +
                        "                JSONObject record = (JSONObject) item;\n" +
                        "                // CDC 场景：UPDATE/DELETE 事件包含变更前数据 __before，先处理它\n" +
                        "                JSONObject before = record.getJSONObject(\"__before\");\n" +
                        "                if (before != null) {\n" +
                        "                    before.put(\"__processed\", true);\n" +
                        "                    result.add(before);\n" +
                        "                }\n" +
                        "                // 处理当前记录：新增/修改字段\n" +
                        "                record.put(\"processed\", true);\n" +
                        "                record.put(\"process_time\", System.currentTimeMillis());\n" +
                        "                result.add(record);\n" +
                        "            }\n" +
                        "            flowFile.setJsonArray(result);\n" +
                        "            // 新增字段后同步表元数据，确保下游组件能识别\n" +
                        "            flowFile.upsertColumnMeta(\"processed\", \"BOOLEAN\");\n" +
                        "            flowFile.upsertColumnMeta(\"process_time\", \"BIGINT\");\n" +
                        "            log.info(\"JSON数组处理完成，输出 \" + result.size() + \" 条\");\n" +
                        "        }\n\n" +
                        "        // 2. 处理单个 JSON 对象\n" +
                        "        if (flowFile.getJsonObject() != null) {\n" +
                        "            JSONObject record = flowFile.getJsonObject();\n" +
                        "            record.put(\"processed\", true);\n" +
                        "            record.put(\"process_time\", System.currentTimeMillis());\n" +
                        "            // 新增字段后同步表元数据，确保下游组件能识别\n" +
                        "            flowFile.upsertColumnMeta(\"processed\", \"BOOLEAN\");\n" +
                        "            flowFile.upsertColumnMeta(\"process_time\", \"BIGINT\");\n" +
                        "            log.info(\"JSON对象处理完成\");\n" +
                        "        }\n\n" +
                        "        // 3. 其它格式（CSV/TEXT/BINARY）原样透传\n" +
                        "        return flowFile;\n" +
                        "    }\n" +
                        "}"
        );
    }

    /**
     * 验证Java代码语法（预编译检查）
     * @return 验证结果
     */
    public boolean validateJavaCode() {
        if (scriptCode == null || scriptCode.trim().isEmpty()) {
            return false;
        }

        try {
            // 尝试编译代码来验证语法
            engine.compileScript(className, scriptCode);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置用户自定义Java代码
     * @param scriptCode Java源代码
     */
    public void setScriptCode(String scriptCode) {
        this.scriptCode = scriptCode;
    }

    /**
     * 获取用户自定义Java代码
     * @return Java源代码
     */
    public String getScriptCode() {
        return scriptCode;
    }

    /**
     * 设置自定义类名（可选）
     * @param className 类名
     */
    public void setClassName(String className) {
        this.className = className;
    }

    /**
     * 获取类名
     * @return 类名
     */
    public String getClassName() {
        return className;
    }
}