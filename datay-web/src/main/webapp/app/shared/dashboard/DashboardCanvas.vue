<template>
  <div class="dashboard-canvas">
    <div
      v-if="showHeader !== false && (spec.title || spec.description)"
      class="dashboard-canvas-header"
    >
      <h2 v-if="spec.title" class="dashboard-canvas-title">{{ spec.title }}</h2>
      <p v-if="spec.description" class="dashboard-canvas-desc">
        {{ spec.description }}
      </p>
    </div>

    <div class="dashboard-canvas-grid" :style="gridStyle">
      <div
        v-for="widget in widgets"
        :key="widget.id"
        class="dashboard-canvas-item"
        :style="itemStyle(widget)"
      >
        <DashboardWidget
          :widget="widget"
          :result="resultOf(widget)"
          :loading="loadingOf(widget)"
          :error="errorOf(widget)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

import DashboardWidget from "./DashboardWidget.vue";
import type {
  DashboardSpec,
  DashboardWidget as Widget,
  DatasetResult,
} from "./types";

const props = defineProps<{
  spec: DashboardSpec;
  datasetData?: Record<string, DatasetResult>;
  loadingDatasets?: Record<string, boolean>;
  datasetErrors?: Record<string, string>;
  /** 是否展示标题/描述头部；独立查看器由顶栏展示时可传 false。 */
  showHeader?: boolean;
}>();

const widgets = computed(() => props.spec.widgets ?? []);

const gridStyle = computed(() => {
  const layout = props.spec.layout ?? {};
  return {
    gridTemplateColumns: `repeat(${layout.columns ?? 12}, minmax(0, 1fr))`,
    gridAutoRows: `${layout.rowHeight ?? 90}px`,
    gap: `${layout.gap ?? 16}px`,
  };
});

const itemStyle = (widget: Widget) => {
  const layout = widget.layout;
  return {
    gridColumn: `${(layout?.x ?? 0) + 1} / span ${layout?.w ?? 6}`,
    gridRow: `${(layout?.y ?? 0) + 1} / span ${layout?.h ?? 4}`,
  };
};

const resultOf = (widget: Widget) =>
  widget.datasetId ? props.datasetData?.[widget.datasetId] : undefined;
const loadingOf = (widget: Widget) =>
  widget.datasetId ? props.loadingDatasets?.[widget.datasetId] : false;
const errorOf = (widget: Widget) =>
  widget.datasetId ? props.datasetErrors?.[widget.datasetId] : "";
</script>

<style scoped>
.dashboard-canvas {
  padding: 16px;
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
