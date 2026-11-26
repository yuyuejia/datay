<template>
  <div class="dashboard-clock" :style="textStyle">{{ text }}</div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import type { DashboardWidget } from './types';

const props = defineProps<{ widget: DashboardWidget }>();

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'];
const now = ref(dayjs());
let timer = 0;

const format = computed(() => props.widget.props?.format ?? 'date-week');

const text = computed(() => {
  const value = now.value;
  switch (format.value) {
    case 'date':
      return value.format('YYYY-MM-DD');
    case 'time':
      return value.format('HH:mm:ss');
    case 'datetime':
      return value.format('YYYY-MM-DD HH:mm:ss');
    default: {
      const weekday = WEEKDAYS[value.day()] ?? '';
      return `${value.format('YYYY-MM-DD')} 星期${weekday}`;
    }
  }
});

const textStyle = computed<Record<string, string>>(() => {
  const style = props.widget.style?.title;
  return {
    ...(style?.color ? { color: style.color } : {}),
    ...(style?.fontSize != null ? { fontSize: `${style.fontSize}px` } : {}),
    ...(style?.fontWeight != null ? { fontWeight: String(style.fontWeight) } : {}),
    ...(style?.letterSpacing != null ? { letterSpacing: `${style.letterSpacing}px` } : {}),
  };
});

const interval = () => (format.value === 'time' || format.value === 'datetime' ? 1000 : 60000);

const start = () => {
  if (typeof window === 'undefined') {
    return;
  }
  window.clearInterval(timer);
  now.value = dayjs();
  timer = window.setInterval(() => {
    now.value = dayjs();
  }, interval());
};

watch(format, start);
onMounted(start);
onBeforeUnmount(() => window.clearInterval(timer));
</script>

<style scoped>
.dashboard-clock {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 12px;
  overflow: hidden;
  color: var(--dashboard-text-secondary, #94a3b8);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 1px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
