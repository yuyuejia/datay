/**
 * 分析看板 spec 类型定义。
 *
 * 与后端 `DashboardSpecValidator` 的规范化结构保持一致，供设计页预览与独立查看器共用。
 */

export type DatasetType = 'METRIC' | 'SQL';

export type WidgetType = 'echarts' | 'html' | 'table' | 'kpi' | 'pivot' | 'title' | 'clock' | 'container';

export type TitleFrame = 'none' | 'tech-frame' | 'bracket';

export type ClockFormat = 'date' | 'datetime' | 'time' | 'date-week';

/** 大屏标题栏 / 日期时间组件的展示内容。 */
export interface WidgetProps {
  text?: string;
  subText?: string;
  align?: 'left' | 'center' | 'right';
  frame?: TitleFrame;
  format?: ClockFormat;
}

export type ChartType =
  | 'bar'
  | 'line'
  | 'area'
  | 'pie'
  | 'donut'
  | 'gauge'
  | 'scatter'
  | 'radar'
  | 'funnel'
  | 'heatmap'
  | 'treemap'
  | 'waterfall'
  | 'sankey'
  | 'graph'
  | 'boxplot'
  | 'map';

export type ChartSubtype = 'grouped' | 'stacked' | 'stackedPercent' | 'smooth' | 'step';

export type ChartOrientation = 'vertical' | 'horizontal';

export type ChartSeriesType = 'bar' | 'line' | 'area';

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

/** 大屏画布定位方式：grid 使用 24 栅格，px 使用画布绝对像素。 */
export type CanvasUnit = 'grid' | 'px';

export interface ScreenCanvas {
  unit: CanvasUnit;
  /** 画布内边距，0 表示贴边。 */
  padding: number;
  /** 栅格间距，仅 grid 模式生效。 */
  gap: number;
}

export type ThemePreset = 'tech-blue' | 'tech-cyan' | 'gov-red' | 'ink-dark' | 'custom';

export type ThemeFontFamily = 'system' | 'number-digital' | 'number-condensed';

export interface ThemeMapTokens {
  area?: string;
  border?: string;
  emphasis?: string;
  label?: string;
}

/** 大屏主题 token：预设提供默认值，看板可逐项覆盖。 */
export interface ThemeTokens {
  primary?: string;
  secondary?: string;
  accent?: string;
  text?: string;
  textSecondary?: string;
  surface?: string;
  border?: string;
  /** 图表序列色，最多 12 个。 */
  palette?: string[];
  fontFamily?: ThemeFontFamily;
  map?: ThemeMapTokens;
}

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
  /** 画布定位与留白配置；缺省为 24 栅格 + 16px 内边距。 */
  canvas?: ScreenCanvas;
  /** 主题预设名称；tokens 为其覆盖项。 */
  themePreset?: ThemePreset;
  tokens?: ThemeTokens;
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

/** v2 维度字段：x 作为分类轴，series 作为系列拆分，row/column 用于透视表。 */
export interface WidgetEncodeDimension {
  field: string;
  role: 'x' | 'series' | 'row' | 'column';
  name?: string;
}

/** v2 度量字段：y 用于坐标轴，value 用于单值/透视场景，size 用于散点大小。 */
export interface WidgetEncodeMeasure {
  field: string;
  agg: Aggregation;
  role: 'y' | 'value' | 'size';
  name?: string;
  format?: WidgetFormat;
}

/** v2 数据变换；P0 支持占总比。 */
export interface WidgetTransform {
  type: 'percentOfTotal';
  measure?: string;
}

export interface WidgetTableColumn {
  field: string;
  label?: string;
  width?: number;
  format?: WidgetFormat;
}

export interface WidgetEncode {
  dimensions?: WidgetEncodeDimension[];
  measures?: WidgetEncodeMeasure[];
  sortField?: string | null;
  sortOrder?: 'asc' | 'desc';
  limit?: number;
  groupBy?: boolean;
  transform?: WidgetTransform[];
  /** 表格列配置；按数组顺序展示。 */
  columns?: WidgetTableColumn[];
  /** 表格前端分页大小；不配置时不分页。 */
  pageSize?: number;
}

/** v2 图表声明：chart.type 是唯一权威类型来源，echartsOption 只做样式覆盖。 */
export interface ChartSeries {
  measure: string;
  type: ChartSeriesType;
  axis?: 'left' | 'right';
  stack?: string;
  smooth?: boolean;
  step?: boolean | 'start' | 'middle' | 'end';
  name?: string;
}

export type MapLayerKind = 'region' | 'effectScatter' | 'lines';

/** 地图图层：region 填色 / effectScatter 涟漪点 / lines 飞线。 */
export interface MapLayerSpec {
  kind: MapLayerKind;
  /** 图层数据集；缺省使用组件自身数据集。 */
  datasetId?: string;
  /** region：区域数值字段与聚合方式。 */
  valueField?: string;
  agg?: Aggregation;
  /** effectScatter：经纬度与名称字段。 */
  lngField?: string;
  latField?: string;
  nameField?: string;
  /** effectScatter：点大小（px）。 */
  symbolSize?: number[];
  /** effectScatter / lines：颜色。 */
  color?: string;
  /** lines：起止经纬度字段。 */
  fromLngField?: string;
  fromLatField?: string;
  toLngField?: string;
  toLatField?: string;
  /** lines：曲率 0..1。 */
  curveness?: number;
  /** lines：飞线动画。 */
  effect?: { show?: boolean; period?: number; trailLength?: number };
}

export interface DashboardChart {
  type?: ChartType;
  subtype?: ChartSubtype;
  orientation?: ChartOrientation;
  series?: ChartSeries[];
  /** 地图区域；当前仅内置中国省级地图。 */
  map?: 'china';
  /** 地图多图层；为空时沿用单层地图渲染。 */
  layers?: MapLayerSpec[];
  [key: string]: any;
}

export interface WidgetFormat {
  unit?: string;
  precision?: number;
  prefix?: string;
  suffix?: string;
}

export type FormatRuleOperator = 'gt' | 'gte' | 'lt' | 'lte' | 'eq' | 'neq' | 'between';

export interface WidgetFormatRule {
  id?: string;
  /** 可选：只对指定字段生效；省略时对组件内所有数值单元格生效。 */
  field?: string;
  operator: FormatRuleOperator;
  value: number;
  value2?: number;
  color?: string;
  background?: string;
  bold?: boolean;
}

export interface WidgetLayout {
  x: number;
  y: number;
  w: number;
  h: number;
}

export type CardPreset = 'none' | 'tech-corner' | 'tech-frame' | 'bracket';

export type WidgetFillType = 'none' | 'color' | 'gradient';

export interface WidgetFill {
  type: WidgetFillType;
  color?: string;
  gradient?: { from: string; to: string; angle: number };
}

export interface WidgetCardStyle {
  preset?: CardPreset;
  fill?: WidgetFill;
  /** 0..1 */
  opacity?: number;
  border?: {
    width?: number;
    color?: string;
    style?: 'solid' | 'dashed' | 'dotted';
    radius?: number;
  };
  shadow?: { x?: number; y?: number; blur?: number; spread?: number; color?: string };
  padding?: number;
  backdropBlur?: number;
}

export interface WidgetTitleStyle {
  show?: boolean;
  icon?: string;
  fontSize?: number;
  fontWeight?: number;
  color?: string;
  align?: 'left' | 'center' | 'right';
  height?: number;
  letterSpacing?: number;
  background?: string;
  decoration?: 'none' | 'underline' | 'gradient-underline' | 'left-bar';
}

export interface WidgetBodyStyle {
  padding?: number;
}

export type ChartStylePreset = 'tech' | 'minimal' | 'dark';

export type LegendPosition = 'top' | 'bottom' | 'left' | 'right';

export type LineStyleType = 'solid' | 'dashed' | 'dotted';

/** 单个坐标轴的样式覆盖：显隐、标题、标签、刻度与网格线。 */
export interface WidgetChartAxisStyle {
  show?: boolean;
  /** 轴标题文字；留空沿用 echartsOption 中的 name。 */
  title?: string;
  /** 轴标签字号。 */
  labelFontSize?: number;
  /** 是否显示刻度标记。 */
  tick?: boolean;
  /** 轴线颜色；留空跟随图表风格预设。 */
  lineColor?: string;
  /** 网格线（分割线）显隐。 */
  grid?: boolean;
  /** 网格线样式。 */
  gridStyle?: LineStyleType;
}

/** 图表样式预设：只填充缺省样式，echartsOption 中的显式配置优先。 */
export interface WidgetChartStyle {
  preset?: ChartStylePreset;
  /** 柱状/面积渐变；缺省按预设决定。 */
  gradient?: boolean;
  /** 折线发光；缺省按预设决定。 */
  glow?: boolean;
  showAxis?: boolean;
  showLegend?: boolean;
  showLabel?: boolean;
  /** 图例位置。 */
  legendPosition?: LegendPosition;
  /** 图例文字字号。 */
  legendFontSize?: number;
  /** 轴标题字号。 */
  axisTitleFontSize?: number;
  /** 轴标题颜色；留空跟随图表风格预设。 */
  axisTitleColor?: string;
  /** 数据标签字号。 */
  labelFontSize?: number;
  /** 柱条圆角。 */
  barRadius?: number;
  /** 横轴样式覆盖。 */
  xAxis?: WidgetChartAxisStyle;
  /** 纵轴样式覆盖。 */
  yAxis?: WidgetChartAxisStyle;
}

export type KpiLayout = 'label-top' | 'label-left' | 'value-only';

export interface WidgetKpiStyle {
  /** 覆盖组件标题作为 KPI 标签。 */
  label?: string;
  icon?: string;
  layout?: KpiLayout;
  value?: {
    fontSize?: number;
    color?: string;
    fontFamily?: ThemeFontFamily;
    bold?: boolean;
    letterSpacing?: number;
  };
  unit?: {
    text?: string;
    fontSize?: number;
    color?: string;
  };
  /** 数值变化时是否做滚动动画。 */
  animate?: boolean;
  /** 小数位，覆盖 format.precision。 */
  decimals?: number;
}

/** 图表点击行为：无 / 明细查看 / 图表联动。 */
export type WidgetClickAction = 'none' | 'detail' | 'linkage';

/** 跨数据集联动时的字段关联：当前图表字段 → 目标图表字段。 */
export interface WidgetLinkageMapping {
  sourceField: string;
  targetField: string;
}

/** 联动目标组件；同数据集无需 fieldMap。 */
export interface WidgetLinkageTarget {
  widgetId: string;
  fieldMap?: WidgetLinkageMapping[];
}

/** 组件交互配置：点击行为与联动目标。 */
export interface WidgetInteraction {
  clickAction?: WidgetClickAction;
  targets?: WidgetLinkageTarget[];
}

/** 运行时联动条件：字段与点击取值。 */
export interface LinkageCondition {
  field: string;
  values: Array<string | number>;
}

/** 选中态：点击的数据项位置，用于源图表高亮、其余置灰。 */
export interface LinkageSelection {
  seriesIndex?: number;
  dataIndex?: number;
  dataType?: string;
}

/** 图表联动回调：来源组件、点击维度与选中数据项。 */
export type WidgetLinkageHandler = (widgetId: string, dimensions: DrillDimension[], selection?: LinkageSelection) => void;

/** 当前生效的图表联动。 */
export interface ActiveWidgetLinkage {
  sourceWidgetId: string;
  sourceTitle: string;
  /** 展示用文案，如「城市 = 北京」。 */
  label: string;
  conditions: LinkageCondition[];
  /** 本次联动实际生效的目标组件 id，切换/取消时用于精确恢复。 */
  targetWidgetIds?: string[];
  /** 源图表中被点击的数据项，用于高亮选中、置灰其余。 */
  selection?: LinkageSelection;
}

/** 组件皮肤；只影响 DOM 呈现，不参与取数与聚合。 */
export interface WidgetStyle {
  card?: WidgetCardStyle;
  title?: WidgetTitleStyle;
  body?: WidgetBodyStyle;
  kpi?: WidgetKpiStyle;
  chart?: WidgetChartStyle;
}

export interface DashboardWidget {
  id: string;
  title?: string;
  type: WidgetType;
  layout: WidgetLayout;
  /** 叠放层级，仅 px 画布生效；0 表示默认层级。 */
  zIndex?: number;
  /** 组件皮肤（卡片/标题/内容区）。 */
  style?: WidgetStyle;
  /** 标题栏 / 日期时间的文本与格式。 */
  props?: WidgetProps;
  /** 点击交互与图表联动配置。 */
  interaction?: WidgetInteraction;
  /** 所属容器 id；容器本身不再嵌套。 */
  parentId?: string;
  datasetId?: string;
  /** v2 显式映射与聚合。 */
  encode?: WidgetEncode;
  /** v2 图表类型与系列声明。 */
  chart?: DashboardChart;
  /** KPI 等组件的展示格式。 */
  format?: WidgetFormat;
  /** 表格/透视表/KPI 的条件格式规则。 */
  formatRules?: WidgetFormatRule[];
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

/** 筛选目标统一由服务端执行；client 仅为历史类型兼容。 */
export type FilterTargetMode = 'server' | 'client';

/** 筛选器目标：一个筛选器可同时作用于多个数据集。 */
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
  /** 筛选目标，支持多数据集与 pushdown。 */
  targets: FilterTarget[];
}

export interface DashboardSpec {
  version: 2;
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
  /** 结果是否已由服务端完成分组聚合。 */
  aggregated?: boolean;
  /** 结果是否因全局行数上限被截断。 */
  truncated?: boolean;
  rowLimit?: number;
}

export type FilterValues = Record<string, any>;

export type WidgetPageLoader = (widgetId: string, page: number) => Promise<void>;
export type TablePageLoader = (page: number) => Promise<void>;

/** 图表明细下钻的单个维度条件。 */
export interface DrillDimension {
  field: string;
  values: Array<string | number>;
}

/** 明细下钻请求参数（分页由服务端执行）。 */
export interface WidgetDrillRequest {
  /** 发起下钻的组件 id，用于叠加该组件的联动条件。 */
  widgetId?: string;
  datasetId: string;
  dimensions: DrillDimension[];
  page: number;
  pageSize: number;
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}

/** 明细加载器：由 useDashboardData 提供，内部负责拼接当前筛选器与分页参数。 */
export type WidgetDrillLoader = (request: WidgetDrillRequest) => Promise<DatasetResult>;
