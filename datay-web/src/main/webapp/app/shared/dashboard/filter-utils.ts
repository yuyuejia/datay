import type { DashboardFilter, FilterValues } from "./types";

/**
 * 依筛选器定义生成初始值：优先 defaultValue，否则按类型给空值。
 */
export function initFilterValues(filters?: DashboardFilter[]): FilterValues {
  const values: FilterValues = {};
  for (const filter of filters ?? []) {
    if (filter.defaultValue !== undefined) {
      values[filter.id] = filter.defaultValue;
    } else if (filter.type === "multiSelect") {
      values[filter.id] = [];
    } else if (filter.type === "dateRange" || filter.type === "numberRange") {
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
export function filtersFromQuery(
  filters: DashboardFilter[] | undefined,
  search: string,
): FilterValues {
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
    if (filter.type === "multiSelect") {
      values[filter.id] = raw.split(",").filter(Boolean);
    } else {
      values[filter.id] = raw;
    }
  }
  return values;
}
