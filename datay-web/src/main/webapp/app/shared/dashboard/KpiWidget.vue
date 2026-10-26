<template>
  <div class="kpi-widget">
    <div class="kpi-widget-value" :style="valueStyle">{{ displayValue }}</div>
    <div v-if="caption" class="kpi-widget-caption">{{ caption }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import { formatRuleStyle, resolveFormatRule } from './format-rules';
import { resolveKpiValue } from './spec-to-option';
import type { DashboardWidget, DatasetResult } from './types';

const props = defineProps<{
  widget: DashboardWidget;
  result?: DatasetResult;
}>();

const kpi = computed(() => resolveKpiValue(props.widget, props.result));
const caption = computed(() => props.widget.title || kpi.value.label || '');

const valueStyle = computed(() =>
  formatRuleStyle(resolveFormatRule(props.widget.formatRules, props.widget.encode?.measures?.[0]?.field, kpi.value.value)),
);

const displayValue = computed(() => {
  const value = kpi.value.value;
  if (value === null || value === undefined) {
    return '--';
  }
  const format = props.widget.format;
  const precision = format?.precision;
  const formatter = new Intl.NumberFormat(undefined, {
    minimumFractionDigits: precision,
    maximumFractionDigits: precision ?? (Number.isInteger(value) ? 0 : 2),
    useGrouping: true,
  });
  const formatted = formatter.format(value);
  return `${format?.prefix ?? ''}${formatted}${format?.unit ?? ''}${format?.suffix ?? ''}`;
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
  color: var(--dashboard-kpi-value, #303133);
  font-size: 30px;
  font-weight: 600;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi-widget-caption {
  margin-top: 6px;
  color: var(--dashboard-kpi-caption, #909399);
  font-size: 13px;
}
</style>
