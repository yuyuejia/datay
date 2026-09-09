import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DataSourceService from '@/entities/data-source/data-source.service';
import DataApiService from './data-api.service';
import { DataApi, type IDataApi } from '@/shared/model/data-api.model';
import { DATA_API_SOURCE_TYPE_TABLE, DATA_API_SOURCE_TYPE_SQL, DATA_API_STATUS_ENABLED } from '@/shared/model/data-api.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  compatConfig: { MODE: 3 },
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

    const sourceTypeOptions = [
      { value: DATA_API_SOURCE_TYPE_TABLE, label: '数据源的表' },
      { value: DATA_API_SOURCE_TYPE_SQL, label: '自定义 SQL' },
    ];

    const statusOptions = [
      { value: 'ENABLED', label: '启用（对外可访问）' },
      { value: 'DISABLED', label: '停用（禁止对外访问）' },
    ];

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

    const retrieveDataApi = async (dataApiId: number) => {
      try {
        const res = await dataApiService().find(dataApiId);
        res.createTime = new Date(res.createTime);
        res.updateTime = new Date(res.updateTime);
        dataApi.value = res;
        await loadDataSources();
        await fetchSchemas();
        await fetchTables();
        await fetchColumns();
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
      }
    };

    const onDataSourceChange = (event: Event) => {
      const target = event.target as HTMLSelectElement;
      dataApi.value.dataSourceId = target.value ? Number(target.value) : null;
      dataApi.value.schemaName = null;
      dataApi.value.tableName = null;
      schemas.value = [];
      tables.value = [];
      columns.value = [];
      fetchSchemas();
    };

    const onSchemaChange = (event: Event) => {
      const target = event.target as HTMLSelectElement;
      dataApi.value.schemaName = target.value || null;
      dataApi.value.tableName = null;
      tables.value = [];
      columns.value = [];
      fetchTables();
    };

    const onTableChange = (event: Event) => {
      const target = event.target as HTMLSelectElement;
      dataApi.value.tableName = target.value || null;
      columns.value = [];
      fetchColumns();
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
      dataSourceLabel,
      SOURCE_TYPE_TABLE: DATA_API_SOURCE_TYPE_TABLE,
      SOURCE_TYPE_SQL: DATA_API_SOURCE_TYPE_SQL,
    };
  },
  methods: {
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
      if (!api.dataSourceId) {
        this.alertService.showError('请选择数据源');
        return;
      }
      if (api.sourceType === this.SOURCE_TYPE_TABLE) {
        if (!api.schemaName || !api.tableName) {
          this.alertService.showError('请选择 Schema 和数据表');
          return;
        }
      } else if (api.sourceType === this.SOURCE_TYPE_SQL) {
        if (!api.sqlText || !api.sqlText.trim()) {
          this.alertService.showError('请填写自定义 SQL');
          return;
        }
      } else {
        this.alertService.showError('请选择数据来源类型');
        return;
      }

      this.isSaving = true;
      const payload: IDataApi = { ...this.dataApi, name: api.name.trim(), code: api.code.trim() };
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
