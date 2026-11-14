export interface IDataApi {
  id?: string;
  name?: string | null;
  code?: string | null;
  description?: string | null;
  dataSourceId?: string | null;
  sourceType?: string | null;
  schemaName?: string | null;
  tableName?: string | null;
  sqlText?: string | null;
  apiConfig?: string | null;
  datasetId?: string | null;
  directoryId?: string | null;
  datasetName?: string | null;
  datasetType?: string | null;
  status?: string | null;
  tenantId?: string | null;
  updateTime?: Date | null;
  createTime?: Date | null;
}

/** 前置处理：调用其他接口获取 Token 的配置。 */
export interface IApiPreProcess {
  enabled: boolean;
  url: string;
  method: string;
  headers?: Record<string, string>;
  body?: string;
  /** 从响应中提取 Token 的 JSONPath，如 $.data.accessToken。 */
  tokenPath?: string;
  /** 缓存秒数，<=0 表示不缓存。 */
  cacheTtlSeconds?: number;
  /** 前置请求是否校验 SSL 证书，默认校验。 */
  sslVerify?: boolean;
}

/** 已注册 HTTP API 的转发配置，序列化后存入 apiConfig 字段。 */
export interface IApiConfig {
  url: string;
  method: string;
  headers?: Record<string, string>;
  body?: string;
  timeout?: number;
  sslVerify?: boolean;
  /** 从响应中提取返回给调用方的数据路径（JSONPath），留空返回完整响应。 */
  jsonPath?: string;
  preProcess?: IApiPreProcess;
}

/** 安全解析后端返回的 apiConfig JSON 字符串。 */
export function parseApiConfig(apiConfig?: string | null): IApiConfig | null {
  if (!apiConfig) {
    return null;
  }
  try {
    return JSON.parse(apiConfig);
  } catch {
    return null;
  }
}

export class DataApi implements IDataApi {
  constructor(
    public id?: string,
    public name?: string | null,
    public code?: string | null,
    public description?: string | null,
    public dataSourceId?: string | null,
    public sourceType?: string | null,
    public schemaName?: string | null,
    public tableName?: string | null,
    public sqlText?: string | null,
    public status?: string | null,
    public tenantId?: string | null,
    public updateTime?: Date | null,
    public createTime?: Date | null,
    public apiConfig?: string | null,
    public datasetId?: string | null,
    public directoryId?: string | null,
  ) {}
}

/** 数据服务调用日志。 */
export interface IDataApiCallLog {
  id?: string;
  apiId?: string | null;
  apiCode?: string | null;
  apiName?: string | null;
  sourceType?: string | null;
  requestSource?: string | null;
  requestMethod?: string | null;
  requestPath?: string | null;
  requestParams?: string | null;
  callerUserId?: number | null;
  callerLogin?: string | null;
  clientIp?: string | null;
  userAgent?: string | null;
  status?: string | null;
  httpStatus?: number | null;
  errorMessage?: string | null;
  durationMs?: number | null;
  resultRows?: number | null;
  statDate?: string | null;
  statHour?: number | null;
  createTime?: Date | string | null;
}

/** 调用统计的单日趋势。 */
export interface IDataApiCallTrendPoint {
  date: string;
  totalCount: number;
  successCount: number;
  failureCount: number;
  avgDurationMs: number;
  maxDurationMs: number;
}

/** 数据服务调用统计汇总。 */
export interface IDataApiCallStats {
  apiId?: string | null;
  apiCode?: string | null;
  apiName?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  totalCount: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  avgDurationMs: number;
  maxDurationMs: number;
  trend: IDataApiCallTrendPoint[];
}

export const DATA_API_SOURCE_TYPE_TABLE = 'TABLE';
export const DATA_API_SOURCE_TYPE_SQL = 'SQL';
export const DATA_API_SOURCE_TYPE_API = 'API';
export const DATA_API_SOURCE_TYPE_DATASET = 'DATASET';
export const DATA_API_STATUS_ENABLED = 'ENABLED';
export const DATA_API_STATUS_DISABLED = 'DISABLED';
