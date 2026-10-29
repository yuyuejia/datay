<template>
  <div class="dashboard-viewer" :class="viewerClasses" :style="backgroundStyle">
    <div v-if="!isScreenMode && !isEmbedded" class="dashboard-viewer-topbar">
      <div class="dashboard-viewer-brand">
        <span class="dashboard-viewer-title">{{ brandTitle }}</span>
        <el-tag v-if="isPreview" size="small" type="warning">设计预览</el-tag>
      </div>
      <el-button text type="primary" @click="reload">刷新</el-button>
    </div>

    <el-result v-if="error" icon="error" title="看板加载失败" :sub-title="error" class="dashboard-viewer-error" />

    <template v-else-if="spec">
      <template v-if="isScreenMode">
        <template v-if="(spec.filters || []).length">
          <button
            type="button"
            class="dashboard-viewer-filters-toggle"
            :class="{ 'is-open': screenFiltersOpen }"
            :title="screenFiltersOpen ? '收起筛选器' : '展开筛选器'"
            :aria-label="screenFiltersOpen ? '收起筛选器' : '展开筛选器'"
            :aria-expanded="screenFiltersOpen"
            @click="screenFiltersOpen = !screenFiltersOpen"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M3 5h18l-7 8v6l-4 2v-8L3 5z"></path>
            </svg>
          </button>
          <div v-if="screenFiltersOpen" class="dashboard-viewer-filters-float">
            <DashboardFilters
              :filters="spec.filters || []"
              :model-value="filterValues"
              :dashboard-id="dashboardId"
              :spec="spec"
              @update:model-value="onFiltersChange"
            />
          </div>
        </template>
        <DashboardStage
          class="dashboard-viewer-stage"
          :width="screenWidth"
          :height="screenHeight"
          zoom="fit"
          :fit-mode="fitMode"
          :padding="0"
          :background-style="{ backgroundColor: 'transparent' }"
        >
          <DashboardCanvas
            :spec="spec"
            :dataset-data="datasetData"
            :widget-data="widgetData"
            :loading-datasets="loadingDatasets"
            :dataset-errors="datasetErrors"
            :widget-errors="widgetErrors"
            :server-pagination-widgets="serverPaginationWidgets"
            :load-widget-page="loadWidgetPage"
            :data-version="dataVersion"
            :show-header="false"
          />
        </DashboardStage>
      </template>

      <template v-else>
        <DashboardFilters
          :filters="spec.filters || []"
          :model-value="filterValues"
          :dashboard-id="dashboardId"
          :spec="spec"
          @update:model-value="onFiltersChange"
        />
        <DashboardCanvas
          :spec="spec"
          :dataset-data="datasetData"
          :widget-data="widgetData"
          :loading-datasets="loadingDatasets"
          :dataset-errors="datasetErrors"
          :widget-errors="widgetErrors"
          :server-pagination-widgets="serverPaginationWidgets"
          :load-widget-page="loadWidgetPage"
          :data-version="dataVersion"
          :show-header="false"
        />
      </template>
    </template>

    <div v-else class="dashboard-viewer-loading" v-loading="true"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watchEffect } from 'vue';

import DashboardCanvas from '@/shared/dashboard/DashboardCanvas.vue';
import DashboardFilters from '@/shared/dashboard/DashboardFilters.vue';
import DashboardStage from '@/shared/dashboard/DashboardStage.vue';
import DashboardService from '@/shared/dashboard/dashboard.service';
import { filtersFromQuery, initFilterValues } from '@/shared/dashboard/filter-utils';
import {
  normalizeDashboardSpec,
  resolveScreenBackgroundStyle,
  resolveScreenFitMode,
  resolveScreenTheme,
} from '@/shared/dashboard/layout-defaults';
import { cleanupDashboardPreviewDrafts, readDashboardPreviewDraft } from '@/shared/dashboard/preview-draft';
import { isEmbedMode } from '@/shared/config/embed';
import type { DashboardSpec, FilterValues } from '@/shared/dashboard/types';
import { useDashboardAutoRefresh } from '@/shared/dashboard/useDashboardAutoRefresh';
import { useDashboardBackgroundImage } from '@/shared/dashboard/useDashboardBackgroundImage';
import { useDashboardData } from '@/shared/dashboard/useDashboardData';

const service = new DashboardService();
const dashboardId = ref<string | undefined>();
const dashboardName = ref('');
const spec = ref<DashboardSpec | undefined>();
const filterValues = ref<FilterValues>({});
const error = ref('');
const isPreview = ref(false);
const screenFiltersOpen = ref(false);

const isScreenMode = computed(() => spec.value?.layout?.mode === 'screen');
const isEmbedded = isEmbedMode();
watchEffect(() => {
  if (!isScreenMode.value || !(spec.value?.filters?.length ?? 0)) {
    screenFiltersOpen.value = false;
  }
});
const screenWidth = computed(() => spec.value?.layout?.screen?.width ?? 1920);
const screenHeight = computed(() => spec.value?.layout?.screen?.height ?? 1080);
const screenTheme = computed(() => resolveScreenTheme(spec.value?.layout));
const isDarkTheme = computed(() => screenTheme.value === 'dark');
const fitMode = computed(() => resolveScreenFitMode(spec.value?.layout));
const backgroundImagePath = computed(() => {
  const background = spec.value?.layout?.screen?.background;
  return background?.type === 'image' ? (background.imagePath ?? '') : '';
});
const { imageUrl: backgroundImageUrl } = useDashboardBackgroundImage(backgroundImagePath);
const backgroundStyle = computed(() => resolveScreenBackgroundStyle(spec.value?.layout, backgroundImageUrl.value));
const refreshSeconds = computed(() => spec.value?.layout?.screen?.autoRefreshSeconds ?? 0);

const viewerClasses = computed(() => ({
  'is-screen': isScreenMode.value,
  'is-embed': isEmbedded,
  'dashboard-theme-dark': isDarkTheme.value,
  'dashboard-theme-light': !isDarkTheme.value,
}));

const brandTitle = computed(() => spec.value?.title || dashboardName.value || '分析看板');

const {
  datasetData,
  widgetData,
  loadingDatasets,
  datasetErrors,
  widgetErrors,
  serverPaginationWidgets,
  dataVersion,
  loadAll,
  loadWidgetPage,
} = useDashboardData({
  dashboardId,
  spec,
});

const refresh = () => loadAll(filterValues.value, { force: true });
const { schedule: scheduleAutoRefresh } = useDashboardAutoRefresh(refreshSeconds, refresh);

watchEffect(() => {
  if (typeof document !== 'undefined') {
    document.documentElement.classList.toggle('dark', isDarkTheme.value);
  }
});

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') {
    document.documentElement.classList.remove('dark');
  }
});

let debounceTimer: number | undefined;
const onFiltersChange = (values: FilterValues) => {
  filterValues.value = values;
  window.clearTimeout(debounceTimer);
  debounceTimer = window.setTimeout(async () => {
    await loadAll(values);
    scheduleAutoRefresh();
  }, 300);
};

const reload = async () => {
  if (spec.value) {
    await loadAll(filterValues.value, { force: true });
    scheduleAutoRefresh();
  }
};

const applyPreviewDraft = async (draftId: string) => {
  const draft = readDashboardPreviewDraft(draftId);
  if (!draft) {
    throw new Error('预览数据已失效，请回到看板设计器重新打开预览');
  }
  isPreview.value = true;
  dashboardId.value = undefined;
  dashboardName.value = draft.name || draft.spec.title || '看板预览';
  spec.value = normalizeDashboardSpec(draft.spec);
  document.title = `${dashboardName.value} - 设计预览`;
  filterValues.value = {
    ...initFilterValues(draft.spec.filters),
    ...filtersFromQuery(draft.spec.filters, window.location.search),
  };
  await loadAll(filterValues.value, { force: true });
};

const resolveDashboard = async () => {
  const params = new URLSearchParams(window.location.search);
  const idParam = params.get('id');
  const code = params.get('code');
  if (idParam) {
    dashboardId.value = idParam;
    return service.get(dashboardId.value);
  }
  if (code) {
    const byCode = await service.getByCode(code);
    dashboardId.value = byCode.id;
    return byCode;
  }
  throw new Error('缺少看板参数：请在地址中提供 ?id= 或 ?code=');
};

onMounted(async () => {
  try {
    cleanupDashboardPreviewDrafts();
    const params = new URLSearchParams(window.location.search);
    const draftId = params.get('draft');
    if (draftId) {
      await applyPreviewDraft(draftId);
      return;
    }
    const dashboard = await resolveDashboard();
    dashboardName.value = dashboard.name || '';
    document.title = dashboard.name || '分析看板';
    const parsed = normalizeDashboardSpec(DashboardService.parseSpec(dashboard));
    spec.value = parsed;
    filterValues.value = {
      ...initFilterValues(parsed.filters),
      ...filtersFromQuery(parsed.filters, window.location.search),
    };
    await loadAll(filterValues.value, { force: true });
  } catch (err: any) {
    error.value = err?.response?.data?.message || err?.message || '看板加载失败';
  }
});
</script>

<style scoped>
.dashboard-viewer {
  min-height: 100vh;
  padding-bottom: 24px;
}

/* 嵌入模式：隐藏顶部工具栏，铺满 iframe */
.dashboard-viewer.is-embed {
  padding-bottom: 0;
}

.dashboard-viewer.is-screen {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
  padding-bottom: 0;
  overflow: hidden;
  background: var(--dashboard-letterbox, #e9edf3);
}

.dashboard-viewer-stage {
  min-height: 0;
}

.dashboard-viewer.is-screen .dashboard-viewer-stage {
  position: absolute;
  inset: 0;
  height: 100%;
}

.dashboard-viewer-filters-toggle {
  position: absolute;
  top: 10px;
  right: 0;
  z-index: 21;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 32px;
  padding: 0;
  color: var(--dashboard-filter-text, #606266);
  cursor: pointer;
  background: var(--dashboard-filter-bg, rgb(255 255 255 / 82%));
  border: 1px solid var(--dashboard-border, rgb(0 0 0 / 8%));
  border-right: none;
  border-radius: 8px 0 0 8px;
  opacity: 0.65;
  transition:
    opacity 0.15s ease,
    background 0.15s ease;
}

.dashboard-viewer-filters-toggle:hover,
.dashboard-viewer-filters-toggle.is-open {
  background: var(--dashboard-filter-bg, #fff);
  opacity: 1;
}

.dashboard-viewer-filters-toggle svg {
  width: 14px;
  height: 14px;
}

.dashboard-viewer-filters-float {
  position: absolute;
  top: 56px;
  left: 50%;
  z-index: 20;
  max-width: calc(100vw - 24px);
  transform: translateX(-50%);
}

.dashboard-viewer-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--dashboard-surface, #fff);
  box-shadow: var(--dashboard-shadow, 0 1px 4px rgb(0 0 0 / 6%));
}

.dashboard-viewer-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.dashboard-viewer-title {
  color: var(--dashboard-text, #303133);
  font-size: 18px;
  font-weight: 600;
}

.dashboard-viewer-error {
  margin-top: 80px;
}

.dashboard-viewer-loading {
  height: 300px;
}
</style>
