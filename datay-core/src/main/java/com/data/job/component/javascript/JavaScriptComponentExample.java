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
            "import com.alibaba.fastjson2.JSONArray;\n" +
            "import com.alibaba.fastjson2.JSONArray;\n" +
            "import java.util.Map;\n" +
            "public class UserScript {\n" +
            "    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {\n" +
            "        // 添加处理时间戳\n" +
            "        flowFile.setAttribute(\"processed_time\", System.currentTimeMillis());\n" +
            "        \n" +
            "        // 添加组件信息\n" +
            "        flowFile.setAttribute(\"processor_id\", attributes.get(\"componentId\"));\n" +
            "        \n" +
            "        // 记录日志\n" +
            "        LogFunction log = (LogFunction) context.get(\"log\");\n" +
            "        log.info(\"处理FlowFile，当前时间: \" + System.currentTimeMillis());\n" +
            "        \n" +
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
            "            \n" +
            "            for (int i = 0; i < jsonArray.size(); i++) {\n" +
            "                JSONObject item = jsonArray.getJSONObject(i);\n" +
            "                \n" +
            "                // 添加处理逻辑：为每个对象添加处理标记\n" +
            "                item.put(\"processed\", true);\n" +
            "                item.put(\"process_timestamp\", System.currentTimeMillis());\n" +
            "                \n" +
            "                processedArray.add(item);\n" +
            "            }\n" +
            "            \n" +
            "            flowFile.setJsonArray(processedArray);\n" +
            "        }\n" +
            "        \n" +
            "        // 处理JSON对象数据\n" +
            "        if (flowFile.getJsonObject() != null) {\n" +
            "            JSONObject jsonObject = flowFile.getJsonObject();\n" +
            "            jsonObject.put(\"processed\", true);\n" +
            "            jsonObject.put(\"process_timestamp\", System.currentTimeMillis());\n" +
            "        }\n" +
            "        \n" +
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
            "import com.alibaba.fastjson2.JSONArray;\n" +
            "import com.alibaba.fastjson2.JSONObject;\n" +
            "import java.util.Map;\n\n" +
            "public class UserScript {\n" +
            "    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {\n" +
            "        LogFunction log = (LogFunction) context.get(\"log\");\n" +
            "        \n" +
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
            "    }\n" +
            "    \n" +
            "    private FlowFile processJsonArray(FlowFile flowFile, LogFunction log) {\n" +
            "        JSONArray array = flowFile.getJsonArray();\n" +
            "        log.info(\"处理JSON数组，大小: \" + array.size());\n" +
            "        \n" +
            "        // 这里可以添加复杂的数据处理逻辑\n" +
            "        // 例如：数据过滤、字段转换、聚合计算等\n" +
            "        \n" +
            "        return flowFile;\n" +
            "    }\n" +
            "    \n" +
            "    private FlowFile processJsonObject(FlowFile flowFile, LogFunction log) {\n" +
            "        JSONObject obj = flowFile.getJsonObject();\n" +
            "        log.info(\"处理JSON对象，键数量: \" + obj.size());\n" +
            "        \n" +
            "        // 这里可以添加复杂的数据处理逻辑\n" +
            "        \n" +
            "        return flowFile;\n" +
            "    }\n" +
            "    \n" +
            "    private FlowFile processCsvData(FlowFile flowFile, LogFunction log) {\n" +
            "        String csv = flowFile.getCsvData();\n" +
            "        log.info(\"处理CSV数据，长度: \" + csv.length());\n" +
            "        \n" +
            "        // 这里可以添加CSV数据处理逻辑\n" +
            "        \n" +
            "        return flowFile;\n" +
            "    }\n" +
            "    \n" +
            "    private FlowFile processTextData(FlowFile flowFile, LogFunction log) {\n" +
            "        String text = flowFile.getTextData();\n" +
            "        log.info(\"处理文本数据，长度: \" + text.length());\n" +
            "        \n" +
            "        // 这里可以添加文本处理逻辑\n" +
            "        \n" +
            "        return flowFile;\n" +
            "    }\n" +
            "}"
        );
    }
}
