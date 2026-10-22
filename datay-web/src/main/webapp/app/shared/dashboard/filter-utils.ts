import type { DashboardFilter, DatasetResult, FilterValues } from './types';

/**
 * 依筛选器定义生成初始值：优先 defaultValue，否则按类型给空值。
 */
export function initFilterValues(filters?: DashboardFilter[]): FilterValues {
  const values: FilterValues = {};
  for (const filter of filters ?? []) {
    if (filter.defaultValue !== undefined) {
      values[filter.id] = filter.defaultValue;
    } else if (filter.type === 'multiSelect') {
      values[filter.id] = [];
    } else if (filter.type === 'dateRange' || filter.type === 'numberRange') {
      values[filter.id] = null;
    } else {
      values[filter.id] = undefined;
    }
  }
  return values;
}

/**
 * 从 URL 查询参数还原筛选项（支持 f_<id> 前缀）。
 */
export function filtersFromQuery(filters: DashboardFilter[] | undefined, search: string): FilterValues {
  const values: FilterValues = {};
  if (!filters || filters.length === 0) {
    return values;
  }
  const params = new URLSearchParams(search);
  for (const filter of filters) {
    const raw = params.get(`f_${filter.id}`);
    if (raw === null) {
      continue;
    }
    if (filter.type === 'multiSelect') {
      values[filter.id] = raw.split(',').filter(Boolean);
    } else {
      values[filter.id] = raw;
    }
  }
  return values;
}

const isEmptyFilterValue = (value: any): boolean => {
  if (value === null || value === undefined) {
    return true;
  }
  if (typeof value === 'string') {
    return value.trim() === '';
  }
  if (Array.isArray(value)) {
    return value.length === 0;
  }
  if (typeof value === 'object') {
    return Object.values(value).every(item => item === null || item === undefined || item === '');
  }
  return false;
};

const toComparableTime = (value: any): number | null => {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? time : null;
};

const matchesFilter = (cell: any, filter: DashboardFilter, value: any): boolean => {
  if (isEmptyFilterValue(value)) {
    return true;
  }
  switch (filter.type) {
    case 'multiSelect': {
      const selected = Array.isArray(value) ? value : [value];
      return selected.map(item => String(item)).includes(String(cell ?? ''));
    }
    case 'select':
      return String(cell ?? '') === String(value);
    case 'input':
      return String(cell ?? '')
        .toLowerCase()
        .includes(String(value).toLowerCase());
    case 'dateRange': {
      const range = value as { start?: string; end?: string };
      const current = toComparableTime(cell);
      if (current === null) {
        return false;
      }
      const start = toComparableTime(range?.start);
      const end = toComparableTime(range?.end);
      return (start === null || current >= start) && (end === null || current <= end);
    }
    case 'numberRange': {
      const range = value as { min?: number; max?: number };
      const current = Number(cell);
      if (!Number.isFinite(current)) {
        return false;
      }
      return (
        (range?.min === null || range?.min === undefined || current >= Number(range.min)) &&
        (range?.max === null || range?.max === undefined || current <= Number(range.max))
      );
    }
    default:
      return true;
  }
};

/**
 * 对单个数据集结果应用基于「数据集 + 字段」的筛选器。
 *
 * <p>旧版 bindings 仍由服务端处理，这里只处理新版直接字段配置，避免重复过滤。
 */
export function applyDashboardFilters(
  result: DatasetResult,
  filters: DashboardFilter[] | undefined,
  values: FilterValues,
  datasetId: string,
): DatasetResult {
  const rows = result.rows;
  if (!Array.isArray(rows)) {
    return result;
  }
  const effectiveFilters = (filters ?? []).filter(
    filter => filter.datasetId === datasetId && !!filter.field && !(filter.bindings && filter.bindings.length > 0),
  );
  if (effectiveFilters.length === 0) {
    return result;
  }
  const filteredRows = rows.filter(row =>
    effectiveFilters.every(filter => matchesFilter(row[filter.field as string], filter, values[filter.id])),
  );
  return {
    ...result,
    rows: filteredRows,
    affectedRows: filteredRows.length,
  };
}

/** 从数据集结果中提取某字段的去重选项。 */
export function distinctFieldValues(result: DatasetResult | undefined, field?: string, limit = 200): string[] {
  if (!field || !Array.isArray(result?.rows)) {
    return [];
  }
  const values = new Set<string>();
  for (const row of result.rows) {
    const value = row[field];
    if (value !== null && value !== undefined && String(value) !== '') {
      values.add(String(value));
    }
    if (values.size >= limit) {
      break;
    }
  }
  return Array.from(values);
}
