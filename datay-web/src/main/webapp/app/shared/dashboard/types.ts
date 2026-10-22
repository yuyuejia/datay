/**
 * 分析看板 spec 类型定义。
 *
 * 与后端 `DashboardSpecValidator` 的规范化结构保持一致，供设计页预览与独立查看器共用。
 */

export type DatasetType = 'METRIC' | 'SQL';

export type WidgetType = 'echarts' | 'html' | 'table' | 'kpi';

export type ChartType = 'bar' | 'line' | 'pie' | 'area';

/** 从组件库拖拽到画布时携带的组件信息。 */
export interface WidgetPalettePayload {
  type: WidgetType;
  chartType?: ChartType;
  w: number;
  h: number;
}

/** DataTransfer 中组件库拖拽数据使用的 MIME 类型。 */
export const WIDGET_PALETTE_MIME = 'application/x-datay-dashboard-widget';

export type FilterType = 'dateRange' | 'select' | 'multiSelect' | 'input' | 'numberRange';

export type BindingKind = 'metricTime' | 'metricDimension' | 'metricFactField' | 'sqlParam';

export interface DashboardLayout {
  columns?: number;
  rowHeight?: number;
  gap?: number;
}

export interface MetricDimensionSpec {
  dimensionModelCode: string;
  dimensionFieldNames?: string[];
  dimensionFieldName?: string;
  levelIndex?: number;
}

export interface MetricQuerySpec {
  metricCodes: string[];
  dimensions?: MetricDimensionSpec[];
  timeRange?: { start?: string; end?: string };
  filterConfig?: string | Record<string, any> | null;
}

export interface DashboardDataset {
  id: string;
  name?: string;
  type: DatasetType;
  /** 引用数据集库中的数据集 id；存在时 metricQuery/sql 由服务端按最新定义解析。 */
  refId?: string;
  metricQuery?: MetricQuerySpec;
  sql?: string;
  dataSourceId?: string;
  /** 数据集字段缓存，仅设计器使用，不参与看板运行时查询。 */
  fields?: string;
}

export interface DataRefSeries {
  name?: string;
  field: string;
}

export interface DataRef {
  categoryField?: string;
  valueField?: string;
  series?: DataRefSeries[];
  seriesField?: string | null;
  sortField?: string | null;
  sortOrder?: 'asc' | 'desc';
  limit?: number;
  /** 图表按分类字段聚合数值；表格/KPI 不使用该配置。 */
  groupBy?: boolean;
}

export interface WidgetLayout {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface DashboardWidget {
  id: string;
  title?: string;
  type: WidgetType;
  layout: WidgetLayout;
  datasetId?: string;
  dataRef?: DataRef;
  echartsOption?: Record<string, any>;
  html?: string;
}

export interface FilterOptions {
  mode?: 'dimension' | 'static';
  dimensionModelCode?: string;
  dimensionFieldName?: string;
  values?: string[];
}

export interface FilterBinding {
  datasetId: string;
  kind: BindingKind;
  dimensionModelCode?: string;
  dimensionFieldName?: string;
  factFieldName?: string;
  param?: string;
  paramEnd?: string;
}

export interface DashboardFilter {
  id: string;
  type: FilterType;
  label?: string;
  defaultValue?: any;
  options?: FilterOptions;
  /** 筛选器直接绑定的看板数据集别名。 */
  datasetId?: string;
  /** 数据集中的筛选字段；下拉选项也取自该字段的去重值。 */
  field?: string;
  /** 兼容旧版看板的多绑定配置。 */
  bindings?: FilterBinding[];
}

export interface DashboardSpec {
  title?: string;
  description?: string;
  layout?: DashboardLayout;
  datasets: DashboardDataset[];
  widgets: DashboardWidget[];
  filters?: DashboardFilter[];
}

export interface DatasetResult {
  columns?: string[];
  rows?: Record<string, any>[];
  /** 列标签：字段名 → 展示名称（通常为字段描述，为空时回退字段名）。 */
  columnLabels?: Record<string, string>;
  affectedRows?: number;
  sql?: string;
}

export type FilterValues = Record<string, any>;
