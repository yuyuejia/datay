import {
  defineComponent,
  inject,
  ref,
  nextTick,
  onMounted,
  onBeforeUnmount,
  watch,
  computed,
} from "vue";
import { useAlertService } from "@/shared/alert/alert.service";
import type {
  AiStatus,
  AiToolTrace,
  AiAssistantInfo,
} from "./ai-assistant.service";
import AiAssistantService from "./ai-assistant.service";

interface AiChatMessage {
  role: "user" | "assistant";
  content: string;
  loading?: boolean;
  statusText?: string;
  sql?: string;
  explanation?: string;
  toolCalls?: AiToolTrace[];
  meta?: { rounds?: number; toolCalls?: number; tokens?: number };
}

export default defineComponent({
  name: "DataQueryAi",
  props: {
    modelValue: { type: Boolean, default: undefined },
    value: { type: Boolean, default: undefined }, // Vue 2 compat: v-model → value
    dataSourceId: { type: [Number, String], default: undefined },
    assistantId: { type: String, default: "query" },
  },
  emits: ["update:modelValue", "input", "apply-sql"],
  setup(props, { emit }) {
    const alertService = inject("alertService", () => useAlertService(), true);
    const aiService = inject(
      "aiAssistantService",
      () => new AiAssistantService(),
    );

    const visible = ref(false);
    const input = ref("");
    const generating = ref(false);
    const messages = ref<AiChatMessage[]>([]);
    const messageListRef = ref<HTMLElement | null>(null);
    const status = ref<AiStatus>({ available: false });
    const assistants = ref<AiAssistantInfo[]>([]);

    const currentAssistant = computed(() =>
      assistants.value.find((item) => item.id === props.assistantId),
    );
    const assistantTitle = computed(
      () => currentAssistant.value?.name || "AI 助手",
    );
    const emptyHint = computed(
      () =>
        currentAssistant.value?.description ||
        "用一句话描述你的需求，我来生成 SQL",
    );
    const inputPlaceholder = computed(
      () => "描述你的需求，Enter 发送，Shift+Enter 换行",
    );
    const samples = computed(() => currentAssistant.value?.samplePrompts || []);
    const tools = computed(() => currentAssistant.value?.tools || []);

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
      // 只回传纯文本轮次，工具调用轨迹不参与多轮上下文，避免上下文膨胀
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
        statusText: "正在思考…",
      };
      messages.value.push(placeholder);
      await scrollToBottom();

      // 提示用户 Agent 正在循环调用工具
      const slowTimer = window.setTimeout(() => {
        placeholder.statusText = "正在查询数据库并校验 SQL…";
      }, 2500);

      try {
        const result = await aiService().generate(
          text,
          props.dataSourceId,
          history,
          props.assistantId,
        );
        placeholder.loading = false;
        placeholder.content = result.explanation || "";
        placeholder.explanation = cleanExplanation(result.explanation);
        placeholder.sql = result.sql || undefined;
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

    /**
     * 移除回复中的 SQL 代码块，解释区只保留文字说明，避免与 SQL 卡片重复。
     */
    const cleanExplanation = (text?: string): string => {
      if (!text) return "";
      return text.replace(/```(?:sql|SQL)?[\s\S]*?```/g, "").trim();
    };

    const renderMarkdown = (text?: string): string => {
      if (!text) return "";
      const escaped = text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
      return escaped
        .replace(/`([^`]+)`/g, "<code>$1</code>")
        .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    };

    const formatArgs = (args?: Record<string, any>): string => {
      if (!args || Object.keys(args).length === 0) return "";
      try {
        return JSON.stringify(args);
      } catch {
        return "";
      }
    };

    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        send();
      }
    };

    /** 关闭抽屉 */
    const close = () => {
      visible.value = false;
      emit("update:modelValue", false);
      emit("input", false); // Vue 2 compat
    };

    /** ESC 键关闭 */
    const onEscKeydown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && visible.value) {
        close();
      }
    };

    const applySample = (sample: string) => {
      input.value = sample;
    };

    /**
     * 新开会话：清空当前对话与输入，后续请求不再携带历史上下文。
     * 生成过程中禁止操作，避免打断进行中的请求。
     */
    const newSession = () => {
      if (generating.value) return;
      if (messages.value.length === 0 && !input.value) return;
      messages.value = [];
      input.value = "";
      alertService.showSuccess("已开启新会话");
    };

    const copySql = async (sql?: string) => {
      if (!sql) return;
      try {
        await navigator.clipboard.writeText(sql);
        alertService.showSuccess("SQL 已复制");
      } catch {
        alertService.showError("复制失败，请手动选择复制");
      }
    };

    const applySql = (sql?: string) => {
      if (!sql) return;
      emit("apply-sql", sql);
      alertService.showSuccess("SQL 已应用到编辑器");
    };

    onMounted(() => {
      loadStatus();
      document.addEventListener("keydown", onEscKeydown);
    });

    onBeforeUnmount(() => {
      document.removeEventListener("keydown", onEscKeydown);
    });

    // 双向同步：父组件改变 modelValue/value 时同步到本地 visible
    watch(
      () => props.modelValue ?? props.value,
      (val) => {
        if (val !== undefined && val !== visible.value) {
          visible.value = val;
        }
      },
    );
    // 本地 visible 变化时通知父组件
    watch(visible, (val) => {
      emit("update:modelValue", val);
      emit("input", val); // Vue 2 compat
    });

    return {
      visible,
      input,
      generating,
      messages,
      messageListRef,
      status,
      assistants,
      currentAssistant,
      samples,
      tools,
      assistantTitle,
      emptyHint,
      inputPlaceholder,
      send,
      onKeydown,
      newSession,
      close,
      applySample,
      copySql,
      applySql,
      formatArgs,
      renderMarkdown,
    };
  },
});
