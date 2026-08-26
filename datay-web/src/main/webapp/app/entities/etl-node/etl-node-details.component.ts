import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import ETLNodeService from './etl-node.service';
import { useDateFormat } from '@/shared/composables';
import { type IETLNode } from '@/shared/model/etl-node.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'ETLNodeDetails',
  setup() {
    const dateFormat = useDateFormat();
    const eTLNodeService = inject('eTLNodeService', () => new ETLNodeService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const eTLNode: Ref<IETLNode> = ref({});

    const retrieveETLNode = async eTLNodeId => {
      try {
        const res = await eTLNodeService().find(eTLNodeId);
        eTLNode.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.eTLNodeId) {
      retrieveETLNode(route.params.eTLNodeId);
    }

    return {
      ...dateFormat,
      alertService,
      eTLNode,

      previousState,
    };
  },
});
