import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import JobDependService from './job-depend.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { type IJobDepend, JobDepend } from '@/shared/model/job-depend.model';

export default defineComponent({
  name: 'JobDependUpdate',
  setup() {
    const jobDependService = inject('jobDependService', () => new JobDependService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const jobDepend: Ref<IJobDepend> = ref(new JobDepend());
    const isSaving = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    const retrieveJobDepend = async jobDependId => {
      try {
        const res = await jobDependService().find(jobDependId);
        res.createTime = new Date(res.createTime);
        jobDepend.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.jobDependId) {
      retrieveJobDepend(route.params.jobDependId);
    }

    const validations = useValidation();
    const validationRules = {
      parentJobCode: {},
      childJobCode: {},
      jobCode: {},
      lastInterval: {},
      createTime: {},
      tenantId: {},
    };
    const v$ = useVuelidate(validationRules, jobDepend as any);
    v$.value.$validate();

    return {
      jobDependService,
      alertService,
      jobDepend,
      previousState,
      isSaving,
      currentLanguage,
      v$,
      ...useDateFormat({ entityRef: jobDepend }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.jobDepend.id) {
        this.jobDependService()
          .update(this.jobDepend)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A JobDepend is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.jobDependService()
          .create(this.jobDepend)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A JobDepend is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
