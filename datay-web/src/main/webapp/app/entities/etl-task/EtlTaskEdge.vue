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
</script>

<template>
  <BaseEdge :id="id" :path="pathData[0]" :marker-end="markerEnd" />
  <EdgeLabelRenderer>
    <div
      class="etl-edge-label"
      :class="{ 'is-empty': !labelText }"
      :style="{ transform: `translate(-50%, -50%) translate(${pathData[1]}px, ${pathData[2]}px)` }"
      title="双击设置连线标签"
      @dblclick="openEditor"
      @click.stop
    >
      <span v-if="labelText">{{ labelText }}</span>
      <span v-else>＋标签</span>
    </div>
  </EdgeLabelRenderer>
</template>

<style scoped>
.etl-edge-label {
  position: absolute;
  pointer-events: all;
  cursor: pointer;
  font-size: 11px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 8px;
  background-color: #ecf5ff;
  color: #409eff;
  border: 1px solid #d9ecff;
  white-space: nowrap;
  user-select: none;
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
