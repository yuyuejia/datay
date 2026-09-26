import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import ETLEdgeService from './etl-edge.service';
import { type IETLEdge } from '@/shared/model/etl-edge.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  name: 'ETLEdgeDetails',
  setup() {
    const eTLEdgeService = inject('eTLEdgeService', () => new ETLEdgeService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const eTLEdge: Ref<IETLEdge> = ref({});

    const retrieveETLEdge = async eTLEdgeId => {
      try {
        const res = await eTLEdgeService().find(eTLEdgeId);
        eTLEdge.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.eTLEdgeId) {
      retrieveETLEdge(route.params.eTLEdgeId);
    }

    return {
      alertService,
      eTLEdge,

      previousState,
    };
  },
});
