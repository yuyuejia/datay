package com.data.job.component.javascript;

import com.data.job.FlowComponent;
import com.data.job.FlowFile;

/**
 * JavaScript组件 - 支持用户自定义Java代码处理flowfile的数据和属性
 * 在Spring Boot RestartClassLoader环境下工作
 */
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
            logInfo("Java脚本执行错误: " + e.getMessage());
            // 发生错误时传递原始flowFile
            writeRecords(flowFile);
        }
    }

    /**
     * 获取默认脚本模板
     */
    private String getDefaultScript() {
        return (
                "import com.data.job.FlowFile;\n" +
                        "import com.alibaba.fastjson2.JSONArray;\n" +
                        "import com.alibaba.fastjson2.JSONObject;\n" +
                        "import java.util.Map;\n\n" +
                        "public class UserScript {\n" +
                        "    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {\n" +
                        "        // 添加处理时间戳\n" +
                        "        String event = flowFile.getAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE).toString();\n" +
                        "        if(\"INSERT\".equals(event)){\n" +
                        "        }else if(\"UPDATE\".equals(event)){\n" +
                        "            //如果是 update 事件，获取 before 数据\n" +
                        "            JSONArray records = flowFile.getJsonArray();\n" +
                        "            for (Object record : records) {\n" +
                        "                JSONObject jsonRecord = (JSONObject) record;\n" +
                        "                JSONObject beforeRecord = jsonRecord.getJSONObject(\"__before\");\n" +
                        "                Double order_amount = beforeRecord.getDouble(\"order_amount\");\n" +
                        "                //如果是删除事件，金额字段加负号\n" +
                        "                if(order_amount == null){\n" +
                        "                    beforeRecord.put(\"order_amount\", -1 * order_amount);\n" +
                        "                }\n" +
                        "                records.add(beforeRecord);\n" +
                        "            }\n" +
                        "        }else if(\"DELETE\".equals(event)){\n" +
                        "            JSONArray records = flowFile.getJsonArray();\n" +
                        "            for (Object record : records) {\n" +
                        "                JSONObject jsonRecord = (JSONObject) record;\n" +
                        "                Double order_amount = jsonRecord.getDouble(\"order_amount\");\n" +
                        "                //如果是删除事件，金额字段加负号\n" +
                        "                if(order_amount == null){\n" +
                        "                    jsonRecord.put(\"order_amount\", -1 * order_amount);\n" +
                        "                }\n" +
                        "            }\n" +
                        "        }" +
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