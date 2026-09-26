import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import JobDependService from './job-depend.service';
import { useDateFormat } from '@/shared/composables';
import { type IJobDepend } from '@/shared/model/job-depend.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  name: 'JobDependDetails',
  setup() {
    const dateFormat = useDateFormat();
    const jobDependService = inject('jobDependService', () => new JobDependService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const jobDepend: Ref<IJobDepend> = ref({});

    const retrieveJobDepend = async jobDependId => {
      try {
        const res = await jobDependService().find(jobDependId);
        jobDepend.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.jobDependId) {
      retrieveJobDepend(route.params.jobDependId);
    }

    return {
      ...dateFormat,
      alertService,
      jobDepend,

      previousState,
    };
  },
});
