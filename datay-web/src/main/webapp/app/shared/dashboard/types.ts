/**
 * 分析看板 spec 类型定义。
 *
 * 与后端 `DashboardSpecValidator` 的规范化结构保持一致，供设计页预览与独立查看器共用。
 */

export type DatasetType = 'METRIC' | 'SQL';

export type WidgetType = 'echarts' | 'html' | 'table' | 'kpi';

export type ChartType = 'bar' | 'line' | 'pie' | 'area';

export type Aggregation = 'sum' | 'avg' | 'min' | 'max' | 'count' | 'none';

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

/** 看板展示模式：standard 为自适应画布，screen 为固定分辨率大屏。 */
export type DashboardMode = 'standard' | 'screen';

export type ScreenFitMode = 'contain' | 'stretch' | 'cover';

export type DashboardTheme = 'light' | 'dark';

export type DashboardBackgroundType = 'color' | 'gradient' | 'image';

export interface DashboardGradient {
  from: string;
  to: string;
  angle: number;
}

export interface DashboardBackground {
  type: DashboardBackgroundType;
  color?: string;
  gradient?: DashboardGradient;
  /** 服务端文件存储中的背景图路径。 */
  imagePath?: string;
  imageContentType?: string;
}

export interface DashboardScreen {
  width: number;
  height: number;
  fitMode?: ScreenFitMode;
  theme?: DashboardTheme;
  background?: DashboardBackground;
  /** 自动刷新间隔（秒），0 表示关闭。 */
  autoRefreshSeconds?: number;
}

export interface DashboardLayout {
  mode?: DashboardMode;
  columns?: number;
  rowHeight?: number;
  gap?: number;
  /** 大屏模式下的设计分辨率（逻辑像素）。 */
  screen?: DashboardScreen;
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

/** v1 显式数据映射与聚合配置。 */
export interface WidgetEncodeValue {
  field: string;
  name?: string;
  agg?: Aggregation;
}

export interface WidgetTableColumn {
  field: string;
  label?: string;
  width?: number;
  format?: WidgetFormat;
}

export interface WidgetEncode {
  categoryField?: string;
  values?: WidgetEncodeValue[];
  seriesField?: string | null;
  sortField?: string | null;
  sortOrder?: 'asc' | 'desc';
  limit?: number;
  groupBy?: boolean;
  /** 表格列配置；按数组顺序展示。 */
  columns?: WidgetTableColumn[];
  /** 表格前端分页大小；不配置时不分页。 */
  pageSize?: number;
}

/** v1 图表类型声明；具体样式仍由 echartsOption 承载。 */
export interface DashboardChart {
  type?: string;
  [key: string]: any;
}

export interface WidgetFormat {
  unit?: string;
  precision?: number;
  prefix?: string;
  suffix?: string;
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
  /** v1 显式映射与聚合。 */
  encode?: WidgetEncode;
  /** v1 图表类型。 */
  chart?: DashboardChart;
  /** KPI 等组件的展示格式。 */
  format?: WidgetFormat;
  echartsOption?: Record<string, any>;
  html?: string;
}

export interface FilterOptions {
  values?: string[];
}

export interface FilterPushdown {
  kind: BindingKind;
  dimensionModelCode?: string;
  dimensionFieldName?: string;
  factFieldName?: string;
  param?: string;
  paramEnd?: string;
}

export type FilterTargetMode = 'server' | 'client';

/** v1 筛选器目标：一个筛选器可同时作用于多个数据集。 */
export interface FilterTarget {
  datasetId: string;
  /** 直接字段筛选（当前只支持客户端过滤）。 */
  field?: string;
  mode?: FilterTargetMode;
  /** 服务端下推配置；datasetId 由 target 提供。 */
  pushdown?: FilterPushdown;
}

export interface DashboardFilter {
  id: string;
  type: FilterType;
  label?: string;
  defaultValue?: any;
  options?: FilterOptions;
  /** v1 筛选目标，支持多数据集与 pushdown。 */
  targets: FilterTarget[];
}

export interface DashboardSpec {
  version: 1;
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
  /** 列类型：字段名 → 数据集字段缓存中的类型。 */
  columnTypes?: Record<string, string>;
  /** 数值列，供格式化与聚合提示使用。 */
  numericColumns?: string[];
  affectedRows?: number;
  sql?: string;
  /** 结果是否因全局行数上限被截断。 */
  truncated?: boolean;
  rowLimit?: number;
}

export type FilterValues = Record<string, any>;

export type WidgetPageLoader = (widgetId: string, page: number) => Promise<void>;
export type TablePageLoader = (page: number) => Promise<void>;
