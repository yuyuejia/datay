<template>
  <div class="kpi-widget" :class="`is-${resolved.layout}`">
    <div v-if="showLabel" class="kpi-widget-label">
      <font-awesome-icon v-if="resolved.icon" :icon="resolved.icon" class="kpi-widget-icon" />
      <span class="kpi-widget-label-text">{{ resolved.label }}</span>
    </div>
    <div class="kpi-widget-value-row">
      <span class="kpi-widget-value" :style="valueStyle">{{ displayValue }}</span>
      <span v-if="unitText" class="kpi-widget-unit" :style="resolved.unitStyle">{{ unitText }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { formatRuleStyle, resolveFormatRule } from './format-rules';
import { resolveKpiValue } from './spec-to-option';
import type { DashboardWidget, DatasetResult } from './types';
import { resolveKpiStyle } from './widget-style';

const props = defineProps<{
  widget: DashboardWidget;
  result?: DatasetResult;
}>();

const kpi = computed(() => resolveKpiValue(props.widget, props.result));
const resolved = computed(() => resolveKpiStyle(props.widget, kpi.value.label));

/** 当前展示值（动画过程中为中间值）。 */
const displayed = ref<number | null>(kpi.value.value);
let rafId = 0;
const ANIMATION_DURATION = 600;

const stopAnimation = () => {
  if (rafId) {
    cancelAnimationFrame(rafId);
    rafId = 0;
  }
};

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false;

watch(
  () => kpi.value.value,
  (next, previous) => {
    stopAnimation();
    if (!resolved.value.animate || prefersReducedMotion() || next === null || previous === null || previous === undefined) {
      displayed.value = next;
      return;
    }
    const from = displayed.value ?? previous ?? 0;
    const start = performance.now();
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / ANIMATION_DURATION);
      const eased = 1 - (1 - progress) ** 3;
      displayed.value = from + (next - from) * eased;
      if (progress < 1) {
        rafId = requestAnimationFrame(step);
      } else {
        displayed.value = next;
        rafId = 0;
      }
    };
    rafId = requestAnimationFrame(step);
  },
  { immediate: true },
);

onBeforeUnmount(stopAnimation);

const decimals = computed(() => resolved.value.decimals ?? props.widget.format?.precision);
const formatter = computed(
  () =>
    new Intl.NumberFormat(undefined, {
      minimumFractionDigits: decimals.value,
      maximumFractionDigits: decimals.value ?? (Number.isInteger(kpi.value.value) ? 0 : 2),
      useGrouping: true,
    }),
);

/** 独立单位：配置了 style.kpi.unit.text 时单独用小字号渲染。 */
const unitText = computed(() => (resolved.value.unitText ? resolved.value.unitText : ''));
const hasSeparateUnit = computed(() => !!resolved.value.unitText);

const displayValue = computed(() => {
  if (displayed.value === null || displayed.value === undefined) {
    return '--';
  }
  const format = props.widget.format;
  const unit = hasSeparateUnit.value ? '' : (format?.unit ?? '');
  return `${format?.prefix ?? ''}${formatter.value.format(displayed.value)}${unit}${format?.suffix ?? ''}`;
});

const showLabel = computed(() => resolved.value.layout !== 'value-only' && !!resolved.value.label);

const ruleStyle = computed(() =>
  formatRuleStyle(resolveFormatRule(props.widget.formatRules, props.widget.encode?.measures?.[0]?.field, kpi.value.value)),
);

/** 条件格式优先于 KPI 基础样式。 */
const valueStyle = computed(() => ({ ...resolved.value.valueStyle, ...ruleStyle.value }));
</script>

<style scoped>
.kpi-widget {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  height: 100%;
  padding: 0 8px;
  overflow: hidden;
}

.kpi-widget.is-label-left {
  flex-direction: row;
  align-items: center;
  gap: 16px;
}

.kpi-widget-label {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  color: var(--dashboard-kpi-caption, #909399);
  font-size: 13px;
  line-height: 1.2;
}

.kpi-widget-label-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi-widget-icon {
  flex: 0 0 auto;
  opacity: 0.9;
}

.kpi-widget-value-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.kpi-widget.is-label-left .kpi-widget-value-row {
  flex: 1;
  justify-content: flex-end;
}

.kpi-widget-value {
  overflow: hidden;
  color: var(--dashboard-kpi-value, #303133);
  font-size: 32px;
  font-weight: 600;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi-widget-unit {
  flex: 0 0 auto;
  color: var(--dashboard-kpi-caption, #909399);
  font-size: 14px;
  white-space: nowrap;
}
</style>
