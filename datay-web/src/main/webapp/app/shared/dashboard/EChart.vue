<template>
  <div ref="container" class="shared-echart" :class="{ 'is-clickable': clickable }"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { echarts } from './echarts';
import type { ECharts } from './echarts';
import './geo/china-map';
import { chartSetOptionOptions } from './render-tuning';
import type { DashboardTheme } from './types';

const props = withDefaults(
  defineProps<{
    option: Record<string, any>;
    theme?: DashboardTheme;
    /** 是否允许点击数据项；由图表下钻能力决定。 */
    clickable?: boolean;
    /** 主题序列色；未配置时使用 ECharts 内置主题色。 */
    palette?: string[];
  }>(),
  {
    theme: 'light',
    clickable: false,
    palette: undefined,
  },
);

const emit = defineEmits<{
  click: [params: Record<string, any>];
}>();

const container = ref<HTMLElement | null>(null);
let chart: ECharts | null = null;
let observer: ResizeObserver | null = null;

const render = () => {
  if (chart) {
    // 主题背景保持透明，让组件卡片背景透出；主题序列色可被 echartsOption.color 覆盖。
    // 增量合并且替换系列/坐标轴，避免自动刷新或切图时残留旧结构、打断动画。
    chart.setOption(
      {
        backgroundColor: 'transparent',
        ...(props.palette?.length ? { color: props.palette } : {}),
        ...(props.option || {}),
      },
      chartSetOptionOptions(),
    );
  }
};

const initChart = () => {
  if (!container.value) {
    return;
  }
  chart?.dispose();
  chart = echarts.init(container.value, props.theme === 'dark' ? 'dashboard-dark' : undefined);
  chart.on('click', params => emit('click', params as Record<string, any>));
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
  () => props.palette,
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

.shared-echart.is-clickable {
  cursor: pointer;
}
</style>
