package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.ai.llm.ChatMessage;
import com.data.ai.llm.ChatResponse;
import com.data.ai.llm.LlmClientConfig;
import com.data.ai.llm.OpenAiCompatibleClient;
import com.data.ai.llm.ToolDefinition;
import com.data.expression.ParameterUtil;
import com.data.job.ComponentRegister;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.JsonTableMetaUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型组件：按行调用 OpenAI 兼容的 Chat Completions 接口。
 *
 * <p>模型调用复用 DataY Core 的共享客户端 {@link OpenAiCompatibleClient}（与 DataY Web 的 AI 助手同源实现），
 * 支持 function calling，可通过 {@link #resolveTools()} 扩展工具注册。
 *
 * <p>系统提示词与用户输入均支持动态参数（{@code ${字段名}}、{@code ${attr:属性名}}、{@code #{...}}），
 * 其中 {@code ${字段名}} 会优先按当前行数据字段赋值，取不到时回退到 FlowFile 属性。
 * 每行独立调用模型：若模型返回 JSON 对象，则将其属性扩充到该行数据；否则将文本结果写入
 * {@code llm_result} 字段后传递到下游。
 */
@ComponentRegister("LlmComponent")
public class LlmComponent extends FlowComponent {

    public static final String RESULT_FIELD = "llm_result";

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private String systemPrompt;
    private String userPrompt;
    private String baseUrl = DEFAULT_BASE_URL;
    private String apiKey;
    private String model = "gpt-4o-mini";
    private double temperature = 0.2;
    private int timeoutSeconds = 120;
    private int retryCount = 1;
    private int retryIntervalMillis = 1000;
    private int debugSampleRows = 3;

    private OpenAiCompatibleClient client;

    public LlmComponent() {
        setType(ComponentType.OPERATOR);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile.getAttribute("_end") != null) {
            writeRecords(flowFile);
            return;
        }

        JSONArray records = flowFile.getJsonArray();
        if (records != null && !records.isEmpty()) {
            processArray(flowFile, records);
            return;
        }

        JSONObject single = flowFile.getJsonObject();
        if (single != null) {
            JSONObject result = processRecord(single, flowFile, 1);
            JSONArray output = new JSONArray();
            output.add(result);
            FlowFile outFile = new FlowFile();
            outFile.getAttributeMap().putAll(flowFile.getAttributeMap());
            outFile.setJsonObject(result);
            updateTableMeta(outFile, flowFile, output);
            writeRecords(outFile);
            return;
        }

        if (flowFile.getData() != null) {
            writeRecords(flowFile);
        }
    }

    private void processArray(FlowFile flowFile, JSONArray records) {
        int limit = records.size();
        if (getContext() != null && getContext().isDebugMode() && debugSampleRows > 0 && limit > debugSampleRows) {
            limit = debugSampleRows;
            logInfo("调试模式：仅处理前 " + limit + " 行（共 " + records.size() + " 行），正式运行时处理全部数据");
        }

        JSONArray output = new JSONArray(limit);
        for (int i = 0; i < limit; i++) {
            Object item = records.get(i);
            if (item instanceof JSONObject) {
                output.add(processRecord((JSONObject) item, flowFile, i + 1));
            } else {
                output.add(item);
            }
        }

        FlowFile outFile = new FlowFile();
        outFile.getAttributeMap().putAll(flowFile.getAttributeMap());
        outFile.setJsonArray(output);
        updateTableMeta(outFile, flowFile, output);
        writeRecords(outFile);
    }

    private JSONObject processRecord(JSONObject record, FlowFile flowFile, int rowIndex) {
        Map<String, Object> variables = new HashMap<>(record);
        // 按行处理时，FLOW_FILE_DATA / ROW_DATA 指向当前行，避免每个请求都携带整个数据集
        variables.put("FLOW_FILE_DATA", record);
        variables.put("ROW_DATA", record);
        String system = ParameterUtil.replaceParameters(systemPrompt, flowFile, variables);
        String user = ParameterUtil.replaceParameters(userPrompt, flowFile, variables);
        if (user == null || user.trim().isEmpty()) {
            user = record.toJSONString();
        }

        String content = chat(system, user, rowIndex);
        mergeResult(record, content);
        return record;
    }

    /**
     * 将模型返回内容合并到当前行：JSON 对象扩充属性，其它内容写入 {@link #RESULT_FIELD} 字段。
     */
    static void mergeResult(JSONObject record, String content) {
        if (content == null) {
            record.put(RESULT_FIELD, null);
            return;
        }
        String json = extractJsonObject(content);
        if (json != null) {
            try {
                JSONObject result = JSON.parseObject(json);
                if (result != null) {
                    for (Map.Entry<String, Object> entry : result.entrySet()) {
                        record.put(entry.getKey(), entry.getValue());
                    }
                    return;
                }
            } catch (Exception e) {
                // 内容形似 JSON 但解析失败时，退化为文本结果
            }
        }
        record.put(RESULT_FIELD, content);
    }

    /**
     * 提取文本中的 JSON 对象，支持去除 &#96;&#96;&#96;json 代码块包裹；非 JSON 对象返回 null。
     */
    static String extractJsonObject(String content) {
        if (content == null) {
            return null;
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence);
            }
            text = text.trim();
        }
        return text.startsWith("{") ? text : null;
    }

    private String chat(String system, String user, int rowIndex) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("未配置大模型 API Key");
        }

        List<ChatMessage> messages = new ArrayList<>();
        if (system != null && !system.trim().isEmpty()) {
            messages.add(ChatMessage.system(system));
        }
        messages.add(ChatMessage.user(user));
        List<ToolDefinition> tools = resolveTools();

        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("大模型调用被中断（任务已取消或调试超时）");
        }

        Exception lastException = null;
        int attempts = Math.max(1, retryCount);
        for (int attempt = 0; attempt < attempts; attempt++) {
            long callStart = System.currentTimeMillis();
            logInfo(
                "调用大模型：行=" + rowIndex + "，第 " + (attempt + 1) + "/" + attempts + " 次，model=" + model
                    + "，systemPrompt=" + preview(system) + "，userPrompt=" + preview(user)
            );
            try {
                ChatResponse response = getClient().chat(messages, tools, false);
                logInfo(
                    "大模型返回：行=" + rowIndex + "，第 " + (attempt + 1) + "/" + attempts + " 次，耗时 "
                        + (System.currentTimeMillis() - callStart) + " ms，promptTokens=" + response.getPromptTokens()
                        + "，completionTokens=" + response.getCompletionTokens() + "，内容=" + preview(response.getContent())
                );
                return response.getContent();
            } catch (IOException e) {
                lastException = e;
                logError("大模型请求失败(第 " + (attempt + 1) + "/" + attempts + " 次)：" + describeException(e));
                if (Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("大模型调用被中断（任务已取消或调试超时）", e);
                }
                sleepBeforeRetry(attempt, attempts);
            }
        }
        throw new RuntimeException("大模型调用失败：" + describeException(lastException), lastException);
    }

    /**
     * 注册给模型的工具集合，默认为空；后续支持工具注册时可覆写该方法。
     */
    protected List<ToolDefinition> resolveTools() {
        return List.of();
    }

    private OpenAiCompatibleClient getClient() {
        if (client == null) {
            LlmClientConfig config = new LlmClientConfig();
            config.setBaseUrl((baseUrl == null || baseUrl.trim().isEmpty()) ? DEFAULT_BASE_URL : baseUrl);
            config.setApiKey(apiKey);
            config.setModel(model);
            config.setTemperature(temperature);
            config.setTimeoutSeconds(timeoutSeconds);
            client = new OpenAiCompatibleClient(config);
        }
        return client;
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace("\r", " ").replace("\n", " ");
        return singleLine.length() > 300 ? singleLine.substring(0, 300) + "..." : singleLine;
    }

    private void sleepBeforeRetry(int attempt, int attempts) {
        if (attempt + 1 >= attempts || retryIntervalMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(retryIntervalMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("大模型调用被中断（任务已取消或调试超时）", e);
        }
    }

    private static String describeException(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 10) {
            if (builder.length() > 0) {
                builder.append(" <- ");
            }
            builder.append(current.getClass().getSimpleName());
            if (current.getMessage() != null && !current.getMessage().isEmpty()) {
                builder.append(": ").append(current.getMessage());
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private void updateTableMeta(FlowFile outFile, FlowFile inFile, JSONArray output) {
        Object meta = inFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        TableMeta existing = meta instanceof TableMeta ? (TableMeta) meta : null;
        String dbType = existing != null && existing.getDbType() != null ? existing.getDbType() : "mysql";
        TableMeta tableMeta = JsonTableMetaUtils.build(output, dbType);
        if (tableMeta != null) {
            if (existing != null) {
                tableMeta.setTable(existing.getTable());
                tableMeta.setSchema(existing.getSchema());
            }
            outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        } else if (existing != null) {
            outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, existing);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (client != null) {
            client.close();
        }
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public void setTemperature(String temperature) {
        if (temperature != null && !temperature.trim().isEmpty()) {
            try {
                this.temperature = Double.parseDouble(temperature.trim());
            } catch (NumberFormatException e) {
                this.temperature = 0.2;
            }
        }
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public void setTimeoutSeconds(String timeoutSeconds) {
        if (timeoutSeconds != null && !timeoutSeconds.trim().isEmpty()) {
            try {
                this.timeoutSeconds = Integer.parseInt(timeoutSeconds.trim());
            } catch (NumberFormatException e) {
                this.timeoutSeconds = 120;
            }
        }
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void setRetryCount(String retryCount) {
        if (retryCount != null && !retryCount.trim().isEmpty()) {
            try {
                this.retryCount = Integer.parseInt(retryCount.trim());
            } catch (NumberFormatException e) {
                this.retryCount = 1;
            }
        }
    }

    public int getRetryIntervalMillis() {
        return retryIntervalMillis;
    }

    public void setRetryIntervalMillis(int retryIntervalMillis) {
        this.retryIntervalMillis = retryIntervalMillis;
    }

    public void setRetryIntervalMillis(String retryIntervalMillis) {
        if (retryIntervalMillis != null && !retryIntervalMillis.trim().isEmpty()) {
            try {
                this.retryIntervalMillis = Integer.parseInt(retryIntervalMillis.trim());
            } catch (NumberFormatException e) {
                this.retryIntervalMillis = 1000;
            }
        }
    }

    public int getDebugSampleRows() {
        return debugSampleRows;
    }

    public void setDebugSampleRows(int debugSampleRows) {
        this.debugSampleRows = debugSampleRows;
    }

    public void setDebugSampleRows(String debugSampleRows) {
        if (debugSampleRows != null && !debugSampleRows.trim().isEmpty()) {
            try {
                this.debugSampleRows = Integer.parseInt(debugSampleRows.trim());
            } catch (NumberFormatException e) {
                this.debugSampleRows = 3;
            }
        }
    }
}
