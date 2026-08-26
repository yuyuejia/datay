package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.expression.ParameterUtil;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;

/**
 * GenerateFlowFile组件 - 支持手动输入文本数据和数据格式，生成FlowFile
 * 该组件可以作为数据流的源头，生成指定格式的数据流
 */
public class GenerateFlowFile extends FlowComponent {

    private String attribute; //属性内容
    // 用户输入的文本数据
    private String inputData;

    // 数据格式类型
    private String dataFormat;

    // 分隔符（用于CSV格式）
    private String delimiter = ",";

    // 是否包含表头（用于CSV格式）
    private boolean hasHeader = false;

    // JSON字段映射（用于JSON格式）
    private String fieldMapping;

    // 循环输出次数（默认1次，即不循环）
    private int loopCount = 1;

    // 每次输出后的等待时间（毫秒，默认1000ms）
    private int waitTime = 1000;

     public GenerateFlowFile() {
        setType(ComponentType.SOURCE);  // 设置为Source类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        logInfo("开始生成FlowFile数据，格式: " + dataFormat);

        if (inputData == null || inputData.trim().isEmpty()) {
            logInfo("警告：输入数据为空，将生成空的FlowFile");
            // 生成空的FlowFile
            FlowFile emptyFlowFile = new FlowFile();
            emptyFlowFile.setAttribute("_empty", true);
            writeRecords(emptyFlowFile);
            return;
        }

        try {
            if (loopCount > 1) {
                // 启用循环输出模式
                executeLoopOutput();
            } else {
                // 单次输出模式
                executeSingleOutput();
            }
        } catch (Exception e) {
            logInfo("生成FlowFile时发生错误: " + e.getMessage());
            throw new RuntimeException("生成FlowFile失败", e);
        }
    }

    /**
     * 单次输出模式
     */
    private void executeSingleOutput() {
        FlowFile generatedFlowFile = generateFlowFileFromInput();
        writeRecords(generatedFlowFile);
        logInfo("成功生成FlowFile，数据格式: " + dataFormat);
    }

    /**
     * 循环输出模式
     */
    private void executeLoopOutput() {
        logInfo("启用循环输出模式，总次数: " + loopCount + "，每次等待: " + waitTime + "ms");

        for (int i = 1; i <= loopCount; i++) {
            try {
                // 在每次循环开始前检查中断状态
                if (Thread.currentThread().isInterrupted()) {
                    logInfo("循环输出模式被中断，当前循环次数: " + (i - 1) + "/" + loopCount);
                    return;
                }
                // 生成FlowFile
                FlowFile generatedFlowFile = generateFlowFileFromInput();

                // 设置循环相关信息
                generatedFlowFile.setAttribute("_loopIndex", i);
                generatedFlowFile.setAttribute("_loopTotal", loopCount);
                generatedFlowFile.setAttribute("_loopTimestamp", System.currentTimeMillis());

                // 输出FlowFile
                writeRecords(generatedFlowFile);

                logInfo("第 " + i + "/" + loopCount + " 次输出FlowFile成功");

                // 如果不是最后一次输出，则等待指定时间
                if (i < loopCount) {
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // 恢复中断状态
                    }
                }
            } catch (Exception e) {
                logInfo("第 " + i + " 次输出FlowFile时发生错误: " + e.getMessage());
                // 检查是否是中断异常
            }
        }

        logInfo("循环输出完成，共输出 " + loopCount + " 次FlowFile");
    }

    /**
     * 根据输入数据和格式生成FlowFile
     */
    private FlowFile generateFlowFileFromInput() {
        FlowFile flowFile = new FlowFile();
        // 设置通用属性
        flowFile.setAttribute("_source", "GenerateFlowFile");
        flowFile.setAttribute("_dataFormat", dataFormat);
        flowFile.setAttribute("_timestamp", System.currentTimeMillis());
        // 设置属性
        if (attribute != null && !attribute.trim().isEmpty()) {
            //解析 json
            JSONObject jsonObject = JSONObject.parseObject(attribute);
            // 遍历jsonObject，设置属性
            for (String key : jsonObject.keySet()) {
                flowFile.setAttribute(key, ParameterUtil.replaceParameters(jsonObject.get(key).toString()));
            }
        }

        switch (dataFormat.toUpperCase()) {
            case "TEXT":
                generateTextFlowFile(flowFile);
                break;
            case "CSV":
                generateCsvFlowFile(flowFile);
                break;
            case "JSON_ARRAY":
                generateJsonArrayFlowFile(flowFile);
                break;
            case "JSON_OBJECT":
                generateJsonObjectFlowFile(flowFile);
                break;
            default:
                throw new IllegalArgumentException("不支持的数据格式: " + dataFormat);
        }
        return flowFile;
    }

    /**
     * 生成文本格式的FlowFile
     */
    private void generateTextFlowFile(FlowFile flowFile) {
        flowFile.setTextData(ParameterUtil.replaceParameters(inputData, flowFile.getAttributeMap()));
    }

    /**
     * 生成CSV格式的FlowFile
     */
    private void generateCsvFlowFile(FlowFile flowFile) {
        // 简单的CSV处理，将输入数据按行分割
        String[] lines = ParameterUtil.replaceParameters(inputData, flowFile.getAttributeMap()).split("\\r?\\n");
        StringBuilder csvBuilder = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (hasHeader && i == 0) {
                // 第一行作为表头
                csvBuilder.append(lines[i]).append("\n");
            } else {
                // 数据行，按分隔符处理
                String[] fields = lines[i].split(delimiter);
                for (int j = 0; j < fields.length; j++) {
                    csvBuilder.append(fields[j].trim());
                    if (j < fields.length - 1) {
                        csvBuilder.append(delimiter);
                    }
                }
                csvBuilder.append("\n");
            }
        }

        flowFile.setCsvData(csvBuilder.toString());
    }

    /**
     * 生成JSON数组格式的FlowFile
     */
    private void generateJsonArrayFlowFile(FlowFile flowFile) {
        try {
            // 尝试直接解析为JSON数组
            JSONArray jsonArray = JSONArray.parseArray(ParameterUtil.replaceParameters(inputData, flowFile.getAttributeMap()));
            flowFile.setJsonArray(jsonArray);
            logInfo("生成JSON数组数据，元素数量: " + jsonArray.size());
        } catch (Exception e) {
            // 如果解析失败，将每行作为一个字符串元素
            String[] lines = ParameterUtil.replaceParameters(inputData, flowFile.getAttributeMap()).split("\\r?\\n");
            JSONArray jsonArray = new JSONArray();
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    jsonArray.add(line.trim());
                }
            }
            flowFile.setJsonArray(jsonArray);
            logInfo("将输入数据转换为JSON数组，元素数量: " + jsonArray.size());
        }
    }

    /**
     * 生成JSON对象格式的FlowFile
     */
    private void generateJsonObjectFlowFile(FlowFile flowFile) {
        try {
            // 尝试直接解析为JSON对象
            JSONObject jsonObject = JSONObject.parseObject(ParameterUtil.replaceParameters(inputData, flowFile.getAttributeMap()));
            flowFile.setJsonObject(jsonObject);
            logInfo("生成JSON对象数据");
        } catch (Exception e) {
            // 如果解析失败，使用字段映射或创建简单对象
            JSONObject jsonObject = new JSONObject();

            if (fieldMapping != null && !fieldMapping.trim().isEmpty()) {
                // 使用字段映射
                String[] mappings = fieldMapping.split(",");
                String[] lines = ParameterUtil.replaceParameters(inputData, flowFile.getAttributeMap()).split("\\r?\\n");

                if (lines.length > 0) {
                    String[] values = lines[0].split(delimiter);
                    for (int i = 0; i < Math.min(mappings.length, values.length); i++) {
                        jsonObject.put(mappings[i].trim(), values[i].trim());
                    }
                }
            } else {
                // 创建简单对象，包含原始数据
                jsonObject.put("com/data", inputData);
                jsonObject.put("type", "text");
            }

            flowFile.setJsonObject(jsonObject);
            logInfo("将输入数据转换为JSON对象");
        }
    }

    // Getter和Setter方法，用于参数注入

    public String getInputData() {
        return inputData;
    }

    public void setInputData(String inputData) {
        this.inputData = inputData;
    }

    public String getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public boolean isHasHeader() {
        return hasHeader;
    }

    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    public String getFieldMapping() {
        return fieldMapping;
    }

    public void setFieldMapping(String fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    public int getLoopCount() {
        return loopCount;
    }

    public void setLoopCount(int loopCount) {
        this.loopCount = loopCount;
    }

    public int getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getAttribute() {
        return attribute;
    }
}
