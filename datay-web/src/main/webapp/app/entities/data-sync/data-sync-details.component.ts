import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DataSyncService from './data-sync.service';
import { useDateFormat } from '@/shared/composables';
import { type IDataSync } from '@/shared/model/data-sync.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  name: 'DataSyncDetails',
  setup() {
    const dateFormat = useDateFormat();
    const dataSyncService = inject('dataSyncService', () => new DataSyncService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const dataSync: Ref<IDataSync> = ref({});

    const retrieveDataSync = async dataSyncId => {
      try {
        const res = await dataSyncService().find(dataSyncId);
        dataSync.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.dataSyncId) {
      retrieveDataSync(route.params.dataSyncId);
    }

    return {
      ...dateFormat,
      alertService,
      dataSync,

      previousState,
    };
  },
});
