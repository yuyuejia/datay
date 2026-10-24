<template>
  <div class="dashboard-canvas" :class="{ 'is-screen': isScreenMode }">
    <div v-if="showHeader !== false && !isScreenMode && (spec.title || spec.description)" class="dashboard-canvas-header">
      <h2 v-if="spec.title" class="dashboard-canvas-title">{{ spec.title }}</h2>
      <p v-if="spec.description" class="dashboard-canvas-desc">
        {{ spec.description }}
      </p>
    </div>

    <div class="dashboard-canvas-grid" :style="gridStyle">
      <div v-for="widget in widgets" :key="widget.id" class="dashboard-canvas-item" :style="itemStyle(widget)">
        <DashboardWidget :widget="widget" :result="resultOf(widget)" :loading="loadingOf(widget)" :error="errorOf(widget)" :theme="theme" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import DashboardWidget from './DashboardWidget.vue';
import { DEFAULT_GAP, DEFAULT_ROW_HEIGHT, GRID_COLUMNS } from './designer/grid-layout';
import { normalizeTheme } from './layout-defaults';
import type { DashboardSpec, DashboardWidget as Widget, DatasetResult } from './types';

const props = defineProps<{
  spec: DashboardSpec;
  datasetData?: Record<string, DatasetResult>;
  loadingDatasets?: Record<string, boolean>;
  datasetErrors?: Record<string, string>;
  /** 是否展示标题/描述头部；独立查看器由顶栏展示时可传 false。 */
  showHeader?: boolean;
}>();

const widgets = computed(() => props.spec.widgets ?? []);
const isScreenMode = computed(() => props.spec.layout?.mode === 'screen');
const theme = computed(() => normalizeTheme(props.spec.layout?.screen?.theme));

const gridStyle = computed(() => {
  const layout = props.spec.layout ?? {};
  return {
    gridTemplateColumns: `repeat(${layout.columns ?? GRID_COLUMNS}, minmax(0, 1fr))`,
    gridAutoRows: `${layout.rowHeight ?? DEFAULT_ROW_HEIGHT}px`,
    gap: `${layout.gap ?? DEFAULT_GAP}px`,
  };
});

const itemStyle = (widget: Widget) => {
  const layout = widget.layout;
  return {
    gridColumn: `${(layout?.x ?? 0) + 1} / span ${layout?.w ?? 12}`,
    gridRow: `${(layout?.y ?? 0) + 1} / span ${layout?.h ?? 8}`,
  };
};

const resultOf = (widget: Widget) => (widget.datasetId ? props.datasetData?.[widget.datasetId] : undefined);
const loadingOf = (widget: Widget) => (widget.datasetId ? props.loadingDatasets?.[widget.datasetId] : false);
const errorOf = (widget: Widget) => (widget.datasetId ? props.datasetErrors?.[widget.datasetId] : '');
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
