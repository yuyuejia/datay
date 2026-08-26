import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import DataSyncTableConfigService from './data-sync-table-config.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { DataSyncTableConfig, type IDataSyncTableConfig } from '@/shared/model/data-sync-table-config.model';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'DataSyncTableConfigUpdate',
  setup() {
    const dataSyncTableConfigService = inject('dataSyncTableConfigService', () => new DataSyncTableConfigService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const dataSyncTableConfig: Ref<IDataSyncTableConfig> = ref(new DataSyncTableConfig());
    const isSaving = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    const retrieveDataSyncTableConfig = async dataSyncTableConfigId => {
      try {
        const res = await dataSyncTableConfigService().find(dataSyncTableConfigId);
        res.updateTime = new Date(res.updateTime);
        res.createTime = new Date(res.createTime);
        dataSyncTableConfig.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.dataSyncTableConfigId) {
      retrieveDataSyncTableConfig(route.params.dataSyncTableConfigId);
    }

    const validations = useValidation();
    const validationRules = {
      syncTask: {},
      srcDatasource: {},
      srcSchemaName: {},
      srcTableName: {},
      srcColPks: {},
      desDatasource: {},
      desSchemaName: {},
      desTableName: {},
      desColPks: {},
      jobDesc: {},
      columeConfig: {},
      updateTime: {},
      createTime: {},
      project: {},
      tenantId: {},
      dr: {},
    };
    const v$ = useVuelidate(validationRules, dataSyncTableConfig as any);
    v$.value.$validate();

    return {
      dataSyncTableConfigService,
      alertService,
      dataSyncTableConfig,
      previousState,
      isSaving,
      currentLanguage,
      v$,
      ...useDateFormat({ entityRef: dataSyncTableConfig }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.dataSyncTableConfig.id) {
        this.dataSyncTableConfigService()
          .update(this.dataSyncTableConfig)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A DataSyncTableConfig is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.dataSyncTableConfigService()
          .create(this.dataSyncTableConfig)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A DataSyncTableConfig is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
