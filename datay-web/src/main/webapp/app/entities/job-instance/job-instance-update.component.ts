import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import JobInstanceService from './job-instance.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { type IJobInstance, JobInstance } from '@/shared/model/job-instance.model';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'JobInstanceUpdate',
  setup() {
    const jobInstanceService = inject('jobInstanceService', () => new JobInstanceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const jobInstance: Ref<IJobInstance> = ref(new JobInstance());
    const isSaving = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    const retrieveJobInstance = async jobInstanceId => {
      try {
        const res = await jobInstanceService().find(jobInstanceId);
        res.createTime = new Date(res.createTime);
        jobInstance.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.jobInstanceId) {
      retrieveJobInstance(route.params.jobInstanceId);
    }

    const validations = useValidation();
    const validationRules = {
      instanceCode: {},
      jobName: {},
      jobCode: {},
      type: {},
      jobContext: {},
      status: {},
      jobMessage: {},
      execNode: {},
      startTime: {},
      endTime: {},
      createTime: {},
      project: {},
      tenantId: {},
    };
    const v$ = useVuelidate(validationRules, jobInstance as any);
    v$.value.$validate();

    return {
      jobInstanceService,
      alertService,
      jobInstance,
      previousState,
      isSaving,
      currentLanguage,
      v$,
      ...useDateFormat({ entityRef: jobInstance }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.jobInstance.id) {
        this.jobInstanceService()
          .update(this.jobInstance)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A JobInstance is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.jobInstanceService()
          .create(this.jobInstance)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A JobInstance is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
