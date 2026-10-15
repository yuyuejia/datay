package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.ai.llm.SystemOneClient;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 决策模型组件对接本地 Ollama 的联调测试。
 *
 * <p>需要本地启动 Ollama（0.35+）并已拉取决策模型（默认 {@code tev1:0.8b}）。
 * 未启动或未拉取模型时自动跳过，不影响常规回归。
 *
 * <p>可通过系统属性覆盖：
 * <ul>
 *     <li>{@code datay.test.decision.root}：Ollama 根地址，默认 {@code http://localhost:11434}</li>
 *     <li>{@code datay.test.decision.model}：决策模型，默认 {@code tev1:0.8b}</li>
 * </ul>
 */
public class DecisionComponentOllamaTest {

    private static final String ROOT = System.getProperty("datay.test.decision.root", "http://localhost:11434");
    private static final String MODEL = System.getProperty("datay.test.decision.model", "tev1:0.8b");

    private static final String QUESTIONS =
        "{\"team\":{\"type\":\"choice\",\"instructions\":\"Which team should handle this ticket?\","
            + "\"criteria\":{\"billing\":\"Payments and refunds\",\"technical\":\"Bugs and integrations\",\"other\":\"None of the above\"}},"
            + "\"refund\":{\"type\":\"noul\",\"instructions\":\"Does the customer explicitly ask for a refund?\"},"
            + "\"urgency\":{\"type\":\"score\",\"instructions\":\"How urgent is this ticket?\",\"criteria\":[\"Routine\",\"Soon\",\"Urgent\"]}}";

    @BeforeAll
    static void checkOllama() {
        try {
            String tags = httpGet(ROOT + "/api/tags");
            Assumptions.assumeTrue(tags.contains("tev1"), "未找到决策模型 tev1，跳过联调测试");
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Ollama 未启动，跳过联调测试：" + e.getMessage());
        }
    }

    @Test
    void shouldDecideAndFlattenAnswersIntoRows() {
        FlowFile flowFile = new FlowFile();
        JSONArray rows = new JSONArray();
        JSONObject r1 = new JSONObject();
        r1.put("ticket", "I was charged twice. Please refund the extra payment.");
        rows.add(r1);
        JSONObject r2 = new JSONObject();
        r2.put("ticket", "The app crashes when I open the settings page on Android.");
        rows.add(r2);
        flowFile.setJsonArray(rows);

        DecisionComponent component = new DecisionComponent();
        component.setBaseUrl(ROOT + "/v1");
        component.setApiKey("ollama");
        component.setModel(MODEL);
        component.setQuestions(QUESTIONS);
        component.setStateFields("ticket");
        component.setAnswerPrefix("decision_");
        component.setTimeoutSeconds(120);
        component.setTaskLogger(new TaskLogger("decision-verify", "ollama-it"));

        component.execute(flowFile);

        assertEquals(2, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            assertTrue(row.containsKey("decision_team"), "row " + i + " 缺少 decision_team");
            assertNotNull(row.getString("decision_team"));
            assertNotNull(row.getBigDecimal("decision_refund"), "row " + i + " 缺少 decision_refund 数值");
            assertNotNull(row.getBigDecimal("decision_urgency"), "row " + i + " 缺少 decision_urgency 数值");
            assertNotNull(row.getBigDecimal("decision_team_confidence"), "row " + i + " 缺少 team confidence");
            assertNotNull(row.getBigDecimal("decision_urgency_confidence"), "row " + i + " 缺少 urgency confidence");
            assertFalse(row.containsKey("decision_team_probabilities"), "默认不合并 probabilities");
            System.out.println("决策结果 row" + (i + 1) + " = " + row.toJSONString());
        }
    }

    @Test
    void shouldGiveActionableErrorWhenModelMissing() {
        SystemOneClient client = new SystemOneClient(systemOneConfig("definitely-not-installed-model"));
        JSONObject state = new JSONObject();
        state.put("input", "ping");
        JSONObject questions = new JSONObject();
        JSONObject alive = new JSONObject();
        alive.put("type", "noul");
        alive.put("instructions", "alive?");
        questions.put("alive", alive);

        java.io.IOException error = org.junit.jupiter.api.Assertions.assertThrows(
            java.io.IOException.class,
            () -> client.decide(state, questions)
        );
        assertTrue(error.getMessage().contains("未安装"), "应提示模型未安装：" + error.getMessage());
        assertTrue(error.getMessage().contains("ollama pull"), "应提示拉取命令：" + error.getMessage());
        client.close();
        System.out.println("缺失模型提示 = " + error.getMessage());
    }

    private static com.data.ai.llm.LlmClientConfig systemOneConfig(String model) {
        com.data.ai.llm.LlmClientConfig config = new com.data.ai.llm.LlmClientConfig();
        config.setBaseUrl(ROOT + "/v1");
        config.setApiKey("ollama");
        config.setModel(model);
        config.setTimeoutSeconds(30);
        return config;
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(1500);
        conn.setReadTimeout(5000);
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
