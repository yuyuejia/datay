import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DpTableService from './dp-table.service';
import { useDateFormat } from '@/shared/composables';
import { type IDpTable } from '@/shared/model/dp-table.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'DpTableDetails',
  setup() {
    const dateFormat = useDateFormat();
    const dpTableService = inject('dpTableService', () => new DpTableService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const dpTable: Ref<IDpTable> = ref({});

    const retrieveDpTable = async dpTableId => {
      try {
        const res = await dpTableService().find(dpTableId);
        dpTable.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.dpTableId) {
      retrieveDpTable(route.params.dpTableId);
    }

    return {
      ...dateFormat,
      alertService,
      dpTable,

      previousState,
    };
  },
});
