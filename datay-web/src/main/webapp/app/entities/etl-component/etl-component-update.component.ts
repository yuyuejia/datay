import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import ETLComponentService from './etl-component.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { ETLComponent, type IETLComponent } from '@/shared/model/etl-component.model';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'ETLComponentUpdate',
  setup() {
    const eTLComponentService = inject('eTLComponentService', () => new ETLComponentService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const eTLComponent: Ref<IETLComponent> = ref(new ETLComponent());
    const isSaving = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    const retrieveETLComponent = async eTLComponentId => {
      try {
        const res = await eTLComponentService().find(eTLComponentId);
        res.updateTime = new Date(res.updateTime);
        res.createTime = new Date(res.createTime);
        eTLComponent.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.eTLComponentId) {
      retrieveETLComponent(route.params.eTLComponentId);
    }

    const validations = useValidation();
    const validationRules = {
      name: {},
      code: {},
      desc: {},
      group: {},
      type: {},
      config: {},
      status: {},
      updateTime: {},
      createTime: {},
      creater: {},
      tenantId: {},
      dr: {},
    };
    const v$ = useVuelidate(validationRules, eTLComponent as any);
    v$.value.$validate();

    return {
      eTLComponentService,
      alertService,
      eTLComponent,
      previousState,
      isSaving,
      currentLanguage,
      v$,
      ...useDateFormat({ entityRef: eTLComponent }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.eTLComponent.id) {
        this.eTLComponentService()
          .update(this.eTLComponent)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A ETLComponent is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.eTLComponentService()
          .create(this.eTLComponent)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A ETLComponent is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
