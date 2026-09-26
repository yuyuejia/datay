import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DataSourceService from '@/entities/data-source/data-source.service';
import DataApiService from './data-api.service';
import {
  DataApi,
  type IApiConfig,
  type IDataApi,
  parseApiConfig,
  DATA_API_SOURCE_TYPE_API,
  DATA_API_SOURCE_TYPE_SQL,
  DATA_API_SOURCE_TYPE_TABLE,
  DATA_API_STATUS_ENABLED,
} from '@/shared/model/data-api.model';
import { parseCurl } from '@/shared/util/curl';
import { useAlertService } from '@/shared/alert/alert.service';

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
    url: '',
    method: 'POST',
    headers: {},
    body: '',
    tokenPath: '',
    cacheTtlSeconds: 0,
    sslVerify: true,
  },
});

const parseJsonObject = (text: string): Record<string, string> => {
  if (!text || !text.trim()) {
    return {};
  }
  const parsed = JSON.parse(text);
  return parsed && typeof parsed === 'object' ? parsed : {};
};

export default defineComponent({
  name: 'DataApiUpdate',
  setup() {
    const dataApiService = inject('dataApiService', () => new DataApiService());
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const dataApi: Ref<IDataApi> = ref(new DataApi());
    const isSaving = ref(false);

    const route = useRoute();
    const router = useRouter();
    const previousState = () => router.go(-1);

    const isEdit = ref(false);

    const dataSources = ref<any[]>([]);
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

    const httpMethods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD'];

    const sourceTypeOptions = [
      { value: DATA_API_SOURCE_TYPE_TABLE, label: '数据源的表' },
      { value: DATA_API_SOURCE_TYPE_SQL, label: '自定义 SQL' },
      { value: DATA_API_SOURCE_TYPE_API, label: '已存在的 API' },
    ];

    const statusOptions = [
      { value: 'ENABLED', label: '启用（对外可访问）' },
      { value: 'DISABLED', label: '停用（禁止对外访问）' },
    ];

    const hasApiBody = computed(() => apiConfig.value.method !== 'GET' && apiConfig.value.method !== 'HEAD');

    const loadDataSources = async () => {
      try {
        const res = await dataSourceService().retrieve({ page: 0, size: 500 });
        dataSources.value = res.data || [];
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

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

    const retrieveDataApi = async (dataApiId: number) => {
      try {
        const res = await dataApiService().find(dataApiId);
        res.createTime = new Date(res.createTime);
        res.updateTime = new Date(res.updateTime);
        dataApi.value = res;
        applyApiConfig(res.apiConfig);
        await loadDataSources();
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
      retrieveDataApi(Number(route.params.dataApiId));
    } else {
      dataApi.value.sourceType = DATA_API_SOURCE_TYPE_TABLE;
      dataApi.value.status = DATA_API_STATUS_ENABLED;
      loadDataSources();
    }

    const onSourceTypeChange = () => {
      if (dataApi.value.sourceType === DATA_API_SOURCE_TYPE_SQL) {
        dataApi.value.schemaName = null;
        dataApi.value.tableName = null;
        columns.value = [];
      } else if (dataApi.value.sourceType === DATA_API_SOURCE_TYPE_API) {
        dataApi.value.schemaName = null;
        dataApi.value.tableName = null;
        dataApi.value.sqlText = null;
        columns.value = [];
      }
    };

    const onDataSourceChange = (value: any) => {
      dataApi.value.dataSourceId = value ? Number(value) : null;
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
      httpMethods,
      hasApiBody,
      sourceTypeOptions,
      statusOptions,
      loadDataSources,
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
          ...config.preProcess,
          url: config.preProcess.url.trim(),
          method: config.preProcess.method || 'POST',
          headers: preHeaders,
          sslVerify: config.preProcess.sslVerify !== false,
          cacheTtlSeconds: Number(config.preProcess.cacheTtlSeconds) || 0,
        };
      }
      return result;
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
      let apiConfigPayload: IApiConfig | null = null;
      if (api.sourceType === this.SOURCE_TYPE_TABLE) {
        if (!api.dataSourceId) {
          this.alertService.showError('请选择数据源');
          return;
        }
        if (!api.schemaName || !api.tableName) {
          this.alertService.showError('请选择 Schema 和数据表');
          return;
        }
      } else if (api.sourceType === this.SOURCE_TYPE_SQL) {
        if (!api.dataSourceId) {
          this.alertService.showError('请选择数据源');
          return;
        }
        if (!api.sqlText || !api.sqlText.trim()) {
          this.alertService.showError('请填写自定义 SQL');
          return;
        }
      } else if (api.sourceType === this.SOURCE_TYPE_API) {
        apiConfigPayload = this.buildApiConfig();
        if (!apiConfigPayload) {
          return;
        }
      } else {
        this.alertService.showError('请选择数据来源类型');
        return;
      }

      const payload: IDataApi = { ...this.dataApi, name: api.name.trim(), code: api.code.trim() };
      if (api.sourceType === this.SOURCE_TYPE_API && apiConfigPayload) {
        payload.apiConfig = JSON.stringify(apiConfigPayload);
        payload.dataSourceId = null;
        payload.schemaName = null;
        payload.tableName = null;
        payload.sqlText = null;
      }

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
  },
});
