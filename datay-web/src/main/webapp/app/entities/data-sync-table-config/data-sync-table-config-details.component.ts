import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DataSyncTableConfigService from './data-sync-table-config.service';
import { useDateFormat } from '@/shared/composables';
import { type IDataSyncTableConfig } from '@/shared/model/data-sync-table-config.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  name: 'DataSyncTableConfigDetails',
  setup() {
    const dateFormat = useDateFormat();
    const dataSyncTableConfigService = inject('dataSyncTableConfigService', () => new DataSyncTableConfigService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const dataSyncTableConfig: Ref<IDataSyncTableConfig> = ref({});

    const retrieveDataSyncTableConfig = async dataSyncTableConfigId => {
      try {
        const res = await dataSyncTableConfigService().find(dataSyncTableConfigId);
        dataSyncTableConfig.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.dataSyncTableConfigId) {
      retrieveDataSyncTableConfig(route.params.dataSyncTableConfigId);
    }

    return {
      ...dateFormat,
      alertService,
      dataSyncTableConfig,

      previousState,
    };
  },
});
