import {
  type Ref,
  defineComponent,
  inject,
  onMounted,
  onUnmounted,
  ref,
  watch,
} from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";

import { useAlertService } from "@/shared/alert/alert.service";
import DashboardService from "@/shared/dashboard/dashboard.service";
import type { AnalysisDashboard } from "@/shared/dashboard/dashboard.service";
import { useDateFormat } from "@/shared/composables";

/**
 * 分析看板列表：支持搜索、AI 创建、新页签打开、重命名与删除。
 */
export default defineComponent({
  compatConfig: { MODE: 3 },
  name: "AnalysisDashboard",
  setup() {
    const router = useRouter();
    const dateFormat = useDateFormat();
    const alertService = inject("alertService", () => useAlertService(), true);
    const service = new DashboardService();

    const dashboards: Ref<AnalysisDashboard[]> = ref([]);
    const isFetching = ref(false);
    const search = ref("");
    const removeEntity = ref<any>(null);
    const removeId: Ref<number | null> = ref(null);

    const retrieveDashboards = async () => {
      isFetching.value = true;
      try {
        dashboards.value = await service.list({
          page: 0,
          size: 200,
          search: search.value,
        });
      } catch (err: any) {
        alertService.showHttpError(err.response);
      } finally {
        isFetching.value = false;
      }
    };

    let searchTimer: ReturnType<typeof setTimeout> | null = null;
    watch(search, () => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
      searchTimer = setTimeout(retrieveDashboards, 300);
    });

    onUnmounted(() => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
    });

    onMounted(retrieveDashboards);

    const openInNewTab = (row: AnalysisDashboard) => {
      window.open(`/dashboard.html?id=${row.id}`, "_blank");
    };

    const design = (row?: AnalysisDashboard) => {
      router.push({
        name: "AnalysisDashboardDesign",
        query: row?.id ? { id: String(row.id) } : {},
      });
    };

    const rename = async (row: AnalysisDashboard) => {
      try {
        const { value } = await ElMessageBox.prompt(
          "请输入新的看板名称",
          "重命名看板",
          {
            inputValue: row.name,
            inputValidator: (input: string) =>
              !!input && input.trim().length > 0,
          },
        );
        const detail = await service.get(row.id as number);
        await service.update(row.id as number, {
          ...detail,
          name: value.trim(),
        });
        ElMessage.success("已重命名");
        await retrieveDashboards();
      } catch (err: any) {
        if (err !== "cancel") {
          alertService.showHttpError(err?.response);
        }
      }
    };

    const prepareRemove = (row: AnalysisDashboard) => {
      removeId.value = row.id ?? null;
      removeEntity.value.show();
    };

    const closeDialog = () => removeEntity.value.hide();

    const removeDashboard = async () => {
      try {
        await service.remove(removeId.value as number);
        ElMessage.success("看板已删除");
        closeDialog();
        await retrieveDashboards();
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    const statusLabel = (status?: string) =>
      status === "DISABLED" ? "停用" : "启用";

    return {
      dashboards,
      isFetching,
      search,
      removeId,
      removeEntity,
      retrieveDashboards,
      prepareRemove,
      closeDialog,
      removeDashboard,
      openInNewTab,
      design,
      rename,
      statusLabel,
      ...dateFormat,
    };
  },
});
