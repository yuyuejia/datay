import type { DatasetColumn } from '@/shared/dataset/types';
import type { ChartType, DashboardWidget, WidgetEncode, WidgetEncodeDimension, WidgetType } from './types';

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
  columnLabels: Record<string, string> = {},
): DashboardField[] {
  const declaredByName = new Map(declaredColumns.map(column => [column.name, column]));
  return columns.map(name => {
    const declared = declaredByName.get(name);
    const type = inferFieldType(declared ?? name, rows);
    return {
      name,
      label: declared?.description || declared?.label || columnLabels[name] || name,
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
  return chartType !== 'gauge' && chartType !== 'scatter' && chartType !== 'boxplot';
}

/** 从组件或 ECharts option 中解析当前图表类型。 */
export function resolveChartType(source?: DashboardWidget | Record<string, any>): ChartType {
  if (source && 'chart' in source && (source as DashboardWidget).chart?.type) {
    return (source as DashboardWidget).chart!.type as ChartType;
  }
  const option = (source && 'echartsOption' in source ? (source as DashboardWidget).echartsOption : source) as
    | Record<string, any>
    | undefined;
  const series = option?.series;
  const first = Array.isArray(series) ? series[0] : series;
  if (first?.type === 'line' && first?.areaStyle) {
    return 'area';
  }
  if (first?.type === 'pie' && (first?.radius || first?.avoidLabelOverlap)) {
    return 'donut';
  }
  return [
    'bar',
    'line',
    'area',
    'pie',
    'donut',
    'gauge',
    'scatter',
    'radar',
    'funnel',
    'heatmap',
    'treemap',
    'waterfall',
    'sankey',
    'graph',
    'boxplot',
  ].includes(first?.type)
    ? (first.type as ChartType)
    : 'bar';
}

/**
 * 为新组件生成默认 encode。
 *
 * - 图表：维度 x + 度量 y，仪表盘只保留度量；
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
    return { measures: [{ field, agg: 'sum', role: 'value' }] };
  }
  const categoryField = pickCategoryField(fields) ?? '';
  const secondCategory = pickCategoryField(fields.filter(field => field.name !== categoryField && !field.numeric));
  const valueField = pickValueField(fields, categoryField) ?? '';
  if (type === 'pivot') {
    const dimensions: WidgetEncodeDimension[] = [{ field: categoryField, role: 'row' }];
    if (secondCategory && secondCategory !== categoryField) {
      dimensions.push({ field: secondCategory, role: 'column' });
    }
    return {
      dimensions,
      measures: [{ field: valueField, agg: 'sum', role: 'value' }],
      limit: 100,
    };
  }
  if (chartType === 'gauge') {
    return {
      measures: [{ field: valueField, agg: 'sum', role: 'value' }],
      limit: 100,
      groupBy: false,
    };
  }
  const dimensions: WidgetEncodeDimension[] = [{ field: categoryField, role: 'x' }];
  if ((chartType === 'heatmap' || chartType === 'sankey' || chartType === 'graph') && secondCategory && secondCategory !== categoryField) {
    dimensions.push({ field: secondCategory, role: 'series' });
  }
  const valueOnly = ['pie', 'donut', 'funnel', 'treemap', 'heatmap', 'sankey', 'graph'].includes(chartType ?? '');
  const measureRole: 'y' | 'value' = valueOnly ? 'value' : 'y';
  return {
    dimensions,
    measures: [{ field: valueField, agg: 'sum', role: measureRole }],
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
  if (widget.type === 'pivot') {
    if (fields.length > 0) {
      const dimensions = [...(encode.dimensions ?? [])];
      const rowIndex = dimensions.findIndex(dimension => dimension.role === 'row');
      if (rowIndex < 0) {
        dimensions.push({ field: pickCategoryField(fields) ?? '', role: 'row' });
        encode.dimensions = dimensions;
        changed = true;
      } else if (!dimensions[rowIndex].field) {
        dimensions[rowIndex] = { ...dimensions[rowIndex], field: pickCategoryField(fields) ?? '' };
        encode.dimensions = dimensions;
        changed = true;
      }
      if (!encode.measures?.length) {
        encode.measures = [{ field: pickValueField(fields) ?? '', agg: 'sum', role: 'value' }];
        changed = true;
      } else if (!encode.measures[0].field) {
        encode.measures = [{ ...encode.measures[0], field: pickValueField(fields) ?? '', role: 'value' }, ...encode.measures.slice(1)];
        changed = true;
      }
    }
  } else if (widget.type === 'echarts') {
    const chartType = resolveChartType(widget);
    if (chartType !== 'gauge' && fields.length > 0) {
      const dimensions = [...(encode.dimensions ?? [])];
      const xIndex = dimensions.findIndex(dimension => dimension.role === 'x');
      if (xIndex < 0) {
        dimensions.push({ field: pickCategoryField(fields) ?? '', role: 'x' });
        encode.dimensions = dimensions;
        changed = true;
      } else if (!dimensions[xIndex].field) {
        dimensions[xIndex] = { ...dimensions[xIndex], field: pickCategoryField(fields) ?? '' };
        encode.dimensions = dimensions;
        changed = true;
      }
      if (
        (chartType === 'heatmap' || chartType === 'sankey' || chartType === 'graph') &&
        !dimensions.find(dimension => dimension.role === 'series')
      ) {
        const secondCategory = pickCategoryField(fields.filter(field => field.name !== dimensions[xIndex]?.field && !field.numeric));
        if (secondCategory && secondCategory !== dimensions[xIndex]?.field) {
          dimensions.push({ field: secondCategory, role: 'series' });
          encode.dimensions = dimensions;
          changed = true;
        }
      }
    }
    if (fields.length > 0) {
      const categoryField = encode.dimensions?.find(dimension => dimension.role === 'x')?.field;
      const role: 'y' | 'value' = chartType === 'gauge' ? 'value' : 'y';
      if (!encode.measures?.length) {
        encode.measures = [{ field: pickValueField(fields, categoryField) ?? '', agg: 'sum', role }];
        changed = true;
      } else if (!encode.measures[0].field) {
        encode.measures = [
          { ...encode.measures[0], field: pickValueField(fields, categoryField) ?? '', role: encode.measures[0].role ?? role },
          ...encode.measures.slice(1),
        ];
        changed = true;
      }
    }
    const groupBy = shouldGroupByCategory(widget.type, chartType);
    if (encode.groupBy !== groupBy) {
      encode.groupBy = groupBy;
      changed = true;
    }
  } else if (widget.type === 'kpi' && !encode.measures?.[0]?.field && fields.length > 0) {
    encode.measures = [{ field: pickValueField(fields) ?? '', agg: 'sum', role: 'value' }];
    changed = true;
  }
  return changed;
}

/** 判断图表的默认分类/数值字段是否已经完整。 */
export function hasCompleteDefaultFields(widget: DashboardWidget): boolean {
  if (!widget.encode || widget.type === 'html' || widget.type === 'table') {
    return true;
  }
  if (widget.type === 'kpi' || widget.type === 'pivot') {
    if (widget.type === 'pivot' && !widget.encode.dimensions?.find(dimension => dimension.role === 'row')?.field) {
      return false;
    }
    return !!widget.encode.measures?.[0]?.field;
  }
  const chartType = resolveChartType(widget);
  if (chartType === 'gauge') {
    return !!widget.encode.measures?.[0]?.field;
  }
  const hasX = !!widget.encode.dimensions?.find(dimension => dimension.role === 'x')?.field;
  if (chartType === 'heatmap' || chartType === 'sankey' || chartType === 'graph') {
    return (
      hasX && !!widget.encode.dimensions?.find(dimension => dimension.role === 'series')?.field && !!widget.encode.measures?.[0]?.field
    );
  }
  return hasX && !!widget.encode.measures?.[0]?.field;
}
