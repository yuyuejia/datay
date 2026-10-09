import { type Ref, computed, defineComponent, inject, ref, watch } from 'vue';

import DataSourceService from './data-source.service';
import { type DbType, type ExtraParamDef, dbTypes } from './db-types';
import {
  type OracleIdentifierType,
  type UrlMode,
  buildSimpleUrl,
  isNetworkType,
  parseSimpleUrl,
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
    const duckdbMode = ref('file');
    const duckdbFile = ref('');
    const urlMode = ref<UrlMode>('simple');
    const oracleIdentifierType = ref<OracleIdentifierType>('service');

    const isDuckDb = computed(() => dataSource.value.type === 'DUCKDB');

    // DuckDB / DuckLake 通过本地文件或挂载方式访问，无需用户名密码
    const requiresCredentials = computed(() => !['DUCKDB', 'DUCKLAKE'].includes(dataSource.value.type ?? ''));

    const showUrlModeToggle = computed(() => !!dataSource.value.type && isNetworkType(dataSource.value.type));

    const isOracle = computed(() => dataSource.value.type === 'ORACLE');

    const schemaEnabled = computed(() => !!selectedDbType.value?.schemaEnabled);

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
      },
    );

    watch(duckdbMode, () => {
      syncExtraParamsFromTemplate();
    });

    const effectiveExtraParamsTemplate = computed<ExtraParamDef[]>(() => {
      const dbType = selectedDbType.value;
      if (!dbType) return [];
      const mode = dbType.connectionModes?.find(m => m.value === duckdbMode.value);
      return mode?.extraParamsTemplate ?? dbType.extraParamsTemplate ?? [];
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

    const inferDuckdbMode = (url?: string | null) => (url && url.startsWith('quack:') ? 'quack' : 'file');

    const extractDuckdbFile = (url?: string | null) =>
      url && url.startsWith('jdbc:duckdb:') ? url.substring('jdbc:duckdb:'.length) : '';

    const applyUrlModeFromUrl = () => {
      const type = dataSource.value.type;
      urlMode.value = dataSource.value.connectionMode === 'simple' ? 'simple' : 'custom';
      if (urlMode.value === 'simple' && type && isNetworkType(type)) {
        const fields = parseSimpleUrl(type, dataSource.value.url);
        if (fields) {
          dataSource.value.hostname = fields.hostname;
          dataSource.value.port = fields.port;
          dataSource.value.database = dataSource.value.database || fields.database;
          if (fields.oracleIdentifierType) {
            oracleIdentifierType.value = fields.oracleIdentifierType;
          }
        }
      }
    };

    const initModal = async () => {
      dataSource.value = new DataSource();
      currentStep.value = 1;
      extraParamRows.value = [];
      duckdbMode.value = 'file';
      duckdbFile.value = '';
      urlMode.value = 'simple';
      oracleIdentifierType.value = 'service';
      if (props.mode === 'edit' && props.dataSourceId) {
        try {
          const res = await dataSourceService().find(props.dataSourceId as string);
          res.updateTime = new Date(res.updateTime);
          res.createTime = new Date(res.createTime);
          dataSource.value = res;
          duckdbMode.value = inferDuckdbMode(res.url);
          duckdbFile.value = extractDuckdbFile(res.url);
          applyUrlModeFromUrl();
          currentStep.value = 2;
          syncExtraParamsFromTemplate();
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

    const selectedDbType = computed<DbType | null>(() => {
      if (!dataSource.value.type) return null;
      return dbTypes.find(db => db.name === dataSource.value.type) || null;
    });

    const selectDbType = (dbType: DbType) => {
      dataSource.value.type = dbType.name;
      duckdbMode.value = dbType.defaultConnectionMode || 'file';
      urlMode.value = isNetworkType(dbType.name) ? 'simple' : 'custom';
      oracleIdentifierType.value = 'service';
      if (dbType.defaultPort && !dataSource.value.port) {
        dataSource.value.port = dbType.defaultPort;
      }
      if (dbType.supportedVersions.length > 0 && !dataSource.value.version) {
        dataSource.value.version = dbType.supportedVersions[dbType.supportedVersions.length - 1];
      }
      updateUrl();
    };

    const onConnectionModeChange = (mode: string) => {
      duckdbMode.value = mode;
      const modeDef = selectedDbType.value?.connectionModes?.find(m => m.value === mode);
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
      if (dbType) {
        duckdbMode.value = dbType.defaultConnectionMode || 'file';
        urlMode.value = isNetworkType(dbType.name) ? 'simple' : 'custom';
        oracleIdentifierType.value = 'service';
        if (dbType.defaultPort && !dataSource.value.port) {
          dataSource.value.port = dbType.defaultPort;
        }
        if (dbType.supportedVersions.length > 0 && !dataSource.value.version) {
          dataSource.value.version = dbType.supportedVersions[dbType.supportedVersions.length - 1];
        }
        updateUrl();
      }
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

      if (dbType.name === 'DUCKDB') {
        if (duckdbMode.value === 'quack') {
          const host = dataSource.value.hostname || '';
          if (!host) {
            dataSource.value.url = '';
            return;
          }
          dataSource.value.url = dataSource.value.port ? `quack:${host}:${dataSource.value.port}` : `quack:${host}`;
        } else {
          dataSource.value.url = 'jdbc:duckdb:' + (duckdbFile.value || '');
        }
        return;
      }

      if (dbType.name === 'DUCKLAKE') {
        // DuckLake 元数据地址由用户填写，仅在为空时填入模板默认值，避免测试连接/保存时被覆盖
        if (!dataSource.value.url || !dataSource.value.url.trim()) {
          dataSource.value.url = buildSimpleUrl(dbType.name, { hostname: '', port: '', database: '' }, dataSource.value.version);
        }
        return;
      }

      if (urlMode.value === 'custom') {
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
      dataSource.value.connectionMode = urlMode.value;
      if (urlMode.value === 'custom') {
        const parsed = parseSimpleUrl(dataSource.value.type, dataSource.value.url);
        if (parsed?.database) {
          dataSource.value.database = parsed.database;
        }
      }
      if (!schemaEnabled.value) {
        dataSource.value.schemaName = isOracle.value
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
      if (!dataSource.value.name || !dataSource.value.name.trim()) {
        alertService.showError('请输入数据源名称');
        return false;
      }
      if (!dataSource.value.type) {
        alertService.showError('请选择数据源类型');
        return false;
      }
      if (isDuckDb.value) {
        if (duckdbMode.value === 'quack' && (!dataSource.value.hostname || !dataSource.value.hostname.trim())) {
          alertService.showError('请输入 Quack 服务地址');
          return false;
        }
        return true;
      }
      if (dataSource.value.type === 'DUCKLAKE') {
        return true;
      }
      if (urlMode.value === 'custom') {
        if (!dataSource.value.url || !dataSource.value.url.trim()) {
          alertService.showError('请输入 JDBC URL');
          return false;
        }
        return true;
      }
      if (!dataSource.value.hostname || !dataSource.value.hostname.trim()) {
        alertService.showError('请输入IP/主机');
        return false;
      }
      if (!dataSource.value.database || !dataSource.value.database.trim()) {
        alertService.showError(isOracle.value ? '请输入服务名或 SID' : '请输入数据库名');
        return false;
      }
      return true;
    };

    const testConnectionDisabled = computed(() => {
      if (isTestingConnection.value || !dataSource.value.type || !dataSource.value.url) return true;
      if (!requiresCredentials.value) return false;
      if (urlMode.value === 'simple' && !dataSource.value.hostname) return true;
      return !dataSource.value.username;
    });

    const save = async () => {
      if (!validateForm()) return;
      updateUrl();
      syncDerivedFields();
      buildExtraParamsFromRows();
      if (!requiresCredentials.value) {
        dataSource.value.username = null;
        dataSource.value.password = null;
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
      extraParamRows,
      duckdbMode,
      duckdbFile,
      isDuckDb,
      requiresCredentials,
      urlMode,
      oracleIdentifierType,
      showUrlModeToggle,
      isOracle,
      schemaEnabled,
      effectiveExtraParamsTemplate,
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