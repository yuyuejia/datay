import type { DashboardFilter, FilterValues } from './types';

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
