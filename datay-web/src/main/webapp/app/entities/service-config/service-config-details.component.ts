import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import ServiceConfigService from './service-config.service';
import { useDateFormat } from '@/shared/composables';
import { type IServiceConfig } from '@/shared/model/service-config.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  name: 'ServiceConfigDetails',
  setup() {
    const dateFormat = useDateFormat();
    const serviceConfigService = inject('serviceConfigService', () => new ServiceConfigService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const serviceConfig: Ref<IServiceConfig> = ref({});

    const retrieveServiceConfig = async serviceConfigId => {
      try {
        const res = await serviceConfigService().find(serviceConfigId);
        serviceConfig.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.serviceConfigId) {
      retrieveServiceConfig(route.params.serviceConfigId);
    }

    return {
      ...dateFormat,
      alertService,
      serviceConfig,

      previousState,
    };
  },
});
