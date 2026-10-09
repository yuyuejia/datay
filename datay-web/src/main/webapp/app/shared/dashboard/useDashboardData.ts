import { ref } from "vue";
import type { Ref } from "vue";

import DashboardService from "./dashboard.service";
import type { DashboardSpec, DatasetResult, FilterValues } from "./types";

/**
 * 看板取数组合式函数。
 *
 * - 已保存看板（提供 dashboardId）走 `/datasets/{id}/query`；
 * - 未保存预览（仅提供 spec）走 `/preview-dataset`。
 */
export function useDashboardData(config: {
  dashboardId?: Ref<string | undefined>;
  spec: Ref<DashboardSpec | undefined>;
}) {
  const service = new DashboardService();
  const datasetData = ref<Record<string, DatasetResult>>({});
  const loadingDatasets = ref<Record<string, boolean>>({});
  const datasetErrors = ref<Record<string, string>>({});
  const loading = ref(false);

  const loadAll = async (filterValues: FilterValues) => {
    const spec = config.spec.value;
    if (!spec) {
      return;
    }
    const datasetIds = Array.from(
      new Set((spec.datasets ?? []).map((dataset) => dataset.id)),
    );
    loading.value = true;
    await Promise.all(
      datasetIds.map(async (datasetId) => {
        loadingDatasets.value[datasetId] = true;
        datasetErrors.value[datasetId] = "";
        try {
          const result = await queryOne(datasetId, filterValues);
          datasetData.value[datasetId] = result;
        } catch (error: any) {
          datasetErrors.value[datasetId] =
            error?.response?.data?.title ||
            error?.response?.data?.detail ||
            error?.response?.data?.message ||
            error?.message ||
            "查询失败";
        } finally {
          loadingDatasets.value[datasetId] = false;
        }
      }),
    );
    loading.value = false;
  };

  const queryOne = (
    datasetId: string,
    filterValues: FilterValues,
  ): Promise<DatasetResult> => {
    const id = config.dashboardId?.value;
    if (id) {
      return service.queryDataset(id, datasetId, filterValues);
    }
    if (!config.spec.value) {
      return Promise.reject(new Error("看板尚未就绪"));
    }
    return service.previewDataset(config.spec.value, datasetId, filterValues);
  };

  return { datasetData, loadingDatasets, datasetErrors, loading, loadAll };
}
