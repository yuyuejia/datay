<template>
  <div ref="container" class="shared-echart" :class="{ 'is-clickable': clickable }"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { echarts } from './echarts';
import type { ECharts } from './echarts';
import './geo/china-map';
import { applyLinkageSelection } from './linkage-highlight';
import { chartSetOptionOptions } from './render-tuning';
import type { DashboardTheme, LinkageSelection } from './types';

const props = withDefaults(
  defineProps<{
    option: Record<string, any>;
    theme?: DashboardTheme;
    /** 是否允许点击数据项；由图表下钻能力决定。 */
    clickable?: boolean;
    /** 主题序列色；未配置时使用 ECharts 内置主题色。 */
    palette?: string[];
    /** 联动选中态：只做置灰渲染，不重建 option 或重新取数。 */
    dimSelection?: LinkageSelection | null;
  }>(),
  {
    theme: 'light',
    clickable: false,
    palette: undefined,
    dimSelection: null,
  },
);

const emit = defineEmits<{
  click: [params: Record<string, any>];
}>();

const container = ref<HTMLElement | null>(null);
let chart: ECharts | null = null;
let observer: ResizeObserver | null = null;

/**
 * 只更新系列样式实现置灰：不替换系列结构，避免触发源图表重绘/重新动画。
 */
const applyDim = () => {
  if (!chart || !props.dimSelection) {
    return;
  }
  const dimmed = applyLinkageSelection(props.option, props.dimSelection);
  if (!dimmed || !Array.isArray(dimmed.series)) {
    return;
  }
  chart.setOption({ series: dimmed.series }, { notMerge: false, lazyUpdate: true });
};

/** 取消选中时恢复原始系列样式。 */
const resetDim = () => {
  if (!chart || !Array.isArray(props.option?.series)) {
    return;
  }
  chart.setOption({ series: props.option.series }, { notMerge: false, lazyUpdate: true });
};

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
    // 数据刷新后重新叠加当前选中态，避免置灰效果丢失。
    applyDim();
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
  () => props.dimSelection,
  (next, previous) => {
    if (next) {
      applyDim();
    } else if (previous) {
      resetDim();
    }
  },
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
