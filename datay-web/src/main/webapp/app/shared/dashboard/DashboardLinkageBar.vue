<template>
  <div v-if="linkage" class="dashboard-linkage-bar">
    <span class="dashboard-linkage-source">联动 {{ linkage.sourceTitle }}</span>
    <span class="dashboard-linkage-condition">{{ linkage.label }}</span>
    <span class="dashboard-linkage-count">{{ linkedCount }} 个组件</span>
    <button type="button" class="dashboard-linkage-clear" title="清除联动" @click="emit('clear')">清除联动</button>
  </div>
</template>

<script setup lang="ts">
import type { ActiveWidgetLinkage } from './types';

defineProps<{
  linkage?: ActiveWidgetLinkage | null;
  linkedCount?: number;
}>();

const emit = defineEmits<{ clear: [] }>();
</script>

<style scoped>
.dashboard-linkage-bar {
  position: absolute;
  bottom: 14px;
  left: 50%;
  z-index: 20;
  display: flex;
  gap: 8px;
  align-items: center;
  max-width: calc(100% - 24px);
  padding: 6px 10px 6px 12px;
  overflow: hidden;
  transform: translateX(-50%);
  font-size: 12px;
  color: #1f2d3d;
  white-space: nowrap;
  background: rgb(255 255 255 / 96%);
  border: 1px solid #bfdfff;
  border-radius: 999px;
  box-shadow: 0 6px 18px rgb(0 0 0 / 12%);
}

.dashboard-linkage-source {
  color: #409eff;
  font-weight: 600;
}

.dashboard-linkage-condition {
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
}

.dashboard-linkage-count {
  color: #909399;
}

.dashboard-linkage-clear {
  padding: 2px 8px;
  color: #409eff;
  font-size: 12px;
  cursor: pointer;
  background: #ecf5ff;
  border: none;
  border-radius: 999px;
}

.dashboard-linkage-clear:hover {
  background: #d9ecff;
}
</style>
