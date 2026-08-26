import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import ETLComponentService from './etl-component.service';
import { useDateFormat } from '@/shared/composables';
import { type IETLComponent } from '@/shared/model/etl-component.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'ETLComponentDetails',
  setup() {
    const dateFormat = useDateFormat();
    const eTLComponentService = inject('eTLComponentService', () => new ETLComponentService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const eTLComponent: Ref<IETLComponent> = ref({});

    const retrieveETLComponent = async eTLComponentId => {
      try {
        const res = await eTLComponentService().find(eTLComponentId);
        eTLComponent.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.eTLComponentId) {
      retrieveETLComponent(route.params.eTLComponentId);
    }

    return {
      ...dateFormat,
      alertService,
      eTLComponent,

      previousState,
    };
  },
});
