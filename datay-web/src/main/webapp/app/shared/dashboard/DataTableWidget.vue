<template>
  <div class="data-table-widget">
    <el-table :data="pagedRows" height="100%" size="small" border stripe :cell-style="cellStyleForRow">
      <el-table-column
        v-for="column in tableColumns"
        :key="column.field"
        :prop="column.field"
        :label="column.label"
        :min-width="column.width ?? 120"
        show-overflow-tooltip
      >
        <template #default="{ row }">{{ formatCell(row[column.field], column.format) }}</template>
      </el-table-column>
    </el-table>
    <div v-if="serverPaged" class="data-table-pagination">
      <el-button size="small" :disabled="serverPage <= 1 || serverLoading" @click="goServerPage(-1)">上一页</el-button>
      <span class="data-table-page-info">第 {{ serverPage }} 页</span>
      <el-button size="small" :disabled="rows.length < pageSize || serverLoading" @click="goServerPage(1)">下一页</el-button>
    </div>
    <div v-else-if="pageSize > 0" class="data-table-pagination">
      <el-pagination
        :current-page="page"
        :page-size="pageSize"
        :total="rows.length"
        layout="total, prev, pager, next"
        small
        @current-change="value => (page = value)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { formatRuleStyle, resolveFormatRule } from './format-rules';
import { applyEncodeRows } from './spec-to-option';
import type { DashboardWidget, DatasetResult, TablePageLoader, WidgetFormat } from './types';

const props = defineProps<{
  widget: DashboardWidget;
  result?: DatasetResult;
  serverPaged?: boolean;
  pageLoader?: TablePageLoader;
  pageResetKey?: number;
}>();

type TableColumn = {
  field: string;
  label: string;
  width?: number;
  format?: WidgetFormat;
};

const page = ref(1);
const serverPage = ref(1);
const serverLoading = ref(false);
const pageSize = computed(() => props.widget.encode?.pageSize ?? 0);

const labelFor = (field: string): string => props.result?.columnLabels?.[field] || field;

const tableColumns = computed<TableColumn[]>(() => {
  const configured = props.widget.encode?.columns ?? [];
  if (configured.length > 0) {
    return configured.map(column => ({
      field: column.field,
      label: column.label || labelFor(column.field),
      width: column.width,
      format: column.format,
    }));
  }
  return (props.result?.columns ?? []).map(field => ({
    field,
    label: labelFor(field),
    width: 120,
  }));
});

const rows = computed(() => applyEncodeRows(props.widget.encode, props.result?.rows ?? []));
const pagedRows = computed(() => {
  if (props.serverPaged) {
    return rows.value;
  }
  if (pageSize.value <= 0) {
    return rows.value;
  }
  const start = (page.value - 1) * pageSize.value;
  return rows.value.slice(start, start + pageSize.value);
});

watch(
  () => [props.widget.id, pageSize.value, props.pageResetKey],
  () => {
    page.value = 1;
    serverPage.value = 1;
  },
);

watch(
  () => props.result?.rows?.length,
  () => {
    if (!props.serverPaged) {
      page.value = 1;
    }
  },
);

const goServerPage = async (delta: number) => {
  if (!props.pageLoader) {
    return;
  }
  const next = serverPage.value + delta;
  if (next < 1) {
    return;
  }
  serverLoading.value = true;
  try {
    await props.pageLoader(next);
    serverPage.value = next;
  } finally {
    serverLoading.value = false;
  }
};

const cellStyle = (value: any, field: string): Record<string, string | number> =>
  formatRuleStyle(resolveFormatRule(props.widget.formatRules, field, value));

const cellStyleForRow = ({ row, column }: { row: Record<string, any>; column: { property?: string } }) => {
  const field = column.property;
  return field ? cellStyle(row[field], field) : {};
};

const formatCell = (value: any, format?: WidgetFormat): string => {
  if (value === null || value === undefined) {
    return '';
  }
  if (!format) {
    return String(value);
  }
  const numeric = typeof value === 'number' ? value : Number(value);
  const formatted = Number.isFinite(numeric)
    ? new Intl.NumberFormat(undefined, {
        minimumFractionDigits: format.precision,
        maximumFractionDigits: format.precision ?? (Number.isInteger(numeric) ? 0 : 2),
      }).format(numeric)
    : String(value);
  return `${format.prefix ?? ''}${formatted}${format.unit ?? ''}${format.suffix ?? ''}`;
};
</script>

<style scoped>
.data-table-widget {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.data-table-widget :deep(.el-table) {
  flex: 1;
  min-height: 0;
}

.data-table-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
}

.data-table-page-info {
  color: #606266;
  font-size: 12px;
}
</style>
