import { type Ref, defineComponent, inject, onMounted, onUnmounted, ref, watch } from 'vue';

import JobService from './job.service';
import { type IJob } from '@/shared/model/job.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  name: 'Job',
  setup() {
    const dateFormat = useDateFormat();
    const jobService = inject('jobService', () => new JobService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref('id');
    const reverse = ref(false);
    const totalItems = ref(0);
    const search = ref('');

    const jobs: Ref<IJob[]> = ref([]);

    const isFetching = ref(false);

    const clear = () => {
      page.value = 1;
    };

    const sort = (): Array<any> => {
      const result = [`${propOrder.value},${reverse.value ? 'desc' : 'asc'}`];
      if (propOrder.value !== 'id') {
        result.push('id');
      }
      return result;
    };

    const retrieveJobs = async () => {
      isFetching.value = true;
      try {
        const paginationQuery = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
          search: search.value,
        };
        const res = await jobService().retrieve(paginationQuery);
        totalItems.value = Number(res.headers['x-total-count']);
        queryCount.value = totalItems.value;
        jobs.value = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isFetching.value = false;
      }
    };

    let searchTimer: ReturnType<typeof setTimeout> | null = null;

    watch(search, () => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
      searchTimer = setTimeout(() => {
        if (page.value === 1) {
          retrieveJobs();
        } else {
          clear();
        }
      }, 300);
    });

    onUnmounted(() => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
    });

    onMounted(async () => {
      await retrieveJobs();
    });

    const removeId: Ref<number> = ref(null);
    const removeEntity = ref<any>(null);
    const prepareRemove = (instance: IJob) => {
      removeId.value = instance.id;
      removeEntity.value.show();
    };
    const closeDialog = () => {
      removeEntity.value.hide();
    };
    const removeJob = async () => {
      try {
        await jobService().delete(removeId.value);
        const message = `A Job is deleted with identifier ${removeId.value}`;
        alertService.showInfo(message, { variant: 'danger' });
        removeId.value = null;
        retrieveJobs();
        closeDialog();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const handleExecuteOnce = async (job: IJob) => {
      try {
        await jobService().executeOnce(job.id);
        alertService.showInfo(`Job ${job.id} 已成功执行一次`);
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const changeOrder = (newOrder: string) => {
      const [prop, order] = newOrder.split(',');
      if (propOrder.value === prop) {
        reverse.value = order === 'desc';
      } else {
        propOrder.value = prop;
        reverse.value = order === 'desc';
      }
    };

    const handleSortChange = (column: { prop: string; order: 'ascending' | 'descending' | null }) => {
      if (column.prop && column.order) {
        const order = column.order === 'ascending' ? 'asc' : 'desc';
        changeOrder(`${column.prop},${order}`);
      }
    };

    // Whenever order changes, reset the pagination
    watch([propOrder, reverse, itemsPerPage], async () => {
      if (page.value === 1) {
        // first page, retrieve new data
        await retrieveJobs();
      } else {
        // reset the pagination
        clear();
      }
    });

    // Whenever page changes, switch to the new page.
    watch(page, async () => {
      await retrieveJobs();
    });

    return {
      jobs,
      search,
      isFetching,
      retrieveJobs,
      clear,
      ...dateFormat,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removeJob,
      handleExecuteOnce,
      itemsPerPage,
      queryCount,
      page,
      propOrder,
      reverse,
      totalItems,
      changeOrder,
      handleSortChange,
    };
  },
});