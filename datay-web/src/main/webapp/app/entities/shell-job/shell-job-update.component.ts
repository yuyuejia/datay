import { computed, defineComponent } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import ShellJobEditor from './shell-job-editor.vue';
import { type IJob } from '@/shared/model/job.model';

export default defineComponent({
  name: 'ShellJobUpdate',
  components: {
    ShellJobEditor,
  },
  setup() {
    const route = useRoute();
    const router = useRouter();

    const jobId = computed(() => (route.params.jobId ? String(route.params.jobId) : null));
    const directoryId = computed(() => (route.query.directoryId ? String(route.query.directoryId) : null));

    const onSaved = (job?: IJob) => {
      router.push({
        name: 'ShellJob',
        query: job?.id ? { jobId: job.id } : {},
      });
    };

    return {
      jobId,
      directoryId,
      onSaved,
    };
  },
});
