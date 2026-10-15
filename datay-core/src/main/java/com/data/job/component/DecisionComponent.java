package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.ai.llm.LlmClientConfig;
import com.data.ai.llm.SystemOneClient;
import com.data.ai.llm.SystemOneResponse;
import com.data.expression.ParameterUtil;
import com.data.job.ComponentRegister;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.JsonTableMetaUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 决策模型组件：按行调用 Ollama 的 Jev-style 决策接口（{@code /v1/systemone}）。
 *
 * <p>把当前行数据作为 {@code state}，对配置的一组命名问题（{@code choice} / {@code noul} / {@code score}）
 * 一次性求解，并把答案展平合并回该行，便于下游自动建表：
 * <ul>
 *     <li>{@code choice}：写入 {@code <前缀><问题名>}（选项值），可选 {@code _confidence} / {@code _probabilities}；</li>
 *     <li>{@code noul}：写入 {@code <前缀><问题名>}（0~1 概率）；</li>
 *     <li>{@code score}：写入 {@code <前缀><问题名>}（0~1 分值），可选 {@code _confidence} / {@code _probabilities} / {@code _legend}。</li>
 * </ul>
 *
 * <p>{@code state} 的构造优先级：{@code state} 模板（支持动态参数）→ {@code stateFields} 字段子集 → 整行数据。
 */
@ComponentRegister(
    value = "DecisionComponent",
    name = "决策模型",
    group = "数据处理",
    desc = "按行调用 Ollama 决策模型接口（/v1/systemone），对一组命名问题一次性作答并把结果展平到行数据。",
    order = 20
)
public class DecisionComponent extends FlowComponent {

    public static final String DEFAULT_MODEL = "nimble";

    private static final String DEFAULT_BASE_URL = "http://localhost:11434/v1";
    private static final String DEFAULT_API_KEY = "ollama";

    private String baseUrl = DEFAULT_BASE_URL;
    private String apiKey = DEFAULT_API_KEY;
    private String model = DEFAULT_MODEL;
    private int timeoutSeconds = 120;
    private int retryCount = 1;
    private int retryIntervalMillis = 1000;
    private int debugSampleRows = 3;

    /** 问题定义（JSON 对象），键为问题名，值为 {type, instructions, criteria}。 */
    private String questions;
    /** state 模板（JSON 文本），支持动态参数；为空时按 stateFields / 整行构造。 */
    private String state;
    /** state 字段子集（逗号分隔），仅在 state 模板为空时生效；为空时使用整行。 */
    private String stateFields;
    /** 合并到行数据时的字段前缀。 */
    private String answerPrefix = "";
    /** 是否合并置信度字段 {@code _confidence}。 */
    private boolean includeConfidence = true;
    /** 是否合并概率分布字段 {@code _probabilities}（score 另含 {@code _legend}）。 */
    private boolean includeProbabilities = false;
    /** 可选：把原始 answers JSON 写入该字段。 */
    private String resultField;

    private SystemOneClient client;
    private JSONObject parsedQuestions;

    public DecisionComponent() {
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
        JSONObject stateData = buildState(record, flowFile, state, stateFields);
        SystemOneResponse response = decide(stateData, rowIndex);
        mergeAnswers(record, response.getAnswers(), answerPrefix, includeConfidence, includeProbabilities, resultField);
        return record;
    }

    /**
     * 构造 state：优先按模板（支持动态参数），否则按字段子集，最后回退整行。
     */
    static JSONObject buildState(JSONObject record, FlowFile flowFile, String stateTemplate, String stateFields) {
        Map<String, Object> variables = new HashMap<>(record);
        variables.put("FLOW_FILE_DATA", record);
        variables.put("ROW_DATA", record);

        if (stateTemplate != null && !stateTemplate.trim().isEmpty()) {
            String replaced = ParameterUtil.replaceParameters(stateTemplate, flowFile, variables);
            if (replaced != null) {
                try {
                    JSONObject parsed = JSON.parseObject(replaced);
                    if (parsed != null) {
                        return parsed;
                    }
                } catch (Exception e) {
                    // 非 JSON 模板时退化为单字段 state
                }
                JSONObject wrapper = new JSONObject();
                wrapper.put("state", replaced);
                return wrapper;
            }
        }

        if (stateFields != null && !stateFields.trim().isEmpty()) {
            JSONObject selected = new JSONObject();
            for (String field : stateFields.split(",")) {
                String name = field.trim();
                if (!name.isEmpty() && record.containsKey(name)) {
                    selected.put(name, record.get(name));
                }
            }
            return selected;
        }

        return new JSONObject(record);
    }

    /**
     * 把 answers 展平合并到行数据；{@code resultField} 非空时额外写入原始 answers JSON。
     */
    static void mergeAnswers(
        JSONObject record,
        JSONObject answers,
        String prefix,
        boolean includeConfidence,
        boolean includeProbabilities,
        String resultField
    ) {
        String fieldPrefix = prefix == null ? "" : prefix;
        if (answers != null) {
            for (String name : answers.keySet()) {
                Object value = answers.get(name);
                String key = fieldPrefix + name;
                if (!(value instanceof JSONObject)) {
                    record.put(key, value);
                    continue;
                }
                JSONObject answer = (JSONObject) value;
                String type = answer.getString("type");
                if ("noul".equals(type) || (type == null && answer.containsKey("noul"))) {
                    record.put(key, answer.get("noul"));
                } else if ("score".equals(type) || (type == null && answer.containsKey("score"))) {
                    record.put(key, answer.get("score"));
                    putConfidence(record, key, answer, includeConfidence);
                    putProbabilities(record, key, answer, includeProbabilities);
                } else if ("choice".equals(type) || (type == null && answer.containsKey("choice"))) {
                    record.put(key, answer.get("choice"));
                    putConfidence(record, key, answer, includeConfidence);
                    putProbabilities(record, key, answer, includeProbabilities);
                } else {
                    record.put(key, answer.toJSONString());
                }
            }
        }
        if (resultField != null && !resultField.trim().isEmpty()) {
            record.put(resultField.trim(), answers == null ? "{}" : answers.toJSONString());
        }
    }

    private static void putConfidence(JSONObject record, String key, JSONObject answer, boolean include) {
        if (include && answer.containsKey("confidence")) {
            record.put(key + "_confidence", answer.get("confidence"));
        }
    }

    private static void putProbabilities(JSONObject record, String key, JSONObject answer, boolean include) {
        if (!include) {
            return;
        }
        JSONObject probabilities = answer.getJSONObject("probabilities");
        if (probabilities != null) {
            record.put(key + "_probabilities", probabilities.toJSONString());
        }
        JSONObject legend = answer.getJSONObject("legend");
        if (legend != null) {
            record.put(key + "_legend", legend.toJSONString());
        }
    }

    private SystemOneResponse decide(JSONObject stateData, int rowIndex) {
        JSONObject questionsPayload = resolveQuestions();

        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("决策模型调用被中断（任务已取消或调试超时）");
        }

        Exception lastException = null;
        int attempts = Math.max(1, retryCount);
        for (int attempt = 0; attempt < attempts; attempt++) {
            long callStart = System.currentTimeMillis();
            logInfo(
                "调用决策模型：行=" + rowIndex + "，第 " + (attempt + 1) + "/" + attempts + " 次，model=" + model
                    + "，问题=" + questionsPayload.keySet() + "，state=" + preview(stateData.toJSONString())
            );
            try {
                SystemOneResponse response = getClient().decide(stateData, questionsPayload);
                logInfo(
                    "决策模型返回：行=" + rowIndex + "，第 " + (attempt + 1) + "/" + attempts + " 次，耗时 "
                        + (System.currentTimeMillis() - callStart) + " ms，inputTokens=" + response.getInputTokens()
                        + "，outputTokens=" + response.getOutputTokens() + "，answers=" + preview(response.getAnswers().toJSONString())
                );
                return response;
            } catch (IOException e) {
                lastException = e;
                logError("决策模型请求失败(第 " + (attempt + 1) + "/" + attempts + " 次)：" + describeException(e));
                if (Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("决策模型调用被中断（任务已取消或调试超时）", e);
                }
                sleepBeforeRetry(attempt, attempts);
            }
        }
        throw new RuntimeException("决策模型调用失败：" + describeException(lastException), lastException);
    }

    private JSONObject resolveQuestions() {
        if (parsedQuestions == null) {
            parsedQuestions = parseQuestions(questions);
        }
        return parsedQuestions;
    }

    /**
     * 解析问题定义，要求为非空 JSON 对象；每个问题应为包含 {@code type} 的对象。
     */
    static JSONObject parseQuestions(String questions) {
        if (questions == null || questions.trim().isEmpty()) {
            throw new IllegalArgumentException("决策模型组件未配置问题（questions）");
        }
        JSONObject parsed;
        try {
            parsed = JSON.parseObject(questions);
        } catch (Exception e) {
            throw new IllegalArgumentException("决策模型问题（questions）不是合法的 JSON 对象：" + e.getMessage(), e);
        }
        if (parsed == null || parsed.isEmpty()) {
            throw new IllegalArgumentException("决策模型问题（questions）不能为空");
        }
        for (String name : parsed.keySet()) {
            Object value = parsed.get(name);
            if (!(value instanceof JSONObject) || !((JSONObject) value).containsKey("type")) {
                throw new IllegalArgumentException("决策模型问题「" + name + "」缺少 type 字段（choice/noul/score）");
            }
        }
        return parsed;
    }

    private SystemOneClient getClient() {
        if (client == null) {
            LlmClientConfig config = new LlmClientConfig();
            config.setBaseUrl((baseUrl == null || baseUrl.trim().isEmpty()) ? DEFAULT_BASE_URL : baseUrl);
            config.setApiKey((apiKey == null || apiKey.trim().isEmpty()) ? DEFAULT_API_KEY : apiKey);
            config.setModel((model == null || model.trim().isEmpty()) ? DEFAULT_MODEL : model);
            config.setTimeoutSeconds(timeoutSeconds);
            client = new SystemOneClient(config);
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
            throw new RuntimeException("决策模型调用被中断（任务已取消或调试超时）", e);
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

    public String getQuestions() {
        return questions;
    }

    public void setQuestions(String questions) {
        this.questions = questions;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStateFields() {
        return stateFields;
    }

    public void setStateFields(String stateFields) {
        this.stateFields = stateFields;
    }

    public String getAnswerPrefix() {
        return answerPrefix;
    }

    public void setAnswerPrefix(String answerPrefix) {
        this.answerPrefix = answerPrefix;
    }

    public boolean isIncludeConfidence() {
        return includeConfidence;
    }

    public void setIncludeConfidence(boolean includeConfidence) {
        this.includeConfidence = includeConfidence;
    }

    public void setIncludeConfidence(String includeConfidence) {
        if (includeConfidence != null && !includeConfidence.trim().isEmpty()) {
            this.includeConfidence = Boolean.parseBoolean(includeConfidence.trim());
        }
    }

    public boolean isIncludeProbabilities() {
        return includeProbabilities;
    }

    public void setIncludeProbabilities(boolean includeProbabilities) {
        this.includeProbabilities = includeProbabilities;
    }

    public void setIncludeProbabilities(String includeProbabilities) {
        if (includeProbabilities != null && !includeProbabilities.trim().isEmpty()) {
            this.includeProbabilities = Boolean.parseBoolean(includeProbabilities.trim());
        }
    }

    public String getResultField() {
        return resultField;
    }

    public void setResultField(String resultField) {
        this.resultField = resultField;
    }
}
