<template>
  <div class="input-group">
    <!-- 输入框（带“选择”按钮），点击可打开模态框 -->
    <el-input :model-value="displayText" readonly placeholder="请选择数据源" @click="openModal">
      <template #append>
        <el-button @click="openModal">选择</el-button>
      </template>
    </el-input>

    <app-modal v-model="showModal" id="dataSourceModal" :title="modalTitle" size="xl">
      <div class="ds-selector-body">
        <!-- 搜索与类型过滤 -->
        <div class="ds-toolbar">
          <el-input v-model="searchQuery" placeholder="搜索名称 / 地址 / 类型" clearable class="ds-search">
            <template #prefix>
              <font-awesome-icon icon="search" />
            </template>
          </el-input>
          <el-select
            v-if="!props.dataSourceType"
            v-model="selectedDataSourceType"
            placeholder="全部类型"
            clearable
            class="ds-type-filter"
            @change="handleTypeChange"
          >
            <el-option v-for="option in dataSourceTypeOptions" :key="option.value" :value="option.value" :label="option.label" />
          </el-select>
          <el-tag v-else type="info" size="small" class="ds-type-tag">{{ typeLabel(props.dataSourceType) }}</el-tag>
        </div>

        <!-- 数据源表格 -->
        <el-table
          :data="pagedDataSources"
          v-loading="loading"
          max-height="44vh"
          border
          stripe
          highlight-current-row
          :row-key="row => row.id"
          :row-class-name="rowClassName"
          class="ds-table"
          @row-click="handleRowClick"
        >
          <el-table-column width="52" align="center">
            <template #header>选择</template>
            <template #default="{ row }">
              <div class="ds-radio-cell">
                <el-radio :model-value="selectedDataSource" :value="String(row.id)" @click.stop @change="() => handleRowClick(row)">
                  <span />
                </el-radio>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
          <el-table-column label="类型" width="150">
            <template #default="{ row }">
              <span class="ds-type-cell">
                <img v-if="getTypeImage(row.type)" :src="getTypeImage(row.type)" class="ds-type-icon" :alt="row.type" />
                <font-awesome-icon v-else icon="database" class="ds-type-fa-icon" />
                <span>{{ typeLabel(row.type) }}</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="地址" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.url || '-' }}</template>
          </el-table-column>
          <el-table-column label="Schema" width="220">
            <template #default="{ row }">
              <div v-if="isCurrentRow(row)" class="ds-schema-cell" @click.stop>
                <el-select
                  v-model="selectedSchema"
                  size="small"
                  placeholder="请选择 Schema"
                  filterable
                  :loading="schemaLoading"
                  :disabled="!schemaLoading && schemas.length === 0"
                >
                  <el-option v-for="item in schemas" :key="item" :value="item" :label="item" />
                </el-select>
              </div>
              <span v-else class="ds-schema-default">{{ row.schemaName || '-' }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="无匹配的数据源" :image-size="70" />
          </template>
        </el-table>

        <div class="ds-pagination">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="filteredDataSources.length"
            layout="total, sizes, prev, pager, next"
            size="small"
            background
          />
        </div>
      </div>

      <template #modal-footer>
        <div class="ds-footer">
          <span class="ds-selected-hint">{{ selectionSummary }}</span>
          <div class="ds-footer-actions">
            <el-button @click="showModal = false">取消</el-button>
            <el-button type="primary" :disabled="!canConfirm" @click="confirmSelection">确认</el-button>
          </div>
        </div>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';

import DataSourceService from '@/entities/data-source/data-source.service';
import { findDbType } from '@/entities/data-source/db-types';
import type { IDataSource } from '@/shared/model/data-source.model';

const MAX_DATA_SOURCES = 1000;

const props = defineProps<{
  datasourceId?: string | null;
  schema?: string | null;
  dataSourceType?: string | null; // 数据源类型过滤参数
}>();

const emit = defineEmits(['selected']);

const normalizeType = (type?: string | null): string => (type || '').trim().toUpperCase();

const typeLabel = (type?: string | null): string => {
  const dbType = findDbType(type);
  return dbType?.displayName ?? (type ? type.toUpperCase() : '');
};

const getTypeImage = (type?: string | null): string | undefined => findDbType(type)?.image;

const dataSourceService = new DataSourceService();
const showModal = ref(false);
const dataSources = ref<IDataSource[]>([]);
const schemas = ref<string[]>([]);
const selectedDataSource = ref<string | null>(props.datasourceId || null);
const selectedSchema = ref<string | null>(props.schema || null);
const selectedDataSourceType = ref<string | null>(props.dataSourceType ? normalizeType(props.dataSourceType) : null);
const searchQuery = ref('');
const loading = ref(false);
const schemaLoading = ref(false);
let schemaRequestId = 0;

// 计算属性：模态框标题
const modalTitle = computed(() => (props.dataSourceType ? `选择 ${typeLabel(props.dataSourceType)} 数据源` : '选择数据源'));

// 计算属性：类型筛选项（按实际数据源类型去重）
const dataSourceTypeOptions = computed(() => {
  const options = new Map<string, string>();
  if (props.dataSourceType) {
    options.set(normalizeType(props.dataSourceType), typeLabel(props.dataSourceType));
  }
  dataSources.value.forEach(source => {
    const value = normalizeType(source.type);
    if (value && !options.has(value)) {
      options.set(value, typeLabel(source.type));
    }
  });
  return Array.from(options, ([value, label]) => ({ value, label }));
});

// 计算属性：按类型与关键字过滤后的数据源
const filteredDataSources = computed(() => {
  const typeFilter = normalizeType(selectedDataSourceType.value);
  const keyword = searchQuery.value.trim().toLowerCase();

  return dataSources.value.filter(source => {
    if (typeFilter && normalizeType(source.type) !== typeFilter) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [source.name, source.description, source.url, source.hostname, source.port, source.schemaName, typeLabel(source.type)].some(
      value =>
        String(value ?? '')
          .toLowerCase()
          .includes(keyword),
    );
  });
});

// 分页（前端分页，配合本地搜索与类型过滤）
const currentPage = ref(1);
const pageSize = ref(10);

const pagedDataSources = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredDataSources.value.slice(start, start + pageSize.value);
});

const selectedSource = computed(() => dataSources.value.find(source => String(source.id) === String(selectedDataSource.value)) || null);

// 计算属性：触发框展示文案
const displayText = computed(() => {
  if (!selectedDataSource.value) {
    return '';
  }
  const name = selectedSource.value?.name ?? String(selectedDataSource.value);
  return selectedSchema.value ? `${name} - ${selectedSchema.value}` : name;
});

const selectionSummary = computed(() => {
  if (!selectedSource.value) {
    return '';
  }
  return selectedSchema.value ? `已选：${selectedSource.value.name} / ${selectedSchema.value}` : `已选：${selectedSource.value.name}`;
});

const canConfirm = computed(() => Boolean(selectedDataSource.value && selectedSchema.value));

const isCurrentRow = (row: IDataSource) => String(row.id) === String(selectedDataSource.value);

const rowClassName = ({ row }: { row: IDataSource }) => (isCurrentRow(row) ? 'ds-selected-row' : '');

// 打开弹窗或数据加载完成后，定位到当前已选数据源所在页
const focusSelectedSource = () => {
  const index = filteredDataSources.value.findIndex(source => String(source.id) === String(selectedDataSource.value));
  if (index >= 0) {
    currentPage.value = Math.floor(index / pageSize.value) + 1;
  }
};

const openModal = () => {
  showModal.value = true;
  focusSelectedSource();
  if (!dataSources.value.length && !loading.value) {
    fetchDataSources();
  }
};

const fetchDataSources = async () => {
  loading.value = true;
  try {
    const res = await dataSourceService.retrieve({
      page: 0,
      size: MAX_DATA_SOURCES,
      sort: ['type,asc', 'name,asc'],
    });
    dataSources.value = Array.isArray(res.data) ? res.data : (res.data?.content ?? []);
    focusSelectedSource();

    // 如果传入了 datasourceId，加载对应的 schemas
    if (props.datasourceId) {
      await fetchSchemas(props.datasourceId, true);
    }
  } catch (error) {
    console.error('获取数据源失败', error);
    ElMessage.error('获取数据源失败');
  } finally {
    loading.value = false;
  }
};

const fetchSchemas = async (dataSourceId: string | null = selectedDataSource.value, preferPropSchema = false) => {
  const requestId = ++schemaRequestId;

  if (!dataSourceId) {
    schemas.value = [];
    selectedSchema.value = null;
    schemaLoading.value = false;
    return;
  }

  schemaLoading.value = true;
  try {
    const res = await dataSourceService.getSchemas(String(dataSourceId));
    if (requestId !== schemaRequestId) {
      return;
    }
    schemas.value = Array.isArray(res.data) ? res.data : [];

    const preferredSchema = preferPropSchema && props.schema && schemas.value.includes(props.schema) ? props.schema : null;
    if (preferredSchema) {
      selectedSchema.value = preferredSchema;
    } else if (selectedSchema.value && schemas.value.includes(selectedSchema.value)) {
      // 保留仍然有效的当前选择
    } else {
      // 只有一个 Schema 时自动选中，减少一次点击
      selectedSchema.value = schemas.value.length === 1 ? schemas.value[0] : null;
    }
  } catch (error) {
    if (requestId !== schemaRequestId) {
      return;
    }
    schemas.value = [];
    selectedSchema.value = null;
    console.error('获取 Schema 失败', error);
    ElMessage.warning('获取 Schema 失败');
  } finally {
    if (requestId === schemaRequestId) {
      schemaLoading.value = false;
    }
  }
};

const handleTypeChange = () => {
  // 仅当已选数据源不再符合筛选条件时才清空，避免误清空
  const stillVisible = filteredDataSources.value.some(source => String(source.id) === String(selectedDataSource.value));
  if (selectedDataSource.value && !stillVisible) {
    selectedDataSource.value = null;
    selectedSchema.value = null;
    schemas.value = [];
  }
};

const handleRowClick = async (row: IDataSource) => {
  if (!row?.id) {
    return;
  }

  const rowId = String(row.id);
  const isSameRow = rowId === String(selectedDataSource.value);
  selectedDataSource.value = rowId;

  if (isSameRow && (schemas.value.length || schemaLoading.value)) {
    return;
  }

  selectedSchema.value = null;
  schemas.value = [];
  await fetchSchemas(rowId);
};

const confirmSelection = () => {
  if (!canConfirm.value) {
    return;
  }
  emit('selected', {
    dataSourceId: selectedDataSource.value,
    schema: selectedSchema.value,
  });
  showModal.value = false;
};

watch(
  () => [props.datasourceId, props.schema] as const,
  ([newId, newSchema], [oldId, oldSchema]) => {
    if (newId === oldId && newSchema === oldSchema) {
      return;
    }
    // 组件内部确认后父组件会回写同样的值，此时无需重复加载 Schema
    if ((newId || null) === selectedDataSource.value && (newSchema || null) === selectedSchema.value) {
      return;
    }
    selectedDataSource.value = newId || null;
    selectedSchema.value = newSchema || null;
    if (newId) {
      fetchSchemas(newId, true);
    } else {
      schemas.value = [];
    }
  },
);

watch(
  () => props.dataSourceType,
  value => {
    selectedDataSourceType.value = value ? normalizeType(value) : null;
    handleTypeChange();
  },
);

// 搜索、类型过滤或每页条数变化时回到第一页
watch([searchQuery, selectedDataSourceType, pageSize], () => {
  currentPage.value = 1;
});

// 数据量变化导致当前页超出范围时，自动回到最后一页
watch(
  () => filteredDataSources.value.length,
  length => {
    const maxPage = Math.max(1, Math.ceil(length / pageSize.value));
    if (currentPage.value > maxPage) {
      currentPage.value = maxPage;
    }
  },
);

onMounted(fetchDataSources);
</script>

<style scoped>
.input-group {
  margin-bottom: 1rem;
}

.ds-selector-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ds-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ds-search {
  max-width: 460px;
}

.ds-type-filter {
  width: 180px;
  flex: none;
}

.ds-type-tag {
  flex: none;
}

.ds-table {
  width: 100%;
}

.ds-table :deep(.el-table__row) {
  cursor: pointer;
}

/* 单选框固定行高并垂直居中，避免 Element Plus 默认 32px 高度造成偏移 */
.ds-radio-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 23px;
  line-height: 1;
}

.ds-radio-cell :deep(.el-radio) {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 23px;
  margin: 0;
  padding: 0;
  line-height: 1;
}

.ds-radio-cell :deep(.el-radio__input) {
  display: flex;
  align-items: center;
  height: 14px;
}

.ds-radio-cell :deep(.el-radio__label) {
  display: none;
}

.ds-table :deep(.ds-selected-row) {
  --el-table-tr-bg-color: var(--el-color-primary-light-9);
}

.ds-type-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.ds-type-icon {
  width: 16px;
  height: 16px;
  object-fit: contain;
}

.ds-type-fa-icon {
  width: 16px;
  color: var(--el-text-color-secondary, #909399);
}

.ds-pagination {
  display: flex;
  justify-content: flex-end;
}

.ds-schema-cell,
.ds-schema-cell :deep(.el-select) {
  width: 100%;
}

.ds-schema-default {
  color: var(--el-text-color-secondary, #909399);
}

.ds-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ds-selected-hint {
  overflow: hidden;
  color: var(--el-color-primary, #409eff);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ds-footer-actions {
  display: flex;
  flex: none;
  gap: 8px;
}
</style>
