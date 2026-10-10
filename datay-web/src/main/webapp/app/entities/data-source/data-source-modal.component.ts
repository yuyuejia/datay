import { type Ref, computed, defineComponent, inject, ref, watch } from 'vue';

import DataSourceService from './data-source.service';
import {
  type ConnectionModeDef,
  type DbFormConfig,
  type DbType,
  type ExtraParamDef,
  type IdentifierTypeDef,
  dbTypes,
  resolveDbFormConfig,
} from './db-types';
import {
  type OracleIdentifierType,
  type UrlMode,
  buildSimpleUrl,
  parseSimpleUrl,
  renderUrlTemplate,
} from './db-url.util';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { DataSource, type IDataSource } from '@/shared/model/data-source.model';

interface ExtraParamRow {
  key: string;
  value: string;
  required: boolean;
  label: string;
  description?: string;
}

export default defineComponent({
  name: 'DataSourceModal',
  props: {
    show: {
      type: Boolean,
      default: false,
    },
    mode: {
      type: String,
      default: 'create',
    },
    dataSourceId: {
      type: [Number, String],
      default: null,
    },
  },
  emits: ['update:show', 'saved'],
  setup(props, { emit }) {
    const dateFormat = useDateFormat();
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const showModal = ref(false);
    const currentStep = ref(1);
    const dataSource: Ref<IDataSource> = ref(new DataSource());
    const isSaving = ref(false);
    const isTestingConnection = ref(false);
    const extraParamRows: Ref<ExtraParamRow[]> = ref([]);
    const selectedConnectionMode = ref('');
    const filePath = ref('');
    const urlMode = ref<UrlMode>('simple');
    const oracleIdentifierType = ref<OracleIdentifierType>('service');
    const driverStatus: Ref<any> = ref(null);
    const isDownloadingDriver = ref(false);

    const selectedDbType = computed<DbType | null>(() => {
      if (!dataSource.value.type) return null;
      return dbTypes.find(db => db.name === dataSource.value.type) || null;
    });

    // 表单字段/连接能力完全由数据源类型配置驱动
    const formConfig = computed<DbFormConfig>(() => resolveDbFormConfig(selectedDbType.value));

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
    const requiresCredentials = computed(() => formConfig.value.credentials);

    watch(
      () => props.show,
      newVal => {
        showModal.value = newVal;
        if (newVal) {
          initModal();
        }
      },
    );

    watch(showModal, newVal => {
      emit('update:show', newVal);
    });

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

    const initModal = async () => {
      dataSource.value = new DataSource();
      currentStep.value = 1;
      extraParamRows.value = [];
      selectedConnectionMode.value = '';
      filePath.value = '';
      urlMode.value = 'simple';
      oracleIdentifierType.value = 'service';
      driverStatus.value = null;
      if (props.mode === 'edit' && props.dataSourceId) {
        try {
          const res = await dataSourceService().find(props.dataSourceId as string);
          res.updateTime = new Date(res.updateTime);
          res.createTime = new Date(res.createTime);
          dataSource.value = res;
          applyUrlModeFromUrl();
          currentStep.value = 2;
          syncExtraParamsFromTemplate();
          loadDriverStatus();
        } catch (error) {
          alertService.showHttpError(error.response);
          closeModal();
        }
      }
    };

    const modalTitle = computed(() => {
      return props.mode === 'edit' ? '编辑数据源' : '创建数据源';
    });

    const selectedTypeName = computed(() => dataSource.value.type || '');

    const selectDbType = (dbType: DbType) => {
      dataSource.value.type = dbType.name;
      const cfg = resolveDbFormConfig(dbType);
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
      loadDriverStatus();
    };

    const onConnectionModeChange = (mode: string) => {
      selectedConnectionMode.value = mode;
      const modeDef = formConfig.value.connectionModes.find(m => m.value === mode);
      if (modeDef?.defaultPort && !dataSource.value.port) {
        dataSource.value.port = modeDef.defaultPort;
      }
      updateUrl();
    };

    const selectAndGo = (dbType: DbType) => {
      selectDbType(dbType);
      goToStep(2);
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

    const updateUrl = () => {
      const dbType = selectedDbType.value;
      if (!dbType) return;

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

    const goToStep = (step: number) => {
      if (step === 2 && !dataSource.value.type) {
        alertService.showError('请先选择数据源类型');
        return;
      }
      currentStep.value = step;
    };

    const closeModal = () => {
      showModal.value = false;
    };

    const handleHidden = () => {
      emit('update:show', false);
    };

    const validateForm = (): boolean => {
      const cfg = formConfig.value;
      if (!dataSource.value.name || !dataSource.value.name.trim()) {
        alertService.showError('请输入数据源名称');
        return false;
      }
      if (!dataSource.value.type) {
        alertService.showError('请选择数据源类型');
        return false;
      }
      // 连接方式型（如 DuckDB）：按所选模式校验
      if (hasConnectionModes.value) {
        const modeDef = selectedConnectionModeDef.value;
        if (modeDef?.network && (!dataSource.value.hostname || !dataSource.value.hostname.trim())) {
          alertService.showError('请输入' + (modeDef.hostLabel || '主机'));
          return false;
        }
        return true;
      }
      if (urlMode.value === 'custom') {
        if (!dataSource.value.url || !dataSource.value.url.trim()) {
          alertService.showError('请输入 JDBC URL');
          return false;
        }
        return true;
      }
      if (cfg.network && (!dataSource.value.hostname || !dataSource.value.hostname.trim())) {
        alertService.showError('请输入IP/主机');
        return false;
      }
      if (cfg.databaseEnabled && (!dataSource.value.database || !dataSource.value.database.trim())) {
        alertService.showError(hasIdentifierTypes.value ? `请输入${databaseLabel.value}` : '请输入数据库名');
        return false;
      }
      return true;
    };

    const testConnectionDisabled = computed(() => {
      if (isTestingConnection.value || !dataSource.value.type || !dataSource.value.url) return true;
      const cfg = formConfig.value;
      if (!cfg.credentials) return false;
      if (selectedConnectionModeDef.value?.network && !dataSource.value.hostname) return true;
      if (cfg.network && urlMode.value === 'simple' && !dataSource.value.hostname) return true;
      return !dataSource.value.username;
    });

    const save = async () => {
      if (!validateForm()) return;
      updateUrl();
      syncDerivedFields();
      buildExtraParamsFromRows();
      const cfg = formConfig.value;
      if (cfg.clearCredentialsOnSave) {
        dataSource.value.username = null;
        dataSource.value.password = null;
      }
      if (cfg.clearDatabaseOnSave) {
        dataSource.value.database = null;
        dataSource.value.schemaName = null;
      }

      isSaving.value = true;
      try {
        if (dataSource.value.id) {
          await dataSourceService().update(dataSource.value);
          alertService.showSuccess(`数据源 ${dataSource.value.name} 已更新`);
        } else {
          await dataSourceService().create(dataSource.value);
          alertService.showSuccess(`数据源 ${dataSource.value.name} 已创建`);
        }
        emit('saved');
        closeModal();
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isSaving.value = false;
      }
    };

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
      showModal,
      currentStep,
      dataSource,
      isSaving,
      isTestingConnection,
      modalTitle,
      dbTypes,
      selectedTypeName,
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
      selectDbType,
      selectAndGo,
      onTypeChange,
      onConnectionModeChange,
      onUrlModeChange,
      onOracleIdentifierTypeChange,
      updateUrl,
      goToStep,
      closeModal,
      handleHidden,
      save,
      testConnection,
      addExtraParam,
      removeExtraParam,
      ...dateFormat,
    };
  },
});
