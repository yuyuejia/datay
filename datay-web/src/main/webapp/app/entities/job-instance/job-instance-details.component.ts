import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import JobInstanceService from './job-instance.service';
import { useDateFormat } from '@/shared/composables';
import { type IJobInstance } from '@/shared/model/job-instance.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  name: 'JobInstanceDetails',
  setup() {
    const dateFormat = useDateFormat();
    const jobInstanceService = inject('jobInstanceService', () => new JobInstanceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const jobInstance: Ref<IJobInstance> = ref({});

    const retrieveJobInstance = async jobInstanceId => {
      try {
        const res = await jobInstanceService().find(jobInstanceId);
        jobInstance.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.jobInstanceId) {
      retrieveJobInstance(route.params.jobInstanceId);
    }

    return {
      ...dateFormat,
      alertService,
      jobInstance,

      previousState,
    };
  },
});
