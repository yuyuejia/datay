import FileManagementService, { type FileStorageConfig } from '@/entities/file-management/file-management.service';
import EtlTaskStateService, { type IStatusStorageConfig } from '@/entities/etl-task/etl-task-state.service';
import DataSourceService from '@/entities/data-source/data-source.service';
import LlmConfigService, { type ILlmConfig } from './llm-config.service';

export type ConfigFieldType = 'text' | 'password' | 'select' | 'datasource' | 'number' | 'switch';

export interface ConfigField {
  key: string;
  label: string;
  type: ConfigFieldType;
  placeholder?: string;
  hint?: string;
  options?: Array<{ label: string; value: string }>;
  min?: number;
  max?: number;
  step?: number;
  precision?: number;
  showWhen?: (values: Record<string, any>) => boolean;
}

export interface ConfigTestResult {
  success?: boolean;
  strategy?: string;
  error?: string;
}

/**
 * 服务配置分组定义。
 *
 * 每个分组声明其可配置项（字段元数据）以及读写/测试绑定，
 * 页面据此渲染表单，是“当前租户可配置项”的单一来源。
 */
export interface ConfigGroup {
  key: string;
  label: string;
  description: string;
  fields: ConfigField[];
  load: () => Promise<Record<string, any>>;
  save: (values: Record<string, any>) => Promise<void>;
  test?: () => Promise<ConfigTestResult>;
}

const STORAGE_TYPE_OPTIONS = [
  { label: '本地文件（local）', value: 'local' },
  { label: 'MinIO 对象存储（minio）', value: 'minio' },
];

const isLocal = (values: Record<string, any>): boolean => values.type !== 'minio';
const isMinio = (values: Record<string, any>): boolean => values.type === 'minio';

const fileStorageFields: ConfigField[] = [
  {
    key: 'type',
    label: '存储类型',
    type: 'select',
    options: STORAGE_TYPE_OPTIONS,
    hint: '单机部署可选本地文件，集群部署建议使用 MinIO。',
  },
  {
    key: 'localBasePath',
    label: '本地存储目录',
    type: 'text',
    placeholder: './data/files',
    showWhen: isLocal,
  },
  {
    key: 'minioEndpoint',
    label: 'MinIO 地址',
    type: 'text',
    placeholder: 'http://127.0.0.1:9000',
    showWhen: isMinio,
  },
  { key: 'minioAccessKey', label: 'Access Key', type: 'text', showWhen: isMinio },
  { key: 'minioSecretKey', label: 'Secret Key', type: 'password', showWhen: isMinio },
  { key: 'minioBucketName', label: 'Bucket', type: 'text', placeholder: 'datay-files', showWhen: isMinio },
];

const statusStorageFields: ConfigField[] = [
  {
    key: 'type',
    label: '存储类型',
    type: 'select',
    options: STORAGE_TYPE_OPTIONS,
    hint: '单机部署使用 local；master/worker 分离部署必须使用 minio 以共享增量状态。',
  },
  {
    key: 'localBasePath',
    label: '本地状态目录',
    type: 'text',
    placeholder: './log',
    showWhen: isLocal,
  },
  {
    key: 'minioEndpoint',
    label: 'MinIO 地址',
    type: 'text',
    placeholder: 'http://127.0.0.1:9000',
    showWhen: isMinio,
  },
  { key: 'minioAccessKey', label: 'Access Key', type: 'text', showWhen: isMinio },
  { key: 'minioSecretKey', label: 'Secret Key', type: 'password', showWhen: isMinio },
  { key: 'minioBucketName', label: 'Bucket', type: 'text', placeholder: 'datay-status', showWhen: isMinio },
];

const llmFields: ConfigField[] = [
  {
    key: 'enabled',
    label: '启用大模型',
    type: 'switch',
    hint: '关闭后 AI 助手与「大模型」组件均不可用。',
  },
  {
    key: 'baseUrl',
    label: 'API 地址',
    type: 'text',
    placeholder: 'https://api.deepseek.com/v1',
    hint: 'OpenAI 兼容的接口根地址，需包含版本段（如 /v1）；留空则使用系统级配置。',
  },
  {
    key: 'apiKey',
    label: 'API Key',
    type: 'password',
    hint: '留空则使用系统级配置中的 API Key；系统级亦为空时大模型不可用。',
  },
  { key: 'model', label: '模型', type: 'text', placeholder: 'deepseek-chat', hint: '留空则使用系统级配置。' },
  {
    key: 'temperature',
    label: '采样温度',
    type: 'number',
    min: 0,
    max: 2,
    step: 0.1,
    precision: 2,
    hint: '生成 SQL 等场景建议保持较低值。',
  },
  { key: 'timeoutSeconds', label: '请求超时（秒）', type: 'number', min: 1, max: 600, step: 1 },
  { key: 'maxToolRounds', label: '最大工具轮次', type: 'number', min: 1, max: 20, step: 1, hint: 'Agent 循环的轮次上限。' },
  { key: 'maxToolCallsPerRound', label: '单轮最大工具调用', type: 'number', min: 1, max: 50, step: 1 },
  {
    key: 'allowMutatingTools',
    label: '允许写操作工具',
    type: 'switch',
    hint: '默认禁止，需与助手自身声明同时放行。',
  },
];

export const SERVICE_CONFIG_GROUPS: ConfigGroup[] = [
  {
    key: 'data-warehouse',
    label: '默认数仓',
    description: '选择当前租户的默认数仓，数据模型物化时将默认使用该数据源。',
    fields: [{ key: 'dataSourceId', label: '默认数仓数据源', type: 'datasource', hint: '仅列出当前租户的数据源。' }],
    load: async () => {
      const res = await new DataSourceService().getDefaultWarehouse();
      return { dataSourceId: res.data?.dataSourceId ?? null };
    },
    save: async values => {
      if (values.dataSourceId == null) {
        throw new Error('请选择默认数仓数据源');
      }
      await new DataSourceService().setDefaultWarehouse(values.dataSourceId);
    },
  },
  {
    key: 'file-storage',
    label: '文件存储',
    description: '配置上传文件与 ETL 文件的存储后端，决定文件管理页面读写的存储位置。',
    fields: fileStorageFields,
    load: () => new FileManagementService().getStorageConfig().then(cfg => ({ ...cfg })),
    save: values => new FileManagementService().updateStorageConfig(values as FileStorageConfig),
    test: () => new FileManagementService().testStorageConfig(),
  },
  {
    key: 'status-storage',
    label: '日志目录',
    description: '配置 ETL 任务增量状态/位点的存储目录或 MinIO 后端（即状态存储配置）。',
    fields: statusStorageFields,
    load: () => new EtlTaskStateService().getConfig().then(cfg => ({ ...cfg })),
    save: values => new EtlTaskStateService().updateConfig(values as IStatusStorageConfig),
    test: () => new EtlTaskStateService().testConfig(),
  },
  {
    key: 'llm',
    label: '大模型',
    description: '配置当前租户的 OpenAI 兼容服务（API 助手与「大模型」组件共用）；留空或未配置的项自动使用系统级配置（配置文件 datay.ai.*）。',
    fields: llmFields,
    load: () => new LlmConfigService().getConfig().then(cfg => ({ ...cfg })),
    save: values => new LlmConfigService().updateConfig(values as ILlmConfig),
    test: () => new LlmConfigService().testConfig(),
  },
];

export default SERVICE_CONFIG_GROUPS;
