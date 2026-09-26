<template>
  <div class="dashboard-viewer">
    <div class="dashboard-viewer-topbar">
      <div class="dashboard-viewer-brand">
        <span class="dashboard-viewer-title">{{ brandTitle }}</span>
      </div>
      <el-button text type="primary" @click="reload">刷新</el-button>
    </div>

    <el-result
      v-if="error"
      icon="error"
      title="看板加载失败"
      :sub-title="error"
      class="dashboard-viewer-error"
    />

    <template v-else-if="spec">
      <DashboardFilters
        :filters="spec.filters || []"
        :model-value="filterValues"
        :dashboard-id="dashboardId"
        @update:model-value="onFiltersChange"
      />
      <DashboardCanvas
        :spec="spec"
        :dataset-data="datasetData"
        :loading-datasets="loadingDatasets"
        :dataset-errors="datasetErrors"
        :show-header="false"
      />
    </template>

    <div v-else class="dashboard-viewer-loading" v-loading="true"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";

import DashboardCanvas from "@/shared/dashboard/DashboardCanvas.vue";
import DashboardFilters from "@/shared/dashboard/DashboardFilters.vue";
import DashboardService from "@/shared/dashboard/dashboard.service";
import {
  filtersFromQuery,
  initFilterValues,
} from "@/shared/dashboard/filter-utils";
import type { DashboardSpec, FilterValues } from "@/shared/dashboard/types";
import { useDashboardData } from "@/shared/dashboard/useDashboardData";

const service = new DashboardService();
const dashboardId = ref<number | undefined>();
const dashboardName = ref("");
const spec = ref<DashboardSpec | undefined>();
const filterValues = ref<FilterValues>({});
const error = ref("");

const brandTitle = computed(
  () => spec.value?.title || dashboardName.value || "分析看板",
);

const { datasetData, loadingDatasets, datasetErrors, loadAll } =
  useDashboardData({ dashboardId, spec });

let debounceTimer: number | undefined;
const onFiltersChange = (values: FilterValues) => {
  filterValues.value = values;
  window.clearTimeout(debounceTimer);
  debounceTimer = window.setTimeout(() => loadAll(values), 300);
};

const reload = () => {
  if (spec.value) {
    loadAll(filterValues.value);
  }
};

const resolveDashboard = async () => {
  const params = new URLSearchParams(window.location.search);
  const idParam = params.get("id");
  const code = params.get("code");
  if (idParam) {
    dashboardId.value = Number(idParam);
    return service.get(dashboardId.value);
  }
  if (code) {
    const byCode = await service.getByCode(code);
    dashboardId.value = byCode.id;
    return byCode;
  }
  throw new Error("缺少看板参数：请在地址中提供 ?id= 或 ?code=");
};

onMounted(async () => {
  try {
    const dashboard = await resolveDashboard();
    dashboardName.value = dashboard.name || "";
    document.title = dashboard.name || "分析看板";
    const parsed = DashboardService.parseSpec(dashboard);
    spec.value = parsed;
    filterValues.value = {
      ...initFilterValues(parsed.filters),
      ...filtersFromQuery(parsed.filters, window.location.search),
    };
    await loadAll(filterValues.value);
  } catch (err: any) {
    error.value =
      err?.response?.data?.message || err?.message || "看板加载失败";
  }
});
</script>

<style scoped>
.dashboard-viewer {
  min-height: 100vh;
  padding-bottom: 24px;
}

.dashboard-viewer-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  box-shadow: 0 1px 4px rgb(0 0 0 / 6%);
}

.dashboard-viewer-brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.dashboard-viewer-title {
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.dashboard-viewer-error {
  margin-top: 80px;
}

.dashboard-viewer-loading {
  height: 300px;
}
</style>
