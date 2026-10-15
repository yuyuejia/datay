package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.Component.ComponentType;
import com.data.job.FlowFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DecisionComponentTest {

    @Test
    public void testComponentType() {
        assertEquals(ComponentType.OPERATOR, new DecisionComponent().getType());
    }

    @Test
    public void testParseQuestionsValid() {
        String questions = "{\"team\":{\"type\":\"choice\",\"instructions\":\"which\",\"criteria\":{\"a\":\"A\"}}}";
        JSONObject parsed = DecisionComponent.parseQuestions(questions);
        assertEquals("choice", parsed.getJSONObject("team").getString("type"));
    }

    @Test
    public void testParseQuestionsBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> DecisionComponent.parseQuestions("  "));
        assertThrows(IllegalArgumentException.class, () -> DecisionComponent.parseQuestions(null));
    }

    @Test
    public void testParseQuestionsNotJsonThrows() {
        assertThrows(IllegalArgumentException.class, () -> DecisionComponent.parseQuestions("not json"));
    }

    @Test
    public void testParseQuestionsMissingTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> DecisionComponent.parseQuestions("{\"team\":{\"instructions\":\"x\"}}"));
    }

    @Test
    public void testBuildStateWholeRow() {
        JSONObject record = new JSONObject();
        record.put("ticket", "charged twice");
        record.put("amount", 20);
        JSONObject state = DecisionComponent.buildState(record, new FlowFile(), null, null);
        assertEquals("charged twice", state.getString("ticket"));
        assertEquals(20, state.getIntValue("amount"));
    }

    @Test
    public void testBuildStateFields() {
        JSONObject record = new JSONObject();
        record.put("ticket", "charged twice");
        record.put("noise", "ignore me");
        JSONObject state = DecisionComponent.buildState(record, new FlowFile(), null, "ticket");
        assertEquals("charged twice", state.getString("ticket"));
        assertFalse(state.containsKey("noise"));
    }

    @Test
    public void testBuildStateTemplateWithDynamicParam() {
        JSONObject record = new JSONObject();
        record.put("content", "great product");
        FlowFile flowFile = new FlowFile();
        JSONObject state = DecisionComponent.buildState(record, flowFile, "{\"ticket\":\"${content}\"}", null);
        assertEquals("great product", state.getString("ticket"));
    }

    @Test
    public void testMergeAnswersChoiceWithConfidence() {
        JSONObject answers = new JSONObject();
        JSONObject team = new JSONObject();
        team.put("type", "choice");
        team.put("choice", "billing");
        team.put("confidence", 0.922);
        answers.put("team", team);

        JSONObject record = new JSONObject();
        DecisionComponent.mergeAnswers(record, answers, "", true, false, null);
        assertEquals("billing", record.getString("team"));
        assertEquals(0.922, record.getDoubleValue("team_confidence"), 0.0001);
        assertFalse(record.containsKey("team_probabilities"));
    }

    @Test
    public void testMergeAnswersNoul() {
        JSONObject answers = new JSONObject();
        JSONObject refund = new JSONObject();
        refund.put("type", "noul");
        refund.put("noul", 0.997);
        answers.put("refund", refund);

        JSONObject record = new JSONObject();
        DecisionComponent.mergeAnswers(record, answers, "", true, true, null);
        assertEquals(0.997, record.getDoubleValue("refund"), 0.0001);
        assertFalse(record.containsKey("refund_confidence"));
    }

    @Test
    public void testMergeAnswersScoreWithProbabilitiesAndLegend() {
        JSONObject answers = new JSONObject();
        JSONObject urgency = new JSONObject();
        urgency.put("type", "score");
        urgency.put("score", 0.815);
        urgency.put("confidence", 0.046);
        JSONObject probabilities = new JSONObject();
        probabilities.put("0", 0.378);
        probabilities.put("1", 0.429);
        urgency.put("probabilities", probabilities);
        JSONObject legend = new JSONObject();
        legend.put("0", "Routine");
        legend.put("2", "Urgent");
        urgency.put("legend", legend);
        answers.put("urgency", urgency);

        JSONObject record = new JSONObject();
        DecisionComponent.mergeAnswers(record, answers, "", true, true, null);
        assertEquals(0.815, record.getDoubleValue("urgency"), 0.0001);
        assertEquals(0.046, record.getDoubleValue("urgency_confidence"), 0.0001);
        assertTrue(record.getString("urgency_probabilities").contains("0.429"));
        assertTrue(record.getString("urgency_legend").contains("Routine"));
    }

    @Test
    public void testMergeAnswersPrefixAndTogglesOff() {
        JSONObject answers = new JSONObject();
        JSONObject team = new JSONObject();
        team.put("type", "choice");
        team.put("choice", "billing");
        team.put("confidence", 0.9);
        JSONObject probabilities = new JSONObject();
        probabilities.put("billing", 0.985);
        team.put("probabilities", probabilities);
        answers.put("team", team);

        JSONObject record = new JSONObject();
        DecisionComponent.mergeAnswers(record, answers, "decision_", false, false, null);
        assertEquals("billing", record.getString("decision_team"));
        assertFalse(record.containsKey("decision_team_confidence"));
        assertFalse(record.containsKey("decision_team_probabilities"));
    }

    @Test
    public void testMergeAnswersResultField() {
        JSONObject answers = new JSONObject();
        JSONObject team = new JSONObject();
        team.put("type", "choice");
        team.put("choice", "billing");
        answers.put("team", team);

        JSONObject record = new JSONObject();
        DecisionComponent.mergeAnswers(record, answers, "", true, false, "decision_result");
        assertTrue(record.getString("decision_result").contains("billing"));
    }

    @Test
    public void testMergeAnswersUnrecognizedType() {
        JSONObject answers = new JSONObject();
        JSONObject custom = new JSONObject();
        custom.put("type", "custom");
        custom.put("value", 1);
        answers.put("q", custom);

        JSONObject record = new JSONObject();
        DecisionComponent.mergeAnswers(record, answers, "", true, false, null);
        assertTrue(record.getString("q").contains("\"value\":1"));
    }
}
