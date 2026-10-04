/** 数据应用资产包的市场条目。 */
export interface IAppPackage {
  id?: number;
  tenantId?: string | null;
  name?: string | null;
  code?: string | null;
  description?: string | null;
  /** 业务场景分类。 */
  category?: string | null;
  version?: string | null;
  /** SYSTEM 系统预制 / TENANT 租户导出。 */
  packageType?: string | null;
  status?: string | null;
  /** 资产构成摘要。 */
  itemSummary?: Record<string, number>;
  /** 资产包内容 JSON（列表接口不返回）。 */
  content?: string | null;
  contentSize?: number | null;
  createUser?: string | null;
  createTime?: Date | null;
  updateTime?: Date | null;
}

/** 资产包清单结构（前端只读取用于预览）。 */
export interface IAppPackageContent {
  packageFormat?: string;
  formatVersion?: string;
  meta?: {
    name?: string;
    code?: string;
    description?: string;
    category?: string;
    version?: string;
    exportTime?: string;
    source?: string;
    exportedBy?: string;
  };
  dataSources?: Array<{ oldId?: number; name?: string; type?: string; url?: string }>;
  modelDirectories?: Array<{ oldId?: number; name?: string }>;
  models?: Array<{ oldId?: number; name?: string; code?: string; modelType?: string; fields?: unknown[] }>;
  metricDirectories?: Array<{ oldId?: number; name?: string }>;
  metrics?: Array<{ oldId?: number; name?: string; code?: string; metricType?: string; unit?: string }>;
  etlTasks?: Array<{ oldId?: number; taskName?: string; taskCode?: string; nodes?: unknown[]; edges?: unknown[] }>;
  sqlJobs?: Array<{ oldId?: number; jobName?: string; cron?: string }>;
  dagJobs?: Array<{ oldId?: number; jobName?: string; cron?: string }>;
  otherJobs?: Array<{ oldId?: number; jobName?: string; type?: string }>;
  jobDepends?: unknown[];
  summary?: Record<string, number>;
}

/** 导出向导候选资产。 */
export interface IAppPackageOption {
  id?: number;
  name?: string | null;
  code?: string | null;
  subtitle?: string | null;
  owner?: string | null;
}

export interface IAppPackageExportOptions {
  dataSources: IAppPackageOption[];
  models: IAppPackageOption[];
  metrics: IAppPackageOption[];
  etlTasks: IAppPackageOption[];
  sqlJobs: IAppPackageOption[];
  dagJobs: IAppPackageOption[];
  categories: string[];
}

/** 导出请求。 */
export interface IAppPackageExportRequest {
  name?: string;
  code?: string;
  description?: string;
  category?: string;
  version?: string;
  dataSourceIds: number[];
  modelIds: number[];
  metricIds: number[];
  etlTaskIds: number[];
  sqlJobIds: number[];
  dagJobIds: number[];
  includeReferences: boolean;
  saveToMarket: boolean;
  overwrite?: boolean;
}

/** 数据源处理明细。 */
export interface IAppPackageDataSourceMapping {
  oldId?: number;
  newId?: number;
  key?: string;
  name?: string;
  action?: 'CREATED' | 'REUSED' | 'MAPPED';
}

/** 初始化请求。 */
export interface IAppPackageInitRequest {
  packageId?: number | null;
  content?: string | null;
  conflictStrategy?: 'RENAME' | 'SKIP';
  dataSourceMapping?: Record<string, number>;
  onlineJobs?: boolean;
}

/** 初始化结果。 */
export interface IAppPackageInitResult {
  packageId?: number | null;
  packageName?: string | null;
  packageCode?: string | null;
  status?: string | null;
  message?: string | null;
  counts?: Record<string, number>;
  dataSources?: IAppPackageDataSourceMapping[];
  idMapping?: Record<string, Record<string, number>>;
  warnings?: string[];
  /** 初始化后的建议操作，例如「先运行某某编排任务」。 */
  nextSteps?: string[];
}

/** 数据应用初始化记录。 */
export interface IAppPackageInstance {
  id?: number;
  tenantId?: string | null;
  packageId?: number | null;
  packageCode?: string | null;
  packageName?: string | null;
  status?: string | null;
  message?: string | null;
  mapping?: Record<string, any>;
  createUser?: string | null;
  createTime?: Date | null;
}

/** 资产构成摘要的展示顺序与中文名。 */
export const APP_PACKAGE_SUMMARY_LABELS: Array<{ key: string; label: string }> = [
  { key: 'dataSources', label: '数据源' },
  { key: 'modelDirectories', label: '模型目录' },
  { key: 'models', label: '数据模型' },
  { key: 'modelFields', label: '模型字段' },
  { key: 'metricDirectories', label: '指标目录' },
  { key: 'metrics', label: '指标' },
  { key: 'etlTasks', label: 'ETL 任务' },
  { key: 'sqlJobs', label: 'SQL 任务' },
  { key: 'dagJobs', label: '编排任务' },
  { key: 'otherJobs', label: '其它任务' },
  { key: 'jobDepends', label: '任务依赖' },
];

/** 安全解析资产包内容 JSON。 */
export function parsePackageContent(content?: string | null): IAppPackageContent | null {
  if (!content) {
    return null;
  }
  try {
    return JSON.parse(content);
  } catch {
    return null;
  }
}

/** 资产构成摘要转成「名称：数量」列表，只保留数量大于 0 的项。 */
export function summaryEntries(summary?: Record<string, number> | null): Array<{ label: string; value: number }> {
  if (!summary) {
    return [];
  }
  return APP_PACKAGE_SUMMARY_LABELS.filter((item) => (summary[item.key] || 0) > 0).map((item) => ({
    label: item.label,
    value: summary[item.key],
  }));
}

/**
 * 从后端错误响应里挑出可读的错误说明。
 *
 * <p>后端 {@code detail} 是真正的业务错误信息（可能带 {@code 400 BAD_REQUEST "..."} 前缀）；
 * {@code message} 往往是 {@code error.http.400} 这类 i18n key，因此只在没有 detail 时才回退到 message。
 */
export function pickHttpErrorMessage(error: any, fallback: string): string {
  const data = error?.response?.data ?? {};
  for (const candidate of [data.detail, data.message]) {
    if (typeof candidate !== 'string') {
      continue;
    }
    const text = candidate
      .trim()
      .replace(/^\d{3}\s+[A-Z_]+\s+"/, '')
      .replace(/"$/, '')
      .trim();
    if (text && !text.startsWith('error.')) {
      return text;
    }
  }
  return fallback;
}
