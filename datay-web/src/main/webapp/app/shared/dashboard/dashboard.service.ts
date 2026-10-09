import axios from "axios";

import type { DashboardSpec, DatasetResult, FilterValues } from "./types";

const baseApiUrl = "api/analysis-dashboards";

export interface AnalysisDashboard {
  id?: string;
  name: string;
  code?: string;
  description?: string;
  dataSourceId?: string;
  spec?: string;
  status?: string;
  createTime?: string;
  updateTime?: string;
}

export interface FilterOptionResult {
  values: string[];
  total: number;
}

/**
 * 分析看板服务：看板 CRUD、数据集取数、筛选器选项、spec 预检。
 *
 * 同时被主应用（设计 / 管理页）与独立看板页引用。
 */
export default class DashboardService {
  list(
    params: { page?: number; size?: number; search?: string } = {},
  ): Promise<AnalysisDashboard[]> {
    const query: Record<string, any> = {
      page: params.page ?? 0,
      size: params.size ?? 20,
    };
    if (params.search) {
      query.search = params.search;
    }
    return axios.get(baseApiUrl, { params: query }).then((res) => res.data);
  }

  get(id: string): Promise<AnalysisDashboard> {
    return axios.get(`${baseApiUrl}/${id}`).then((res) => res.data);
  }

  getByCode(code: string): Promise<AnalysisDashboard> {
    return axios
      .get(`${baseApiUrl}/by-code/${encodeURIComponent(code)}`)
      .then((res) => res.data);
  }

  create(dto: AnalysisDashboard): Promise<AnalysisDashboard> {
    return axios.post(baseApiUrl, dto).then((res) => res.data);
  }

  update(id: string, dto: AnalysisDashboard): Promise<AnalysisDashboard> {
    return axios.put(`${baseApiUrl}/${id}`, dto).then((res) => res.data);
  }

  remove(id: string): Promise<void> {
    return axios.delete(`${baseApiUrl}/${id}`).then(() => undefined);
  }

  queryDataset(
    id: string,
    datasetId: string,
    filterValues?: FilterValues,
  ): Promise<DatasetResult> {
    return axios
      .post(
        `${baseApiUrl}/${id}/datasets/${encodeURIComponent(datasetId)}/query`,
        filterValues ?? {},
      )
      .then((res) => res.data);
  }

  previewDataset(
    spec: DashboardSpec,
    datasetId: string,
    filterValues?: FilterValues,
    dataSourceId?: string,
  ): Promise<DatasetResult> {
    return axios
      .post(`${baseApiUrl}/preview-dataset`, {
        spec,
        datasetId,
        filterValues: filterValues ?? {},
        dataSourceId,
      })
      .then((res) => res.data);
  }

  loadFilterOptions(
    id: string,
    filterId: string,
    keyword?: string,
  ): Promise<FilterOptionResult> {
    return axios
      .get(
        `${baseApiUrl}/${id}/filters/${encodeURIComponent(filterId)}/options`,
        { params: keyword ? { keyword } : {} },
      )
      .then((res) => res.data);
  }

  previewFilterOptions(
    spec: DashboardSpec,
    filterId: string,
    keyword?: string,
  ): Promise<FilterOptionResult> {
    return axios
      .post(`${baseApiUrl}/preview-filter-options`, { spec, filterId, keyword })
      .then((res) => res.data);
  }

  validateSpec(spec: DashboardSpec | string): Promise<DashboardSpec> {
    return axios
      .post(`${baseApiUrl}/validate-spec`, { spec })
      .then((res) => res.data);
  }

  /** 解析看板 DTO 中的 spec 字符串。 */
  static parseSpec(dashboard: AnalysisDashboard): DashboardSpec {
    if (!dashboard?.spec) {
      throw new Error("看板定义为空");
    }
    return typeof dashboard.spec === "string"
      ? JSON.parse(dashboard.spec)
      : (dashboard.spec as unknown as DashboardSpec);
  }
}
