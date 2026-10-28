<script setup>
import { computed } from 'vue';
import { BaseEdge, EdgeLabelRenderer, getBezierPath } from '@vue-flow/core';

const props = defineProps({
  id: { type: String, required: true },
  sourceX: { type: Number, required: true },
  sourceY: { type: Number, required: true },
  targetX: { type: Number, required: true },
  targetY: { type: Number, required: true },
  sourcePosition: { type: String, default: 'right' },
  targetPosition: { type: String, default: 'left' },
  markerEnd: { type: [String, Object], default: null },
  data: { type: Object, default: () => ({}) },
  label: { type: [String, Object], default: '' },
});

const pathData = computed(() =>
  getBezierPath({
    sourceX: props.sourceX,
    sourceY: props.sourceY,
    targetX: props.targetX,
    targetY: props.targetY,
    sourcePosition: props.sourcePosition,
    targetPosition: props.targetPosition,
  }),
);

const labelText = computed(() => {
  if (typeof props.label === 'string' && props.label.trim()) {
    return props.label.trim();
  }
  return props.data?.label ? String(props.data.label) : '';
});

const openEditor = event => {
  event.stopPropagation();
  if (typeof props.data?.onEditLabel === 'function') {
    props.data.onEditLabel(props.id);
  }
};

const tooltip = computed(() => {
  const capacity = props.data?.queueCapacity;
  const base = '双击设置连线标签与队列大小';
  return capacity ? `${base}（当前队列大小：${capacity}）` : base;
});

const deleteEdge = event => {
  event.stopPropagation();
  if (typeof props.data?.onDeleteEdge === 'function') {
    props.data.onDeleteEdge(props.id);
  }
};
</script>

<template>
  <BaseEdge :id="id" :path="pathData[0]" :marker-end="markerEnd" />
  <EdgeLabelRenderer>
    <div
      class="etl-edge-label-group"
      :style="{ transform: `translate(-50%, -50%) translate(${pathData[1]}px, ${pathData[2]}px)` }"
      @click.stop
    >
      <div class="etl-edge-label" :class="{ 'is-empty': !labelText }" :title="tooltip" @dblclick="openEditor">
        <span v-if="labelText">{{ labelText }}</span>
        <span v-else>＋标签</span>
      </div>
      <button type="button" class="etl-edge-delete" title="删除连线" aria-label="删除连线" @click.stop.prevent="deleteEdge">×</button>
    </div>
  </EdgeLabelRenderer>
</template>

<style scoped>
.etl-edge-label-group {
  position: absolute;
  pointer-events: all;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  user-select: none;
}

.etl-edge-label {
  cursor: pointer;
  font-size: 11px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 8px;
  background-color: #ecf5ff;
  color: #409eff;
  border: 1px solid #d9ecff;
}

.etl-edge-delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  padding: 0;
  font-size: 12px;
  line-height: 1;
  border-radius: 50%;
  border: 1px solid #fbc4c4;
  background-color: #fef0f0;
  color: #f56c6c;
  cursor: pointer;
  opacity: 0;
  pointer-events: none;
  transition:
    opacity 0.2s,
    background-color 0.2s;
}

.etl-edge-label-group:hover .etl-edge-delete,
.etl-edge-delete:focus-visible {
  opacity: 0.75;
  pointer-events: all;
}

.etl-edge-delete:hover {
  opacity: 1;
  background-color: #f56c6c;
  border-color: #f56c6c;
  color: #fff;
}

.etl-edge-label.is-empty {
  background-color: #f5f7fa;
  color: #c0c4cc;
  border-color: #ebeef5;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.etl-edge-label.is-empty:hover {
  opacity: 1;
}
</style>
