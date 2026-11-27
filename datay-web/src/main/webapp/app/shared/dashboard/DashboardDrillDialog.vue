<template>
  <AppModal
    :model-value="modelValue"
    size="xl"
    :class="theme === 'dark' ? 'dashboard-theme-dark' : 'dashboard-theme-light'"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <template #modal-title>
      <div class="drill-dialog-title">
        <span class="drill-dialog-title-text">明细数据{{ widget?.title ? ` · ${widget.title}` : '' }}</span>
        <span class="drill-dialog-subtitle">已去除图表级分组聚合，仅保留点击条件</span>
      </div>
    </template>

    <div class="drill-dialog">
      <div v-if="conditionTags.length" class="drill-conditions">
        <span class="drill-conditions-label">筛选条件</span>
        <el-tag v-for="tag in conditionTags" :key="tag.field" size="small" type="info" class="drill-condition-tag">
          {{ tag.label }}：{{ tag.value }}
        </el-tag>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" class="drill-error" />

      <div v-loading="loading" class="drill-table-wrap">
        <el-table v-if="columns.length" :data="result?.rows ?? []" height="440" size="small" border stripe>
          <el-table-column
            v-for="column in columns"
            :key="column"
            :prop="column"
            :label="labelFor(column)"
            :min-width="120"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ formatCell(row[column], column) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else-if="!loading && !error" description="没有匹配的明细数据" :image-size="72" />
      </div>

      <div v-if="result?.truncated" class="drill-truncated">结果已达到当前页行数上限，可继续翻页查看更多。</div>
    </div>

    <template #modal-footer>
      <div class="drill-footer">
        <span class="drill-page-info">第 {{ page }} 页 · 每页 {{ pageSize }} 行</span>
        <div class="drill-footer-actions">
          <el-button size="small" :disabled="!canPrev" @click="changePage(-1)">上一页</el-button>
          <el-button size="small" :disabled="!canNext" @click="changePage(1)">下一页</el-button>
          <el-button size="small" type="primary" @click="emit('update:modelValue', false)">关闭</el-button>
        </div>
      </div>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import AppModal from '@/shared/ui/AppModal.vue';

import { drillFieldLabel } from './drill-utils';
import type { DashboardTheme, DashboardWidget, DatasetResult, DrillDimension, WidgetDrillLoader } from './types';

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    widget?: DashboardWidget;
    dimensions: DrillDimension[];
    loader?: WidgetDrillLoader;
    /** 图表当前结果，用于明细加载前展示字段标签。 */
    chartResult?: DatasetResult;
    pageSize?: number;
    /** 看板主题；弹窗会套用对应主题变量，避免深色看板出现浅色弹窗。 */
    theme?: DashboardTheme;
  }>(),
  {
    widget: undefined,
    loader: undefined,
    chartResult: undefined,
    pageSize: 50,
    theme: 'light',
  },
);

const emit = defineEmits<{
  'update:modelValue': [boolean];
}>();

const page = ref(1);
const result = ref<DatasetResult | undefined>();
const loading = ref(false);
const error = ref('');
let sequence = 0;

const columns = computed(() => result.value?.columns ?? []);
const labels = computed<Record<string, string>>(() => result.value?.columnLabels ?? props.chartResult?.columnLabels ?? {});
const numericColumns = computed(() => new Set(result.value?.numericColumns ?? []));

const conditionTags = computed(() =>
  props.dimensions.map(dimension => ({
    field: dimension.field,
    label: drillFieldLabel(props.widget, dimension.field, labels.value),
    value: dimension.values.map(value => String(value)).join('、'),
  })),
);

const canPrev = computed(() => page.value > 1 && !loading.value);
const canNext = computed(() => (result.value?.rows?.length ?? 0) >= props.pageSize && !loading.value);

const labelFor = (field: string): string => labels.value[field] || field;

const formatCell = (value: any, field: string): string => {
  if (value === null || value === undefined) {
    return '';
  }
  if (typeof value === 'number' && numericColumns.value.has(field)) {
    return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value);
  }
  return String(value);
};

const loadPage = async () => {
  if (!props.loader || !props.widget?.datasetId) {
    return;
  }
  const current = ++sequence;
  loading.value = true;
  error.value = '';
  try {
    const configuredSort = props.widget.encode?.sortField || undefined;
    const data = await props.loader({
      widgetId: props.widget.id,
      datasetId: props.widget.datasetId,
      dimensions: props.dimensions,
      page: page.value,
      pageSize: props.pageSize,
      sortField: configuredSort || props.dimensions[0]?.field,
      sortOrder: configuredSort ? props.widget.encode?.sortOrder : 'asc',
    });
    if (current !== sequence) {
      return;
    }
    result.value = data;
  } catch (err: any) {
    if (current !== sequence) {
      return;
    }
    error.value =
      err?.response?.data?.errorMessage ||
      err?.response?.data?.detail ||
      err?.response?.data?.message ||
      err?.response?.data?.title ||
      err?.message ||
      '明细查询失败';
  } finally {
    if (current === sequence) {
      loading.value = false;
    }
  }
};

const changePage = (delta: number) => {
  const next = page.value + delta;
  if (next < 1) {
    return;
  }
  page.value = next;
  void loadPage();
};

watch(
  () => props.modelValue,
  visible => {
    if (visible) {
      page.value = 1;
      result.value = undefined;
      error.value = '';
      void loadPage();
    }
  },
  { immediate: true },
);

watch(
  () => props.dimensions,
  () => {
    if (props.modelValue) {
      page.value = 1;
      result.value = undefined;
      error.value = '';
      void loadPage();
    }
  },
);
</script>

<style scoped>
.drill-dialog-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.drill-dialog-title-text {
  color: var(--el-text-color-primary, #303133);
  font-size: 16px;
  font-weight: 600;
}

.drill-dialog-subtitle {
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  font-weight: 400;
}

.drill-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.drill-conditions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.drill-conditions-label {
  color: var(--el-text-color-regular, #606266);
  font-size: 12px;
}

.drill-condition-tag {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drill-error {
  margin: 0;
}

.drill-table-wrap {
  min-height: 220px;
}

.drill-truncated {
  color: var(--el-color-warning, #e6a23c);
  font-size: 12px;
}

.drill-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}

.drill-page-info {
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
}

.drill-footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
