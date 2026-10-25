import { sanitizeEchartsOption } from './echarts-option-sanitizer';
import type { Aggregation, DashboardWidget, DatasetResult, WidgetEncode } from './types';

/**
 * 把 AI 生成的 ECharts option 与查询返回的数据结合。
 *
 * AI 负责图表类型与样式（option），`encode` 负责声明如何把数据集列映射到图上；
 * 本模块据此把真实的分类轴数据、系列数据注入 option，保证看板数据来自实时查询。
 */

function toNumber(value: any): number | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function sortRows(rows: Record<string, any>[], field: string, order?: 'asc' | 'desc'): Record<string, any>[] {
  const direction = order === 'desc' ? -1 : 1;
  return [...rows].sort((a, b) => {
    const left = a[field];
    const right = b[field];
    const leftNumber = Number(left);
    const rightNumber = Number(right);
    if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) {
      return (leftNumber - rightNumber) * direction;
    }
    return String(left ?? '').localeCompare(String(right ?? '')) * direction;
  });
}

function firstSeriesType(option: Record<string, any>): string | undefined {
  const series = option?.series;
  if (Array.isArray(series) && series.length > 0) {
    return series[0]?.type;
  }
  if (series && typeof series === 'object') {
    return series.type;
  }
  return undefined;
}

function baseSeries(option: Record<string, any>): Record<string, any>[] {
  if (Array.isArray(option.series)) {
    return option.series;
  }
  if (option.series && typeof option.series === 'object') {
    return [option.series];
  }
  return [];
}

function setAxisData(option: Record<string, any>, axisKey: 'xAxis' | 'yAxis', categories: any[]): void {
  const axis = option[axisKey];
  if (Array.isArray(axis)) {
    option[axisKey] = axis.map((item, index) => (index === 0 ? { ...item, data: categories } : item));
  } else if (axis && typeof axis === 'object') {
    option[axisKey] = { ...axis, data: categories };
  } else {
    option[axisKey] = { type: 'category', data: categories };
  }
}

/**
 * 分类轴既可能在 x 轴（纵向柱/折线），也可能在 y 轴（横向柱）。
 * 优先识别 type=category 的轴，否则默认 x 轴。
 */
function setCategoryAxis(option: Record<string, any>, categories: any[]): void {
  const yAxis = Array.isArray(option.yAxis) ? option.yAxis[0] : option.yAxis;
  const axisKey = yAxis && yAxis.type === 'category' ? 'yAxis' : 'xAxis';
  setAxisData(option, axisKey, categories);
}

function resolveSeriesDefs(encode: WidgetEncode, columns: string[], categoryField?: string) {
  if (encode.values && encode.values.length > 0) {
    return encode.values;
  }
  return columns.filter(column => column !== categoryField).map(column => ({ name: column, field: column, agg: 'sum' as const }));
}

/**
 * 按 encode 对行做排序与 limit 截断。
 *
 * 图表和明细表共用同一套数据裁剪规则，避免配置项只对部分组件生效。
 */
export function applyEncodeRows(encode: WidgetEncode | undefined, rows: Record<string, any>[]): Record<string, any>[] {
  let result = [...rows];
  if (encode?.sortField) {
    result = sortRows(result, encode.sortField, encode.sortOrder);
  }
  if (encode?.limit && result.length > encode.limit) {
    result = result.slice(0, encode.limit);
  }
  return result;
}

/**
 * 解析字段的聚合方式；未声明 agg 时默认 SUM。
 */
export function aggregationFor(encode: WidgetEncode | undefined, field: string): Aggregation {
  return encode?.values?.find(value => value.field === field)?.agg ?? 'sum';
}

function aggregateValues(values: number[], agg: Aggregation): number | null {
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
}

/**
 * 按分类字段聚合图表数据。
 *
 * <p>柱/折/面积图按「分类 + 系列」聚合，饼图只按分类聚合；聚合方式来自
 * `encode.values[].agg`。聚合后再执行排序与 limit，避免明细行数上限截断同一分类的数据。
 */
export function aggregateChartRows(
  encode: WidgetEncode | undefined,
  rows: Record<string, any>[],
  chartType?: string,
): Record<string, any>[] {
  const valueFields = Array.from(new Set((encode?.values?.map(value => value.field) ?? []).filter((field): field is string => !!field)));
  if (!encode?.groupBy || !encode.categoryField || valueFields.length === 0) {
    return rows;
  }
  const categoryField = encode.categoryField;
  const seriesField = chartType === 'pie' ? undefined : encode.seriesField || undefined;
  const groups = new Map<string, { row: Record<string, any>; values: Map<string, number[]> }>();
  for (const row of rows) {
    const category = row[categoryField];
    const series = seriesField ? row[seriesField] : undefined;
    const key = JSON.stringify([category, series]);
    let grouped = groups.get(key);
    if (!grouped) {
      grouped = {
        row: { ...row },
        values: new Map(valueFields.map(field => [field, []])),
      };
      groups.set(key, grouped);
    }
    for (const field of valueFields) {
      const value = toNumber(row[field]);
      if (value !== null) {
        grouped.values.get(field)?.push(value);
      }
    }
  }
  return Array.from(groups.values()).map(group => {
    const result = { ...group.row };
    for (const field of valueFields) {
      result[field] = aggregateValues(group.values.get(field) ?? [], aggregationFor(encode, field));
    }
    return result;
  });
}

/**
 * v1 展示字段完全由 encode 显式声明，不再自动把 _id/_sk 替换为 _name。
 */
export function resolveNameField(field: string | undefined, _columns: string[]): string | undefined {
  return field;
}

/**
 * 构建最终 ECharts option；无数据或未声明 encode 时原样返回 AI option。
 */
export function buildEchartsOption(widget: DashboardWidget, result?: DatasetResult): Record<string, any> {
  const option: Record<string, any> = sanitizeEchartsOption(JSON.parse(JSON.stringify(widget.echartsOption || {})));
  // 组件卡片已展示标题，移除图表内 title 避免重复（默认保留 dashboard-widget-title）
  delete option.title;
  const chartType = firstSeriesType(option);
  if (chartType !== 'pie' && chartType !== 'gauge') {
    if (!option.xAxis) {
      option.xAxis = { type: 'category' };
    }
    if (!option.yAxis) {
      option.yAxis = { type: 'value' };
    }
  }
  const encode = widget.encode;
  if (!encode || !result || !Array.isArray(result.rows) || result.rows.length === 0) {
    return option;
  }

  const columns = result.columns ?? [];
  const fieldLabel = (field?: string): string => (field ? result.columnLabels?.[field] || field : '');
  const categoryField = resolveNameField(encode.categoryField ?? columns[0], columns) ?? '';
  const valueField = resolveNameField(encode.values?.[0]?.field ?? columns.find(column => column !== categoryField), columns);
  const seriesField = resolveNameField(encode.seriesField ?? undefined, columns);
  const sortField = resolveNameField(encode.sortField ?? undefined, columns);
  const resolvedValues = encode.values?.map(value => ({
    ...value,
    field: resolveNameField(value.field, columns) ?? value.field,
  }));
  const effectiveEncode: WidgetEncode = {
    ...encode,
    categoryField,
    seriesField,
    sortField,
    values: resolvedValues,
  };
  const groupedRows = effectiveEncode.groupBy ? aggregateChartRows(effectiveEncode, result.rows, chartType) : result.rows;
  const rows = applyEncodeRows(effectiveEncode, groupedRows);

  if (chartType === 'pie') {
    option.series = [
      {
        ...(baseSeries(option)[0] || {}),
        type: 'pie',
        name: (baseSeries(option)[0] || {}).name || fieldLabel(valueField),
        data: rows.map(row => ({
          name: row[categoryField],
          value: toNumber(row[valueField as string]),
        })),
      },
    ];
    return option;
  }

  if (chartType === 'gauge') {
    const first = rows[0];
    option.series = [
      {
        ...(baseSeries(option)[0] || {}),
        type: 'gauge',
        data: [
          {
            value: toNumber(first[valueField as string]),
            name: first[categoryField] ?? widget.title,
          },
        ],
      },
    ];
    return option;
  }

  if (categoryField) {
    setCategoryAxis(
      option,
      rows.map(row => row[categoryField]),
    );
  }

  const base = baseSeries(option);

  if (seriesField) {
    const valueFieldName = valueField as string;
    const categories = Array.from(new Set(rows.map(row => row[categoryField])));
    if (categoryField) {
      setCategoryAxis(option, categories);
    }
    const groups = Array.from(new Set(rows.map(row => row[seriesField])));
    option.series = groups.map((group, index) => {
      const template = base[index] || base[0] || {};
      const data = categories.map(category => {
        const row = rows.find(item => item[categoryField] === category && item[seriesField] === group);
        return row ? toNumber(row[valueFieldName]) : null;
      });
      return { ...template, name: String(group), data };
    });
    return option;
  }

  const seriesDefs = resolveSeriesDefs(effectiveEncode, columns, categoryField).map(series => ({
    ...series,
    field: resolveNameField(series.field, columns) ?? series.field,
  }));
  option.series = seriesDefs.map((series, index) => {
    const template = base[index] || base[0] || {};
    return {
      ...template,
      name: series.name && series.name !== series.field ? series.name : fieldLabel(series.field),
      data: rows.map(row => toNumber(row[series.field])),
    };
  });
  return option;
}

/**
 * 计算 KPI 单值：优先取 encode.values[0]，否则取首个数值列。
 */
export function resolveKpiValue(widget: DashboardWidget, result?: DatasetResult): { value: number | null; label?: string } {
  if (!result || !Array.isArray(result.rows) || result.rows.length === 0) {
    return { value: null };
  }
  const rows = result.rows;
  const encode = widget.encode;
  const field = encode?.values?.[0]?.field;
  if (!field) {
    return { value: null };
  }
  const values = rows.map(row => toNumber(row[field])).filter((value): value is number => value !== null);
  return { value: aggregateValues(values, aggregationFor(encode, field)) };
}
