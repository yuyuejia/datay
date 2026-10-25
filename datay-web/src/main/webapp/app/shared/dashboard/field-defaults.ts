import type { DatasetColumn } from '@/shared/dataset/types';
import type { ChartType, DashboardWidget, WidgetEncode, WidgetType } from './types';

/** 看板字段元数据：字段名、展示名称、类型以及是否数值字段。 */
export interface DashboardField {
  name: string;
  label: string;
  type: string;
  numeric: boolean;
}

const NUMERIC_TYPE_PATTERN =
  /^(number|numeric|decimal|double|float|real|int|integer|bigint|smallint|tinyint|long|short|byte|money|currency|percent)/i;

const CATEGORY_NAME_PATTERN =
  /(date|time|month|year|quarter|week|day|category|type|status|region|province|city|country|channel|store|product|brand|department|dept|group|name)/i;

const VALUE_NAME_PATTERN =
  /(amount|value|total|sum|count|qty|quantity|revenue|sales|price|cost|profit|score|rate|ratio|num|pct|percent|balance|volume)/i;

/** 判断字段类型是否属于数值类型。 */
export function isNumericFieldType(type?: string): boolean {
  return NUMERIC_TYPE_PATTERN.test((type ?? '').trim());
}

function normalizeType(type?: string): string {
  return (type ?? '').trim().toLowerCase();
}

function inferValueType(value: unknown): string | undefined {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? 'number' : undefined;
  }
  if (typeof value === 'boolean') {
    return 'boolean';
  }
  if (value instanceof Date) {
    return 'date';
  }
  return 'string';
}

/**
 * 根据缓存字段类型或结果行推断字段类型。
 *
 * <p>SQL 数据集结果默认只返回列名，因此优先读取数据集字段缓存中的类型；
 * 缓存缺失时用第一行非空值兜底推断，保证数值字段能被优先选中。
 */
export function inferFieldType(column: DatasetColumn | string, rows: Array<Record<string, any>> = []): string {
  const name = typeof column === 'string' ? column : column.name;
  const declaredType = typeof column === 'string' ? '' : normalizeType(column.type);
  if (declaredType) {
    return declaredType;
  }
  for (const row of rows) {
    const type = inferValueType(row?.[name]);
    if (type) {
      return type;
    }
  }
  return 'unknown';
}

/** 构建带类型的字段列表，保持数据集原始列顺序。 */
export function buildDashboardFields(
  columns: string[],
  rows: Array<Record<string, any>> = [],
  declaredColumns: DatasetColumn[] = [],
): DashboardField[] {
  const declaredByName = new Map(declaredColumns.map(column => [column.name, column]));
  return columns.map(name => {
    const declared = declaredByName.get(name);
    const type = inferFieldType(declared ?? name, rows);
    return {
      name,
      label: declared?.description || declared?.label || name,
      type,
      numeric: isNumericFieldType(type),
    };
  });
}

function categoryScore(field: DashboardField): number {
  let score = 0;
  if (CATEGORY_NAME_PATTERN.test(field.name)) {
    score += 20;
  }
  if (!/(^|_)(id|sk|key)$/i.test(field.name)) {
    score += 10;
  }
  if (field.type === 'date' || field.type === 'string') {
    score += 5;
  }
  return score;
}

function valueScore(field: DashboardField): number {
  let score = 0;
  if (VALUE_NAME_PATTERN.test(field.name)) {
    score += 20;
  }
  if (!/(^|_)(id|sk|key)$/i.test(field.name)) {
    score += 10;
  }
  return score;
}

/** 选择默认分类字段：优先非数值、非代理键字段。 */
export function pickCategoryField(fields: DashboardField[]): string | undefined {
  const candidates = fields
    .map((field, index) => ({ field, index }))
    .filter(item => !item.field.numeric)
    .sort((left, right) => categoryScore(right.field) - categoryScore(left.field) || left.index - right.index);
  if (candidates.length > 0) {
    return candidates[0].field.name;
  }
  return fields.find(field => !/(^|_)(id|sk|key)$/i.test(field.name))?.name ?? fields[0]?.name;
}

/**
 * 选择默认数值字段：优先数值类型，并使用常见度量字段名排序。
 *
 * <p>没有类型信息时返回 undefined，等待结果行到达后再补默认值，
 * 避免把文本字段误选为数值字段。
 */
export function pickValueField(fields: DashboardField[], categoryField?: string): string | undefined {
  const numericCandidates = fields
    .map((field, index) => ({ field, index }))
    .filter(item => item.field.numeric && item.field.name !== categoryField)
    .sort((left, right) => valueScore(right.field) - valueScore(left.field) || left.index - right.index);
  if (numericCandidates.length > 0) {
    return numericCandidates[0].field.name;
  }
  if (fields.some(field => field.type === 'unknown')) {
    return undefined;
  }
  return fields.find(field => field.name !== categoryField)?.name;
}

/** 根据组件与图表类型判断分类字段是否需要 group by。 */
export function shouldGroupByCategory(type: WidgetType, chartType?: ChartType): boolean {
  if (type !== 'echarts') {
    return false;
  }
  return chartType === 'bar' || chartType === 'line' || chartType === 'pie' || chartType === 'area';
}

/** 从 ECharts option 中解析当前图表类型。 */
export function resolveChartType(option?: Record<string, any>): ChartType {
  const series = option?.series;
  const first = Array.isArray(series) ? series[0] : series;
  if (first?.type === 'line' && first?.areaStyle) {
    return 'area';
  }
  return first?.type === 'line' || first?.type === 'pie' || first?.type === 'bar' ? first.type : 'bar';
}

/**
 * 为新组件生成默认 encode。
 *
 * - 图表：选择默认分类字段与数值字段，并按图表类型设置 groupBy；
 * - KPI：只设置默认数值字段；
 * - 表格：保留明细行，只设置 limit。
 */
export function createDefaultEncode(
  type: WidgetType,
  chartType: ChartType | undefined,
  fields: DashboardField[],
): WidgetEncode | undefined {
  if (type === 'html') {
    return undefined;
  }
  if (type === 'table') {
    return { limit: 100 };
  }
  if (type === 'kpi') {
    const field = pickValueField(fields) ?? '';
    return { values: [{ field, agg: 'sum' }] };
  }
  const categoryField = pickCategoryField(fields) ?? '';
  const valueField = pickValueField(fields, categoryField) ?? '';
  return {
    categoryField,
    values: [{ field: valueField, agg: 'sum' }],
    limit: 100,
    groupBy: shouldGroupByCategory(type, chartType),
  };
}

/**
 * 数据到达后补全尚未设置的默认字段。
 *
 * <p>只填充空值，不覆盖用户已经手动选择的字段。
 */
export function ensureDefaultFields(widget: DashboardWidget, fields: DashboardField[]): boolean {
  if (!widget.encode || widget.type === 'html' || widget.type === 'table') {
    return false;
  }
  const encode = widget.encode;
  let changed = false;
  if (widget.type === 'echarts') {
    if (!encode.categoryField && fields.length > 0) {
      encode.categoryField = pickCategoryField(fields) ?? '';
      changed = true;
    }
    if (!encode.values?.[0]?.field && fields.length > 0) {
      encode.values = [{ field: pickValueField(fields, encode.categoryField) ?? '', agg: 'sum' }];
      changed = true;
    }
    const groupBy = shouldGroupByCategory(widget.type, resolveChartType(widget.echartsOption));
    if (encode.groupBy !== groupBy) {
      encode.groupBy = groupBy;
      changed = true;
    }
  } else if (widget.type === 'kpi' && !encode.values?.[0]?.field && fields.length > 0) {
    encode.values = [{ field: pickValueField(fields) ?? '', agg: 'sum' }];
    changed = true;
  }
  return changed;
}

/** 判断图表的默认分类/数值字段是否已经完整。 */
export function hasCompleteDefaultFields(widget: DashboardWidget): boolean {
  if (!widget.encode || widget.type === 'html' || widget.type === 'table') {
    return true;
  }
  if (widget.type === 'kpi') {
    return !!widget.encode.values?.[0]?.field;
  }
  return !!widget.encode.categoryField && !!widget.encode.values?.[0]?.field;
}
