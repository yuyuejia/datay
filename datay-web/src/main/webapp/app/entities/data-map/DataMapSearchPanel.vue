<template>
  <div class="data-map-search">
    <div class="data-map-search-input">
      <el-input
        :model-value="query"
        placeholder="搜索数据源、表、字段、ETL、模型、指标、数据集、看板…"
        clearable
        @update:model-value="emit('update:query', $event)"
        @keyup.enter="emit('search')"
        @clear="emit('search')"
      >
        <template #prefix>
          <font-awesome-icon icon="search" />
        </template>
        <template #append>
          <el-button native-type="button" :loading="loading" @click="emit('search')">检索</el-button>
        </template>
      </el-input>
    </div>

    <div v-if="result?.indexing" class="data-map-search-indexing">
      <font-awesome-icon icon="circle-notch" spin />
      索引构建中，稍候即可查看完整地图
    </div>

    <div v-if="facetTypes.length" class="data-map-search-facets">
      <span class="data-map-search-facets-label">类型</span>
      <el-button
        v-for="facet in facetTypes"
        :key="facet.key"
        size="small"
        round
        :type="selectedTypes.includes(facet.key) ? 'primary' : ''"
        @click="toggleType(facet.key)"
      >
        {{ facet.label }}
        <span class="data-map-search-facet-count">{{ facet.count }}</span>
      </el-button>
      <el-button v-if="selectedTypes.length" size="small" text @click="clearTypes">清空</el-button>
    </div>

    <div class="data-map-search-summary">
      <span>
        共 <strong>{{ result?.total ?? 0 }}</strong> 个资产
        <template v-if="result?.tookMs"> · {{ result.tookMs }} ms</template>
      </span>
      <span v-if="selectedTypes.length" class="data-map-search-filtered">已筛选 {{ selectedTypes.length }} 类</span>
    </div>

    <div v-if="loading" class="data-map-search-loading">
      <el-skeleton :rows="6" animated />
    </div>

    <div v-else-if="result?.items?.length" class="data-map-search-results">
      <div
        v-for="item in result.items"
        :key="item.node.id"
        class="data-map-search-result"
        :class="{ active: item.node.id === selectedNodeId }"
        @click="emit('select', item.node)"
      >
        <div class="data-map-search-result-header">
          <el-tag size="small" effect="dark" :color="typeColor(item.node.type)" :style="{ color: '#fff' }" class="data-map-search-type-tag">
            {{ typeLabel(item.node.type) }}
          </el-tag>
          <span class="data-map-search-result-name" :title="item.node.name">{{ item.node.name }}</span>
          <span v-if="item.node.code" class="data-map-search-result-code">{{ item.node.code }}</span>
        </div>
        <div v-if="item.node.qualifiedName" class="data-map-search-result-qualified">{{ item.node.qualifiedName }}</div>
        <div v-if="item.highlight || item.node.description" class="data-map-search-result-desc">
          {{ item.highlight || item.node.description }}
        </div>
        <div class="data-map-search-result-meta">
          <span v-if="item.node.dataSourceName">数据源：{{ item.node.dataSourceName }}</span>
          <span v-if="item.node.status">状态：{{ item.node.status }}</span>
          <span v-if="item.node.degree">关系：{{ item.node.degree }}</span>
        </div>
      </div>
    </div>

    <el-empty v-else :description="query ? '没有匹配的资产' : '暂无资产，构建索引后可查看全域数据地图'" :image-size="72" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import { DATA_MAP_TYPE_COLORS, DATA_MAP_TYPE_LABELS } from './data-map-graph-option';
import type { DataMapFacet, DataMapNode, DataMapSearchResult } from './data-map.service';

const props = withDefaults(
  defineProps<{
    query: string;
    result?: DataMapSearchResult | null;
    loading?: boolean;
    selectedTypes: string[];
    selectedNodeId?: string;
    typeLabels?: Record<string, string>;
  }>(),
  {
    result: null,
    loading: false,
    selectedNodeId: '',
    typeLabels: () => ({}),
  },
);

const emit = defineEmits<{
  'update:query': [value: string];
  search: [];
  select: [node: DataMapNode];
  'types-change': [types: string[]];
}>();

const facetTypes = computed<DataMapFacet[]>(() => props.result?.facets?.types || []);

const typeLabel = (type: string) => props.typeLabels?.[type] || DATA_MAP_TYPE_LABELS[type] || type;

const typeColor = (type: string) => DATA_MAP_TYPE_COLORS[type] || '#909399';

const toggleType = (type: string) => {
  const next = props.selectedTypes.includes(type) ? props.selectedTypes.filter(item => item !== type) : [...props.selectedTypes, type];
  emit('types-change', next);
};

const clearTypes = () => emit('types-change', []);
</script>

<style scoped>
.data-map-search {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.data-map-search-input {
  margin-bottom: 10px;
}

.data-map-search-indexing {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f4f4f5;
  color: #606266;
  font-size: 12px;
}

.data-map-search-facets {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.data-map-search-facets-label {
  color: #909399;
  font-size: 12px;
}

.data-map-search-facet-count {
  margin-left: 4px;
  opacity: 0.72;
  font-size: 11px;
}

.data-map-search-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #909399;
  font-size: 12px;
  margin-bottom: 8px;
}

.data-map-search-filtered {
  color: #409eff;
}

.data-map-search-results {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 2px;
}

.data-map-search-result {
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background-color 0.15s;
}

.data-map-search-result:hover,
.data-map-search-result.active {
  border-color: #409eff;
  background: #f0f7ff;
}

.data-map-search-result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.data-map-search-result-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: #303133;
}

.data-map-search-result-code {
  color: #909399;
  font-size: 12px;
}

.data-map-search-type-tag {
  flex: 0 0 auto;
}

.data-map-search-result-qualified {
  margin-top: 4px;
  color: #606266;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.data-map-search-result-desc {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.data-map-search-result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 6px;
  color: #a8abb2;
  font-size: 11px;
}

.data-map-search-loading {
  padding: 4px;
}
</style>
