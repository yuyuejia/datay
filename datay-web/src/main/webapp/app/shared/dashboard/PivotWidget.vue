<template>
  <div class="pivot-widget">
    <div v-if="!table || table.rows.length === 0" class="pivot-widget-empty">暂无可透视的数据</div>
    <div v-else class="pivot-widget-scroll">
      <table class="pivot-widget-table">
        <thead>
          <tr>
            <th class="pivot-widget-row-header">{{ table.rowLabel }}</th>
            <th v-for="column in table.columns" :key="column.key" :class="{ 'is-measure': column.isMeasure }">
              {{ column.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in table.rows" :key="row.key">
            <th class="pivot-widget-row-header">{{ row.label }}</th>
            <td
              v-for="column in table.columns"
              :key="column.key"
              :class="{ 'is-measure': column.isMeasure }"
              :style="cellStyle(column, row.values[column.key])"
            >
              {{ formatCell(row.values[column.key], column.format) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import { formatRuleStyle, resolveFormatRule } from './format-rules';
import { applyEncodeRows } from './spec-to-option';
import type { Aggregation, DashboardWidget, DatasetResult, WidgetFormat } from './types';

const props = defineProps<{
  widget: DashboardWidget;
  result?: DatasetResult;
}>();

type PivotColumn = {
  key: string;
  label: string;
  columnKey: string;
  measureField: string;
  format?: WidgetFormat;
  isMeasure: boolean;
};

type PivotRow = {
  key: string;
  label: string;
  values: Record<string, number | null>;
};

type PivotTable = {
  rowLabel: string;
  columns: PivotColumn[];
  rows: PivotRow[];
};

const labelFor = (field: string): string => props.result?.columnLabels?.[field] || field;

const aggregateValues = (values: number[], agg: Aggregation): number | null => {
  if (values.length === 0) {
    return null;
  }
  switch (agg) {
    case 'avg':
      return values.reduce((sum, value) => sum + value, 0) / values.length;
    case 'min':
      return Math.min(...values);
    case 'max':
      return Math.max(...values);
    case 'count':
      return values.length;
    case 'none':
      return values[0];
    default:
      return values.reduce((sum, value) => sum + value, 0);
  }
};

const toNumber = (value: any): number | null => {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
};

const table = computed<PivotTable | null>(() => {
  const encode = props.widget.encode;
  const source = applyEncodeRows(encode, props.result?.rows ?? []);
  const rowDimensions = (encode?.dimensions ?? []).filter(dimension => dimension.role === 'row');
  const columnDimension = (encode?.dimensions ?? []).find(dimension => dimension.role === 'column');
  const measures = encode?.measures ?? [];
  if (rowDimensions.length === 0 || measures.length === 0 || source.length === 0) {
    return null;
  }
  const rowFields = rowDimensions.map(dimension => dimension.field);
  const columnField = columnDimension?.field;
  const rowLabel = rowDimensions.map(dimension => dimension.name || labelFor(dimension.field)).join(' / ');
  const rowKeyOf = (row: Record<string, any>): string => rowFields.map(field => String(row[field] ?? '')).join(' / ');
  const columnKeyOf = (row: Record<string, any>): string => (columnField ? String(row[columnField] ?? '') : '');
  const rowKeys = Array.from(new Set(source.map(rowKeyOf)));
  const columnKeys = columnField ? Array.from(new Set(source.map(columnKeyOf))) : [''];

  const columns: PivotColumn[] = columnKeys.flatMap(columnKey =>
    measures.map(measure => {
      const measureLabel = measure.name || labelFor(measure.field);
      return {
        key: `${columnKey}\u0000${measure.field}`,
        label: columnField ? (measures.length > 1 ? `${columnKey} · ${measureLabel}` : columnKey) : measureLabel,
        columnKey,
        measureField: measure.field,
        format: measure.format,
        isMeasure: true,
      };
    }),
  );

  const grouped = new Map<string, number[]>();
  for (const sourceRow of source) {
    const rowKey = rowKeyOf(sourceRow);
    const columnKey = columnKeyOf(sourceRow);
    for (const measure of measures) {
      const value = toNumber(sourceRow[measure.field]);
      if (value === null) {
        continue;
      }
      const key = `${rowKey}\u0000${columnKey}\u0000${measure.field}`;
      const values = grouped.get(key);
      if (values) {
        values.push(value);
      } else {
        grouped.set(key, [value]);
      }
    }
  }

  const measureAgg = new Map(measures.map(measure => [measure.field, measure.agg]));
  const alreadyAggregated = props.result?.aggregated === true;
  const rows: PivotRow[] = rowKeys.map(rowKey => {
    const values: Record<string, number | null> = {};
    for (const column of columns) {
      const key = `${rowKey}\u0000${column.columnKey}\u0000${column.measureField}`;
      values[column.key] = alreadyAggregated
        ? (grouped.get(key)?.[0] ?? null)
        : aggregateValues(grouped.get(key) ?? [], measureAgg.get(column.measureField) ?? 'sum');
    }
    return { key: rowKey, label: rowKey, values };
  });

  return { rowLabel, columns, rows };
});

const cellStyle = (column: PivotColumn, value: number | null | undefined): Record<string, string | number> =>
  formatRuleStyle(resolveFormatRule(props.widget.formatRules, column.measureField, value));

const formatCell = (value: number | null | undefined, format?: WidgetFormat): string => {
  if (value === null || value === undefined) {
    return '—';
  }
  const formatted = new Intl.NumberFormat(undefined, {
    minimumFractionDigits: format?.precision,
    maximumFractionDigits: format?.precision ?? (Number.isInteger(value) ? 0 : 2),
    useGrouping: true,
  }).format(value);
  return `${format?.prefix ?? ''}${formatted}${format?.unit ?? ''}${format?.suffix ?? ''}`;
};
</script>

<style scoped>
.pivot-widget {
  height: 100%;
  min-height: 0;
}

.pivot-widget-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--dashboard-text-secondary, #909399);
  font-size: 13px;
}

.pivot-widget-scroll {
  overflow: auto;
  height: 100%;
  border: 1px solid var(--dashboard-border, #ebeef5);
  border-radius: 4px;
}

.pivot-widget-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  color: var(--dashboard-text, #303133);
  font-size: 12px;
  white-space: nowrap;
}

.pivot-widget-table th,
.pivot-widget-table td {
  padding: 8px 10px;
  border-right: 1px solid var(--dashboard-border, #ebeef5);
  border-bottom: 1px solid var(--dashboard-border, #ebeef5);
}

.pivot-widget-table thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  color: var(--dashboard-text-secondary, #606266);
  font-weight: 600;
  text-align: left;
  background: var(--dashboard-surface-muted, #f5f7fa);
}

.pivot-widget-table tbody th {
  position: sticky;
  left: 0;
  z-index: 1;
  color: var(--dashboard-text, #303133);
  font-weight: 500;
  text-align: left;
  background: var(--dashboard-surface, #fff);
}

.pivot-widget-table thead th.pivot-widget-row-header {
  left: 0;
  z-index: 2;
}

.pivot-widget-table td.is-measure {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.pivot-widget-table tbody tr:hover td,
.pivot-widget-table tbody tr:hover th {
  background: var(--dashboard-row-hover, #f5f7fa);
}

.pivot-widget-table th:last-child,
.pivot-widget-table td:last-child {
  border-right: none;
}

.pivot-widget-table tbody tr:last-child th,
.pivot-widget-table tbody tr:last-child td {
  border-bottom: none;
}
</style>
