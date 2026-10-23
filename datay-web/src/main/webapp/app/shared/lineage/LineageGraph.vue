<template>
  <div class="lineage-graph" v-loading="loading">
    <div class="lineage-toolbar">
      <div class="lineage-legend">
        <span v-for="item in legendItems" :key="item.type" class="legend-item">
          <i class="legend-dot" :class="`legend-${item.type.toLowerCase()}`"></i>
          {{ item.label }}
        </span>
      </div>
      <el-button size="small" text :loading="loading" @click="emit('refresh')">
        <font-awesome-icon icon="sync" />
        <span>刷新</span>
      </el-button>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />

    <div v-else class="lineage-canvas">
      <VueFlow
        v-model:nodes="flowNodes"
        v-model:edges="flowEdges"
        :min-zoom="0.2"
        :max-zoom="1.6"
        :fit-view-on-init="true"
        :nodes-connectable="false"
        @node-click="onNodeClick"
      >
        <template #node-lineage="{ data }">
          <div class="lineage-node" :class="[`lineage-node-${String(data.nodeType).toLowerCase()}`, { 'lineage-node-root': data.isRoot }]">
            <Handle type="target" :position="Position.Left" class="lineage-handle" />
            <Handle type="source" :position="Position.Right" class="lineage-handle" />
            <div class="lineage-node-header">
              <span class="lineage-node-type">{{ typeLabel(data.nodeType) }}</span>
              <span class="lineage-node-header-right">
                <span v-if="data.isRoot" class="lineage-node-root-badge">{{ rootLabel }}</span>
                <span v-if="data.subType" class="lineage-node-subtype">{{ data.subType }}</span>
              </span>
            </div>
            <div class="lineage-node-name" :title="data.name">{{ data.name }}</div>
            <div v-if="nodeMeta(data)" class="lineage-node-meta">{{ nodeMeta(data) }}</div>
          </div>
        </template>
        <Background pattern-color="#d9dde3" :gap="18" />
        <Controls />
      </VueFlow>
      <el-empty v-if="!loading && nodes.length === 0" :description="emptyText" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { Handle, MarkerType, Position, VueFlow, useVueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';

import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import '@vue-flow/controls/dist/style.css';

const props = withDefaults(
  defineProps<{
    nodes: any[];
    edges: any[];
    loading?: boolean;
    error?: string;
    emptyText?: string;
    rootId?: string;
    rootLabel?: string;
  }>(),
  {
    loading: false,
    error: '',
    emptyText: '暂无血缘关系',
    rootId: '',
    rootLabel: '当前',
  },
);

const emit = defineEmits<{
  'node-click': [node: any];
  refresh: [];
}>();

const TYPE_LABELS: Record<string, string> = {
  MODEL: '数据模型',
  TABLE: '物理表',
  SYNC: '数据同步',
  ETL: 'ETL 任务',
  METRIC: '指标',
  DATASET: '数据集',
  DASHBOARD: '看板',
};

const { fitView } = useVueFlow();
const flowNodes = ref<any[]>([]);
const flowEdges = ref<any[]>([]);

const legendItems = computed(() => {
  const types = new Set(props.nodes.map(node => node.type));
  return Object.entries(TYPE_LABELS)
    .filter(([type]) => types.has(type))
    .map(([type, label]) => ({ type, label }));
});

const typeLabel = (type: string) => TYPE_LABELS[type] || type || '';

const nodeMeta = (node: any) => {
  const meta = node.meta || {};
  if (node.nodeType === 'MODEL') {
    return [meta.code, meta.table].filter(Boolean).join(' · ');
  }
  if (node.nodeType === 'TABLE') {
    const dataSourceName = meta.dataSourceName || meta.dataSourceId;
    return dataSourceName ? `数据源 ${dataSourceName}` : '';
  }
  if (node.nodeType === 'METRIC') {
    return [meta.code, meta.metricType].filter(Boolean).join(' · ');
  }
  if (node.nodeType === 'DATASET') {
    return [meta.datasetType, meta.refId ? '数据集库' : '看板内嵌'].filter(Boolean).join(' · ');
  }
  if (node.nodeType === 'DASHBOARD') {
    return meta.code || '';
  }
  if (node.nodeType === 'ETL') {
    return meta.nodeType || meta.taskCode || '';
  }
  return meta.jobCode || meta.sourceTable || '';
};

const buildFlow = () => {
  const byLayer = new Map<number, any[]>();
  for (const node of props.nodes) {
    const layer = Number(node.layer ?? 0);
    if (!byLayer.has(layer)) {
      byLayer.set(layer, []);
    }
    byLayer.get(layer)!.push(node);
  }
  const layers = [...byLayer.keys()].sort((a, b) => a - b);
  const minLayer = layers.length ? layers[0] : 0;
  const maxGroupSize = Math.max(1, ...[...byLayer.values()].map(group => group.length));

  const nodes: any[] = [];
  for (const layer of layers) {
    const group = byLayer.get(layer)!.sort((a, b) => String(a.name || '').localeCompare(String(b.name || '')));
    const startY = 50 + ((maxGroupSize - group.length) * 110) / 2;
    group.forEach((node, index) => {
      nodes.push({
        id: node.id,
        type: 'lineage',
        position: {
          x: 80 + (layer - minLayer) * 280,
          y: startY + index * 110,
        },
        data: { ...node, nodeType: node.type, isRoot: node.id === props.rootId },
        connectable: false,
      });
    });
  }

  const edges = props.edges.map((edge: any) => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
    label: edge.label,
    type: 'default',
    markerEnd: MarkerType.ArrowClosed,
    animated: edge.confidence === 'INFERRED',
    style: {
      stroke: edge.confidence === 'INFERRED' ? '#c0c4cc' : '#909399',
      strokeDasharray: edge.confidence === 'INFERRED' ? '6 4' : undefined,
    },
    labelStyle: { fill: '#606266', fontSize: 11 },
    labelBgStyle: { fill: '#ffffff', fillOpacity: 0.9 },
  }));

  flowNodes.value = nodes;
  flowEdges.value = edges;
};

const onNodeClick = ({ node }: any) => {
  if (node?.data) {
    emit('node-click', node.data);
  }
};

watch(
  [() => props.nodes, () => props.edges],
  async () => {
    buildFlow();
    await nextTick();
    setTimeout(() => fitView({ padding: 0.2 }), 50);
  },
  { immediate: true, deep: true },
);
</script>

<style scoped>
.lineage-graph {
  min-height: 480px;
}

.lineage-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.lineage-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  color: #606266;
  font-size: 12px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.legend-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  display: inline-block;
}

.legend-model {
  background: #409eff;
}
.legend-table {
  background: #67c23a;
}
.legend-sync {
  background: #e6a23c;
}
.legend-etl {
  background: #b37feb;
}
.legend-metric {
  background: #f56c6c;
}
.legend-dataset {
  background: #6366f1;
}
.legend-dashboard {
  background: #14b8a6;
}

.lineage-canvas {
  position: relative;
  height: 480px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
  background: #fafbfc;
}

.lineage-canvas :deep(.vue-flow) {
  height: 480px;
}

.lineage-node {
  position: relative;
  width: 190px;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  font-size: 12px;
}

.lineage-handle {
  width: 8px;
  height: 8px;
  background: #fff;
  border: 2px solid #409eff;
}

.lineage-node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 5px 8px;
  border-bottom: 1px solid #f0f2f5;
  border-radius: 8px 8px 0 0;
}

.lineage-node-type {
  font-size: 10px;
  font-weight: 600;
  color: #606266;
  background: #f2f3f5;
  border-radius: 3px;
  padding: 1px 6px;
}

.lineage-node-header-right {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.lineage-node-subtype {
  font-size: 10px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lineage-node-root {
  border-width: 2px;
  border-color: #409eff;
  box-shadow:
    0 0 0 3px rgba(64, 158, 255, 0.18),
    0 6px 18px rgba(64, 158, 255, 0.25);
  z-index: 2;
}

.lineage-node-root-badge {
  flex-shrink: 0;
  font-size: 10px;
  line-height: 16px;
  color: #fff;
  background: #409eff;
  border-radius: 8px;
  padding: 0 7px;
  white-space: nowrap;
}

.lineage-node-name {
  padding: 7px 9px 2px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lineage-node-meta {
  padding: 0 9px 7px;
  color: #909399;
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lineage-node-model {
  border-color: #a0cfff;
}
.lineage-node-model .lineage-node-type {
  color: #409eff;
  background: #ecf5ff;
}
.lineage-node-table {
  border-color: #b3e19d;
}
.lineage-node-table .lineage-node-type {
  color: #529b2e;
  background: #f0f9eb;
}
.lineage-node-sync {
  border-color: #f3d19e;
}
.lineage-node-sync .lineage-node-type {
  color: #b88230;
  background: #fdf6ec;
}
.lineage-node-etl {
  border-color: #d3adf7;
}
.lineage-node-etl .lineage-node-type {
  color: #7b4bb7;
  background: #f8f0ff;
}
.lineage-node-metric {
  border-color: #fab6b6;
}
.lineage-node-metric .lineage-node-type {
  color: #c45656;
  background: #fef0f0;
}
.lineage-node-dataset {
  border-color: #c7d2fe;
}
.lineage-node-dataset .lineage-node-type {
  color: #4f46e5;
  background: #eef2ff;
}
.lineage-node-dashboard {
  border-color: #99e6df;
}
.lineage-node-dashboard .lineage-node-type {
  color: #0f8f82;
  background: #e8fffb;
}
</style>
