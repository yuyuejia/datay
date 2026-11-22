/** 分析数据集类型定义，对应后端 AnalysisDatasetDTO。 */

export type AnalysisDatasetType = 'METRIC' | 'SQL';

export type AnalysisDatasetStatus = 'ENABLED' | 'DISABLED';

export type DatasetPersistStatus = 'DISABLED' | 'PENDING' | 'RUNNING' | 'READY' | 'FAILED' | 'STALE';

export type DatasetPersistWriteMode = 'REPLACE' | 'APPEND';

export interface AnalysisDataset {
  id?: string;
  tenantId?: string;
  name: string;
  code?: string;
  description?: string;
  type: AnalysisDatasetType;
  dataSourceId?: string;
  /** 与类型相关的定义 JSON 字符串，由后端规范化。 */
  definition?: string;
  /** 最近一次预览的列信息 JSON 字符串。 */
  fields?: string;
  status?: AnalysisDatasetStatus;
  /** 是否启用结果持久化。 */
  persistEnabled?: boolean;
  /** 持久化目标数据源。 */
  persistTargetDataSourceId?: string;
  /** 持久化目标 schema。 */
  persistTargetSchema?: string;
  /** 持久化目标表。 */
  persistTargetTable?: string;
  /** 写入模式。 */
  persistWriteMode?: DatasetPersistWriteMode;
  /** 持久化 Cron，为空表示仅手动执行。 */
  persistCron?: string;
  /** 持久化结果状态。 */
  persistStatus?: DatasetPersistStatus;
  persistLastRunTime?: string;
  persistLastSuccessTime?: string;
  persistLastError?: string;
  persistDefinitionHash?: string;
  persistEtlTaskId?: string;
  persistJobId?: string;
  persistRowCount?: number;
  /** 已发布的数据服务信息，由后端列表接口返回。 */
  publishedApiId?: string;
  publishedApiCode?: string;
  publishedApiStatus?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DatasetColumn {
  name: string;
  /** 字段展示名称。 */
  label?: string;
  /** 字段业务描述，图表属性与图例优先使用该名称。 */
  description?: string;
  type?: string;
  /** 是否主键字段。 */
  primaryKey?: boolean;
}

export interface DatasetFields {
  columns?: Array<DatasetColumn | string>;
  columnLabels?: Record<string, string>;
}

export interface MetricDimensionRoleMeta {
  code: string;
  name?: string;
  description?: string;
  sortOrder?: number;
  factFieldName?: string;
}

export interface MetricDimensionMeta {
  dimensionModelCode: string;
  dimensionModelName?: string;
  factFieldName?: string;
  isHierarchy?: boolean;
  dimensionKind?: string;
  isTimeDimension?: boolean;
  displayFieldName?: string;
  recommendedDisplayField?: string;
  roles?: MetricDimensionRoleMeta[];
  fields?: Array<{ fieldName: string; fieldType?: string; description?: string }>;
  levels?: Array<{
    levelIndex: number;
    idField?: string;
    nameField?: string;
    granularity?: string;
    label?: string;
  }>;
  levelCount?: number;
}

export interface MetricTimeFieldMeta {
  fieldName: string;
  fieldType?: string;
  factTable?: string;
}

export interface MetricQueryMeta {
  metrics?: Array<{ name: string; code: string; metricType?: string }>;
  factTables?: string[];
  dimensions?: MetricDimensionMeta[];
  timeFields?: MetricTimeFieldMeta[];
}
