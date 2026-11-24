import type { DashboardWidget, DrillDimension } from './types';

/** ECharts series 上保存下钻元数据的字段：下标与 series.data 一一对应。 */
export const DRILL_META_KEY = '__datayDrill';

/** 桑基图/关系图边（links）上的下钻元数据。 */
export const DRILL_LINK_META_KEY = '__datayDrillLinks';

/** ECharts 点击事件中会用到的字段。 */
export interface DrillClickParams {
  seriesIndex?: number;
  dataIndex?: number;
  dataType?: string;
  name?: string;
  seriesName?: string;
  data?: Record<string, any>;
}

/** 有明确分类轴、可以通过 params.name 兜底的下钻图表类型。 */
const NAME_FALLBACK_TYPES = new Set(['bar', 'line', 'area', 'pie', 'donut', 'funnel', 'treemap', 'waterfall', 'boxplot']);

/** 支持点击查看明细的图表类型（元数据存在时生效）。 */
const DRILLABLE_TYPES = new Set([...NAME_FALLBACK_TYPES, 'scatter', 'heatmap', 'map', 'sankey', 'graph']);

/**
 * 解析组件的图表类型：优先 chart.type，其次 echartsOption.series[0].type，最后回退 bar。
 */
export function resolveWidgetChartType(widget: DashboardWidget): string {
  if (widget.chart?.type) {
    return widget.chart.type;
  }
  const series = widget.echartsOption?.series;
  const first = Array.isArray(series) ? series[0] : series;
  return typeof first?.type === 'string' ? first.type : 'bar';
}

/** 图表是否支持明细下钻（仅用于鼠标样式和点击入口判断）。 */
export function isDrillableWidget(widget: DashboardWidget): boolean {
  return widget.type === 'echarts' && DRILLABLE_TYPES.has(resolveWidgetChartType(widget));
}

function cleanDimensions(raw: unknown): DrillDimension[] | null {
  if (!Array.isArray(raw)) {
    return null;
  }
  const result: DrillDimension[] = [];
  const seen = new Set<string>();
  for (const item of raw) {
    if (!item || typeof item !== 'object') {
      continue;
    }
    const field = (item as DrillDimension).field;
    const values = (item as DrillDimension).values;
    if (typeof field !== 'string' || !field || seen.has(field) || !Array.isArray(values)) {
      continue;
    }
    const cleaned = values.filter(value => value !== null && value !== undefined && String(value) !== '');
    if (cleaned.length === 0) {
      continue;
    }
    seen.add(field);
    result.push({ field, values: cleaned });
  }
  return result.length > 0 ? result : null;
}

function metaFromOption(params: DrillClickParams, option?: Record<string, any>): DrillDimension[] | null {
  if (!option) {
    return null;
  }
  const seriesList = Array.isArray(option.series) ? option.series : option.series ? [option.series] : [];
  const series = seriesList[params.seriesIndex ?? 0];
  if (!series) {
    return null;
  }
  const dataIndex = typeof params.dataIndex === 'number' ? params.dataIndex : -1;
  if (params.dataType === 'edge' && Array.isArray(series[DRILL_LINK_META_KEY])) {
    return dataIndex >= 0 ? cleanDimensions(series[DRILL_LINK_META_KEY][dataIndex]) : null;
  }
  if (Array.isArray(series[DRILL_META_KEY])) {
    return dataIndex >= 0 ? cleanDimensions(series[DRILL_META_KEY][dataIndex]) : null;
  }
  return null;
}

/**
 * 把一次 ECharts 点击转换为下钻维度条件。
 *
 * <p>优先使用 buildEchartsOption 写入 series 的元数据，保证地图原始区域名、
 * 系列拆分等场景都能得到准确条件；简单分类图在元数据缺失时按 params.name 兜底。</p>
 */
export function resolveDrillDimensions(
  params: DrillClickParams,
  widget: DashboardWidget,
  option?: Record<string, any>,
): DrillDimension[] | null {
  const meta = metaFromOption(params, option);
  if (meta) {
    return meta;
  }
  const chartType = resolveWidgetChartType(widget);
  if (!NAME_FALLBACK_TYPES.has(chartType)) {
    return null;
  }
  const xField = widget.encode?.dimensions?.find(dimension => dimension.role === 'x')?.field;
  if (!xField || params.name === undefined || params.name === null || String(params.name) === '') {
    return null;
  }
  return [{ field: xField, values: [params.name] }];
}

/** 条件展示用的字段标签：优先数据集列标签，其次维度声明名称，最后字段名。 */
export function drillFieldLabel(widget: DashboardWidget | undefined, field: string, columnLabels?: Record<string, string>): string {
  const label = columnLabels?.[field];
  if (label) {
    return label;
  }
  const declared = widget?.encode?.dimensions?.find(dimension => dimension.field === field)?.name;
  return declared || field;
}
