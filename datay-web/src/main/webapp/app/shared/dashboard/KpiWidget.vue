<template>
  <div class="kpi-widget">
    <EChart class="kpi-widget-chart" :option="option" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import EChart from './EChart.vue';
import { buildKpiGaugeOption } from './spec-to-option';
import type { DashboardWidget, DatasetResult } from './types';

const props = defineProps<{
  widget: DashboardWidget;
  result?: DatasetResult;
}>();

const option = computed(() => buildKpiGaugeOption(props.widget, props.result));
</script>

<style scoped>
.kpi-widget {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.kpi-widget :deep(.shared-echart) {
  min-height: 0;
}
</style>
