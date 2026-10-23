import axios from 'axios';

import type { AnalysisDataset, AnalysisDatasetType, DatasetColumn, DatasetFields, MetricQueryMeta } from './types';

const baseApiUrl = 'api/analysis-datasets';

export interface DatasetPageParams {
  page?: number;
  size?: number;
  search?: string;
}

export interface DatasetPreviewPayload {
  name?: string;
  type: AnalysisDatasetType;
  dataSourceId?: string;
  definition: string;
  filterValues?: Record<string, any>;
}

export interface DatasetPreviewResult {
  columns?: string[];
  rows?: Record<string, any>[];
  columnLabels?: Record<string, string>;
  affectedRows?: number;
  sql?: string;
}

/**
 * 分析数据集服务：数据集 CRUD、定义校验、预览，以及指标元数据查询。
 */
export default class DatasetService {
  list(params: DatasetPageParams = {}): Promise<AnalysisDataset[]> {
    return axios
      .get(baseApiUrl, {
        params: {
          page: params.page ?? 0,
          size: params.size ?? 200,
          search: params.search || undefined,
        },
      })
      .then(res => res.data);
  }

  all(): Promise<AnalysisDataset[]> {
    return axios.get(`${baseApiUrl}/all`).then(res => res.data);
  }

  get(id: string): Promise<AnalysisDataset> {
    return axios.get(`${baseApiUrl}/${id}`).then(res => res.data);
  }

  getByCode(code: string): Promise<AnalysisDataset> {
    return axios.get(`${baseApiUrl}/by-code/${encodeURIComponent(code)}`).then(res => res.data);
  }

  create(dto: AnalysisDataset): Promise<AnalysisDataset> {
    return axios.post(baseApiUrl, dto).then(res => res.data);
  }

  update(id: string, dto: AnalysisDataset): Promise<AnalysisDataset> {
    return axios.put(`${baseApiUrl}/${id}`, { ...dto, id }).then(res => res.data);
  }

  remove(id: string): Promise<void> {
    return axios.delete(`${baseApiUrl}/${id}`).then(() => undefined);
  }

  validate(dto: AnalysisDataset): Promise<AnalysisDataset> {
    return axios.post(`${baseApiUrl}/validate`, dto).then(res => res.data);
  }

  previewDefinition(payload: DatasetPreviewPayload): Promise<DatasetPreviewResult> {
    return axios.post(`${baseApiUrl}/preview`, payload).then(res => res.data);
  }

  preview(id: string, filterValues?: Record<string, any>): Promise<DatasetPreviewResult> {
    return axios.post(`${baseApiUrl}/${id}/preview`, filterValues ?? {}).then(res => res.data);
  }

  /** 指标数据集可选择指标与维度。 */
  loadMetricMeta(metricCodes: string[]): Promise<MetricQueryMeta> {
    return axios.post('api/metrics/query-meta', { metricCodes }).then(res => res.data);
  }

  static parseDefinition(dataset?: AnalysisDataset): Record<string, any> {
    if (!dataset?.definition) {
      return {};
    }
    if (typeof dataset.definition !== 'string') {
      return dataset.definition;
    }
    try {
      return JSON.parse(dataset.definition);
    } catch {
      return {};
    }
  }

  static parseFields(dataset?: AnalysisDataset): DatasetFields {
    if (!dataset?.fields) {
      return {};
    }
    if (typeof dataset.fields !== 'string') {
      return dataset.fields as DatasetFields;
    }
    try {
      return JSON.parse(dataset.fields);
    } catch {
      return {};
    }
  }

  /** 把缓存的列信息统一成字符串列名列表。 */
  static columnNames(fields?: DatasetFields): string[] {
    const columns = fields?.columns ?? [];
    return columns.map(column => (typeof column === 'string' ? column : column.name)).filter((name): name is string => !!name);
  }

  static columnLabel(fields: DatasetFields | undefined, name: string): string {
    return fields?.columnLabels?.[name] || name;
  }

  static columns(fields?: DatasetFields): DatasetColumn[] {
    const columns = fields?.columns ?? [];
    return columns.map(column => {
      if (typeof column === 'string') {
        return { name: column, label: fields?.columnLabels?.[column] || column };
      }
      const description = column.description || fields?.columnLabels?.[column.name] || column.label || column.name;
      return {
        ...column,
        description,
        label: column.label || description,
      };
    });
  }
}
