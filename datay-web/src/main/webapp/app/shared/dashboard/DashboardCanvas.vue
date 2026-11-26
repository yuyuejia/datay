<template>
  <div class="dashboard-canvas" :class="{ 'is-screen': isScreenMode }" :style="canvasStyle">
    <div v-if="showHeader !== false && !isScreenMode && (spec.title || spec.description)" class="dashboard-canvas-header">
      <h2 v-if="spec.title" class="dashboard-canvas-title">{{ spec.title }}</h2>
      <p v-if="spec.description" class="dashboard-canvas-desc">
        {{ spec.description }}
      </p>
    </div>

    <div class="dashboard-canvas-grid" :class="{ 'is-pixel': isPixel }" :style="layoutStyle">
      <div v-for="widget in widgets" :key="widget.id" class="dashboard-canvas-item" :style="itemStyle(widget)">
        <DashboardWidget
          :widget="widget"
          :result="resultOf(widget)"
          :loading="loadingOf(widget)"
          :error="errorOf(widget)"
          :theme="theme"
          :palette="chartPalette"
          :map-theme="mapTheme"
          :dataset-data="datasetData"
          :server-paged="serverPaginationWidgets?.[widget.id]"
          :page-loader="loadWidgetPage ? page => loadWidgetPage(widget.id, page) : undefined"
          :page-reset-key="dataVersion"
          :drill-loader="loadDrill"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import DashboardWidget from './DashboardWidget.vue';
import { resolveCanvasMetrics } from './designer/grid-layout';
import { normalizeTheme } from './layout-defaults';
import { resolveChartPalette, resolveThemeTokens, themeCssVars } from './theme-presets';
import type { DashboardSpec, DashboardWidget as Widget, DatasetResult, WidgetDrillLoader, WidgetPageLoader } from './types';

const props = defineProps<{
  spec: DashboardSpec;
  datasetData?: Record<string, DatasetResult>;
  widgetData?: Record<string, DatasetResult>;
  loadingDatasets?: Record<string, boolean>;
  datasetErrors?: Record<string, string>;
  widgetErrors?: Record<string, string>;
  serverPaginationWidgets?: Record<string, boolean>;
  loadWidgetPage?: WidgetPageLoader;
  loadDrill?: WidgetDrillLoader;
  dataVersion?: number;
  /** 是否展示标题/描述头部；独立查看器由顶栏展示时可传 false。 */
  showHeader?: boolean;
}>();

const widgets = computed(() => props.spec.widgets ?? []);
const isScreenMode = computed(() => props.spec.layout?.mode === 'screen');
const theme = computed(() => normalizeTheme(props.spec.layout?.screen?.theme));

const metrics = computed(() => resolveCanvasMetrics(props.spec.layout));
const isPixel = computed(() => metrics.value.unit === 'px');

const themeTokens = computed(() => resolveThemeTokens(props.spec.layout?.screen?.themePreset, props.spec.layout?.screen?.tokens));
const themeVars = computed(() => themeCssVars(themeTokens.value));
const chartPalette = computed(() => resolveChartPalette(themeTokens.value));
const mapTheme = computed(() => themeTokens.value.map);

const canvasStyle = computed(() => ({ padding: `${metrics.value.padding}px`, ...themeVars.value }));

const layoutStyle = computed<Record<string, string>>(() => {
  if (isPixel.value) {
    return { display: 'block', position: 'relative', width: '100%', height: '100%' };
  }
  return {
    gridTemplateColumns: `repeat(${metrics.value.columns}, minmax(0, 1fr))`,
    gridAutoRows: `${metrics.value.rowHeight}px`,
    gap: `${metrics.value.gap}px`,
  };
});

const itemStyle = (widget: Widget): Record<string, string | number> => {
  const layout = widget.layout;
  if (isPixel.value) {
    return {
      position: 'absolute',
      left: `${layout?.x ?? 0}px`,
      top: `${layout?.y ?? 0}px`,
      width: `${layout?.w ?? 12}px`,
      height: `${layout?.h ?? 8}px`,
      ...(widget.zIndex ? { zIndex: widget.zIndex } : {}),
    };
  }
  return {
    gridColumn: `${(layout?.x ?? 0) + 1} / span ${layout?.w ?? 12}`,
    gridRow: `${(layout?.y ?? 0) + 1} / span ${layout?.h ?? 8}`,
    ...(widget.zIndex ? { zIndex: widget.zIndex } : {}),
  };
};

const resultOf = (widget: Widget) =>
  props.widgetData?.[widget.id] ?? (widget.datasetId ? props.datasetData?.[widget.datasetId] : undefined);
const loadingOf = (widget: Widget) => (widget.datasetId ? props.loadingDatasets?.[widget.datasetId] : false);
const errorOf = (widget: Widget) =>
  props.widgetErrors?.[widget.id] ?? (widget.datasetId ? props.datasetErrors?.[widget.datasetId] : '') ?? '';
</script>

<style scoped>
.dashboard-canvas {
  box-sizing: border-box;
  padding: 16px;
}

.dashboard-canvas.is-screen {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.dashboard-canvas-header {
  margin-bottom: 16px;
}

.dashboard-canvas-title {
  margin: 0;
  color: #303133;
  font-size: 20px;
  font-weight: 600;
}

.dashboard-canvas-desc {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.dashboard-canvas-grid {
  display: grid;
  align-items: stretch;
}

.dashboard-canvas-item {
  min-width: 0;
  min-height: 0;
}
</style>
