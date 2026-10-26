import { sanitizeEchartsOption } from './echarts-option-sanitizer';
import type {
  Aggregation,
  ChartOrientation,
  ChartSeries,
  ChartType,
  DashboardWidget,
  DatasetResult,
  WidgetEncode,
  WidgetEncodeDimension,
  WidgetEncodeMeasure,
} from './types';

/**
 * 把看板 v2 spec 的图表声明与查询返回的数据结合。
 *
 * `chart.type` 决定图表类型，`encode.dimensions/measures` 决定数据映射，
 * `chart.series` 用于组合图/双轴；`echartsOption` 只作为样式模板。
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

function seriesTemplate(base: Record<string, any>[], index: number): Record<string, any> {
  const template = base[index] ?? base[base.length - 1] ?? {};
  const result = { ...template };
  delete result.data;
  return result;
}

function setAxisData(option: Record<string, any>, axisKey: 'xAxis' | 'yAxis', categories: any[]): void {
  const axis = option[axisKey];
  if (Array.isArray(axis)) {
    option[axisKey] = axis.map((item, index) => (index === 0 ? { ...item, data: categories, type: 'category' } : item));
  } else if (axis && typeof axis === 'object') {
    option[axisKey] = { ...axis, type: 'category', data: categories };
  } else {
    option[axisKey] = { type: 'category', data: categories };
  }
}

function setValueAxis(option: Record<string, any>, axisKey: 'xAxis' | 'yAxis'): void {
  const axis = option[axisKey];
  if (Array.isArray(axis)) {
    option[axisKey] = axis.map((item, index) => (index === 0 ? { ...item, type: 'value' } : item));
  } else if (axis && typeof axis === 'object') {
    option[axisKey] = { ...axis, type: 'value' };
  } else {
    option[axisKey] = { type: 'value' };
  }
}

function isSingleValueChart(chartType: ChartType): boolean {
  return chartType === 'pie' || chartType === 'donut' || chartType === 'funnel' || chartType === 'treemap';
}

function isCartesian(chartType: ChartType): boolean {
  return chartType === 'bar' || chartType === 'line' || chartType === 'area';
}

function isAxisChart(chartType: ChartType): boolean {
  return (
    isCartesian(chartType) || chartType === 'scatter' || chartType === 'heatmap' || chartType === 'waterfall' || chartType === 'boxplot'
  );
}

function chartUsesSeriesDimension(chartType?: string): boolean {
  return (
    chartType === 'bar' ||
    chartType === 'line' ||
    chartType === 'area' ||
    chartType === 'scatter' ||
    chartType === 'heatmap' ||
    chartType === 'sankey' ||
    chartType === 'graph'
  );
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
 * 解析字段的聚合方式；未声明时默认 SUM。
 */
export function aggregationFor(encode: WidgetEncode | undefined, field: string): Aggregation {
  return encode?.measures?.find(measure => measure.field === field)?.agg ?? 'sum';
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

function dimensionByRole(encode: WidgetEncode | undefined, role: WidgetEncodeDimension['role']): WidgetEncodeDimension | undefined {
  return encode?.dimensions?.find(dimension => dimension.role === role);
}

function measuresOf(encode: WidgetEncode | undefined): WidgetEncodeMeasure[] {
  return encode?.measures ?? [];
}

/**
 * 按分类（以及可选的系列）聚合图表数据。
 *
 * <p>饼图/仪表盘只按分类聚合；柱/折/面积图按「分类 + 系列」聚合。
 */
export function aggregateChartRows(
  encode: WidgetEncode | undefined,
  rows: Record<string, any>[],
  chartType?: string,
): Record<string, any>[] {
  const measures = measuresOf(encode);
  const measureFields = measures.map(measure => measure.field);
  if (!encode?.groupBy || measureFields.length === 0) {
    return rows;
  }
  const xField = dimensionByRole(encode, 'x')?.field;
  if (!xField) {
    // 仪表盘等单值场景：直接对全部行聚合。
    const single: Record<string, any> = { ...rows[0] };
    for (const measure of measures) {
      const values = rows.map(row => toNumber(row[measure.field])).filter((value): value is number => value !== null);
      single[measure.field] = aggregateValues(values, measure.agg);
    }
    return [single];
  }
  const seriesField = chartUsesSeriesDimension(chartType) ? dimensionByRole(encode, 'series')?.field : undefined;
  const groups = new Map<string, { row: Record<string, any>; values: Map<string, number[]> }>();
  for (const row of rows) {
    const category = row[xField];
    const series = seriesField ? row[seriesField] : undefined;
    const key = JSON.stringify([category, series]);
    let grouped = groups.get(key);
    if (!grouped) {
      grouped = {
        row: { ...row },
        values: new Map(measureFields.map(field => [field, []])),
      };
      groups.set(key, grouped);
    }
    for (const measure of measures) {
      const value = toNumber(row[measure.field]);
      if (value !== null) {
        grouped.values.get(measure.field)?.push(value);
      }
    }
  }
  return Array.from(groups.values()).map(group => {
    const result = { ...group.row };
    for (const measure of measures) {
      result[measure.field] = aggregateValues(group.values.get(measure.field) ?? [], measure.agg);
    }
    return result;
  });
}

/**
 * v2 展示字段完全由 encode 显式声明，不再自动把 _id/_sk 替换为 _name。
 */
export function resolveNameField(field: string | undefined, _columns: string[]): string | undefined {
  return field;
}

function quantile(sorted: number[], q: number): number {
  if (sorted.length === 0) {
    return 0;
  }
  if (sorted.length === 1) {
    return sorted[0];
  }
  const position = (sorted.length - 1) * q;
  const base = Math.floor(position);
  const rest = position - base;
  const next = sorted[base + 1] ?? sorted[base];
  return sorted[base] + (next - sorted[base]) * rest;
}

function toPercent(value: number | null, total: number): number | null {
  if (value === null || total === 0) {
    return value === null ? null : 0;
  }
  return Math.round((value / total) * 10000) / 100;
}

/**
 * 占总比变换。
 *
 * <p>饼图/环形图按全量合计；柱/折/面积图在存在 series 维度或 stackedPercent 子类型时
 * 按每个分类合计，否则按全量合计。
 */
function applyPercentTransform(
  rows: Record<string, any>[],
  measureFields: string[],
  xField: string | undefined,
  seriesField: string | undefined,
  chartType: ChartType,
  subtype: string | undefined,
): Record<string, any>[] {
  if (measureFields.length === 0) {
    return rows;
  }
  const perCategory = isCartesian(chartType) && (!!seriesField || subtype === 'stackedPercent');
  if (!perCategory || !xField) {
    const totals = new Map<string, number>();
    let grandTotal = 0;
    for (const field of measureFields) {
      const total = rows.reduce((sum, row) => sum + (toNumber(row[field]) ?? 0), 0);
      totals.set(field, total);
      grandTotal += total;
    }
    return rows.map(row => {
      const result = { ...row };
      for (const field of measureFields) {
        const total = measureFields.length > 1 ? (totals.get(field) ?? 0) : grandTotal;
        result[field] = toPercent(toNumber(row[field]), total);
      }
      return result;
    });
  }
  const totalsByCategory = new Map<string, number>();
  for (const row of rows) {
    const key = String(row[xField]);
    const total = measureFields.reduce((sum, field) => sum + (toNumber(row[field]) ?? 0), 0);
    totalsByCategory.set(key, (totalsByCategory.get(key) ?? 0) + total);
  }
  return rows.map(row => {
    const result = { ...row };
    const total = totalsByCategory.get(String(row[xField])) ?? 0;
    for (const field of measureFields) {
      result[field] = toPercent(toNumber(row[field]), total);
    }
    return result;
  });
}

function normalizeSeriesDefs(widget: DashboardWidget, chartType: ChartType): ChartSeries[] {
  const declared = widget.chart?.series;
  if (declared && declared.length > 0) {
    return declared.map(series => ({
      ...series,
      type: series.type ?? (chartType === 'area' ? 'area' : chartType === 'line' ? 'line' : 'bar'),
      axis: series.axis ?? 'left',
    }));
  }
  if (!isCartesian(chartType)) {
    return [];
  }
  return measuresOf(widget.encode).map(measure => ({
    measure: measure.field,
    type: chartType === 'area' ? 'area' : chartType === 'line' ? 'line' : 'bar',
    axis: 'left',
  }));
}

function seriesTypeOf(series: ChartSeries): 'bar' | 'line' {
  return series.type === 'line' || series.type === 'area' ? 'line' : 'bar';
}

function applySeriesStyle(target: Record<string, any>, series: ChartSeries, chartType: ChartType, widget: DashboardWidget): void {
  const subtype = widget.chart?.subtype;
  if (seriesTypeOf(series) === 'line') {
    target.type = 'line';
    if (series.type === 'area' || chartType === 'area') {
      target.areaStyle = target.areaStyle ?? {};
    }
    const smooth = series.smooth ?? subtype === 'smooth';
    if (smooth) {
      target.smooth = true;
    }
    const step = series.step ?? (subtype === 'step' ? true : undefined);
    if (step !== undefined) {
      target.step = step;
      target.smooth = false;
    }
  } else {
    target.type = 'bar';
  }
  if (series.stack) {
    target.stack = series.stack;
  }
}

function setupCartesianAxes(option: Record<string, any>, orientation: ChartOrientation, categories: any[], hasRightAxis: boolean): void {
  if (orientation === 'horizontal') {
    setValueAxis(option, 'xAxis');
    setCategoryAxis(option, categories, 'yAxis');
    if (hasRightAxis) {
      const existing = Array.isArray(option.xAxis) ? option.xAxis : [];
      const left = existing[0] ?? option.xAxis ?? { type: 'value' };
      const right = existing[1] ?? { type: 'value' };
      option.xAxis = [
        { ...left, type: 'value' },
        { ...right, type: 'value' },
      ];
    }
    return;
  }
  setCategoryAxis(option, categories, 'xAxis');
  if (hasRightAxis) {
    const existing = Array.isArray(option.yAxis) ? option.yAxis : [];
    const left = existing[0] ?? option.yAxis ?? { type: 'value' };
    const right = existing[1] ?? { type: 'value' };
    option.yAxis = [
      { ...left, type: 'value' },
      { ...right, type: 'value' },
    ];
  } else {
    setValueAxis(option, 'yAxis');
  }
}

function setCategoryAxis(option: Record<string, any>, categories: any[], axisKey: 'xAxis' | 'yAxis'): void {
  setAxisData(option, axisKey, categories);
}

/**
 * 构建最终 ECharts option；无数据或未声明 encode 时原样返回样式模板。
 */
export function buildEchartsOption(widget: DashboardWidget, result?: DatasetResult): Record<string, any> {
  const option: Record<string, any> = sanitizeEchartsOption(JSON.parse(JSON.stringify(widget.echartsOption || {})));
  // 组件卡片已展示标题，移除图表内 title 避免重复（默认保留 dashboard-widget-title）
  delete option.title;
  const chartType = (widget.chart?.type || firstSeriesType(option) || 'bar') as ChartType;
  const orientation = widget.chart?.orientation ?? 'vertical';
  const subtype = widget.chart?.subtype;
  if (chartType === 'waterfall') {
    option.series = baseSeries(option).map(series => ({ ...series, type: 'bar' }));
  }

  if (isAxisChart(chartType)) {
    if (!option.xAxis) {
      option.xAxis = { type: 'category' };
    }
    if (!option.yAxis) {
      option.yAxis = { type: 'value' };
    }
  } else {
    delete option.xAxis;
    delete option.yAxis;
    delete option.grid;
  }

  const encode = widget.encode;
  if (!encode || !result || !Array.isArray(result.rows) || result.rows.length === 0) {
    return option;
  }

  const columns = result.columns ?? [];
  const fieldLabel = (field?: string): string => (field ? result.columnLabels?.[field] || field : '');
  const dimensions = encode.dimensions ?? [];
  const measures = measuresOf(encode);
  if (dimensions.length === 0 && measures.length === 0) {
    return option;
  }
  const xDimension = dimensions.find(dimension => dimension.role === 'x');
  const seriesDimension = dimensions.find(dimension => dimension.role === 'series');
  const xField = resolveNameField(xDimension?.field, columns);
  const seriesField = chartUsesSeriesDimension(chartType) ? resolveNameField(seriesDimension?.field, columns) : undefined;
  const measureFields = measures.map(measure => resolveNameField(measure.field, columns) ?? measure.field);
  if (measureFields.length === 0) {
    return option;
  }

  const effectiveEncode: WidgetEncode = {
    ...encode,
    dimensions: dimensions.map(dimension => ({
      ...dimension,
      field: resolveNameField(dimension.field, columns) ?? dimension.field,
    })),
    measures: measures.map(measure => ({
      ...measure,
      field: resolveNameField(measure.field, columns) ?? measure.field,
    })),
    sortField: resolveNameField(encode.sortField ?? undefined, columns),
  };
  const groupedRows =
    result.aggregated || chartType === 'boxplot'
      ? result.rows
      : effectiveEncode.groupBy
        ? aggregateChartRows(effectiveEncode, result.rows, chartType)
        : result.rows;

  const hasPercentTransform = (encode.transform ?? []).some(transform => transform.type === 'percentOfTotal');
  let rows =
    hasPercentTransform || subtype === 'stackedPercent'
      ? applyPercentTransform(groupedRows, measureFields, xField, seriesField, chartType, subtype)
      : groupedRows;
  rows = applyEncodeRows(effectiveEncode, rows);

  if (isSingleValueChart(chartType)) {
    const valueField = measureFields[0];
    const base = seriesTemplate(baseSeries(option), 0);
    const seriesType = chartType === 'funnel' ? 'funnel' : chartType === 'treemap' ? 'treemap' : 'pie';
    const series: Record<string, any> = {
      ...base,
      type: seriesType,
      name: base.name || fieldLabel(valueField),
      data: rows.map(row => ({
        name: xField ? row[xField] : fieldLabel(valueField),
        value: toNumber(row[valueField]),
      })),
    };
    if (chartType === 'donut') {
      series.radius = base.radius ?? ['42%', '68%'];
      series.avoidLabelOverlap = true;
    }
    if (chartType === 'funnel') {
      series.sort = base.sort ?? (encode.sortField ? 'none' : 'descending');
      series.gap = base.gap ?? 2;
      series.label = base.label ?? { show: true, position: 'inside' };
    }
    if (chartType === 'treemap') {
      series.leafDepth = base.leafDepth ?? 1;
      series.breadcrumb = base.breadcrumb ?? { show: false };
      series.roam = base.roam ?? false;
      series.label = base.label ?? { show: true };
    }
    option.series = [series];
    return option;
  }

  if (chartType === 'gauge') {
    const valueField = measureFields[0];
    const measure = measures.find(item => item.field === valueField);
    const values = result.rows.map(row => toNumber(row[valueField])).filter((value): value is number => value !== null);
    const value = measure ? aggregateValues(values, measure.agg) : (values[0] ?? null);
    const base = seriesTemplate(baseSeries(option), 0);
    option.series = [
      {
        ...base,
        type: 'gauge',
        data: [
          {
            value,
            name: widget.title || fieldLabel(valueField),
          },
        ],
      },
    ];
    return option;
  }

  if (chartType === 'radar') {
    const indicatorNames = xField ? Array.from(new Set(rows.map(row => row[xField]))) : [];
    const rowFor = (name: any) => (xField ? rows.find(row => row[xField] === name) : undefined);
    const indicator = indicatorNames.map(name => {
      const maxValue = Math.max(...measureFields.map(field => toNumber(rowFor(name)?.[field]) ?? 0), 0);
      return { name: String(name), max: maxValue > 0 ? Math.ceil(maxValue * 1.1) : 1 };
    });
    const base = seriesTemplate(baseSeries(option), 0);
    option.radar = { ...(option.radar ?? {}), indicator };
    option.series = [
      {
        ...base,
        type: 'radar',
        data: measureFields.map((field, index) => ({
          name: fieldLabel(field) || `系列 ${index + 1}`,
          value: indicatorNames.map(name => toNumber(rowFor(name)?.[field]) ?? 0),
        })),
      },
    ];
    return option;
  }

  if (chartType === 'scatter') {
    const yMeasures = measures.filter(measure => measure.role === 'y');
    const scatterMeasures = yMeasures.length > 0 ? yMeasures : [measures[0]];
    const sizeMeasure = measures.find(measure => measure.role === 'size');
    const xValues = xField ? rows.map(row => row[xField]) : [];
    const numericX = xValues.every(value => toNumber(value) !== null);
    const xCategories = Array.from(new Set(xValues));
    if (numericX) {
      setValueAxis(option, 'xAxis');
      setValueAxis(option, 'yAxis');
    } else {
      setCategoryAxis(option, xCategories, 'xAxis');
      setValueAxis(option, 'yAxis');
    }
    const sizeValues = sizeMeasure ? rows.map(row => toNumber(row[sizeMeasure.field]) ?? 0) : [];
    const maxSize = Math.max(...sizeValues, 0);
    const pointFor = (row: Record<string, any>, yField: string) => {
      const rawX = xField ? row[xField] : null;
      const x = numericX ? toNumber(rawX) : xCategories.indexOf(rawX);
      const y = toNumber(row[yField]);
      const point: Record<string, any> = { value: [x, y] };
      if (sizeMeasure && maxSize > 0) {
        const sizeValue = toNumber(row[sizeMeasure.field]) ?? 0;
        point.symbolSize = 8 + Math.round((sizeValue / maxSize) * 24);
      }
      return point;
    };
    const base = baseSeries(option);
    const generated: Record<string, any>[] = [];
    if (seriesField) {
      const groups = Array.from(new Set(rows.map(row => row[seriesField])));
      for (const [measureIndex, measure] of scatterMeasures.entries()) {
        const yField = measure.field;
        for (const group of groups) {
          const groupRows = rows.filter(row => row[seriesField] === group);
          generated.push({
            ...seriesTemplate(base, measureIndex),
            type: 'scatter',
            name: `${fieldLabel(yField)} · ${String(group)}`,
            data: groupRows.map(row => pointFor(row, yField)),
          });
        }
      }
    } else {
      for (const measure of scatterMeasures) {
        const field = measure.field;
        const fieldName = fieldLabel(field);
        generated.push({
          ...seriesTemplate(base, generated.length),
          type: 'scatter',
          name: fieldName,
          data: rows.map(row => pointFor(row, field)),
        });
      }
    }
    option.series = generated;
    return option;
  }

  if (chartType === 'heatmap') {
    const xCategories = xField ? Array.from(new Set(rows.map(row => row[xField]))) : [];
    const yCategories = seriesField ? Array.from(new Set(rows.map(row => row[seriesField]))) : [];
    const valueField =
      measures.find(measure => measure.role === 'value')?.field ??
      measures.find(measure => measure.role === 'y')?.field ??
      measureFields[0];
    const data = rows
      .map(row => {
        const xIndex = xCategories.indexOf(xField ? row[xField] : undefined);
        const yIndex = yCategories.indexOf(seriesField ? row[seriesField] : undefined);
        const value = toNumber(row[valueField]);
        return xIndex >= 0 && yIndex >= 0 && value !== null ? [xIndex, yIndex, value] : null;
      })
      .filter((item): item is number[] => item !== null);
    setCategoryAxis(option, xCategories, 'xAxis');
    setCategoryAxis(option, yCategories, 'yAxis');
    const maxValue = Math.max(...data.map(item => item[2]), 0);
    const visualMap = option.visualMap && !Array.isArray(option.visualMap) ? option.visualMap : {};
    option.visualMap = {
      ...visualMap,
      min: visualMap.min ?? 0,
      max: visualMap.max ?? (maxValue > 0 ? maxValue : 1),
      calculable: visualMap.calculable ?? true,
      orient: visualMap.orient ?? 'horizontal',
      left: visualMap.left ?? 'center',
      bottom: visualMap.bottom ?? 0,
    };
    option.series = [
      {
        ...seriesTemplate(baseSeries(option), 0),
        type: 'heatmap',
        name: fieldLabel(valueField),
        data,
        label: seriesTemplate(baseSeries(option), 0).label ?? { show: true },
      },
    ];
    return option;
  }

  if (chartType === 'sankey' || chartType === 'graph') {
    if (!xField || !seriesField) {
      option.series = [];
      return option;
    }
    const valueField =
      measures.find(measure => measure.role === 'value')?.field ??
      measures.find(measure => measure.role === 'y')?.field ??
      measureFields[0];
    const nodeNames = Array.from(new Set(rows.flatMap(row => [String(row[xField]), String(row[seriesField])])));
    const links = rows.map(row => ({
      source: String(row[xField]),
      target: String(row[seriesField]),
      value: toNumber(row[valueField]) ?? 0,
    }));
    const base = seriesTemplate(baseSeries(option), 0);
    if (chartType === 'sankey') {
      option.series = [
        {
          ...base,
          type: 'sankey',
          data: nodeNames.map(name => ({ name })),
          links,
          emphasis: { focus: 'adjacency' },
          lineStyle: { ...(base.lineStyle ?? {}), curveness: base.lineStyle?.curveness ?? 0.5 },
        },
      ];
    } else {
      option.series = [
        {
          ...base,
          type: 'graph',
          layout: base.layout ?? 'force',
          roam: base.roam ?? true,
          draggable: base.draggable ?? true,
          data: nodeNames.map(name => ({ name })),
          links,
          label: { ...(base.label ?? {}), show: base.label?.show ?? true },
          force: {
            repulsion: 320,
            edgeLength: [60, 120],
            ...(base.force ?? {}),
          },
          lineStyle: { ...(base.lineStyle ?? {}), curveness: base.lineStyle?.curveness ?? 0.1 },
        },
      ];
    }
    return option;
  }

  if (chartType === 'waterfall') {
    const valueField =
      measures.find(measure => measure.role === 'y')?.field ??
      measures.find(measure => measure.role === 'value')?.field ??
      measureFields[0];
    const baseRows = xField ? rows : [];
    let cumulative = 0;
    const baseData: number[] = [];
    const valueData: Array<Record<string, any>> = [];
    for (const row of baseRows) {
      const value = toNumber(row[valueField]) ?? 0;
      const before = cumulative;
      const after = cumulative + value;
      baseData.push(Math.min(before, after));
      valueData.push({
        value: Math.abs(value),
        itemStyle: { color: value >= 0 ? '#67c23a' : '#f56c6c' },
      });
      cumulative = after;
    }
    setCategoryAxis(
      option,
      baseRows.map(row => row[xField as string]),
      'xAxis',
    );
    setValueAxis(option, 'yAxis');
    const base = seriesTemplate(baseSeries(option), 0);
    option.series = [
      {
        ...base,
        type: 'bar',
        name: '累计基数',
        stack: 'waterfall',
        data: baseData,
        itemStyle: { color: 'transparent' },
        emphasis: { disabled: true },
        silent: true,
        label: { show: false },
        tooltip: { show: false },
      },
      {
        ...base,
        type: 'bar',
        name: fieldLabel(valueField),
        stack: 'waterfall',
        data: valueData,
      },
    ];
    return option;
  }

  if (chartType === 'boxplot') {
    const valueField =
      measures.find(measure => measure.role === 'y')?.field ??
      measures.find(measure => measure.role === 'value')?.field ??
      measureFields[0];
    const boxCategories = xField ? Array.from(new Set(rows.map(row => row[xField]))) : [];
    const boxData = boxCategories.map(category => {
      const values = rows
        .filter(row => row[xField as string] === category)
        .map(row => toNumber(row[valueField]))
        .filter((value): value is number => value !== null)
        .sort((left, right) => left - right);
      if (values.length === 0) {
        return [0, 0, 0, 0, 0];
      }
      return [values[0], quantile(values, 0.25), quantile(values, 0.5), quantile(values, 0.75), values[values.length - 1]];
    });
    setCategoryAxis(option, boxCategories, 'xAxis');
    setValueAxis(option, 'yAxis');
    option.series = [
      {
        ...seriesTemplate(baseSeries(option), 0),
        type: 'boxplot',
        name: fieldLabel(valueField),
        data: boxData,
      },
    ];
    return option;
  }

  const categories = xField ? (seriesField ? Array.from(new Set(rows.map(row => row[xField]))) : rows.map(row => row[xField])) : [];
  const seriesDefs = normalizeSeriesDefs(widget, chartType);
  const hasRightAxis = seriesDefs.some(series => series.axis === 'right');
  setupCartesianAxes(option, orientation, categories, hasRightAxis);
  if (hasPercentTransform || subtype === 'stackedPercent') {
    const axisKey = orientation === 'horizontal' ? 'xAxis' : 'yAxis';
    const applyPercentAxis = (axis: Record<string, any>) => ({
      ...axis,
      max: 100,
      axisLabel: { ...(axis.axisLabel ?? {}), formatter: axis.axisLabel?.formatter ?? '{value}%' },
    });
    const axis = option[axisKey];
    option[axisKey] = Array.isArray(axis) ? axis.map(applyPercentAxis) : applyPercentAxis(axis ?? { type: 'value' });
  }

  const base = baseSeries(option);
  const stack = subtype === 'stacked' || subtype === 'stackedPercent' || seriesDefs.some(series => !!series.stack) ? 'total' : undefined;
  const generated: Record<string, any>[] = [];

  for (const [defIndex, def] of seriesDefs.entries()) {
    const measureField = resolveNameField(def.measure, columns) ?? def.measure;
    if (!measureFields.includes(measureField)) {
      continue;
    }
    const groups = seriesField ? Array.from(new Set(rows.map(row => row[seriesField]))) : [undefined];
    for (const group of groups) {
      const data = seriesField
        ? categories.map(category => {
            const row = rows.find(item => {
              if (xField && item[xField] !== category) {
                return false;
              }
              return group === undefined || item[seriesField] === group;
            });
            return row ? toNumber(row[measureField]) : null;
          })
        : rows.map(row => toNumber(row[measureField]));
      const nameParts = [def.name || fieldLabel(measureField)];
      if (seriesField && group !== undefined) {
        nameParts.push(String(group));
      }
      const target: Record<string, any> = {
        ...seriesTemplate(base, defIndex),
        name: nameParts.join(' · '),
        data,
      };
      applySeriesStyle(target, def, chartType, widget);
      if (stack) {
        target.stack = def.stack || stack;
      }
      if (def.axis === 'right') {
        if (orientation === 'horizontal') {
          target.xAxisIndex = 1;
        } else {
          target.yAxisIndex = 1;
        }
      }
      generated.push(target);
    }
  }
  option.series = generated;
  return option;
}

/**
 * 计算 KPI 单值：取 encode.measures[0]，否则取首个数值列。
 */
export function resolveKpiValue(widget: DashboardWidget, result?: DatasetResult): { value: number | null; label?: string } {
  if (!result || !Array.isArray(result.rows) || result.rows.length === 0) {
    return { value: null };
  }
  const rows = result.rows;
  const encode = widget.encode;
  const measure = encode?.measures?.[0];
  const field = measure?.field ?? result.numericColumns?.[0];
  if (!field) {
    return { value: null };
  }
  const values = rows.map(row => toNumber(row[field])).filter((value): value is number => value !== null);
  return {
    value: aggregateValues(values, result.aggregated ? 'none' : (measure?.agg ?? 'sum')),
    label: measure?.name,
  };
}
