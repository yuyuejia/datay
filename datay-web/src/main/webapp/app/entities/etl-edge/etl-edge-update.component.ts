import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import ETLEdgeService from './etl-edge.service';
import { useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { ETLEdge, type IETLEdge } from '@/shared/model/etl-edge.model';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'ETLEdgeUpdate',
  setup() {
    const eTLEdgeService = inject('eTLEdgeService', () => new ETLEdgeService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const eTLEdge: Ref<IETLEdge> = ref(new ETLEdge());
    const isSaving = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

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

    const validations = useValidation();
    const validationRules = {
      taskId: {},
      name: {},
      code: {},
      source: {},
      target: {},
      config: {},
      status: {},
      tenantId: {},
      dr: {},
    };
    const v$ = useVuelidate(validationRules, eTLEdge as any);
    v$.value.$validate();

    return {
      eTLEdgeService,
      alertService,
      eTLEdge,
      previousState,
      isSaving,
      currentLanguage,
      v$,
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.eTLEdge.id) {
        this.eTLEdgeService()
          .update(this.eTLEdge)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A ETLEdge is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.eTLEdgeService()
          .create(this.eTLEdge)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A ETLEdge is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
