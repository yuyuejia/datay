import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import ETLNodeService from './etl-node.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { ETLNode, type IETLNode } from '@/shared/model/etl-node.model';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'ETLNodeUpdate',
  setup() {
    const eTLNodeService = inject('eTLNodeService', () => new ETLNodeService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const eTLNode: Ref<IETLNode> = ref(new ETLNode());
    const isSaving = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    const retrieveETLNode = async eTLNodeId => {
      try {
        const res = await eTLNodeService().find(eTLNodeId);
        res.updateTime = new Date(res.updateTime);
        res.createTime = new Date(res.createTime);
        eTLNode.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.eTLNodeId) {
      retrieveETLNode(route.params.eTLNodeId);
    }

    const validations = useValidation();
    const validationRules = {
      taskId: {},
      label: {},
      code: {},
      desc: {},
      type: {},
      config: {},
      xAxis: {},
      yAxis: {},
      status: {},
      updateTime: {},
      createTime: {},
      tenantId: {},
      dr: {},
    };
    const v$ = useVuelidate(validationRules, eTLNode as any);
    v$.value.$validate();

    return {
      eTLNodeService,
      alertService,
      eTLNode,
      previousState,
      isSaving,
      currentLanguage,
      v$,
      ...useDateFormat({ entityRef: eTLNode }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.eTLNode.id) {
        this.eTLNodeService()
          .update(this.eTLNode)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A ETLNode is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.eTLNodeService()
          .create(this.eTLNode)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A ETLNode is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
