<template>
  <div ref="container" class="shared-echart"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

import { echarts } from "./echarts";
import type { ECharts } from "./echarts";

const props = defineProps<{ option: Record<string, any> }>();

const container = ref<HTMLElement | null>(null);
let chart: ECharts | null = null;
let observer: ResizeObserver | null = null;

const render = () => {
  if (chart) {
    chart.setOption(props.option || {}, true);
  }
};

onMounted(() => {
  if (!container.value) {
    return;
  }
  chart = echarts.init(container.value);
  render();
  observer = new ResizeObserver(() => chart?.resize());
  observer.observe(container.value);
});

watch(
  () => props.option,
  () => render(),
  { deep: true },
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
  min-height: 200px;
}
</style>
