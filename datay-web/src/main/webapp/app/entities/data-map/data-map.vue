<template>
  <div class="data-map-container">
    <div class="page-header">
      <div class="data-map-title-group">
        <h4 class="page-title">数据地图</h4>
        <span class="data-map-subtitle">全域资产检索与关系地图</span>
      </div>
      <div class="data-map-header-actions">
        <el-tag size="small" :type="statusTagType" effect="plain">{{ statusLabel }}</el-tag>
        <span v-if="overview?.indexStatus?.message" class="data-map-status-message">{{ overview.indexStatus.message }}</span>
        <el-button v-if="canReindex" size="small" :loading="rebuilding" @click="reindex">
          <font-awesome-icon icon="sync" />
          刷新索引
        </el-button>
      </div>
    </div>

    <div class="data-map-stats">
      <div class="data-map-stat-card">
        <span class="data-map-stat-label">资产总数</span>
        <strong class="data-map-stat-value">{{ overview?.totalNodes ?? 0 }}</strong>
      </div>
      <div class="data-map-stat-card">
        <span class="data-map-stat-label">关系总数</span>
        <strong class="data-map-stat-value">{{ overview?.totalEdges ?? 0 }}</strong>
      </div>
      <div class="data-map-stat-card">
        <span class="data-map-stat-label">元数据覆盖</span>
        <strong class="data-map-stat-value">{{ overview?.coverage || '-' }}</strong>
      </div>
      <div class="data-map-stat-card data-map-stat-types">
        <span class="data-map-stat-label">资产分布</span>
        <div class="data-map-type-counts">
          <span v-for="(count, type) in topTypeCounts" :key="type" class="data-map-type-count">
            <i class="data-map-type-dot" :style="{ background: typeColor(type) }"></i>
            {{ typeLabel(type) }} {{ count }}
          </span>
        </div>
      </div>
    </div>

    <div class="data-map-layout">
      <aside v-if="searchPanelVisible" class="data-map-search-panel">
        <DataMapSearchPanel
          v-model:query="searchQuery"
          :result="searchResult"
          :loading="searchLoading"
          :selected-types="selectedTypes"
          :selected-node-id="selectedNodeId"
          :type-labels="typeLabels"
          @search="loadSearch"
          @select="onSelectNode"
          @types-change="onTypesChange"
        />
      </aside>

      <section class="data-map-graph-panel">
        <div class="data-map-toolbar">
          <div class="data-map-toolbar-left">
            <el-button size="small" text @click="searchPanelVisible = !searchPanelVisible">
              <font-awesome-icon icon="list" />
              {{ searchPanelVisible ? '收起检索' : '展开检索' }}
            </el-button>
            <el-button-group>
              <el-button size="small" :type="scope === 'full' ? 'primary' : ''" @click="backToFull">全量视图</el-button>
              <el-button size="small" :type="scope === 'subgraph' ? 'primary' : ''" :disabled="!selectedNodeId" @click="focusSelected">
                聚焦当前
              </el-button>
            </el-button-group>
            <el-select
              v-model="dataSourceId"
              size="small"
              clearable
              filterable
              placeholder="全部数据源"
              class="data-map-source-select"
              @change="reloadGraphAndSearch"
            >
              <el-option v-for="source in dataSources" :key="source.id" :label="source.name" :value="source.id" />
            </el-select>
          </div>
          <div class="data-map-toolbar-right">
            <el-switch v-model="includeColumns" size="small" active-text="显示字段" @change="onIncludeColumnsChange" />
            <el-switch v-model="showLabels" size="small" active-text="标签" />
            <el-select v-model="layout" size="small" class="data-map-layout-select">
              <el-option label="力导向布局" value="force" />
              <el-option label="环形布局" value="circular" />
            </el-select>
            <el-button size="small" text :loading="graphLoading" @click="loadGraph">
              <font-awesome-icon icon="sync" />
            </el-button>
          </div>
        </div>

        <el-alert v-if="graphError" :title="graphError" type="error" :closable="false" show-icon class="data-map-graph-error" />
        <div v-else class="data-map-graph-wrap">
          <DataMapGraph
            ref="graphRef"
            :nodes="graph.nodes"
            :links="graph.links"
            :categories="graph.categories"
            :loading="graphLoading"
            :truncated="graph.truncated"
            :layout="layout"
            :show-labels="showLabels"
            :focus-mode="scope === 'subgraph'"
            :center-node-id="scope === 'subgraph' ? rootId : ''"
            @node-click="onSelectNode"
            @node-dblclick="focusNode"
          />
        </div>
      </section>

      <aside v-if="detail || detailLoading" class="data-map-detail-panel">
        <DataMapAssetDetail
          :detail="detail"
          :loading="detailLoading"
          :type-labels="typeLabels"
          @close="closeDetail"
          @focus="focusNode"
          @select-node="onSelectNode"
        />
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';

import DataSourceService from '@/entities/data-source/data-source.service';
import { useStore } from '@/store';
import DataMapAssetDetail from './DataMapAssetDetail.vue';
import DataMapGraph from './DataMapGraph.vue';
import DataMapSearchPanel from './DataMapSearchPanel.vue';
import DataMapService from './data-map.service';
import type {
  DataMapAssetDetail as DataMapAssetDetailType,
  DataMapGraph as DataMapGraphType,
  DataMapNode,
  DataMapOverview,
  DataMapSearchResult,
} from './data-map.service';
import { DATA_MAP_TYPE_COLORS, DATA_MAP_TYPE_LABELS } from './data-map-graph-option';

defineOptions({ name: 'DataMap' });

const route = useRoute();
const store = useStore();
const service = new DataMapService();
const dataSourceService = new DataSourceService();

const canReindex = computed(() =>
  (store.account?.authorities || []).some((authority: string) => ['ROLE_ADMIN', 'ROLE_TENANT_ADMIN'].includes(authority)),
);

const overview = ref<DataMapOverview | null>(null);
const graph = ref<DataMapGraphType>({
  scope: 'full',
  nodes: [],
  links: [],
  categories: [],
  totalNodes: 0,
  totalLinks: 0,
  truncated: false,
});
const searchResult = ref<DataMapSearchResult | null>(null);
const searchQuery = ref('');
const selectedTypes = ref<string[]>([]);
const dataSourceId = ref('');
const dataSources = ref<{ id: string; name: string }[]>([]);
const typeLabels = ref<Record<string, string>>({ ...DATA_MAP_TYPE_LABELS });
const includeColumns = ref(false);
const showLabels = ref(false);
const layout = ref<'force' | 'circular'>('force');
const scope = ref<'full' | 'subgraph'>('full');
const rootId = ref('');
const selectedNodeId = ref('');
const searchPanelVisible = ref(true);
const graphRef = ref<any>(null);

const graphLoading = ref(false);
const graphError = ref('');
const searchLoading = ref(false);
const detailLoading = ref(false);
const rebuilding = ref(false);
const detail = ref<DataMapAssetDetailType | null>(null);

let pollTimer: ReturnType<typeof setInterval> | null = null;

const typeLabel = (type: string) => typeLabels.value[type] || DATA_MAP_TYPE_LABELS[type] || type;
const typeColor = (type: string) => DATA_MAP_TYPE_COLORS[type] || '#909399';

const topTypeCounts = computed(() => {
  const counts = overview.value?.typeCounts || {};
  return Object.fromEntries(Object.entries(counts).slice(0, 8));
});

const statusLabel = computed(() => {
  const status = overview.value?.indexStatus?.status;
  if (!status) {
    return '索引未构建';
  }
  const labels: Record<string, string> = {
    EMPTY: '索引未构建',
    RUNNING: '索引构建中',
    READY: '索引已就绪',
    STALE: '索引待刷新',
    FAILED: '索引构建失败',
  };
  return labels[status] || status;
});

const statusTagType = computed(() => {
  const status = overview.value?.indexStatus?.status;
  if (status === 'READY') {
    return 'success';
  }
  if (status === 'RUNNING') {
    return 'warning';
  }
  if (status === 'FAILED') {
    return 'danger';
  }
  return 'info';
});

const loadOverview = async () => {
  try {
    overview.value = await service.overview();
    if (overview.value.indexStatus?.status === 'RUNNING') {
      startPolling();
    } else if (overview.value.indexStatus?.status === 'READY' && graph.value.nodes.length === 0) {
      await loadGraph();
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.detail || error?.response?.data?.message || error?.message || '加载数据地图失败');
  }
};

const loadGraph = async () => {
  graphLoading.value = true;
  graphError.value = '';
  try {
    graph.value = await service.graph({
      scope: scope.value,
      rootId: scope.value === 'subgraph' ? rootId.value : undefined,
      direction: 'both',
      depth: 2,
      types: selectedTypes.value.join(','),
      dataSourceId: dataSourceId.value || undefined,
      includeColumns: includeColumns.value,
    });
  } catch (error: any) {
    graphError.value = error?.response?.data?.detail || error?.response?.data?.message || error?.message || '关系图加载失败';
  } finally {
    graphLoading.value = false;
  }
};

const loadSearch = async () => {
  searchLoading.value = true;
  try {
    searchResult.value = await service.search({
      q: searchQuery.value,
      types: selectedTypes.value.join(','),
      dataSourceId: dataSourceId.value || undefined,
      limit: 100,
    });
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.detail || error?.response?.data?.message || error?.message || '检索失败');
  } finally {
    searchLoading.value = false;
  }
};

const reloadGraphAndSearch = async () => {
  await Promise.all([loadGraph(), loadSearch()]);
};

const onIncludeColumnsChange = async () => {
  // 等待 v-model 写入后再按最新开关值重新拉取关系图。
  await nextTick();
  await loadGraph();
};

const onTypesChange = async (types: string[]) => {
  selectedTypes.value = types;
  await reloadGraphAndSearch();
};

const loadDataSources = async () => {
  try {
    const response: any = await dataSourceService.retrieve({ page: 0, size: 500, sort: ['name,asc'] });
    // DataSourceService.retrieve 返回的是 AxiosResponse，不是直接的数据数组。
    const rows = Array.isArray(response?.data) ? response.data : [];
    dataSources.value = rows.map((item: any) => ({ id: item.id, name: item.name }));
  } catch {
    dataSources.value = [];
  }
};

const loadTypes = async () => {
  try {
    typeLabels.value = { ...DATA_MAP_TYPE_LABELS, ...(await service.types()) };
  } catch {
    typeLabels.value = { ...DATA_MAP_TYPE_LABELS };
  }
};

const onSelectNode = async (node: DataMapNode) => {
  selectedNodeId.value = node.id;
  detailLoading.value = true;
  try {
    detail.value = await service.detail(node.id);
    graphRef.value?.focusNode?.(node.id);
    syncRouteQuery();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.detail || error?.response?.data?.message || error?.message || '加载资产详情失败');
  } finally {
    detailLoading.value = false;
  }
};

const closeDetail = () => {
  detail.value = null;
  selectedNodeId.value = '';
  syncRouteQuery();
};

const focusSelected = () => {
  if (selectedNodeId.value) {
    focusNode({ id: selectedNodeId.value } as DataMapNode);
  }
};

const focusNode = async (node: DataMapNode) => {
  if (!node?.id) {
    return;
  }
  scope.value = 'subgraph';
  rootId.value = node.id;
  selectedNodeId.value = node.id;
  await loadGraph();
  syncRouteQuery();
};

const backToFull = async () => {
  scope.value = 'full';
  rootId.value = '';
  await loadGraph();
  syncRouteQuery();
};

const reindex = async () => {
  rebuilding.value = true;
  try {
    await service.reindex();
    ElMessage.success('已提交索引刷新任务');
    await loadOverview();
    startPolling();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.detail || error?.response?.data?.message || error?.message || '刷新索引失败');
  } finally {
    rebuilding.value = false;
  }
};

const startPolling = () => {
  if (pollTimer) {
    return;
  }
  pollTimer = setInterval(async () => {
    try {
      overview.value = await service.overview();
      const status = overview.value.indexStatus?.status;
      if (status !== 'RUNNING' && status !== 'EMPTY') {
        stopPolling();
        await Promise.all([loadGraph(), loadSearch()]);
      }
    } catch {
      stopPolling();
    }
  }, 3000);
};

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
};

const syncRouteQuery = () => {
  // 直接改写地址栏，不触发 Vue Router 导航。
  // 外层 Entities 使用 route.fullPath 作为 innerKey，router.replace 会导致整个页面重新挂载。
  const params = new URLSearchParams();
  if (searchQuery.value) {
    params.set('q', searchQuery.value);
  }
  if (selectedNodeId.value) {
    params.set('node', selectedNodeId.value);
  }
  if (scope.value === 'subgraph' && rootId.value) {
    params.set('focus', rootId.value);
  }
  const query = params.toString();
  window.history.replaceState(window.history.state, '', `${route.path}${query ? `?${query}` : ''}${route.hash || ''}`);
};

onMounted(async () => {
  const q = route.query.q;
  const node = route.query.node;
  if (typeof q === 'string') {
    searchQuery.value = q;
  }
  if (typeof node === 'string' && node) {
    selectedNodeId.value = node;
  }
  await Promise.all([loadTypes(), loadDataSources(), loadOverview(), loadSearch()]);
  if (selectedNodeId.value) {
    detailLoading.value = true;
    try {
      detail.value = await service.detail(selectedNodeId.value);
    } finally {
      detailLoading.value = false;
    }
  }
  await loadGraph();
  if (selectedNodeId.value) {
    await nextTick();
    graphRef.value?.focusNode?.(selectedNodeId.value);
  }
});

onBeforeUnmount(stopPolling);
</script>

<style scoped>
.data-map-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  min-height: 620px;
  padding: 0;
  box-sizing: border-box;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.data-map-title-group {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.page-title {
  margin: 0;
  color: #303133;
}

.data-map-subtitle {
  color: #909399;
  font-size: 12px;
}

.data-map-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.data-map-status-message {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #909399;
  font-size: 12px;
}

.data-map-stats {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}

.data-map-stat-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 130px;
  padding: 8px 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
}

.data-map-stat-types {
  flex: 1;
  min-width: 0;
}

.data-map-stat-label {
  color: #909399;
  font-size: 12px;
}

.data-map-stat-value {
  color: #303133;
  font-size: 20px;
  line-height: 1.2;
}

.data-map-type-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 4px;
}

.data-map-type-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  font-size: 12px;
}

.data-map-type-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}

.data-map-layout {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 10px;
}

.data-map-search-panel {
  width: 340px;
  flex: 0 0 340px;
  min-height: 0;
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
}

.data-map-graph-panel {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
  overflow: hidden;
}

.data-map-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid #f0f2f5;
  flex-wrap: wrap;
}

.data-map-toolbar-left,
.data-map-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.data-map-source-select {
  width: 180px;
}

.data-map-layout-select {
  width: 126px;
}

.data-map-graph-wrap {
  flex: 1;
  min-height: 0;
}

.data-map-graph-error {
  margin: 10px;
}

.data-map-detail-panel {
  width: 390px;
  flex: 0 0 390px;
  min-height: 0;
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
  overflow: hidden;
}

@media (max-width: 1200px) {
  .data-map-search-panel {
    display: none;
  }

  .data-map-detail-panel {
    width: 340px;
    flex-basis: 340px;
  }
}
</style>
