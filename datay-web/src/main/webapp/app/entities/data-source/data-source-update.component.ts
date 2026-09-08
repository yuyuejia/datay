import { type Ref, computed, defineComponent, inject, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import DataSourceService from './data-source.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';
import { dbTypes, type DbType, type ExtraParamDef } from './db-types';

import { DataSource, type IDataSource } from '@/shared/model/data-source.model';

interface ExtraParamRow {
  key: string;
  value: string;
  required: boolean;
  label: string;
  description?: string;
}

export default defineComponent({
  compatConfig: { MODE: 3 },
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

    const extraParamRows: Ref<ExtraParamRow[]> = ref([]);

    const syncExtraParamsFromTemplate = () => {
      const dbType = selectedDbType.value;
      if (!dbType) {
        extraParamRows.value = [];
        return;
      }
      const template = dbType.extraParamsTemplate;
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
      },
    );

    const onTypeChange = () => {
      const dbType = selectedDbType.value;
      if (dbType) {
        if (dbType.defaultPort && !dataSource.value.port) {
          dataSource.value.port = dbType.defaultPort;
        }
        if (dbType.supportedVersions.length > 0 && !dataSource.value.version) {
          dataSource.value.version = dbType.supportedVersions[dbType.supportedVersions.length - 1];
        }
        updateUrl();
      }
    };

    const onVersionChange = () => {};

    const updateUrl = () => {
      const dbType = selectedDbType.value;
      if (!dbType) return;

      let urlTemplate = dbType.jdbcUrlTemplate;
      const hostname = dataSource.value.hostname || '{host}';
      const port = dataSource.value.port || '{port}';
      const schemaName = dataSource.value.schemaName || '{database}';

      urlTemplate = urlTemplate.replace(/{host}/g, hostname);
      urlTemplate = urlTemplate.replace(/{port}/g, port);
      urlTemplate = urlTemplate.replace(/{database}/g, schemaName);

      if (dbType.name === 'MYSQL' && dataSource.value.version) {
        if (dataSource.value.version.startsWith('5.')) {
          urlTemplate = urlTemplate.replace('serverTimezone=Asia/Shanghai', 'serverTimezone=UTC');
          if (!urlTemplate.includes('useSSL=')) {
            urlTemplate += '&useSSL=false';
          }
        } else {
          if (!urlTemplate.includes('useSSL=')) {
            urlTemplate += '&useSSL=true';
          }
        }
      }

      dataSource.value.url = urlTemplate;
    };

    const retrieveDataSource = async dataSourceId => {
      try {
        const res = await dataSourceService().find(dataSourceId);
        res.updateTime = new Date(res.updateTime);
        res.createTime = new Date(res.createTime);
        dataSource.value = res;
        syncExtraParamsFromTemplate();
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

    const testConnection = async () => {
      buildExtraParamsFromRows();
      if (!dataSource.value.type || !dataSource.value.url || !dataSource.value.username) {
        alertService.showError('请先填写数据库类型、URL和用户名');
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
      extraParamRows,
      onTypeChange,
      onVersionChange,
      updateUrl,
      testConnection,
      addExtraParam,
      removeExtraParam,
      buildExtraParamsFromRows,
      ...useDateFormat({ entityRef: dataSource }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.buildExtraParamsFromRows();
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