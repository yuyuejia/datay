<template>
  <div class="dashboard-title-widget" :class="[`align-${align}`, frameClass, { 'has-sub': !!subText }]" :style="rootStyle">
    <div class="dashboard-title-main" :style="mainStyle">
      <font-awesome-icon v-if="titleIcon" :icon="titleIcon" class="dashboard-title-icon" />
      <span class="dashboard-title-text">{{ text }}</span>
    </div>
    <div v-if="subText" class="dashboard-title-sub" :style="subStyle">{{ subText }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { DashboardWidget } from './types';

const props = defineProps<{ widget: DashboardWidget }>();

const config = computed(() => props.widget.props ?? {});
const text = computed(() => (config.value.text ?? '').trim() || props.widget.title?.trim() || '领导驾驶舱');
const subText = computed(() => (config.value.subText ?? '').trim());
const align = computed(() => config.value.align ?? 'center');
const frame = computed(() => config.value.frame ?? 'none');
const frameClass = computed(() => {
  if (frame.value === 'tech-frame') {
    return 'is-frame-tech';
  }
  if (frame.value === 'bracket') {
    return 'is-frame-bracket';
  }
  return '';
});

const titleStyle = computed(() => props.widget.style?.title);
const titleIcon = computed(() => titleStyle.value?.icon);

const rootStyle = computed<Record<string, string>>(() => {
  const background = titleStyle.value?.background;
  return background ? { background } : {};
});

const mainStyle = computed<Record<string, string>>(() => {
  const style = titleStyle.value;
  return {
    ...(style?.color ? { color: style.color } : {}),
    ...(style?.fontSize != null ? { fontSize: `${style.fontSize}px` } : {}),
    ...(style?.fontWeight != null ? { fontWeight: String(style.fontWeight) } : {}),
    ...(style?.letterSpacing != null ? { letterSpacing: `${style.letterSpacing}px` } : {}),
  };
});

const subStyle = computed<Record<string, string>>(() => {
  const color = titleStyle.value?.color;
  return color ? { color, opacity: '0.72' } : { opacity: '0.72' };
});
</script>

<style scoped>
.dashboard-title-widget {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-sizing: border-box;
  height: 100%;
  padding: 0 24px;
  overflow: hidden;
}

.align-left {
  align-items: flex-start;
  text-align: left;
}

.align-center {
  align-items: center;
  text-align: center;
}

.align-right {
  align-items: flex-end;
  text-align: right;
}

.dashboard-title-main {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 100%;
  overflow: hidden;
  color: var(--dashboard-text, #e5e7eb);
  font-size: 30px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: 4px;
  text-shadow: 0 0 18px rgb(47 168 255 / 35%);
  white-space: nowrap;
}

.dashboard-title-icon {
  flex: 0 0 auto;
  font-size: 0.8em;
  opacity: 0.9;
}

.dashboard-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-title-sub {
  margin-top: 4px;
  max-width: 100%;
  overflow: hidden;
  color: var(--dashboard-text-secondary, #94a3b8);
  font-size: 14px;
  letter-spacing: 2px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-title-widget.is-frame-tech {
  border: 1px solid transparent;
  border-image: linear-gradient(90deg, transparent, var(--dash-accent, #2fa8ff), transparent) 1;
}

.dashboard-title-widget.is-frame-tech::after {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 60%;
  height: 2px;
  content: '';
  background: linear-gradient(90deg, transparent, var(--dash-accent, #2fa8ff), transparent);
  transform: translateX(-50%);
}

.dashboard-title-widget.is-frame-bracket::before,
.dashboard-title-widget.is-frame-bracket::after {
  position: absolute;
  top: 8px;
  bottom: 8px;
  width: 3px;
  content: '';
  background: linear-gradient(180deg, transparent, var(--dash-accent, #2fa8ff), transparent);
}

.dashboard-title-widget.is-frame-bracket::before {
  left: 6px;
}

.dashboard-title-widget.is-frame-bracket::after {
  right: 6px;
}
</style>
