<template>
  <div class="data-map-detail" v-loading="loading">
    <template v-if="detail">
      <div class="data-map-detail-header">
        <div class="data-map-detail-title">
          <el-tag size="small" effect="plain">{{ typeLabel(detail.node.type) }}</el-tag>
          <span class="data-map-detail-name" :title="detail.node.name">{{ detail.node.name }}</span>
        </div>
        <div class="data-map-detail-actions">
          <el-button size="small" text @click="emit('focus', detail.node)">
            <font-awesome-icon icon="project-diagram" />
            聚焦
          </el-button>
          <el-button v-if="detail.link" size="small" text @click="openLink">
            <font-awesome-icon icon="arrow-right" />
            打开
          </el-button>
          <el-button size="small" text @click="emit('close')">
            <font-awesome-icon icon="times" />
          </el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="data-map-detail-tabs">
        <el-tab-pane label="概览" name="overview">
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="资产类型">{{ typeLabel(detail.node.type) }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.node.code" label="编码">{{ detail.node.code }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.node.qualifiedName" label="限定名">
              {{ detail.node.qualifiedName }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.node.dataSourceName" label="数据源">
              {{ detail.node.dataSourceName }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.node.schema" label="Schema">{{ detail.node.schema }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.node.table" label="表">{{ detail.node.table }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.node.status" label="状态">{{ detail.node.status }}</el-descriptions-item>
            <el-descriptions-item label="关系数">{{ detail.node.degree }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.node.description" label="描述">
              {{ detail.node.description }}
            </el-descriptions-item>
            <el-descriptions-item v-for="entry in metaEntries" :key="entry.key" :label="entry.label">
              {{ entry.value }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane :label="`字段 (${detail.fields?.length || 0})`" name="fields">
          <el-table v-if="detail.fields?.length" :data="detail.fields" size="small" max-height="420">
            <el-table-column prop="name" label="字段" min-width="130" show-overflow-tooltip />
            <el-table-column label="类型" width="110">
              <template #default="scope">{{ scope.row.meta?.dataType || scope.row.meta?.fieldType || '-' }}</template>
            </el-table-column>
            <el-table-column label="主键" width="60" align="center">
              <template #default="scope">
                <font-awesome-icon v-if="scope.row.meta?.primaryKey" icon="check" class="data-map-detail-primary-key" />
              </template>
            </el-table-column>
            <el-table-column prop="description" label="注释" min-width="160" show-overflow-tooltip />
          </el-table>
          <el-empty v-else description="暂无字段信息" :image-size="64" />
        </el-tab-pane>

        <el-tab-pane :label="`上下游 (${relatedEdges.length})`" name="relations">
          <div v-if="relatedEdges.length" class="data-map-detail-relations">
            <div v-for="edge in relatedEdges" :key="edge.id" class="data-map-detail-relation" @click="selectRelated(edge)">
              <el-tag size="small" :type="edge.upstream ? 'warning' : 'success'" effect="plain">
                {{ edge.upstream ? '上游' : '下游' }}
              </el-tag>
              <span class="data-map-detail-relation-label">{{ edge.label || edge.relation }}</span>
              <span class="data-map-detail-relation-node">
                <span class="data-map-detail-relation-type">{{ typeLabel(edge.node?.type) }}</span>
                {{ edge.node?.name || edge.nodeId }}
              </span>
            </div>
          </div>
          <el-empty v-else description="暂无上下游关系" :image-size="64" />
        </el-tab-pane>
      </el-tabs>
    </template>
    <el-empty v-else-if="!loading" description="点击关系图中的节点查看资产详情" :image-size="72" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { DATA_MAP_TYPE_LABELS } from './data-map-graph-option';
import type { DataMapAssetDetail, DataMapEdge, DataMapNode } from './data-map.service';

const props = withDefaults(
  defineProps<{
    detail?: DataMapAssetDetail | null;
    loading?: boolean;
    typeLabels?: Record<string, string>;
  }>(),
  {
    detail: null,
    loading: false,
    typeLabels: () => ({}),
  },
);

const emit = defineEmits<{
  close: [];
  focus: [node: DataMapNode];
  'select-node': [node: DataMapNode];
}>();

const router = useRouter();
const activeTab = ref('overview');

watch(
  () => props.detail?.node?.id,
  () => {
    activeTab.value = 'overview';
  },
);

const typeLabel = (type?: string) => (type ? props.typeLabels?.[type] || DATA_MAP_TYPE_LABELS[type] || type : '');

const META_LABELS: Record<string, string> = {
  type: '数据库类型',
  hostname: '主机',
  port: '端口',
  database: '数据库',
  defaultSchema: '默认 Schema',
  metadataStatus: '元数据状态',
  comment: '注释',
  collectedAt: '采集时间',
  catalog: 'Catalog',
  dataType: '字段类型',
  ordinal: '序号',
  length: '长度',
  precision: '精度',
  scale: '小数位',
  nullable: '可空',
  primaryKey: '主键',
  partitionKey: '分区键',
  defaultValue: '默认值',
  modelType: '模型类型',
  dimensionKind: '维度类型',
  metricType: '指标类型',
  unit: '单位',
  taskType: '任务类型',
  cron: '调度',
  datasetType: '数据集类型',
  sourceType: '来源类型',
  fieldType: '字段类型',
};

const SENSITIVE_KEYS = new Set(['password', 'username', 'secret', 'token', 'extraParams', 'whereClause', 'sql', 'sqlText', 'filterConfig']);

const metaEntries = computed(() => {
  const meta = props.detail?.node?.meta || {};
  return Object.entries(meta)
    .filter(([key, value]) => value != null && value !== '' && !SENSITIVE_KEYS.has(key))
    .filter(([key]) => META_LABELS[key])
    .map(([key, value]) => ({
      key,
      label: META_LABELS[key],
      value: formatValue(value),
    }));
});

type RelatedEdge = DataMapEdge & { upstream: boolean; node?: DataMapNode; nodeId: string };

const relatedEdges = computed<RelatedEdge[]>(() => {
  const detail = props.detail;
  if (!detail) {
    return [];
  }
  const nodeMap = new Map((detail.graph?.nodes || []).map(node => [node.id, node]));
  const result: RelatedEdge[] = [];
  for (const edge of detail.graph?.links || []) {
    if (edge.target === detail.node.id) {
      result.push({ ...edge, upstream: true, nodeId: edge.source, node: nodeMap.get(edge.source) });
    } else if (edge.source === detail.node.id) {
      result.push({ ...edge, upstream: false, nodeId: edge.target, node: nodeMap.get(edge.target) });
    }
  }
  return result;
});

const selectRelated = (edge: RelatedEdge) => {
  if (edge.node) {
    emit('select-node', edge.node);
  }
};

const openLink = () => {
  if (props.detail?.link) {
    router.push(props.detail.link);
  }
};

const formatValue = (value: unknown) => {
  if (typeof value === 'boolean') {
    return value ? '是' : '否';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
};
</script>

<style scoped>
.data-map-detail {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.data-map-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.data-map-detail-title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.data-map-detail-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.data-map-detail-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
}

.data-map-detail-tabs {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.data-map-detail-primary-key {
  color: #e6a23c;
}

.data-map-detail-relations {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.data-map-detail-relation {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
}

.data-map-detail-relation:hover {
  border-color: #409eff;
  background: #f0f7ff;
}

.data-map-detail-relation-label {
  color: #909399;
  font-size: 12px;
  flex: 0 0 auto;
}

.data-map-detail-relation-node {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #303133;
}

.data-map-detail-relation-type {
  color: #409eff;
  font-size: 11px;
  margin-right: 4px;
}
</style>
