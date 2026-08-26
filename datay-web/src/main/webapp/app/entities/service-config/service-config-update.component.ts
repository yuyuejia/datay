import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import ServiceConfigService from './service-config.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { type IServiceConfig, ServiceConfig } from '@/shared/model/service-config.model';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'ServiceConfigUpdate',
  setup() {
    const serviceConfigService = inject('serviceConfigService', () => new ServiceConfigService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const serviceConfig: Ref<IServiceConfig> = ref(new ServiceConfig());
    const isSaving = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    const retrieveServiceConfig = async serviceConfigId => {
      try {
        const res = await serviceConfigService().find(serviceConfigId);
        res.createTime = new Date(res.createTime);
        serviceConfig.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.serviceConfigId) {
      retrieveServiceConfig(route.params.serviceConfigId);
    }

    const validations = useValidation();
    const validationRules = {
      dfGroup: {},
      dfKey: {},
      dfValue: {},
      createTime: {},
      ytenantId: {},
    };
    const v$ = useVuelidate(validationRules, serviceConfig as any);
    v$.value.$validate();

    return {
      serviceConfigService,
      alertService,
      serviceConfig,
      previousState,
      isSaving,
      currentLanguage,
      v$,
      ...useDateFormat({ entityRef: serviceConfig }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.serviceConfig.id) {
        this.serviceConfigService()
          .update(this.serviceConfig)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A ServiceConfig is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.serviceConfigService()
          .create(this.serviceConfig)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A ServiceConfig is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
