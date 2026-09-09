<script setup>
import { computed } from 'vue';
import { Handle, Position } from '@vue-flow/core';

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
});

const categoryMap = {
  数据输入: 'input',
  实时输入: 'input',
  数据输出: 'output',
  数据处理: 'process',
  调试组件: 'debug',
  其他: 'process',
};

const category = computed(() => categoryMap[props.data.group] || 'process');

const configured = computed(() => {
  const config = props.data.config;
  return config != null && Object.keys(config).length > 0;
});

const removeSelf = event => {
  event.stopPropagation();
  if (typeof props.data.onDelete === 'function') {
    props.data.onDelete(props.id);
  }
};
</script>

<template>
  <div class="etl-task-node" :class="[`etl-node-category-${category}`, { selected }]">
    <Handle type="target" :position="Position.Top" class="etl-handle" />
    <div class="etl-node-header">
      <span class="etl-node-chip">{{ data.group || '其他' }}</span>
      <span class="etl-node-remove" title="删除节点" @click="removeSelf">×</span>
    </div>
    <div class="etl-node-name" :title="data.label">{{ data.label }}</div>
    <div class="etl-node-meta">
      <span class="etl-node-code" :title="data.type">{{ data.type }}</span>
      <span class="etl-node-config" :class="{ configured }">{{ configured ? '已配置' : '未配置' }}</span>
    </div>
    <Handle type="source" :position="Position.Bottom" class="etl-handle" />
  </div>
</template>

<style scoped>
.etl-task-node {
  min-width: 180px;
  max-width: 220px;
  border-radius: 8px;
  background-color: #fff;
  border: 1px solid #c8ccd4;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
}

.etl-task-node.selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.25);
}

.etl-node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 6px 4px 8px;
  border-bottom: 1px solid #eef0f3;
}

.etl-node-chip {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #409eff;
  background-color: #ecf5ff;
  border-radius: 3px;
  padding: 1px 6px;
}

.etl-node-remove {
  width: 18px;
  height: 18px;
  line-height: 16px;
  text-align: center;
  border-radius: 50%;
  cursor: pointer;
  color: #909399;
  font-size: 14px;
  visibility: hidden;
}

.etl-task-node:hover .etl-node-remove {
  visibility: visible;
}

.etl-node-remove:hover {
  color: #fff;
  background-color: #f56c6c;
}

.etl-node-name {
  padding: 8px 10px 2px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.etl-node-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 0 10px 8px;
}

.etl-node-code {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.etl-node-config {
  flex-shrink: 0;
  font-size: 11px;
  color: #c0c4cc;
}

.etl-node-config.configured {
  color: #1f9d55;
}

.etl-node-category-input {
  border-top: 3px solid #409eff;
}

.etl-node-category-output {
  border-top: 3px solid #67c23a;
}

.etl-node-category-process {
  border-top: 3px solid #e6a23c;
}

.etl-node-category-debug {
  border-top: 3px solid #b37feb;
}

.etl-handle {
  width: 8px;
  height: 8px;
  background-color: #409eff;
  border: 1px solid #fff;
}
</style>
