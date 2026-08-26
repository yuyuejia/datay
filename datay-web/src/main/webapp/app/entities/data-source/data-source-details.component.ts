import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DataSourceService from './data-source.service';
import { useDateFormat } from '@/shared/composables';
import { type IDataSource } from '@/shared/model/data-source.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'DataSourceDetails',
  setup() {
    const dateFormat = useDateFormat();
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const dataSource: Ref<IDataSource> = ref({});

    const retrieveDataSource = async dataSourceId => {
      try {
        const res = await dataSourceService().find(dataSourceId);
        dataSource.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.dataSourceId) {
      retrieveDataSource(route.params.dataSourceId);
    }

    return {
      ...dateFormat,
      alertService,
      dataSource,

      previousState,
    };
  },
});
