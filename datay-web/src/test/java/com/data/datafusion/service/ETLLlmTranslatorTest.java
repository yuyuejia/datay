package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONUtil;
import com.data.datafusion.ai.llm.LlmConfig;
import com.data.datafusion.ai.llm.LlmConfigService;
import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.repository.ETLEdgeRepository;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.etl.ETLNodeTranslatorRegistry;
import com.data.datafusion.service.etl.LlmNodeTranslator;
import com.data.datafusion.service.mapper.ETLEdgeMapper;
import com.data.datafusion.service.mapper.ETLNodeMapper;
import com.data.datafusion.service.mapper.ETLTaskMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 「大模型」组件翻译为 {@code LlmComponent} 任务定义的单元测试。
 * <p>验证接口地址/密钥/模型等连接参数来自系统配置，而非任务节点配置。
 */
class ETLLlmTranslatorTest {

    private ETLTaskService etlTaskService;

    private ETLNodeMapper eTLNodeMapper;

    private ETLEdgeMapper etlEdgeMapper;

    private LlmConfigService llmConfigService;

    private LlmConfig llmConfig;

    @BeforeEach
    void setUp() {
        eTLNodeMapper = mock(ETLNodeMapper.class);
        etlEdgeMapper = mock(ETLEdgeMapper.class);
        llmConfig = new LlmConfig();
        llmConfig.setBaseUrl("https://api.deepseek.com");
        llmConfig.setApiKey("sk-system-key");
        llmConfig.setModel("deepseek-chat");
        llmConfig.setTemperature(0.3);
        llmConfig.setTimeoutSeconds(60);
        llmConfigService = mock(LlmConfigService.class);
        when(llmConfigService.getConfig()).thenReturn(llmConfig);

        ETLNodeTranslatorRegistry registry = new ETLNodeTranslatorRegistry(List.of(new LlmNodeTranslator(llmConfigService)));
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

    private ETLNode llmNode(String config) {
        ETLNode node = new ETLNode();
        node.setCode("LLM_01");
        node.setLabel("大模型");
        node.setType("LlmComponent");
        node.setConfig(config);
        return node;
    }

    @Test
    void shouldInjectSystemAiConfig() {
        when(eTLNodeMapper.toEntity(anyList())).thenReturn(List.of(
            llmNode("{\"systemPrompt\":\"你是助手\",\"userPrompt\":\"分析：${content}\"}")
        ));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(List.of());

        var unit = JSONUtil.parseObj(etlTaskService.generateETLJobJson(taskDto())).getJSONArray("units").getJSONObject(0);

        assertThat(unit.getStr(".id")).isEqualTo("LLM_01");
        assertThat(unit.getStr(".name")).isEqualTo("LlmComponent");
        assertThat(unit.getStr("baseUrl")).isEqualTo("https://api.deepseek.com");
        assertThat(unit.getStr("apiKey")).isEqualTo("sk-system-key");
        assertThat(unit.getStr("model")).isEqualTo("deepseek-chat");
        assertThat(unit.getStr("temperature")).isEqualTo("0.3");
        assertThat(unit.getStr("timeoutSeconds")).isEqualTo("60");
        assertThat(unit.getStr("systemPrompt")).isEqualTo("你是助手");
        assertThat(unit.getStr("userPrompt")).isEqualTo("分析：${content}");
    }

    @Test
    void shouldFailWhenUserPromptMissing() {
        when(eTLNodeMapper.toEntity(anyList())).thenReturn(List.of(llmNode("{}")));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> etlTaskService.generateETLJobJson(taskDto()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未配置用户输入");
    }

    @Test
    void shouldFailWhenSystemAiNotConfigured() {
        llmConfig.setApiKey("");
        when(eTLNodeMapper.toEntity(anyList())).thenReturn(List.of(llmNode("{\"userPrompt\":\"hi\"}")));
        when(etlEdgeMapper.toEntity(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> etlTaskService.generateETLJobJson(taskDto()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("未配置大模型服务");
    }
}
