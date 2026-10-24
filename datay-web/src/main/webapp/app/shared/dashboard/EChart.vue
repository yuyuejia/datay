<template>
  <div ref="container" class="shared-echart"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { echarts } from './echarts';
import type { ECharts } from './echarts';
import type { DashboardTheme } from './types';

const props = withDefaults(
  defineProps<{
    option: Record<string, any>;
    theme?: DashboardTheme;
  }>(),
  {
    theme: 'light',
  },
);

const container = ref<HTMLElement | null>(null);
let chart: ECharts | null = null;
let observer: ResizeObserver | null = null;

const render = () => {
  if (chart) {
    // 主题背景保持透明，让组件卡片背景透出。
    chart.setOption({ backgroundColor: 'transparent', ...(props.option || {}) }, true);
  }
};

const initChart = () => {
  if (!container.value) {
    return;
  }
  chart?.dispose();
  chart = echarts.init(container.value, props.theme === 'dark' ? 'dashboard-dark' : undefined);
  render();
};

onMounted(() => {
  if (!container.value) {
    return;
  }
  initChart();
  observer = new ResizeObserver(() => chart?.resize());
  observer.observe(container.value);
});

watch(
  () => props.option,
  () => render(),
  { deep: true },
);
watch(
  () => props.theme,
  () => {
    initChart();
  },
);

onBeforeUnmount(() => {
  observer?.disconnect();
  observer = null;
  chart?.dispose();
  chart = null;
});
</script>

<style scoped>
.shared-echart {
  width: 100%;
  height: 100%;
  min-height: 0;
}
</style>
