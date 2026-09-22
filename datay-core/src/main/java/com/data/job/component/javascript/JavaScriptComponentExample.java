package com.data.job.component.javascript;

/**
 * Java脚本组件使用示例
 * 展示如何使用JavaScriptComponent进行自定义数据处理
 */
public class JavaScriptComponentExample {

    /**
     * 示例1：简单的属性处理脚本
     */
    public static String simpleAttributeScript() {
        return (
            "import com.data.job.FlowFile;\n" +
            "import com.data.job.component.javascript.ScriptContext.LogFunction;\n" +
            "import java.util.Map;\n\n" +
            "public class UserScript {\n" +
            "    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {\n" +
            "        // 添加处理时间戳\n" +
            "        flowFile.setAttribute(\"processed_time\", System.currentTimeMillis());\n" +
            "        // 添加组件信息\n" +
            "        flowFile.setAttribute(\"processor_id\", context.get(\"componentId\"));\n" +
            "        // 记录日志\n" +
            "        LogFunction log = (LogFunction) context.get(\"log\");\n" +
            "        log.info(\"处理FlowFile，当前时间: \" + System.currentTimeMillis());\n" +
            "        return flowFile;\n" +
            "    }\n" +
            "}"
        );
    }

    /**
     * 示例2：JSON数据处理脚本
     */
    public static String jsonDataProcessingScript() {
        return (
            "import com.data.job.FlowFile;\n" +
            "import com.alibaba.fastjson2.JSONArray;\n" +
            "import com.alibaba.fastjson2.JSONObject;\n" +
            "import java.util.Map;\n\n" +
            "public class UserScript {\n" +
            "    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {\n" +
            "        // 处理JSON数组数据\n" +
            "        if (flowFile.getJsonArray() != null) {\n" +
            "            JSONArray jsonArray = flowFile.getJsonArray();\n" +
            "            JSONArray processedArray = new JSONArray();\n" +
            "            for (Object item : jsonArray) {\n" +
            "                JSONObject obj = (JSONObject) item;\n" +
            "                // 添加处理逻辑：为每个对象添加处理标记\n" +
            "                obj.put(\"processed\", true);\n" +
            "                obj.put(\"process_timestamp\", System.currentTimeMillis());\n" +
            "                processedArray.add(obj);\n" +
            "            }\n" +
            "            flowFile.setJsonArray(processedArray);\n" +
            "            // 新增字段后同步表元数据，确保下游组件能识别\n" +
            "            flowFile.upsertColumnMeta(\"processed\", \"BOOLEAN\");\n" +
            "            flowFile.upsertColumnMeta(\"process_timestamp\", \"BIGINT\");\n" +
            "        }\n\n" +
            "        // 处理JSON对象数据\n" +
            "        if (flowFile.getJsonObject() != null) {\n" +
            "            JSONObject jsonObject = flowFile.getJsonObject();\n" +
            "            jsonObject.put(\"processed\", true);\n" +
            "            jsonObject.put(\"process_timestamp\", System.currentTimeMillis());\n" +
            "            // 新增字段后同步表元数据，确保下游组件能识别\n" +
            "            flowFile.upsertColumnMeta(\"processed\", \"BOOLEAN\");\n" +
            "            flowFile.upsertColumnMeta(\"process_timestamp\", \"BIGINT\");\n" +
            "        }\n\n" +
            "        return flowFile;\n" +
            "    }\n" +
            "}"
        );
    }

    /**
     * 示例3：复杂的数据转换脚本
     */
    public static String complexTransformationScript() {
        return (
            "import com.data.job.FlowFile;\n" +
            "import com.data.job.component.javascript.ScriptContext.LogFunction;\n" +
            "import com.alibaba.fastjson2.JSONArray;\n" +
            "import com.alibaba.fastjson2.JSONObject;\n" +
            "import java.util.Map;\n\n" +
            "public class UserScript {\n" +
            "    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {\n" +
            "        LogFunction log = (LogFunction) context.get(\"log\");\n" +
            "        try {\n" +
            "            // 根据数据类型进行不同的处理\n" +
            "            switch (flowFile.getDataFormat()) {\n" +
            "                case JSON_ARRAY:\n" +
            "                    return processJsonArray(flowFile, log);\n" +
            "                case JSON_OBJECT:\n" +
            "                    return processJsonObject(flowFile, log);\n" +
            "                case CSV:\n" +
            "                    return processCsvData(flowFile, log);\n" +
            "                case TEXT:\n" +
            "                    return processTextData(flowFile, log);\n" +
            "                default:\n" +
            "                    log.warn(\"不支持的数据格式: \" + flowFile.getDataFormat());\n" +
            "                    return flowFile;\n" +
            "            }\n" +
            "        } catch (Exception e) {\n" +
            "            log.error(\"数据处理失败: \" + e.getMessage());\n" +
            "            return flowFile;\n" +
            "        }\n" +
            "    }\n\n" +
            "    private FlowFile processJsonArray(FlowFile flowFile, LogFunction log) {\n" +
            "        JSONArray array = flowFile.getJsonArray();\n" +
            "        log.info(\"处理JSON数组，大小: \" + array.size());\n" +
            "        // 这里可以添加复杂的数据处理逻辑\n" +
            "        // 例如：数据过滤、字段转换、聚合计算等\n" +
            "        return flowFile;\n" +
            "    }\n\n" +
            "    private FlowFile processJsonObject(FlowFile flowFile, LogFunction log) {\n" +
            "        JSONObject obj = flowFile.getJsonObject();\n" +
            "        log.info(\"处理JSON对象，键数量: \" + obj.size());\n" +
            "        // 这里可以添加复杂的数据处理逻辑\n" +
            "        return flowFile;\n" +
            "    }\n\n" +
            "    private FlowFile processCsvData(FlowFile flowFile, LogFunction log) {\n" +
            "        String csv = flowFile.getCsvData();\n" +
            "        log.info(\"处理CSV数据，长度: \" + csv.length());\n" +
            "        // 这里可以添加CSV数据处理逻辑\n" +
            "        return flowFile;\n" +
            "    }\n\n" +
            "    private FlowFile processTextData(FlowFile flowFile, LogFunction log) {\n" +
            "        String text = flowFile.getTextData();\n" +
            "        log.info(\"处理文本数据，长度: \" + text.length());\n" +
            "        // 这里可以添加文本处理逻辑\n" +
            "        return flowFile;\n" +
            "    }\n" +
            "}"
        );
    }

    /**
     * 示例4：新增字段并同步表元数据
     * 对已有数据增加字段时，必须同时更新 FlowFile 的表元数据属性，
     * 否则下游组件读取不到新字段，导致数据丢失或结构不一致。
     */
    public static String addFieldWithMetadataScript() {
        return (
            "import com.data.job.FlowFile;\n" +
            "import com.data.job.component.javascript.ScriptContext.LogFunction;\n" +
            "import com.alibaba.fastjson2.JSONArray;\n" +
            "import com.alibaba.fastjson2.JSONObject;\n" +
            "import java.util.Map;\n\n" +
            "public class UserScript {\n" +
            "    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {\n" +
            "        LogFunction log = (LogFunction) context.get(\"log\");\n\n" +
            "        if (flowFile.getJsonArray() != null) {\n" +
            "            JSONArray records = flowFile.getJsonArray();\n" +
            "            JSONArray result = new JSONArray();\n" +
            "            for (Object item : records) {\n" +
            "                JSONObject record = (JSONObject) item;\n" +
            "                // 基于上游已有字段 amount 计算并新增 tax_amount\n" +
            "                Object amount = record.get(\"amount\");\n" +
            "                if (amount instanceof Number) {\n" +
            "                    record.put(\"tax_amount\", ((Number) amount).doubleValue() * 1.13);\n" +
            "                }\n" +
            "                result.add(record);\n" +
            "            }\n" +
            "            flowFile.setJsonArray(result);\n" +
            "            // 关键：新增字段后同步表元数据，下游组件才能识别 tax_amount\n" +
            "            flowFile.upsertColumnMeta(\"tax_amount\", \"DOUBLE\");\n" +
            "            log.info(\"已新增字段 tax_amount，共 \" + result.size() + \" 条\");\n" +
            "        }\n\n" +
            "        // 删除字段：flowFile.removeColumnMeta(\"字段名\");\n" +
            "        // 重命名字段：flowFile.renameColumnMeta(\"旧名\", \"新名\");\n" +
            "        return flowFile;\n" +
            "    }\n" +
            "}"
        );
    }
}
