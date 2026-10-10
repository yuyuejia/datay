import { type Ref, computed, defineComponent, inject, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import DataSourceService from './data-source.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';
import {
  dbTypes,
  type ConnectionModeDef,
  type DbFormConfig,
  type ExtraParamDef,
  type IdentifierTypeDef,
  resolveDbFormConfig,
} from './db-types';
import { type OracleIdentifierType, type UrlMode, buildSimpleUrl, parseSimpleUrl, renderUrlTemplate } from './db-url.util';

import { DataSource, type IDataSource } from '@/shared/model/data-source.model';

interface ExtraParamRow {
  key: string;
  value: string;
  required: boolean;
  label: string;
  description?: string;
}

export default defineComponent({
  name: 'DataSourceUpdate',
  setup() {
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const dataSource: Ref<IDataSource> = ref(new DataSource());
    const isSaving = ref(false);
    const isTestingConnection = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    const selectedDbType = computed(() => {
      if (!dataSource.value.type) return null;
      return dbTypes.find(db => db.name === dataSource.value.type) || null;
    });

    // 表单字段/连接能力完全由数据源类型配置驱动，避免按类型硬编码判断
    const formConfig = computed<DbFormConfig>(() => resolveDbFormConfig(selectedDbType.value));

    const extraParamRows: Ref<ExtraParamRow[]> = ref([]);
    const selectedConnectionMode = ref('');
    const filePath = ref('');
    const urlMode = ref<UrlMode>('simple');
    const oracleIdentifierType = ref<OracleIdentifierType>('service');
    const driverStatus: Ref<any> = ref(null);
    const isDownloadingDriver = ref(false);

    const selectedConnectionModeDef = computed<ConnectionModeDef | undefined>(() =>
      formConfig.value.connectionModes.find(m => m.value === selectedConnectionMode.value),
    );

    const showConnectionModeSelector = computed(() => formConfig.value.connectionModes.length > 0);
    const hasConnectionModes = computed(() => formConfig.value.connectionModes.length > 0);
    const showFileField = computed(() => !!selectedConnectionModeDef.value?.file);
    const showNetworkFields = computed(
      () => (formConfig.value.network && urlMode.value === 'simple') || !!selectedConnectionModeDef.value?.network,
    );
    const hostLabel = computed(() =>
      selectedConnectionModeDef.value?.network ? selectedConnectionModeDef.value.hostLabel || '主机' : 'IP/主机',
    );
    const identifierTypes = computed<IdentifierTypeDef[]>(() => formConfig.value.identifierTypes);
    const hasIdentifierTypes = computed(() => identifierTypes.value.length > 0);
    const databaseLabel = computed(() => {
      if (!hasIdentifierTypes.value) return '数据库名';
      const selected = identifierTypes.value.find(t => t.value === oracleIdentifierType.value);
      return selected ? selected.label.replace(/\s*\(.*\)\s*$/, '') : '数据库名';
    });
    // 需要用户名密码（与表单/测试连接逻辑共用）
    const requiresCredentials = computed(() => formConfig.value.credentials);

    const effectiveExtraParamsTemplate = computed<ExtraParamDef[]>(() => {
      const mode = selectedConnectionModeDef.value;
      return mode?.extraParamsTemplate ?? selectedDbType.value?.extraParamsTemplate ?? [];
    });

    const syncExtraParamsFromTemplate = () => {
      const dbType = selectedDbType.value;
      if (!dbType) {
        extraParamRows.value = [];
        return;
      }
      const template = effectiveExtraParamsTemplate.value;
      if (!template || template.length === 0) {
        extraParamRows.value = [];
        return;
      }

      const existingParams = dataSource.value.extraParams || {};
      const rows: ExtraParamRow[] = template.map(def => ({
        key: def.key,
        value: existingParams[def.key] ?? def.defaultValue ?? '',
        required: def.required,
        label: def.label,
        description: def.description,
      }));

      Object.keys(existingParams).forEach(k => {
        if (!template.find(t => t.key === k)) {
          rows.push({ key: k, value: existingParams[k], required: false, label: k });
        }
      });

      extraParamRows.value = rows;
    };

    const buildExtraParamsFromRows = () => {
      const result: Record<string, string> = {};
      extraParamRows.value.forEach(row => {
        if (row.value !== '' && row.value !== null && row.value !== undefined) {
          result[row.key] = row.value;
        }
      });
      dataSource.value.extraParams = Object.keys(result).length > 0 ? result : null;
    };

    const addExtraParam = () => {
      extraParamRows.value.push({ key: '', value: '', required: false, label: '' });
    };

    const removeExtraParam = (index: number) => {
      extraParamRows.value.splice(index, 1);
      buildExtraParamsFromRows();
    };

    watch(
      () => dataSource.value.type,
      () => {
        syncExtraParamsFromTemplate();
        loadDriverStatus();
      },
    );

    watch(selectedConnectionMode, () => {
      syncExtraParamsFromTemplate();
    });

    const loadDriverStatus = async () => {
      if (!selectedDbType.value?.downloadable || !dataSource.value.type) {
        driverStatus.value = null;
        return;
      }
      try {
        driverStatus.value = await dataSourceService().getDriverStatus(dataSource.value.type, dataSource.value.version || undefined);
      } catch (error) {
        driverStatus.value = null;
      }
    };

    const onDriverDownload = async () => {
      if (!dataSource.value.type) return;
      isDownloadingDriver.value = true;
      try {
        const result = await dataSourceService().downloadDriver(dataSource.value.type, dataSource.value.version || undefined);
        alertService.showSuccess(result.message || '驱动下载成功');
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isDownloadingDriver.value = false;
        await loadDriverStatus();
      }
    };

    const onTypeChange = () => {
      const dbType = selectedDbType.value;
      if (!dbType) return;
      const cfg = formConfig.value;
      selectedConnectionMode.value = cfg.defaultConnectionMode ?? cfg.connectionModes[0]?.value ?? '';
      urlMode.value = cfg.network ? 'simple' : 'custom';
      oracleIdentifierType.value = cfg.identifierTypes[0]?.value ?? 'service';
      if (dbType.defaultPort && !dataSource.value.port) {
        dataSource.value.port = dbType.defaultPort;
      }
      if (dbType.supportedVersions.length > 0 && !dataSource.value.version) {
        dataSource.value.version = dbType.supportedVersions[dbType.supportedVersions.length - 1];
      }
      updateUrl();
    };

    const onUrlModeChange = (mode: UrlMode) => {
      urlMode.value = mode;
      if (mode === 'simple') {
        updateUrl();
      }
    };

    const onOracleIdentifierTypeChange = (value: OracleIdentifierType) => {
      oracleIdentifierType.value = value;
      updateUrl();
    };

    const onConnectionModeChange = (mode: string) => {
      selectedConnectionMode.value = mode;
      const modeDef = formConfig.value.connectionModes.find(m => m.value === mode);
      if (modeDef?.defaultPort && !dataSource.value.port) {
        dataSource.value.port = modeDef.defaultPort;
      }
      updateUrl();
    };

    const onVersionChange = () => {};

    const inferConnectionModeFromUrl = (url?: string | null) => {
      const modes = formConfig.value.connectionModes;
      if (modes.length === 0) {
        selectedConnectionMode.value = '';
        filePath.value = '';
        return;
      }
      const matched = modes.find(m => m.urlPrefix && url && url.startsWith(m.urlPrefix));
      selectedConnectionMode.value = matched?.value ?? formConfig.value.defaultConnectionMode ?? modes[0].value;
      const fileMode = modes.find(m => m.file && m.urlPrefix);
      filePath.value = fileMode && url && url.startsWith(fileMode.urlPrefix!) ? url.substring(fileMode.urlPrefix!.length) : '';
    };

    const applyUrlModeFromUrl = () => {
      const cfg = formConfig.value;
      urlMode.value = dataSource.value.connectionMode === 'simple' ? 'simple' : 'custom';
      if (urlMode.value === 'simple' && cfg.network) {
        const fields = parseSimpleUrl(dataSource.value.type, dataSource.value.url);
        if (fields) {
          dataSource.value.hostname = fields.hostname;
          dataSource.value.port = fields.port;
          dataSource.value.database = dataSource.value.database || fields.database;
          if (fields.oracleIdentifierType) {
            oracleIdentifierType.value = fields.oracleIdentifierType;
          }
        }
      }
      inferConnectionModeFromUrl(dataSource.value.url);
    };

    const updateUrl = () => {
      const dbType = selectedDbType.value;
      if (!dbType) return;
      const cfg = formConfig.value;

      const modeDef = selectedConnectionModeDef.value;
      if (modeDef?.urlTemplate) {
        if (modeDef.network && !dataSource.value.hostname) {
          dataSource.value.url = '';
          return;
        }
        dataSource.value.url = renderUrlTemplate(modeDef.urlTemplate, {
          host: dataSource.value.hostname || '',
          port: dataSource.value.port || '',
          database: dataSource.value.database || '',
          file: filePath.value || '',
        });
        return;
      }

      if (urlMode.value === 'custom') {
        // 非网络型/自定义模式：URL 由用户填写，为空时填入模板默认值
        if (!dataSource.value.url || !dataSource.value.url.trim()) {
          dataSource.value.url = buildSimpleUrl(dbType.name, { hostname: '', port: '', database: '' }, dataSource.value.version);
        }
        return;
      }

      dataSource.value.url = buildSimpleUrl(
        dbType.name,
        {
          hostname: dataSource.value.hostname || '',
          port: dataSource.value.port || '',
          database: dataSource.value.database || '',
          oracleIdentifierType: oracleIdentifierType.value,
        },
        dataSource.value.version,
      );
    };

    const syncDerivedFields = () => {
      const cfg = formConfig.value;
      dataSource.value.connectionMode = urlMode.value;
      if (urlMode.value === 'custom') {
        const parsed = parseSimpleUrl(dataSource.value.type, dataSource.value.url);
        if (parsed?.database) {
          dataSource.value.database = parsed.database;
        }
      }
      if (!cfg.schemaEnabled) {
        dataSource.value.schemaName = cfg.schemaFromUsername
          ? (dataSource.value.username || '').toUpperCase()
          : dataSource.value.database;
      }
    };

    const retrieveDataSource = async dataSourceId => {
      try {
        const res = await dataSourceService().find(dataSourceId);
        res.updateTime = new Date(res.updateTime);
        res.createTime = new Date(res.createTime);
        dataSource.value = res;
        applyUrlModeFromUrl();
        syncExtraParamsFromTemplate();
        loadDriverStatus();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.dataSourceId) {
      retrieveDataSource(route.params.dataSourceId);
    }

    const validations = useValidation();
    const validationRules = {
      name: {},
      description: {},
      type: {},
      url: {},
      hostname: {},
      port: {},
      schemaName: {},
      username: {},
      password: {},
      updateTime: {},
      createTime: {},
      tenantId: {},
    };
    const v$ = useVuelidate(validationRules, dataSource as any);
    v$.value.$validate();

    const testConnectionDisabled = computed(() => {
      if (isTestingConnection.value || !dataSource.value.type || !dataSource.value.url) return true;
      const cfg = formConfig.value;
      if (!cfg.credentials) return false;
      if (cfg.network && urlMode.value === 'simple' && !dataSource.value.hostname) return true;
      return !dataSource.value.username;
    });

    const testConnection = async () => {
      updateUrl();
      syncDerivedFields();
      buildExtraParamsFromRows();
      if (!dataSource.value.type || !dataSource.value.url) {
        alertService.showError('请先完成数据源配置');
        return;
      }
      if (requiresCredentials.value && !dataSource.value.username) {
        alertService.showError('请先填写用户名');
        return;
      }

      isTestingConnection.value = true;
      try {
        const result = await dataSourceService().testConnection(dataSource.value);
        if (result.success) {
          alertService.showSuccess(result.message || '连接测试成功');
        } else {
          alertService.showError(result.message || '连接测试失败');
        }
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isTestingConnection.value = false;
      }
    };

    return {
      dataSourceService,
      alertService,
      dataSource,
      previousState,
      isSaving,
      isTestingConnection,
      currentLanguage,
      v$,
      dbTypes,
      selectedDbType,
      formConfig,
      extraParamRows,
      selectedConnectionMode,
      selectedConnectionModeDef,
      filePath,
      requiresCredentials,
      urlMode,
      oracleIdentifierType,
      showConnectionModeSelector,
      hasConnectionModes,
      showFileField,
      showNetworkFields,
      hostLabel,
      identifierTypes,
      hasIdentifierTypes,
      databaseLabel,
      effectiveExtraParamsTemplate,
      driverStatus,
      isDownloadingDriver,
      onDriverDownload,
      testConnectionDisabled,
      onTypeChange,
      onConnectionModeChange,
      onUrlModeChange,
      onOracleIdentifierTypeChange,
      onVersionChange,
      updateUrl,
      testConnection,
      addExtraParam,
      removeExtraParam,
      buildExtraParamsFromRows,
      syncDerivedFields,
      ...useDateFormat({ entityRef: dataSource }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.updateUrl();
      this.syncDerivedFields();
      this.buildExtraParamsFromRows();
      const cfg: DbFormConfig = this.formConfig;
      if (cfg.clearCredentialsOnSave) {
        this.dataSource.username = null;
        this.dataSource.password = null;
      }
      if (cfg.clearDatabaseOnSave) {
        this.dataSource.database = null;
        this.dataSource.schemaName = null;
      }
      if (this.urlMode === 'custom' && (!this.dataSource.url || !this.dataSource.url.trim())) {
        this.alertService.showError('请输入 JDBC URL');
        return;
      }
      this.isSaving = true;
      if (this.dataSource.id) {
        this.dataSourceService()
          .update(this.dataSource)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A DataSource is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.dataSourceService()
          .create(this.dataSource)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A DataSource is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
