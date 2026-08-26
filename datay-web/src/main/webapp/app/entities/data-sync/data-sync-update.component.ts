import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import DataSourceSelector from '@/components/DataSourceSelector.vue';
import DataSourceTableSelector from '@/components/DataSourceTableSelector.vue';
import DataSyncService from './data-sync.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { DataSync, type IDataSync } from '@/shared/model/data-sync.model';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'DataSyncUpdate',
  components: {
    DataSourceSelector,
    DataSourceTableSelector,
  },
  setup() {
    const dataSyncService = inject('dataSyncService', () => new DataSyncService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const dataSync: Ref<IDataSync> = ref(new DataSync());
    const isSaving = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const dataSyncLoaded = ref(false);
    const sourceDataSourceId = ref<number | null>(null);
    const sourceSchema = ref<string | null>(null);
    const targetDataSourceId = ref<number | null>(null);
    const targetSchema = ref<string | null>(null);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    const selectedTables = ref<any[]>([]); // 修改为存储对象数组

    const retrieveDataSync = async dataSyncId => {
      try {
        const res = await dataSyncService().find(dataSyncId);
        res.updateTime = new Date(res.updateTime);
        res.createTime = new Date(res.createTime);
        sourceDataSourceId.value = res.source;
        targetDataSourceId.value = res.target;
        dataSync.value = res;
        // 初始化已选中的表
        if (res.selectedTables) {
          selectedTables.value = res.selectedTables;
          sourceSchema.value = res.selectedTables[0].srcSchemaName;
          targetSchema.value = res.selectedTables[0].desSchemaName;
        }
        // 设置加载完成状态
        dataSyncLoaded.value = true;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.dataSyncId) {
      retrieveDataSync(route.params.dataSyncId);
    } else {
      dataSyncLoaded.value = true;
    }

    const validations = useValidation();
    const validationRules = {
      jobName: {},
      jobCode: {},
      jobDesc: {},
      dir: {},
      type: {
        required: true,
      },
      source: {},
      target: {},
      cron: {},
      jobContext: {},
      status: {},
      lastStatus: {},
      updateTime: {},
      createTime: {},
      project: {},
      tenantId: {},
      dr: {},
    };
    const v$ = useVuelidate(validationRules, dataSync as any);
    v$.value.$validate();

    const handleSourceSelected = (selection: { dataSourceId: number; schema: string }) => {
      dataSync.value.source = `${selection.dataSourceId}`;
      sourceDataSourceId.value = selection.dataSourceId;
      sourceSchema.value = selection.schema;
    };

    const handleTargetSelected = (selection: { dataSourceId: number; schema: string }) => {
      dataSync.value.target = `${selection.dataSourceId}`;
      targetDataSourceId.value = selection.dataSourceId;
      targetSchema.value = selection.schema;
    };

    const updateSelectedTables = (tables: any[]) => {
      // 初始化目标表名和增量字段
      tables.forEach(table => {
        table.srcTableName = table.table;
        table.desTableName = table.srcTableName;
        table.incrementalField = table.incrementalField || '';
      });
      selectedTables.value = tables;
    };

    // 验证增量字段
    const validateIncrementalFields = () => {
      if (dataSync.value.type !== 'INCREMENTAL_SYNC') {
        return true;
      }
      return selectedTables.value.every(table => table.srcColPks && table.srcColPks.trim() !== '');
    };

    return {
      dataSyncService,
      alertService,
      dataSync,
      previousState,
      isSaving,
      currentLanguage,
      v$,
      handleSourceSelected,
      handleTargetSelected,
      selectedTables,
      sourceDataSourceId,
      sourceSchema,
      targetDataSourceId,
      targetSchema,
      updateSelectedTables,
      validateIncrementalFields,
      dataSyncLoaded,
      ...useDateFormat({ entityRef: dataSync }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      // 构建表配置信息
      this.dataSync.selectedTables = this.selectedTables.map(table => ({
        srcTableName: table.srcTableName,
        srcSchemaName: this.sourceSchema,
        desTableName: table.desTableName,
        desSchemaName: this.targetSchema,
        srcColPks: table.srcColPks || null,
      }));

      if (this.dataSync.id) {
        this.dataSyncService()
          .update(this.dataSync)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A DataSync is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.dataSyncService()
          .create(this.dataSync)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A DataSync is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
