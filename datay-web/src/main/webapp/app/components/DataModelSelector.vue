<template>
  <div class="input-group">
    <!-- 输入框（带“选择”按钮），点击可打开模态框 -->
    <el-input :model-value="displayText" readonly placeholder="请选择数据模型" @click="openModal">
      <template #append>
        <el-button @click="openModal">选择</el-button>
      </template>
    </el-input>

    <app-modal v-model="showModal" id="dataModelModal" title="选择数据模型" size="xl">
      <div class="dm-selector-body">
        <!-- 搜索与类型过滤 -->
        <div class="dm-toolbar">
          <el-input v-model="searchQuery" placeholder="搜索名称 / 编码 / 目标表" clearable class="dm-search">
            <template #prefix>
              <font-awesome-icon icon="search" />
            </template>
          </el-input>
          <el-select v-model="selectedModelType" placeholder="全部类型" clearable class="dm-type-filter">
            <el-option v-for="option in modelTypeOptions" :key="option.value" :value="option.value" :label="option.label" />
          </el-select>
        </div>

        <!-- 数据模型表格 -->
        <el-table
          :data="pagedModels"
          v-loading="loading"
          max-height="44vh"
          border
          stripe
          highlight-current-row
          :row-key="row => row.id"
          :row-class-name="rowClassName"
          class="dm-table"
          @row-click="handleRowClick"
        >
          <el-table-column width="52" align="center">
            <template #header>选择</template>
            <template #default="{ row }">
              <div class="dm-radio-cell">
                <el-radio :model-value="selectedModelId" :value="String(row.id)" @click.stop @change="() => handleRowClick(row)">
                  <span />
                </el-radio>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="模型名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="code" label="模型编码" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.code || '-' }}</template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag :type="modelTypeTagType(row.modelType)" size="small">{{ modelTypeLabel(row.modelType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Schema" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.schemaName || '-' }}</template>
          </el-table-column>
          <el-table-column label="目标表" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.tableName || '-' }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="无匹配的数据模型" :image-size="70" />
          </template>
        </el-table>

        <div class="dm-pagination">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="filteredModels.length"
            layout="total, sizes, prev, pager, next"
            size="small"
            background
          />
        </div>
      </div>

      <template #modal-footer>
        <div class="dm-footer">
          <span class="dm-selected-hint">{{ selectionSummary }}</span>
          <div class="dm-footer-actions">
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

import DataModelService from '@/entities/data-model/data-model.service';
import type { IDataModel } from '@/shared/model/data-model.model';

const MODEL_TYPE_LABELS: Record<string, string> = {
  ODS: 'ODS 贴源层',
  DWD: 'DWD 明细层',
  DWS: 'DWS 汇总层',
  ADS: 'ADS 应用层',
  DIMENSION: '维度表',
};

const MODEL_TYPE_TAG_TYPES: Record<string, string> = {
  ODS: 'info',
  DWD: '',
  DWS: 'warning',
  ADS: 'danger',
  DIMENSION: 'primary',
};

const props = defineProps<{
  modelId?: string | null;
}>();

const emit = defineEmits(['selected']);

const dataModelService = new DataModelService();
const showModal = ref(false);
const models = ref<IDataModel[]>([]);
const selectedModelId = ref<string | null>(props.modelId || null);
const selectedModelType = ref<string | null>(null);
const searchQuery = ref('');
const loading = ref(false);

const normalizeType = (type?: string | null): string => (type || '').trim().toUpperCase();

const modelTypeLabel = (type?: string | null): string => {
  const value = normalizeType(type);
  return MODEL_TYPE_LABELS[value] ?? (value || '-');
};

const modelTypeTagType = (type?: string | null): string => MODEL_TYPE_TAG_TYPES[normalizeType(type)] ?? 'info';

// 计算属性：类型筛选项（按实际模型类型去重）
const modelTypeOptions = computed(() => {
  const options = new Map<string, string>();
  models.value.forEach(model => {
    const value = normalizeType(model.modelType);
    if (value && !options.has(value)) {
      options.set(value, modelTypeLabel(value));
    }
  });
  return Array.from(options, ([value, label]) => ({ value, label }));
});

// 计算属性：按类型与关键字过滤后的数据模型
const filteredModels = computed(() => {
  const typeFilter = normalizeType(selectedModelType.value);
  const keyword = searchQuery.value.trim().toLowerCase();

  return models.value.filter(model => {
    if (typeFilter && normalizeType(model.modelType) !== typeFilter) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [
      model.name,
      model.code,
      model.description,
      model.modelType,
      model.schemaName,
      model.tableName,
      modelTypeLabel(model.modelType),
    ].some(value =>
      String(value ?? '')
        .toLowerCase()
        .includes(keyword),
    );
  });
});

// 分页（前端分页，配合本地搜索与类型过滤）
const currentPage = ref(1);
const pageSize = ref(10);

const pagedModels = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredModels.value.slice(start, start + pageSize.value);
});

const selectedModel = computed(() => models.value.find(model => String(model.id) === String(selectedModelId.value)) || null);

// 计算属性：触发框展示文案
const displayText = computed(() => {
  if (!selectedModelId.value) {
    return '';
  }
  const model = selectedModel.value;
  if (!model) {
    return String(selectedModelId.value);
  }
  return model.code ? `${model.name}（${model.code}）` : String(model.name ?? selectedModelId.value);
});

const selectionSummary = computed(() => {
  const model = selectedModel.value;
  if (!model) {
    return '';
  }
  const target = model.tableName ? `${model.schemaName ? `${model.schemaName}.` : ''}${model.tableName}` : '未绑定目标表';
  return `已选：${model.name} / ${target}`;
});

const canConfirm = computed(() => Boolean(selectedModel.value));

const rowClassName = ({ row }: { row: IDataModel }) => (String(row.id) === String(selectedModelId.value) ? 'dm-selected-row' : '');

// 打开弹窗或数据加载完成后，定位到当前已选模型所在页
const focusSelectedModel = () => {
  const index = filteredModels.value.findIndex(model => String(model.id) === String(selectedModelId.value));
  if (index >= 0) {
    currentPage.value = Math.floor(index / pageSize.value) + 1;
  }
};

const openModal = () => {
  showModal.value = true;
  focusSelectedModel();
  if (!models.value.length && !loading.value) {
    fetchModels();
  }
};

const fetchModels = async () => {
  loading.value = true;
  try {
    const res = await dataModelService.retrieve();
    const data = res?.data;
    models.value = Array.isArray(data) ? data : (data?.content ?? []);
    focusSelectedModel();
  } catch (error) {
    console.error('获取数据模型失败', error);
    ElMessage.error('获取数据模型失败');
  } finally {
    loading.value = false;
  }
};

const handleRowClick = (row: IDataModel) => {
  if (!row?.id) {
    return;
  }
  selectedModelId.value = String(row.id);
};

const confirmSelection = () => {
  const model = selectedModel.value;
  if (!model) {
    return;
  }
  showModal.value = false;
  emit('selected', model);
};

watch(
  () => props.modelId,
  value => {
    if ((value || null) !== selectedModelId.value) {
      selectedModelId.value = value || null;
      focusSelectedModel();
    }
  },
);

// 搜索、类型过滤或每页条数变化时回到第一页
watch([searchQuery, selectedModelType, pageSize], () => {
  currentPage.value = 1;
});

// 数据量变化导致当前页超出范围时，自动回到最后一页
watch(
  () => filteredModels.value.length,
  length => {
    const maxPage = Math.max(1, Math.ceil(length / pageSize.value));
    if (currentPage.value > maxPage) {
      currentPage.value = maxPage;
    }
  },
);

onMounted(() => {
  fetchModels();
});
</script>

<style scoped>
.input-group {
  margin-bottom: 1rem;
}

.dm-selector-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dm-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dm-search {
  max-width: 460px;
}

.dm-type-filter {
  width: 180px;
  flex: none;
}

.dm-table {
  width: 100%;
}

.dm-table :deep(.el-table__row) {
  cursor: pointer;
}

/* 单选框固定行高并垂直居中，避免 Element Plus 默认 32px 高度造成偏移 */
.dm-radio-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 23px;
  line-height: 1;
}

.dm-radio-cell :deep(.el-radio) {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 23px;
  margin: 0;
  padding: 0;
  line-height: 1;
}

.dm-radio-cell :deep(.el-radio__input) {
  display: flex;
  align-items: center;
  height: 14px;
}

.dm-radio-cell :deep(.el-radio__label) {
  display: none;
}

.dm-table :deep(.dm-selected-row) {
  --el-table-tr-bg-color: var(--el-color-primary-light-9);
}

.dm-pagination {
  display: flex;
  justify-content: flex-end;
}

.dm-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dm-selected-hint {
  overflow: hidden;
  color: var(--el-color-primary, #409eff);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dm-footer-actions {
  display: flex;
  flex: none;
  gap: 8px;
}
</style>
