import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import JobService from './job.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { type IJob, Job } from '@/shared/model/job.model';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'JobUpdate',
  setup() {
    const jobService = inject('jobService', () => new JobService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const job: Ref<IJob> = ref(new Job());
    const isSaving = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    const retrieveJob = async jobId => {
      try {
        const res = await jobService().find(jobId);
        res.updateTime = new Date(res.updateTime);
        res.createTime = new Date(res.createTime);
        job.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.jobId) {
      retrieveJob(route.params.jobId);
    }

    const validations = useValidation();
    const validationRules = {
      jobName: {},
      jobGroup: {},
      type: {},
      cron: {},
      jobContext: {},
      status: {},
      updateTime: {},
      createTime: {},
      project: {},
      tenantId: {},
    };
    const v$ = useVuelidate(validationRules, job as any);
    v$.value.$validate();

    return {
      jobService,
      alertService,
      job,
      previousState,
      isSaving,
      currentLanguage,
      v$,
      ...useDateFormat({ entityRef: job }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.job.id) {
        this.jobService()
          .update(this.job)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A Job is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.jobService()
          .create(this.job)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A Job is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
