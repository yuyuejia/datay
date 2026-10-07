<script setup>
import { computed } from "vue";
import { Handle, Position } from "@vue-flow/core";

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
});

const typeClass = computed(() =>
  String(props.data.jobType || "JOB").toLowerCase(),
);

const removeSelf = (event) => {
  event.stopPropagation();
  if (typeof props.data.onDelete === "function") {
    props.data.onDelete(props.id);
  }
};
</script>

<template>
  <div
    class="dag-job-node"
    :class="[`dag-node-type-${typeClass}`, { selected }]"
  >
    <Handle type="target" :position="Position.Left" class="dag-handle" />
    <div class="dag-node-header">
      <span class="dag-node-type">{{ data.jobType }}</span>
      <span class="dag-node-remove" title="删除节点" @click="removeSelf"
        >×</span
      >
    </div>
    <div class="dag-node-name" :title="data.label">{{ data.label }}</div>
    <div class="dag-node-meta">
    </div>
    <Handle type="source" :position="Position.Right" class="dag-handle" />
  </div>
</template>

<style scoped>
.dag-job-node {
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

.dag-job-node.selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.25);
}

.dag-node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 6px 4px 8px;
  border-bottom: 1px solid #eef0f3;
}

.dag-node-type {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #409eff;
  background-color: #ecf5ff;
  border-radius: 3px;
  padding: 1px 6px;
}

.dag-node-remove {
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

.dag-job-node:hover .dag-node-remove {
  visibility: visible;
}

.dag-node-remove:hover {
  color: #fff;
  background-color: #f56c6c;
}

.dag-node-name {
  padding: 8px 10px 2px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dag-node-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px 8px;
}

.dag-node-id {
  font-size: 11px;
  color: #909399;
}

.dag-node-status {
  font-size: 11px;
  color: #1f9d55;
}

.dag-node-type-sql {
  border-top: 3px solid #67c23a;
}
.dag-node-type-shell {
  border-top: 3px solid #e6a23c;
}
.dag-node-type-etl {
  border-top: 3px solid #409eff;
}
.dag-node-type-demo {
  border-top: 3px solid #b37feb;
}

.dag-handle {
  width: 8px;
  height: 8px;
  background-color: #409eff;
  border: 1px solid #fff;
}
</style>