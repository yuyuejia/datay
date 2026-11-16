import { computed, defineComponent } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import SqlJobEditor from './sql-job-editor.vue';
import { type IJob } from '@/shared/model/job.model';

export default defineComponent({
  name: 'SqlJobUpdate',
  components: {
    SqlJobEditor,
  },
  setup() {
    const route = useRoute();
    const router = useRouter();

    const jobId = computed(() => (route.params.jobId ? String(route.params.jobId) : null));
    const directoryId = computed(() => (route.query.directoryId ? String(route.query.directoryId) : null));

    const onSaved = (job?: IJob) => {
      router.push({
        name: 'SqlJob',
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
