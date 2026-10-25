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
 *
 * <p>区间筛选器既支持 `start,end` / `min,max` / `start~end` 简写，也支持 JSON
 * 对象或数组，避免深链恢复后把区间值当成普通字符串导致筛选静默失效。
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
      values[filter.id] = parseMultiSelect(raw);
    } else if (filter.type === 'dateRange' || filter.type === 'numberRange') {
      values[filter.id] = parseRangeValue(raw, filter.type);
    } else {
      values[filter.id] = raw;
    }
  }
  return values;
}

const parseMultiSelect = (raw: string): string[] => {
  const trimmed = raw.trim();
  if (trimmed.startsWith('[')) {
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        return parsed.map(item => String(item)).filter(Boolean);
      }
    } catch {
      // 非法 JSON 回退为逗号分隔
    }
  }
  return trimmed.split(',').filter(Boolean);
};

const parseRangeValue = (
  raw: string,
  type: 'dateRange' | 'numberRange',
): { start?: string; end?: string } | { min?: number; max?: number } | null => {
  const trimmed = raw.trim();
  if (!trimmed) {
    return null;
  }
  const startKey = type === 'dateRange' ? 'start' : 'min';
  const endKey = type === 'dateRange' ? 'end' : 'max';

  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        return buildRange(parsed[0], parsed[1], type);
      }
      if (parsed && typeof parsed === 'object') {
        const candidate = parsed as Record<string, unknown>;
        const start = candidate[startKey] ?? candidate.start ?? candidate.min ?? candidate.from;
        const end = candidate[endKey] ?? candidate.end ?? candidate.max ?? candidate.to;
        return buildRange(start, end, type);
      }
    } catch {
      // 非法 JSON 按分隔符继续解析
    }
  }

  const separator = trimmed.includes('~') ? '~' : trimmed.includes(',') ? ',' : null;
  if (separator) {
    const [start, end] = trimmed.split(separator, 2).map(item => item.trim());
    return buildRange(start, end, type);
  }
  return buildRange(trimmed, undefined, type);
};

const buildRange = (
  rawStart: unknown,
  rawEnd: unknown,
  type: 'dateRange' | 'numberRange',
): { start?: string; end?: string } | { min?: number; max?: number } | null => {
  const start = rawStart === null || rawStart === undefined ? '' : String(rawStart).trim();
  const end = rawEnd === null || rawEnd === undefined ? '' : String(rawEnd).trim();
  if (!start && !end) {
    return null;
  }
  if (type === 'dateRange') {
    return { ...(start ? { start } : {}), ...(end ? { end } : {}) };
  }
  const min = start === '' ? undefined : Number(start);
  const max = end === '' ? undefined : Number(end);
  if ((min !== undefined && !Number.isFinite(min)) || (max !== undefined && !Number.isFinite(max))) {
    return null;
  }
  return { ...(min !== undefined ? { min } : {}), ...(max !== undefined ? { max } : {}) };
};

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
 * 对单个数据集结果应用筛选器。
 *
 * <p>只处理 mode=client 的字段目标；pushdown 目标已由服务端处理，这里不重复过滤。
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
  const effective = (filters ?? []).flatMap(filter =>
    (filter.targets ?? [])
      .filter(item => item.datasetId === datasetId && !!item.field && item.mode !== 'server')
      .map(target => ({ filter, target })),
  );
  if (effective.length === 0) {
    return result;
  }
  const columns = result.columns;
  if (Array.isArray(columns) && columns.length > 0) {
    const missing = effective.find(({ target }) => !columns.includes(target.field as string));
    if (missing) {
      throw new Error(`筛选器 ${missing.filter.id} 的字段 ${missing.target.field} 不在数据集 ${datasetId} 的查询结果中`);
    }
  }
  const filteredRows = rows.filter(row =>
    effective.every(({ filter, target }) => matchesFilter(row[target.field as string], filter, values[filter.id])),
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
