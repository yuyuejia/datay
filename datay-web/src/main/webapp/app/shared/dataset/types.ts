/** 分析数据集类型定义，对应后端 AnalysisDatasetDTO。 */

export type AnalysisDatasetType = 'METRIC' | 'SQL';

export type AnalysisDatasetStatus = 'ENABLED' | 'DISABLED';

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
  createTime?: string;
  updateTime?: string;
}

export interface DatasetColumn {
  name: string;
  label?: string;
  type?: string;
}

export interface DatasetFields {
  columns?: Array<DatasetColumn | string>;
  columnLabels?: Record<string, string>;
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
  fields?: Array<{ fieldName: string; fieldType?: string; description?: string }>;
  levels?: Array<{
    levelIndex: number;
    idField?: string;
    nameField?: string;
    granularity?: string;
    label?: string;
  }>;
}

export interface MetricQueryMeta {
  metrics?: Array<{ name: string; code: string; metricType?: string }>;
  dimensions?: MetricDimensionMeta[];
  timeFields?: string[];
}
