import axios from "axios";

const baseApiUrl = "api/ai/metric-rag";

export interface MetricRagStatus {
  enabled?: boolean;
  indexed?: boolean;
  provider?: string;
  dimensions?: number;
  metricCount?: number;
  dimensionCount?: number;
  memberCount?: number;
  builtAt?: string;
  memberIndexEnabled?: boolean;
  error?: string;
}

export interface MetricRagRebuildResult {
  enabled?: boolean;
  provider?: string;
  metricCount?: number;
  dimensionCount?: number;
  memberCount?: number;
  builtAt?: string;
  message?: string;
}

/**
 * 指标知识库（RAG）服务：查看索引状态与手动重建。
 */
export default class MetricRagService {
  getStatus(): Promise<MetricRagStatus> {
    return axios.get(`${baseApiUrl}/status`).then((res) => res.data);
  }

  rebuild(): Promise<MetricRagRebuildResult> {
    return axios.post(`${baseApiUrl}/rebuild`).then((res) => res.data);
  }
}
