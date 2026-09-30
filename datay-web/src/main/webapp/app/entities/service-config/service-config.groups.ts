import FileManagementService, { type FileStorageConfig } from '@/entities/file-management/file-management.service';
import EtlTaskStateService, { type IStatusStorageConfig } from '@/entities/etl-task/etl-task-state.service';
import DataSourceService from '@/entities/data-source/data-source.service';

export type ConfigFieldType = 'text' | 'password' | 'select' | 'datasource';

export interface ConfigField {
  key: string;
  label: string;
  type: ConfigFieldType;
  placeholder?: string;
  hint?: string;
  options?: Array<{ label: string; value: string }>;
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
];

export default SERVICE_CONFIG_GROUPS;
