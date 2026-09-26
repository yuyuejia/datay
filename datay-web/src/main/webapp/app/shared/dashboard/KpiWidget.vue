<template>
  <div class="kpi-widget">
    <div class="kpi-widget-value">{{ displayValue }}</div>
    <div v-if="caption" class="kpi-widget-caption">{{ caption }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

import { resolveKpiValue } from "./spec-to-option";
import type { DashboardWidget, DatasetResult } from "./types";

const props = defineProps<{
  widget: DashboardWidget;
  result?: DatasetResult;
}>();

const kpi = computed(() => resolveKpiValue(props.widget, props.result));
const caption = computed(() => props.widget.title || kpi.value.label || "");

const displayValue = computed(() => {
  const value = kpi.value.value;
  if (value === null || value === undefined) {
    return "--";
  }
  return Number.isInteger(value)
    ? value.toLocaleString()
    : value.toLocaleString(undefined, { maximumFractionDigits: 2 });
});
</script>

<style scoped>
.kpi-widget {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 100%;
  padding: 0 8px;
  overflow: hidden;
}

.kpi-widget-value {
  overflow: hidden;
  color: #303133;
  font-size: 30px;
  font-weight: 600;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi-widget-caption {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}
</style>
