import {
  defineComponent,
  inject,
  ref,
  nextTick,
  onMounted,
  computed,
} from "vue";
import { useAlertService } from "@/shared/alert/alert.service";
import type {
  AiStatus,
  AiToolTrace,
  AiAssistantInfo,
} from "@/entities/data-source/ai-assistant.service";
import AiAssistantService from "@/entities/data-source/ai-assistant.service";
import type { MetricRagStatus } from "./metric-rag.service";
import MetricRagService from "./metric-rag.service";
import { renderMarkdown } from "./markdown";

const ASSISTANT_ID = "metric-query";

interface AiChatMessage {
  role: "user" | "assistant";
  content: string;
  loading?: boolean;
  statusText?: string;
  explanation?: string;
  toolCalls?: AiToolTrace[];
  meta?: { rounds?: number; toolCalls?: number; tokens?: number };
}

/**
 * 智能问数页面：把自然语言问题拆解为指标/维度/业务限定/时间范围，
 * 调用指标查询接口取数，并以 Markdown（含表格）呈现分析结果。
 */
export default defineComponent({
  name: "MetricAi",
  setup() {
    const alertService = inject("alertService", () => useAlertService(), true);
    const aiService = inject(
      "aiAssistantService",
      () => new AiAssistantService(),
    );
    const ragService = inject("metricRagService", () => new MetricRagService());

    const input = ref("");
    const generating = ref(false);
    const messages = ref<AiChatMessage[]>([]);
    const messageListRef = ref<HTMLElement | null>(null);
    const status = ref<AiStatus>({ available: false });
    const assistants = ref<AiAssistantInfo[]>([]);
    const ragStatus = ref<MetricRagStatus | null>(null);
    const toolsExpanded = ref<Record<number, boolean>>({});
    const rebuilding = ref(false);

    const currentAssistant = computed(() =>
      assistants.value.find((item) => item.id === ASSISTANT_ID),
    );
    const samples = computed(() => currentAssistant.value?.samplePrompts || []);
    const emptyHint = computed(
      () =>
        currentAssistant.value?.description ||
        "用一句话描述你想看什么指标，我来拆解并查询数据",
    );

    const ragStatusText = computed(() => {
      const rag = ragStatus.value;
      if (!rag || !rag.enabled) return "知识索引未启用";
      if (!rag.indexed) return "知识索引未构建";
      return `${rag.metricCount ?? 0} 指标 / ${rag.dimensionCount ?? 0} 维度字段 / ${rag.memberCount ?? 0} 成员 · ${rag.provider}`;
    });

    const loadStatus = async () => {
      try {
        status.value = await aiService().getStatus();
        if (status.value.available) {
          assistants.value = await aiService().listAssistants();
        }
      } catch {
        status.value = { available: false, message: "无法获取 AI 助手状态" };
      }
    };

    const loadRagStatus = async () => {
      try {
        ragStatus.value = await ragService().getStatus();
      } catch {
        ragStatus.value = null;
      }
    };

    const rebuildRag = async () => {
      if (rebuilding.value) return;
      rebuilding.value = true;
      try {
        const result = await ragService().rebuild();
        alertService.showSuccess(
          `知识索引已重建：${result.metricCount ?? 0} 指标 / ${result.dimensionCount ?? 0} 维度字段 / ${result.memberCount ?? 0} 成员`,
        );
        await loadRagStatus();
      } catch (err: any) {
        alertService.showError(
          err.response?.data?.message || err.message || "重建知识索引失败",
        );
      } finally {
        rebuilding.value = false;
      }
    };

    const scrollToBottom = async () => {
      await nextTick();
      const el = messageListRef.value;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    };

    const buildHistory = (): {
      role: "user" | "assistant";
      content: string;
    }[] => {
      return messages.value
        .filter((m) => !m.loading && m.content)
        .slice(-8)
        .map((m) => ({
          role: m.role,
          content:
            m.role === "assistant" ? m.explanation || m.content : m.content,
        }));
    };

    const send = async () => {
      const text = input.value.trim();
      if (!text || generating.value) return;

      const history = buildHistory();
      messages.value.push({ role: "user", content: text });
      input.value = "";
      generating.value = true;

      const placeholder: AiChatMessage = {
        role: "assistant",
        content: "",
        loading: true,
        statusText: "正在解析指标与维度…",
      };
      messages.value.push(placeholder);
      await scrollToBottom();

      const slowTimer = window.setTimeout(() => {
        placeholder.statusText = "正在查询指标数据…";
      }, 2500);

      try {
        const result = await aiService().generate(
          text,
          undefined,
          history,
          ASSISTANT_ID,
        );
        placeholder.loading = false;
        placeholder.content = result.explanation || "";
        placeholder.explanation = cleanExplanation(result.explanation);
        placeholder.toolCalls = result.toolCalls || [];
        placeholder.meta = {
          rounds: result.rounds,
          toolCalls: (result.toolCalls || []).length,
          tokens: (result.promptTokens || 0) + (result.completionTokens || 0),
        };
        if (!result.available) {
          placeholder.explanation = result.explanation || "AI 助手不可用";
        }
      } catch (err: any) {
        placeholder.loading = false;
        placeholder.content = "";
        placeholder.explanation =
          err.response?.data?.message || err.message || "生成失败，请稍后重试";
      } finally {
        window.clearTimeout(slowTimer);
        generating.value = false;
        await scrollToBottom();
      }
    };

    // 去掉分析文字中的 SQL 代码块：智能问数以自然语言 + Markdown 表格呈现，不展示底层 SQL。
    const cleanExplanation = (text?: string): string => {
      if (!text) return "";
      return text
        .replace(/```[\s\S]*?```/g, (block) =>
          /```\s*(sql|select|with)\b/i.test(block.trim()) ? "" : block,
        )
        .trim();
    };

    const formatArgs = (args?: Record<string, any>): string => {
      if (!args || Object.keys(args).length === 0) return "";
      try {
        return JSON.stringify(args);
      } catch {
        return "";
      }
    };

    // 工具调用轨迹默认折叠，点击可展开
    const toggleTools = (index: number) => {
      toolsExpanded.value[index] = !toolsExpanded.value[index];
    };

    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        send();
      }
    };

    const onSample = (sample: string) => {
      input.value = sample;
    };

    const newSession = () => {
      if (generating.value) return;
      if (messages.value.length === 0 && !input.value) return;
      messages.value = [];
      input.value = "";
      toolsExpanded.value = {};
      alertService.showSuccess("已开启新会话");
    };

    onMounted(() => {
      loadStatus();
      loadRagStatus();
    });

    return {
      ASSISTANT_ID,
      input,
      generating,
      messages,
      messageListRef,
      status,
      samples,
      emptyHint,
      ragStatus,
      ragStatusText,
      rebuilding,
      loadRagStatus,
      rebuildRag,
      send,
      onKeydown,
      onSample,
      newSession,
      formatArgs,
      renderMarkdown,
      toolsExpanded,
      toggleTools,
    };
  },
});
