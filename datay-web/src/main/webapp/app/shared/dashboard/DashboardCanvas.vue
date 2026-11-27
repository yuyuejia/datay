<template>
  <div class="dashboard-canvas" :class="{ 'is-screen': isScreenMode }" :style="canvasStyle">
    <DashboardLinkageBar :linkage="linkage" :linked-count="linkedCount" @clear="clearLinkage" />
    <div v-if="showHeader !== false && !isScreenMode && (spec.title || spec.description)" class="dashboard-canvas-header">
      <h2 v-if="spec.title" class="dashboard-canvas-title">{{ spec.title }}</h2>
      <p v-if="spec.description" class="dashboard-canvas-desc">
        {{ spec.description }}
      </p>
    </div>

    <div class="dashboard-canvas-grid" :class="{ 'is-pixel': isPixel }" :style="layoutStyle">
      <div
        v-for="widget in widgets"
        :key="widget.id"
        class="dashboard-canvas-item"
        :class="{ 'is-linked': linkageWidgetIds?.[widget.id] }"
        :data-widget-id="widget.id"
        :style="itemStyle(widget)"
      >
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
          :page-reset-key="pageResetKeyOf(widget)"
          :drill-loader="loadDrill"
          :apply-linkage="applyLinkage"
          :linkage-selection="linkageSelectionOf(widget)"
        >
          <template v-if="widget.type === 'container'" #container>
            <div class="dashboard-container-grid">
              <div
                v-for="child in containerChildren(widget.id)"
                :key="child.id"
                class="dashboard-container-cell"
                :class="{ 'is-linked': linkageWidgetIds?.[child.id] }"
                :style="childGridStyle(child.layout)"
              >
                <DashboardWidget
                  :widget="child"
                  :result="resultOf(child)"
                  :loading="loadingOf(child)"
                  :error="errorOf(child)"
                  :theme="theme"
                  :palette="chartPalette"
                  :map-theme="mapTheme"
                  :dataset-data="datasetData"
                  :server-paged="serverPaginationWidgets?.[child.id]"
                  :page-loader="loadWidgetPage ? page => loadWidgetPage(child.id, page) : undefined"
                  :page-reset-key="dataVersion"
                  :drill-loader="loadDrill"
                  :apply-linkage="applyLinkage"
                  :linkage-selection="linkageSelectionOf(child)"
                />
              </div>
            </div>
          </template>
        </DashboardWidget>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import DashboardLinkageBar from './DashboardLinkageBar.vue';
import DashboardWidget from './DashboardWidget.vue';
import { childGridStyle } from './container-layout';
import { resolveCanvasMetrics } from './designer/grid-layout';
import { normalizeTheme } from './layout-defaults';
import { resolveChartPalette, resolveThemeTokens, themeCssVars } from './theme-presets';
import type {
  ActiveWidgetLinkage,
  DashboardSpec,
  DashboardWidget as Widget,
  DatasetResult,
  WidgetDrillLoader,
  WidgetLinkageHandler,
  WidgetPageLoader,
} from './types';

const props = defineProps<{
  spec: DashboardSpec;
  datasetData?: Record<string, DatasetResult>;
  widgetData?: Record<string, DatasetResult>;
  loadingDatasets?: Record<string, boolean>;
  /** 联动等局部刷新时只标记受影响组件的加载状态。 */
  loadingWidgets?: Record<string, boolean>;
  widgetResetKeys?: Record<string, number>;
  datasetErrors?: Record<string, string>;
  widgetErrors?: Record<string, string>;
  serverPaginationWidgets?: Record<string, boolean>;
  loadWidgetPage?: WidgetPageLoader;
  loadDrill?: WidgetDrillLoader;
  /** 当前生效的图表联动；用于顶部提示与目标组件高亮。 */
  linkage?: ActiveWidgetLinkage | null;
  linkageWidgetIds?: Record<string, boolean>;
  applyLinkage?: WidgetLinkageHandler;
  clearLinkage?: () => void;
  dataVersion?: number;
  /** 是否展示标题/描述头部；独立查看器由顶栏展示时可传 false。 */
  showHeader?: boolean;
}>();

const widgets = computed(() => {
  // 子组件在容器内部渲染，画布只渲染顶层组件；容器先渲染作为面板背景。
  const list = (props.spec.widgets ?? []).filter(widget => !widget.parentId);
  return [...list].sort((left, right) => Number(right.type === 'container') - Number(left.type === 'container'));
});
const containerChildren = (containerId: string) => (props.spec.widgets ?? []).filter(widget => widget.parentId === containerId);
const isScreenMode = computed(() => props.spec.layout?.mode === 'screen');
const linkedCount = computed(() => Object.keys(props.linkageWidgetIds ?? {}).length);
const linkageSelectionOf = (widget: Widget) => (props.linkage?.sourceWidgetId === widget.id ? (props.linkage.selection ?? null) : null);
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

const stackOf = (widget: Widget) => (widget.type === 'container' ? 0 : (widget.zIndex ?? 0));

const itemStyle = (widget: Widget): Record<string, string | number> => {
  const layout = widget.layout;
  if (isPixel.value) {
    return {
      position: 'absolute',
      left: `${layout?.x ?? 0}px`,
      top: `${layout?.y ?? 0}px`,
      width: `${layout?.w ?? 12}px`,
      height: `${layout?.h ?? 8}px`,
      zIndex: stackOf(widget),
    };
  }
  return {
    gridColumn: `${(layout?.x ?? 0) + 1} / span ${layout?.w ?? 12}`,
    gridRow: `${(layout?.y ?? 0) + 1} / span ${layout?.h ?? 8}`,
    zIndex: stackOf(widget),
  };
};

const resultOf = (widget: Widget) =>
  props.widgetData?.[widget.id] ?? (widget.datasetId ? props.datasetData?.[widget.datasetId] : undefined);
const loadingOf = (widget: Widget) =>
  props.loadingWidgets?.[widget.id] || (widget.datasetId ? !!props.loadingDatasets?.[widget.datasetId] : false);
const pageResetKeyOf = (widget: Widget) => (props.dataVersion ?? 0) + (props.widgetResetKeys?.[widget.id] ?? 0);
const errorOf = (widget: Widget) =>
  props.widgetErrors?.[widget.id] ?? (widget.datasetId ? props.datasetErrors?.[widget.datasetId] : '') ?? '';
</script>

<style scoped>
.dashboard-canvas {
  position: relative;
  box-sizing: border-box;
  padding: 16px;
}

.dashboard-canvas-item.is-linked,
.dashboard-container-cell.is-linked {
  outline: 2px solid #409eff;
  outline-offset: 2px;
  border-radius: 8px;
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

.dashboard-container-grid {
  display: grid;
  grid-template-columns: repeat(24, minmax(0, 1fr));
  grid-auto-rows: 20px;
  gap: 4px;
  height: 100%;
  padding: 8px;
  overflow: hidden;
  box-sizing: border-box;
}

.dashboard-container-cell {
  min-width: 0;
  min-height: 0;
  height: 100%;
}
</style>
