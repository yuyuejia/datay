package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONUtil;
import com.data.datafusion.ai.decision.DecisionConfig;
import com.data.datafusion.ai.decision.DecisionConfigService;
import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.repository.ETLEdgeRepository;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.service.etl.DecisionNodeTranslator;
import com.data.datafusion.service.etl.ETLNodeTranslatorRegistry;
import com.data.datafusion.service.mapper.ETLEdgeMapper;
import com.data.datafusion.service.mapper.ETLNodeMapper;
import com.data.datafusion.service.mapper.ETLTaskMapper;
import com.data.datafusion.service.dto.ETLTaskDTO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 「决策模型」组件翻译为 {@code DecisionComponent} 任务定义的单元测试。
 * <p>验证接口地址/模型等连接参数来自服务配置，而非任务节点配置。
 */
class ETLDecisionTranslatorTest {

    private static final String QUESTIONS =
        "{\"team\":{\"type\":\"choice\",\"instructions\":\"which\",\"criteria\":{\"a\":\"A\"}}}";

    private ETLTaskService etlTaskService;

    private ETLNodeMapper eTLNodeMapper;

    private ETLEdgeMapper etlEdgeMapper;

    private DecisionConfig decisionConfig;

    @BeforeEach
    void setUp() {
        eTLNodeMapper = mock(ETLNodeMapper.class);
        etlEdgeMapper = mock(ETLEdgeMapper.class);
        decisionConfig = new DecisionConfig();
        decisionConfig.setBaseUrl("http://localhost:11434/v1");
        decisionConfig.setApiKey("ollama");
        decisionConfig.setModel("nimble");
        decisionConfig.setTimeoutSeconds(60);
        DecisionConfigService decisionConfigService = mock(DecisionConfigService.class);
        when(decisionConfigService.getConfig()).thenReturn(decisionConfig);

        ETLNodeTranslatorRegistry registry = new ETLNodeTranslatorRegistry(List.of(new DecisionNodeTranslator(decisionConfigService)));
        etlTaskService = new ETLTaskService(
            mock(ETLTaskRepository.class),
            mock(ETLNodeRepository.class),
            mock(ETLEdgeRepository.class),
            mock(ETLTaskMapper.class),
            eTLNodeMapper,
            etlEdgeMapper,
            mock(JobService.class),
            mock(DataSourceService.class),
            registry
        );
    }

    private ETLTaskDTO taskDto() {
        ETLTaskDTO dto = new ETLTaskDTO();
        dto.setNodes(List.of());
        dto.setEdges(List.of());
        return dto;
    }

    private ETLNode decisionNode(String config) {
        ETLNode node = new ETLNode();
        node.setCode("DECISION_01");
        node.setLabel("决策模型");
        node.setType("DecisionComponent");
        node.setConfig(config);
        return node;
    }

    @Test
    void shouldInjectDecisionConfig() {
        String config = "{\"questions\":\"" + QUESTIONS.replace("\"", "\\\"") + "\",\"stateFields\":\"ticket\",\"answerPrefix\":\"decision_\"}";
        when(eTLNodeMapper.toEntity(anyList())).thenReturn(List.of(decisionNode(config)));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(List.of());

        var unit = JSONUtil.parseObj(etlTaskService.generateETLJobJson(taskDto())).getJSONArray("units").getJSONObject(0);

        assertThat(unit.getStr(".id")).isEqualTo("DECISION_01");
        assertThat(unit.getStr(".name")).isEqualTo("DecisionComponent");
        assertThat(unit.getStr("baseUrl")).isEqualTo("http://localhost:11434/v1");
        assertThat(unit.getStr("apiKey")).isEqualTo("ollama");
        assertThat(unit.getStr("model")).isEqualTo("nimble");
        assertThat(unit.getStr("timeoutSeconds")).isEqualTo("60");
        assertThat(unit.getStr("stateFields")).isEqualTo("ticket");
        assertThat(unit.getStr("answerPrefix")).isEqualTo("decision_");
        assertThat(unit.getStr("questions")).isEqualTo(QUESTIONS);
    }

    @Test
    void shouldFailWhenQuestionsMissing() {
        when(eTLNodeMapper.toEntity(anyList())).thenReturn(List.of(decisionNode("{}")));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> etlTaskService.generateETLJobJson(taskDto()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未配置问题");
    }

    @Test
    void shouldFailWhenDecisionServiceNotConfigured() {
        decisionConfig.setApiKey("");
        when(eTLNodeMapper.toEntity(anyList())).thenReturn(List.of(decisionNode("{\"questions\":\"{}\"}")));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> etlTaskService.generateETLJobJson(taskDto()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("未配置决策模型服务");
    }
}
