import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DataSourceService from '@/entities/data-source/data-source.service';
import ETLTaskDirectoryService from '@/entities/etl-task/etl-task-directory.service';
import DatasetService from '@/shared/dataset/dataset.service';
import DataApiService from './data-api.service';
import {
  DataApi,
  type ApiPreProcessType,
  type IApiConfig,
  type IDataApi,
  type IDataApiDebugResult,
  parseApiConfig,
  DATA_API_SOURCE_TYPE_API,
  DATA_API_SOURCE_TYPE_DATASET,
  DATA_API_SOURCE_TYPE_SQL,
  DATA_API_SOURCE_TYPE_TABLE,
  DATA_API_STATUS_ENABLED,
} from '@/shared/model/data-api.model';
import { parseCurl } from '@/shared/util/curl';
import { type IETLTaskDirectory } from '@/shared/model/etl-task-directory.model';
import { useAlertService } from '@/shared/alert/alert.service';

/** 数据服务目录在公共目录表中的类型标识。 */
const DIRECTORY_TASK_TYPE = 'DATA_API';

const PRE_PROCESS_SCRIPT_EXAMPLE = `// params 为调用方传入的参数
return {
  token: 'Bearer ' + params.appId,
  timestamp: String(Date.now()),
  sign: utils.md5(params.appId + params.secret)
};`;

const createDefaultApiConfig = (): IApiConfig => ({
  url: '',
  method: 'GET',
  headers: {},
  body: '',
  timeout: 30000,
  sslVerify: true,
  jsonPath: '',
  preProcess: {
    enabled: false,
    type: 'http',
    url: '',
    method: 'POST',
    headers: {},
    body: '',
    tokenPath: '',
    cacheTtlSeconds: 0,
    sslVerify: true,
    script: '',
  },
});

const parseJsonObject = (text: string): Record<string, string> => {
  if (!text || !text.trim()) {
    return {};
  }
  const parsed = JSON.parse(text);
  return parsed && typeof parsed === 'object' ? parsed : {};
};

const parseHeadersSafely = (text: string): Record<string, string> => {
  try {
    return parseJsonObject(text);
  } catch {
    return {};
  }
};

const PLACEHOLDER_PATTERN = /\$\{([A-Za-z_][A-Za-z0-9_]*)\}/g;

/** 前置脚本里通过 params.xxx / params['xxx'] 读取的调用参数。 */
const SCRIPT_PARAM_PATTERN = /\bparams\s*(?:\.\s*([A-Za-z_$][\w$]*)|\[\s*['"]([^'"]+)['"]\s*\])/g;

type PreProcessMode = 'none' | 'http' | 'script';

/** 从配置文本中提取 ${param} 占位符名称。 */
const extractPlaceholderNames = (...texts: Array<string | null | undefined>): string[] => {
  const names = new Set<string>();
  texts.forEach(text => {
    if (!text) {
      return;
    }
    PLACEHOLDER_PATTERN.lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = PLACEHOLDER_PATTERN.exec(text)) !== null) {
      names.add(match[1]);
    }
  });
  return Array.from(names);
};

/** 从脚本中提取 params.xxx / params['xxx'] 形式的参数名。 */
const extractScriptParameterNames = (script?: string | null): string[] => {
  const names = new Set<string>();
  if (!script) {
    return [];
  }
  SCRIPT_PARAM_PATTERN.lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = SCRIPT_PARAM_PATTERN.exec(script)) !== null) {
    const name = match[1] || match[2];
    if (name) {
      names.add(name);
    }
  }
  return Array.from(names);
};

const prettyJsonText = (text?: string | null): string => {
  if (!text) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
};

const debugErrorMessage = (error: any): string => {
  const data = error?.response?.data;
  return data?.detail || data?.message || data?.title || error?.message || '调试失败';
};

export default defineComponent({
  name: 'DataApiUpdate',
  setup() {
    const dataApiService = inject('dataApiService', () => new DataApiService());
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const etlTaskDirectoryService = inject('etlTaskDirectoryService', () => new ETLTaskDirectoryService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const dataApi: Ref<IDataApi> = ref(new DataApi());
    const isSaving = ref(false);

    const route = useRoute();
    const router = useRouter();
    const previousState = () => router.go(-1);

    const isEdit = ref(false);

    const dataSources = ref<any[]>([]);
    const dataDirectories: Ref<IETLTaskDirectory[]> = ref([]);
    const datasets = ref<any[]>([]);
    const schemas = ref<string[]>([]);
    const tables = ref<string[]>([]);
    const columns = ref<any[]>([]);
    const loadingSchemas = ref(false);
    const loadingTables = ref(false);
    const loadingColumns = ref(false);

    const apiConfig: Ref<IApiConfig> = ref(createDefaultApiConfig());
    const apiHeadersText = ref('{}');
    const preHeadersText = ref('{}');
    const showCurlImport = ref(false);
    const showPreCurlImport = ref(false);
    const curlText = ref('');
    const preCurlText = ref('');

    const debugVisible = ref(false);
    const debugLoading = ref(false);
    const debugResult: Ref<IDataApiDebugResult | null> = ref(null);
    const debugError = ref('');
    const debugParams = ref<Array<{ name: string; value: string }>>([]);
    const debugDetectedNames = ref<string[]>([]);
    const debugActiveTab = ref('data');

    const debugRows = computed<Record<string, any>[]>(() =>
      Array.isArray(debugResult.value?.data) ? (debugResult.value?.data as Record<string, any>[]) : [],
    );
    const debugColumns = computed<string[]>(() => {
      const columns = debugResult.value?.columns;
      if (Array.isArray(columns) && columns.length > 0) {
        return columns;
      }
      const first = debugRows.value[0];
      return first && typeof first === 'object' && !Array.isArray(first) ? Object.keys(first) : [];
    });
    const debugHasTable = computed(() => debugRows.value.length > 0 && debugColumns.value.length > 0);
    const debugJson = computed(() =>
      debugResult.value?.data === undefined || debugResult.value?.data === null ? '' : JSON.stringify(debugResult.value.data, null, 2),
    );
    const debugUpstream = computed(() => debugResult.value?.upstream ?? null);
    const debugPreProcess = computed(() => debugResult.value?.preProcess ?? null);
    const debugPreProcessJson = computed(() => {
      const preProcess = debugPreProcess.value;
      if (!preProcess) {
        return '';
      }
      return JSON.stringify(
        {
          type: preProcess.type,
          durationMs: preProcess.durationMs,
          output: preProcess.output ?? {},
        },
        null,
        2,
      );
    });
    const debugShowRawBody = computed(() => {
      if (!debugResult.value) {
        return false;
      }
      return dataApi.value.sourceType === DATA_API_SOURCE_TYPE_API ? !!debugUpstream.value : true;
    });
    const debugRawBody = computed(() => {
      if (dataApi.value.sourceType === DATA_API_SOURCE_TYPE_API) {
        return prettyJsonText(debugUpstream.value?.body);
      }
      const result = debugResult.value;
      if (!result) {
        return '';
      }
      // 调试结果中 durationMs、upstream 仅用于调试展示，不属于服务实际返回体。
      const body: Record<string, unknown> = {};
      Object.entries(result).forEach(([key, value]) => {
        if (key !== 'durationMs' && key !== 'upstream' && key !== 'preProcess') {
          body[key] = value;
        }
      });
      return JSON.stringify(body, null, 2);
    });
    const debugUpstreamHeaders = computed(() =>
      Object.entries(debugUpstream.value?.headers || {}).map(([name, values]) => ({
        name,
        value: Array.isArray(values) ? values.join(', ') : String(values),
      })),
    );

    const httpMethods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD'];

    const sourceTypeOptions = [
      { value: DATA_API_SOURCE_TYPE_TABLE, label: '数据源的表' },
      { value: DATA_API_SOURCE_TYPE_SQL, label: '自定义 SQL' },
      { value: DATA_API_SOURCE_TYPE_DATASET, label: '分析数据集' },
      { value: DATA_API_SOURCE_TYPE_API, label: '已存在的 API' },
    ];

    const statusOptions = [
      { value: 'ENABLED', label: '启用（对外可访问）' },
      { value: 'DISABLED', label: '停用（禁止对外访问）' },
    ];

    const hasApiBody = computed(() => apiConfig.value.method !== 'GET' && apiConfig.value.method !== 'HEAD');

    const preProcessMode = computed<PreProcessMode>({
      get() {
        const preProcess = apiConfig.value.preProcess;
        if (!preProcess?.enabled) {
          return 'none';
        }
        return preProcess.type === 'script' ? 'script' : 'http';
      },
      set(mode) {
        const preProcess = apiConfig.value.preProcess;
        if (!preProcess) {
          return;
        }
        preProcess.enabled = mode !== 'none';
        preProcess.type = (mode === 'script' ? 'script' : 'http') as ApiPreProcessType;
      },
    });

    const fillScriptExample = () => {
      if (apiConfig.value.preProcess) {
        apiConfig.value.preProcess.script = PRE_PROCESS_SCRIPT_EXAMPLE;
      }
    };

    const loadDataSources = async () => {
      try {
        const res = await dataSourceService().retrieve({ page: 0, size: 500 });
        dataSources.value = res.data || [];
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const loadDatasets = async () => {
      try {
        datasets.value = await new DatasetService().all();
      } catch (error) {
        datasets.value = [];
      }
    };

    const loadDirectories = async () => {
      try {
        const res = await etlTaskDirectoryService().retrieve(DIRECTORY_TASK_TYPE);
        dataDirectories.value = res.data || [];
      } catch (error) {
        dataDirectories.value = [];
      }
    };

    const directoryTreeOptions = computed(() => {
      const byParent = new Map<string, IETLTaskDirectory[]>();
      dataDirectories.value.forEach(directory => {
        const parentId = directory.parentId || '';
        if (!byParent.has(parentId)) {
          byParent.set(parentId, []);
        }
        byParent.get(parentId)!.push(directory);
      });
      const build = (parentId: string): Array<{ id: string; label: string; children?: any[] }> => {
        return (byParent.get(parentId) || [])
          .slice()
          .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || String(a.name || '').localeCompare(String(b.name || '')))
          .map(directory => {
            const children = build(directory.id!);
            const node: { id: string; label: string; children?: any[] } = {
              id: directory.id!,
              label: directory.name || '',
            };
            if (children.length > 0) {
              node.children = children;
            }
            return node;
          });
      };
      return build('');
    });

    const selectedDataset = computed(() => datasets.value.find((item: any) => String(item.id) === String(dataApi.value.datasetId)));

    const fetchSchemas = async () => {
      if (!dataApi.value.dataSourceId) {
        schemas.value = [];
        return;
      }
      loadingSchemas.value = true;
      try {
        const res = await dataSourceService().getSchemas(dataApi.value.dataSourceId);
        schemas.value = res.data || [];
      } catch (error) {
        schemas.value = [];
        alertService.showHttpError(error.response);
      } finally {
        loadingSchemas.value = false;
      }
    };

    const fetchTables = async () => {
      if (!dataApi.value.dataSourceId || !dataApi.value.schemaName) {
        tables.value = [];
        return;
      }
      loadingTables.value = true;
      try {
        const res = await dataSourceService().getTables(dataApi.value.dataSourceId, dataApi.value.schemaName, 1000);
        tables.value = (res.data || []).map((item: any) => item.table);
      } catch (error) {
        tables.value = [];
        alertService.showHttpError(error.response);
      } finally {
        loadingTables.value = false;
      }
    };

    const fetchColumns = async () => {
      if (!dataApi.value.dataSourceId || !dataApi.value.schemaName || !dataApi.value.tableName) {
        columns.value = [];
        return;
      }
      loadingColumns.value = true;
      try {
        const res = await dataSourceService().getFields(dataApi.value.dataSourceId, dataApi.value.schemaName, dataApi.value.tableName);
        columns.value = res.data || [];
      } catch (error) {
        columns.value = [];
        alertService.showHttpError(error.response);
      } finally {
        loadingColumns.value = false;
      }
    };

    const applyApiConfig = (apiConfigText?: string | null) => {
      const parsed = parseApiConfig(apiConfigText);
      if (!parsed) {
        return;
      }
      const defaults = createDefaultApiConfig();
      apiConfig.value = {
        ...defaults,
        ...parsed,
        preProcess: { ...defaults.preProcess, ...(parsed.preProcess || {}) },
      };
      apiHeadersText.value = JSON.stringify(parsed.headers || {}, null, 2);
      preHeadersText.value = JSON.stringify(parsed.preProcess?.headers || {}, null, 2);
    };

    const retrieveDataApi = async (dataApiId: string) => {
      try {
        const res = await dataApiService().find(dataApiId);
        res.createTime = new Date(res.createTime);
        res.updateTime = new Date(res.updateTime);
        dataApi.value = res;
        applyApiConfig(res.apiConfig);
        await loadDataSources();
        await loadDatasets();
        if (res.sourceType === DATA_API_SOURCE_TYPE_TABLE) {
          await fetchSchemas();
          await fetchTables();
          await fetchColumns();
        }
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.dataApiId) {
      isEdit.value = true;
      retrieveDataApi(String(route.params.dataApiId));
      loadDirectories();
    } else {
      const queryDatasetId = route.query?.datasetId ? String(route.query.datasetId) : null;
      if (queryDatasetId) {
        dataApi.value.sourceType = DATA_API_SOURCE_TYPE_DATASET;
        dataApi.value.datasetId = queryDatasetId;
        dataApi.value.name = route.query?.name ? String(route.query.name) : '';
        dataApi.value.code = route.query?.code ? String(route.query.code) : '';
      } else {
        dataApi.value.sourceType = DATA_API_SOURCE_TYPE_TABLE;
      }
      dataApi.value.directoryId = route.query?.directoryId ? String(route.query.directoryId) : null;
      dataApi.value.status = DATA_API_STATUS_ENABLED;
      loadDataSources();
      loadDirectories();
      loadDatasets();
    }

    const onSourceTypeChange = () => {
      dataApi.value.datasetId = null;
      if (dataApi.value.sourceType === DATA_API_SOURCE_TYPE_SQL) {
        dataApi.value.schemaName = null;
        dataApi.value.tableName = null;
        columns.value = [];
      } else if (dataApi.value.sourceType === DATA_API_SOURCE_TYPE_API) {
        dataApi.value.schemaName = null;
        dataApi.value.tableName = null;
        dataApi.value.sqlText = null;
        columns.value = [];
      } else if (dataApi.value.sourceType === DATA_API_SOURCE_TYPE_DATASET) {
        dataApi.value.dataSourceId = null;
        dataApi.value.schemaName = null;
        dataApi.value.tableName = null;
        dataApi.value.sqlText = null;
        columns.value = [];
      }
    };

    const onDataSourceChange = (value: any) => {
      dataApi.value.dataSourceId = value ? String(value) : null;
      dataApi.value.schemaName = null;
      dataApi.value.tableName = null;
      schemas.value = [];
      tables.value = [];
      columns.value = [];
      fetchSchemas();
    };

    const onSchemaChange = (value: any) => {
      dataApi.value.schemaName = value || null;
      dataApi.value.tableName = null;
      tables.value = [];
      columns.value = [];
      fetchTables();
    };

    const onTableChange = (value: any) => {
      dataApi.value.tableName = value || null;
      columns.value = [];
      fetchColumns();
    };

    const applyCurl = (target: 'main' | 'pre') => {
      const text = target === 'main' ? curlText.value : preCurlText.value;
      if (!text.trim()) {
        alertService.showError('请先粘贴 cURL 命令');
        return;
      }
      const parsed = parseCurl(text);
      if (!parsed.url) {
        alertService.showError('未能从 cURL 命令中解析出请求地址');
        return;
      }
      if (target === 'main') {
        apiConfig.value.url = parsed.url;
        if (parsed.method) {
          apiConfig.value.method = parsed.method;
        }
        if (parsed.body !== undefined) {
          apiConfig.value.body = parsed.body;
        }
        if (parsed.timeout) {
          apiConfig.value.timeout = parsed.timeout;
        }
        if (parsed.sslVerify === false) {
          apiConfig.value.sslVerify = false;
        }
        apiHeadersText.value = JSON.stringify({ ...parseJsonObject(apiHeadersText.value), ...parsed.headers }, null, 2);
      } else {
        const pre = apiConfig.value.preProcess;
        if (!pre) {
          return;
        }
        pre.url = parsed.url;
        if (parsed.method) {
          pre.method = parsed.method;
        }
        if (parsed.body !== undefined) {
          pre.body = parsed.body;
        }
        if (parsed.sslVerify === false) {
          pre.sslVerify = false;
        }
        preHeadersText.value = JSON.stringify({ ...parseJsonObject(preHeadersText.value), ...parsed.headers }, null, 2);
      }
      if (parsed.warnings.length > 0) {
        alertService.showInfo(parsed.warnings.join('；'));
      } else {
        alertService.showSuccess('已根据 cURL 命令填充配置');
      }
      if (target === 'main') {
        curlText.value = '';
        showCurlImport.value = false;
      } else {
        preCurlText.value = '';
        showPreCurlImport.value = false;
      }
    };

    const clearCurlImport = () => {
      curlText.value = '';
      showCurlImport.value = false;
    };

    const clearPreCurlImport = () => {
      preCurlText.value = '';
      showPreCurlImport.value = false;
    };

    const dataSourceLabel = () => {
      const item = dataSources.value.find((s: any) => String(s.id) === String(dataApi.value.dataSourceId));
      return item ? item.name : '';
    };

    return {
      dataApiService,
      alertService,
      dataApi,
      isSaving,
      previousState,
      isEdit,
      dataSources,
      dataDirectories,
      datasets,
      directoryTreeOptions,
      selectedDataset,
      schemas,
      tables,
      columns,
      loadingSchemas,
      loadingTables,
      loadingColumns,
      apiConfig,
      apiHeadersText,
      preHeadersText,
      showCurlImport,
      showPreCurlImport,
      curlText,
      preCurlText,
      debugVisible,
      debugLoading,
      debugResult,
      debugError,
      debugParams,
      debugDetectedNames,
      debugActiveTab,
      debugRows,
      debugColumns,
      debugHasTable,
      debugJson,
      debugUpstream,
      debugPreProcess,
      debugPreProcessJson,
      debugShowRawBody,
      debugRawBody,
      debugUpstreamHeaders,
      httpMethods,
      hasApiBody,
      preProcessMode,
      fillScriptExample,
      sourceTypeOptions,
      statusOptions,
      loadDataSources,
      loadDatasets,
      fetchSchemas,
      fetchTables,
      fetchColumns,
      onSourceTypeChange,
      onDataSourceChange,
      onSchemaChange,
      onTableChange,
      applyCurl,
      clearCurlImport,
      clearPreCurlImport,
      dataSourceLabel,
      SOURCE_TYPE_TABLE: DATA_API_SOURCE_TYPE_TABLE,
      SOURCE_TYPE_SQL: DATA_API_SOURCE_TYPE_SQL,
      SOURCE_TYPE_DATASET: DATA_API_SOURCE_TYPE_DATASET,
      SOURCE_TYPE_API: DATA_API_SOURCE_TYPE_API,
    };
  },
  methods: {
    buildApiConfig(): IApiConfig | null {
      const config = this.apiConfig;
      if (!config.url || !config.url.trim()) {
        this.alertService.showError('请输入请求地址');
        return null;
      }
      let headers: Record<string, string>;
      try {
        headers = parseJsonObject(this.apiHeadersText);
      } catch {
        this.alertService.showError('请求头 JSON 格式错误');
        return null;
      }
      const result: IApiConfig = {
        url: config.url.trim(),
        method: config.method || 'GET',
        headers,
        body: config.body || '',
        timeout: Number(config.timeout) || 30000,
        sslVerify: config.sslVerify !== false,
        jsonPath: config.jsonPath?.trim() || '',
      };
      if (config.preProcess?.enabled) {
        if (config.preProcess.type === 'script') {
          const script = (config.preProcess.script || '').trim();
          if (!script) {
            this.alertService.showError('请填写前置处理脚本');
            return null;
          }
          if (script.length > 64 * 1024) {
            this.alertService.showError('前置处理脚本过长，最多支持 64KB');
            return null;
          }
          result.preProcess = {
            enabled: true,
            type: 'script',
            script,
          };
        } else {
          if (!config.preProcess.url || !config.preProcess.url.trim()) {
            this.alertService.showError('请输入前置处理请求地址');
            return null;
          }
          let preHeaders: Record<string, string>;
          try {
            preHeaders = parseJsonObject(this.preHeadersText);
          } catch {
            this.alertService.showError('前置请求头 JSON 格式错误');
            return null;
          }
          result.preProcess = {
            enabled: true,
            type: 'http',
            url: config.preProcess.url.trim(),
            method: config.preProcess.method || 'POST',
            headers: preHeaders,
            body: config.preProcess.body || '',
            tokenPath: config.preProcess.tokenPath || '',
            sslVerify: config.preProcess.sslVerify !== false,
            cacheTtlSeconds: Number(config.preProcess.cacheTtlSeconds) || 0,
            timeout: Number(config.preProcess.timeout) || undefined,
          };
        }
      }
      return result;
    },
    /**
     * 按当前来源构建服务配置，保存与调试共用，避免两处配置不一致。
     */
    buildSourcePayload(): IDataApi | null {
      const api = this.dataApi;
      let apiConfigPayload: IApiConfig | null = null;
      if (api.sourceType === this.SOURCE_TYPE_TABLE) {
        if (!api.dataSourceId) {
          this.alertService.showError('请选择数据源');
          return null;
        }
        if (!api.schemaName || !api.tableName) {
          this.alertService.showError('请选择 Schema 和数据表');
          return null;
        }
      } else if (api.sourceType === this.SOURCE_TYPE_SQL) {
        if (!api.dataSourceId) {
          this.alertService.showError('请选择数据源');
          return null;
        }
        if (!api.sqlText || !api.sqlText.trim()) {
          this.alertService.showError('请填写自定义 SQL');
          return null;
        }
      } else if (api.sourceType === this.SOURCE_TYPE_DATASET) {
        if (!api.datasetId) {
          this.alertService.showError('请选择要发布的分析数据集');
          return null;
        }
      } else if (api.sourceType === this.SOURCE_TYPE_API) {
        apiConfigPayload = this.buildApiConfig();
        if (!apiConfigPayload) {
          return null;
        }
      } else {
        this.alertService.showError('请选择数据来源类型');
        return null;
      }

      const payload: IDataApi = {
        ...api,
        // 清空目录时显式传空字符串，后端据此移动到未分类
        directoryId: api.directoryId || '',
      };
      if (api.sourceType === this.SOURCE_TYPE_API && apiConfigPayload) {
        payload.apiConfig = JSON.stringify(apiConfigPayload);
        payload.dataSourceId = null;
        payload.schemaName = null;
        payload.tableName = null;
        payload.sqlText = null;
      } else if (api.sourceType === this.SOURCE_TYPE_DATASET) {
        payload.dataSourceId = null;
        payload.schemaName = null;
        payload.tableName = null;
        payload.sqlText = null;
        payload.apiConfig = null;
      }
      return payload;
    },
    save(): void {
      const api = this.dataApi;
      if (!api.name || !api.name.trim()) {
        this.alertService.showError('请输入服务名称');
        return;
      }
      if (!api.code || !api.code.trim()) {
        this.alertService.showError('请输入服务编码');
        return;
      }
      if (!/^[A-Za-z0-9_-]+$/.test(api.code.trim())) {
        this.alertService.showError('服务编码只能包含字母、数字、下划线和中划线');
        return;
      }
      const payload = this.buildSourcePayload();
      if (!payload) {
        return;
      }
      payload.name = api.name.trim();
      payload.code = api.code.trim();

      this.isSaving = true;
      if (this.dataApi.id) {
        this.dataApiService()
          .update(payload)
          .then(() => {
            this.isSaving = false;
            this.alertService.showSuccess('数据服务已更新');
            this.previousState();
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.dataApiService()
          .create(payload)
          .then(() => {
            this.isSaving = false;
            this.alertService.showSuccess('数据服务已创建');
            this.previousState();
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
    defaultDebugParamValue(name: string): string {
      if (name === 'pageNum') {
        return '1';
      }
      if (name === 'pageSize') {
        if (this.dataApi.sourceType === this.SOURCE_TYPE_DATASET) {
          return '1000';
        }
        if (this.dataApi.sourceType === this.SOURCE_TYPE_TABLE) {
          return '20';
        }
      }
      return '';
    },
    /** 根据当前来源识别调试时可能需要填写的参数。 */
    detectDebugParamNames(): string[] {
      const names: string[] = [];
      const api = this.dataApi;
      if (api.sourceType === this.SOURCE_TYPE_TABLE) {
        (this.columns || []).forEach((column: any) => {
          if (column?.name) {
            names.push(column.name);
          }
        });
        names.push('pageNum', 'pageSize');
      } else if (api.sourceType === this.SOURCE_TYPE_SQL) {
        names.push(...extractPlaceholderNames(api.sqlText));
      } else if (api.sourceType === this.SOURCE_TYPE_DATASET) {
        const dataset = this.selectedDataset;
        const definition = DatasetService.parseDefinition(dataset);
        if (dataset?.type === 'SQL') {
          const declared = Array.isArray(definition.params)
            ? definition.params
                .map((param: any) => (typeof param === 'string' ? param : param?.name))
                .filter((name: any): name is string => typeof name === 'string' && name.length > 0)
            : [];
          if (declared.length > 0) {
            names.push(...declared);
          } else {
            names.push(...extractPlaceholderNames(definition.sql));
          }
        } else {
          names.push('start', 'end');
        }
        names.push('pageNum', 'pageSize');
      } else if (api.sourceType === this.SOURCE_TYPE_API) {
        const config = this.apiConfig;
        names.push(...extractPlaceholderNames(config.url, config.body, ...Object.values(parseHeadersSafely(this.apiHeadersText))));
        if (config.preProcess?.enabled) {
          if (config.preProcess.type === 'script') {
            names.push(...extractScriptParameterNames(config.preProcess.script));
          } else {
            names.push(
              ...extractPlaceholderNames(
                config.preProcess.url,
                config.preProcess.body,
                ...Object.values(parseHeadersSafely(this.preHeadersText)),
              ),
            );
          }
        }
      }
      return Array.from(new Set(names)).filter(name => name !== 'token');
    },
    openDebug(): void {
      const api = this.dataApi;
      const payload = this.buildSourcePayload();
      if (!payload) {
        return;
      }
      const detected = this.detectDebugParamNames();
      const detectedSet = new Set(detected);
      const existingValues = new Map(this.debugParams.filter(item => item.name).map(item => [item.name, item.value] as const));
      const manualParams = this.debugParams.filter(item => item.name && !detectedSet.has(item.name));
      // 表字段数量可能很多，默认只放分页参数；已填写过的列过滤条件在再次打开时保留。
      const initialNames = detected.filter(name => {
        const tableColumn = api.sourceType === this.SOURCE_TYPE_TABLE && name !== 'pageNum' && name !== 'pageSize';
        return !tableColumn || existingValues.has(name);
      });
      this.debugDetectedNames = detected;
      this.debugParams = [
        ...initialNames.map(name => ({ name, value: existingValues.get(name) ?? this.defaultDebugParamValue(name) })),
        ...manualParams,
      ];
      this.debugResult = null;
      this.debugError = '';
      this.debugActiveTab = 'data';
      this.debugVisible = true;
    },
    addDebugParam(): void {
      this.debugParams.push({ name: '', value: '' });
    },
    removeDebugParam(index: number): void {
      this.debugParams.splice(index, 1);
    },
    async sendDebug(): Promise<void> {
      const payload = this.buildSourcePayload();
      if (!payload) {
        return;
      }
      const params: Record<string, any> = {};
      this.debugParams.forEach(item => {
        const name = (item.name || '').trim();
        if (name) {
          params[name] = item.value;
        }
      });
      this.debugLoading = true;
      this.debugError = '';
      this.debugResult = null;
      try {
        const result = await this.dataApiService().debug(payload, params);
        this.debugResult = result;
        if (!result || result.code !== 0) {
          this.debugError = result?.message || '调试失败';
          if (result?.upstream) {
            this.debugActiveTab = 'upstream';
          }
        }
      } catch (error) {
        this.debugError = debugErrorMessage(error);
      } finally {
        this.debugLoading = false;
      }
    },
    debugColumnLabel(column: string): string {
      return this.debugResult?.columnLabels?.[column] || column;
    },
    formatDebugCell(value: any): string {
      if (value === null || value === undefined) {
        return '';
      }
      if (typeof value === 'object') {
        try {
          return JSON.stringify(value);
        } catch {
          return String(value);
        }
      }
      return String(value);
    },
  },
});
