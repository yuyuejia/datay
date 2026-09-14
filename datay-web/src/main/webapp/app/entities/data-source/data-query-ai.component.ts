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
  AiSqlStatus,
  AiToolInfo,
  AiToolTrace,
  AiSqlMode,
} from "./data-query-ai.service";
import DataQueryAiService from "./data-query-ai.service";

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

const QUERY_SAMPLES = [
  "查询最近 7 天每天的订单总金额",
  "统计每个用户的下单次数，按次数倒序取前 20",
  "找出从未下过单的用户",
  "按月统计销售额环比增长",
];

const TASK_SAMPLES = [
  "创建一张用户表，包含 id、姓名、邮箱、创建时间字段",
  "把 orders 表中金额大于 1000 的订单同步到 vip_orders 表",
  "给 users 表新增一个 last_login_time 字段",
  "清空临时表 tmp_log 并重新初始化统计数据",
];

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: "DataQueryAi",
  props: {
    modelValue: { type: Boolean, default: undefined },
    value: { type: Boolean, default: undefined }, // Vue 2 compat: v-model → value
    dataSourceId: { type: Number, default: undefined },
    mode: { type: String as () => AiSqlMode, default: "query" },
  },
  emits: ["update:modelValue", "input", "apply-sql"],
  setup(props, { emit }) {
    const alertService = inject("alertService", () => useAlertService(), true);
    const aiService = inject(
      "dataQueryAiService",
      () => new DataQueryAiService(),
    );

    const isTaskMode = computed(() => props.mode === "task");
    const assistantTitle = computed(() =>
      isTaskMode.value ? "AI SQL 任务助手" : "AI SQL 助手",
    );
    const emptyHint = computed(() =>
      isTaskMode.value
        ? "用一句话描述你的 SQL 任务（建表、写入、查询等），我来生成脚本"
        : "用一句话描述你想查什么，我来生成 SQL",
    );
    const inputPlaceholder = computed(() =>
      isTaskMode.value
        ? "描述你的 SQL 任务，Enter 发送，Shift+Enter 换行"
        : "描述你的数据需求，Enter 发送，Shift+Enter 换行",
    );
    const samples = computed(() =>
      isTaskMode.value ? TASK_SAMPLES : QUERY_SAMPLES,
    );

    const visible = ref(false);
    const input = ref("");
    const generating = ref(false);
    const messages = ref<AiChatMessage[]>([]);
    const messageListRef = ref<HTMLElement | null>(null);
    const status = ref<AiSqlStatus>({ available: false });
    const tools = ref<AiToolInfo[]>([]);

    const loadStatus = async () => {
      try {
        status.value = await aiService().getStatus();
        if (status.value.available) {
          tools.value = await aiService().listTools(props.mode);
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
          props.mode,
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
      tools,
      samples,
      isTaskMode,
      assistantTitle,
      emptyHint,
      inputPlaceholder,
      send,
      onKeydown,
      close,
      applySample,
      copySql,
      applySql,
      formatArgs,
      renderMarkdown,
    };
  },
});
