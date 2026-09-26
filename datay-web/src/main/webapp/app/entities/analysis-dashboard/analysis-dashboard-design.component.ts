import {
  type Ref,
  defineComponent,
  inject,
  nextTick,
  onMounted,
  ref,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";

import { useAlertService } from "@/shared/alert/alert.service";
import AiAssistantService from "@/entities/data-source/ai-assistant.service";
import type { AiToolTrace } from "@/entities/data-source/ai-assistant.service";
import DashboardService from "@/shared/dashboard/dashboard.service";
import type { AnalysisDashboard } from "@/shared/dashboard/dashboard.service";
import DashboardCanvas from "@/shared/dashboard/DashboardCanvas.vue";
import DashboardFilters from "@/shared/dashboard/DashboardFilters.vue";
import { initFilterValues } from "@/shared/dashboard/filter-utils";
import type { DashboardSpec, FilterValues } from "@/shared/dashboard/types";
import { useDashboardData } from "@/shared/dashboard/useDashboardData";

const ASSISTANT_ID = "dashboard-design";

/**
 * 提取后端错误信息：JHipster ProblemDetail 的可读信息在 title/detail，message 可能是 i18n key。
 */
const errorMessage = (err: any, fallback: string): string => {
  const data = err?.response?.data;
  return (
    data?.title || data?.detail || data?.message || err?.message || fallback
  );
};

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
  loading?: boolean;
  statusText?: string;
  explanation?: string;
  toolCalls?: AiToolTrace[];
  hasSpec?: boolean;
}

/**
 * AI 看板设计页：左侧对话生成看板定义，右侧实时预览（含筛选器），确认后保存。
 */
export default defineComponent({
  name: "AnalysisDashboardDesign",
  components: { DashboardCanvas, DashboardFilters },
  setup() {
    const route = useRoute();
    const router = useRouter();
    const alertService = inject("alertService", () => useAlertService(), true);
    const aiService = new AiAssistantService();
    const dashboardService = new DashboardService();

    const input = ref("");
    const generating = ref(false);
    const messages: Ref<ChatMessage[]> = ref([]);
    const messageListRef = ref<HTMLElement | null>(null);
    const toolsExpanded = ref<Record<number, boolean>>({});

    const spec = ref<DashboardSpec | undefined>();
    const specError = ref("");
    const filterValues = ref<FilterValues>({});
    const editingId: Ref<number | undefined> = ref(undefined);
    const samples = ref<string[]>([]);

    const saveModal = ref<any>(null);
    const saving = ref(false);
    const form = ref({ name: "", description: "" });

    // 看板定义 JSON 查看/编辑
    const specJsonVisible = ref(false);
    const specJsonText = ref("");
    const specJsonError = ref("");

    const { datasetData, loadingDatasets, datasetErrors, loading, loadAll } =
      useDashboardData({ spec });

    const loadAssistant = async () => {
      try {
        const assistants = await aiService.listAssistants();
        const assistant = assistants.find((item) => item.id === ASSISTANT_ID);
        samples.value = assistant?.samplePrompts ?? [];
      } catch {
        samples.value = [];
      }
    };

    const loadExisting = async (id: number) => {
      try {
        const dashboard = await dashboardService.get(id);
        editingId.value = id;
        form.value.name = dashboard.name || "";
        form.value.description = dashboard.description || "";
        await applySpec(DashboardService.parseSpec(dashboard), false);
      } catch (err: any) {
        alertService.showError(errorMessage(err, "看板加载失败"));
      }
    };

    onMounted(async () => {
      await loadAssistant();
      const id = route.query.id ? Number(route.query.id) : undefined;
      if (id) {
        await loadExisting(id);
      }
    });

    const scrollToBottom = async () => {
      await nextTick();
      const el = messageListRef.value;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    };

    const buildHistory = () =>
      messages.value
        .filter(
          (message) =>
            !message.loading && (message.content || message.explanation),
        )
        .slice(-8)
        .map((message) => ({
          role: message.role,
          content:
            message.role === "assistant"
              ? (message.explanation as string)
              : message.content,
        }));

    const cleanExplanation = (text?: string): string =>
      text ? text.replace(/```[\s\S]*?```/g, "").trim() : "";

    const applySpec = async (rawSpec: DashboardSpec, notify = true) => {
      try {
        const normalized = await dashboardService.validateSpec(rawSpec);
        spec.value = normalized;
        specError.value = "";
        filterValues.value = initFilterValues(normalized.filters);
        if (!form.value.name) {
          form.value.name = normalized.title || "";
        }
        await loadAll(filterValues.value);
        if (notify) {
          ElMessage.success("看板已生成，可在右侧预览并保存");
        }
      } catch (err: any) {
        specError.value = errorMessage(err, "看板定义校验失败");
        spec.value = rawSpec;
        alertService.showError(specError.value);
      }
    };

    const send = async () => {
      const text = input.value.trim();
      if (!text || generating.value) {
        return;
      }
      const history = buildHistory();
      messages.value.push({ role: "user", content: text });
      input.value = "";
      generating.value = true;

      const placeholder: ChatMessage = {
        role: "assistant",
        content: "",
        loading: true,
        statusText: "正在探查指标与数据表…",
      };
      messages.value.push(placeholder);
      await scrollToBottom();

      const slowTimer = window.setTimeout(() => {
        placeholder.statusText = "正在设计看板布局与图表…";
      }, 3000);

      try {
        // 多轮：把当前看板定义作为上下文，让助手在上一次结果基础上调整
        const contextData = spec.value
          ? { currentSpec: spec.value }
          : undefined;
        const result = await aiService.generate(
          text,
          undefined,
          history,
          ASSISTANT_ID,
          contextData,
        );
        placeholder.loading = false;
        placeholder.explanation = cleanExplanation(result.explanation);
        placeholder.toolCalls = result.toolCalls || [];
        if (result.dashboardSpec) {
          placeholder.hasSpec = true;
          await applySpec(result.dashboardSpec as unknown as DashboardSpec);
        } else {
          placeholder.explanation = `${placeholder.explanation}\n\n未能解析到看板定义，请调整描述后重试。`;
          alertService.showError("未能解析到看板定义，请调整描述后重试");
        }
      } catch (err: any) {
        placeholder.loading = false;
        placeholder.explanation = errorMessage(err, "生成失败，请稍后重试");
      } finally {
        window.clearTimeout(slowTimer);
        generating.value = false;
        await scrollToBottom();
      }
    };

    const onKeydown = (event: KeyboardEvent) => {
      if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
        event.preventDefault();
        send();
      }
    };

    const onSample = (sample: string) => {
      input.value = sample;
    };

    const goBack = () => {
      router.push({ name: "AnalysisDashboard" });
    };

    const formatArgs = (args?: Record<string, any>): string => {
      if (!args || Object.keys(args).length === 0) {
        return "";
      }
      try {
        return JSON.stringify(args);
      } catch {
        return "";
      }
    };

    const toggleTools = (index: number) => {
      toolsExpanded.value[index] = !toolsExpanded.value[index];
    };

    const onFiltersChange = (values: FilterValues) => {
      filterValues.value = values;
      loadAll(values);
    };

    const openSpecJson = () => {
      if (!spec.value) {
        alertService.showError("请先让 AI 生成看板定义");
        return;
      }
      specJsonText.value = JSON.stringify(spec.value, null, 2);
      specJsonError.value = "";
      specJsonVisible.value = true;
    };

    const refreshFromSpecJson = async () => {
      try {
        const parsed = JSON.parse(specJsonText.value);
        await applySpec(parsed as DashboardSpec, false);
        specJsonText.value = JSON.stringify(spec.value, null, 2);
        specJsonError.value = "";
        ElMessage.success("已刷新预览");
      } catch (err: any) {
        specJsonError.value = err?.message || "JSON 解析失败";
        alertService.showError(specJsonError.value);
      }
    };

    const openSaveDialog = () => {
      if (!spec.value) {
        alertService.showError("请先让 AI 生成看板定义");
        return;
      }
      if (specError.value) {
        alertService.showError(`看板定义无效：${specError.value}`);
        return;
      }
      saveModal.value.show();
    };

    const save = async () => {
      if (!spec.value) {
        return;
      }
      if (!form.value.name || !form.value.name.trim()) {
        alertService.showError("请填写看板名称");
        return;
      }
      saving.value = true;
      try {
        const payload: AnalysisDashboard = {
          name: form.value.name.trim(),
          description: form.value.description,
          spec: JSON.stringify(spec.value),
          status: "ENABLED",
        };
        let saved: AnalysisDashboard;
        if (editingId.value) {
          saved = await dashboardService.update(editingId.value, {
            ...payload,
            id: editingId.value,
          });
        } else {
          saved = await dashboardService.create(payload);
        }
        saveModal.value.hide();
        ElMessage.success("看板已保存，可在列表点击「新页签打开」查看");
        await router.push({ name: "AnalysisDashboard" });
        if (saved?.id) {
          try {
            window.open(`/dashboard.html?id=${saved.id}`, "_blank");
          } catch {
            // 浏览器可能拦截自动弹窗，列表页可手动打开
          }
        }
      } catch (err: any) {
        alertService.showError(errorMessage(err, "保存失败"));
      } finally {
        saving.value = false;
      }
    };

    return {
      input,
      generating,
      messages,
      messageListRef,
      toolsExpanded,
      spec,
      specError,
      filterValues,
      datasetData,
      loadingDatasets,
      datasetErrors,
      loading,
      samples,
      saveModal,
      saving,
      form,
      specJsonVisible,
      specJsonText,
      specJsonError,
      openSpecJson,
      refreshFromSpecJson,
      send,
      onKeydown,
      onSample,
      goBack,
      formatArgs,
      toggleTools,
      onFiltersChange,
      openSaveDialog,
      save,
    };
  },
});
