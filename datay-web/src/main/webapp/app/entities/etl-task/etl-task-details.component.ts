import { type Ref, defineComponent, inject, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import ETLTaskService from './etl-task.service';
import { useDateFormat } from '@/shared/composables';
import { type IETLTask } from '@/shared/model/etl-task.model';
import { type IJobInstance } from '@/shared/model/job-instance.model';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  name: 'ETLTaskDetails',
  setup() {
    const dateFormat = useDateFormat();
    const eTLTaskService = inject('eTLTaskService', () => new ETLTaskService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const eTLTask: Ref<IETLTask> = ref({});

    const retrieveETLTask = async eTLTaskId => {
      try {
        const res = await eTLTaskService().find(eTLTaskId);
        eTLTask.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    // Task instances list
    const itemsPerPage = ref(10);
    const page: Ref<number> = ref(1);
    const propOrder = ref('createTime');
    const reverse = ref(true);
    const totalItems = ref(0);
    const queryCount: Ref<number> = ref(null);

    const taskInstances: Ref<IJobInstance[]> = ref([]);
    const isFetchingInstances = ref(false);

    const sort = (): Array<any> => {
      const result = [`${propOrder.value},${reverse.value ? 'desc' : 'asc'}`];
      if (propOrder.value !== 'id') {
        result.push('id');
      }
      return result;
    };

    const retrieveTaskInstances = async () => {
      if (!eTLTask.value.id) return;
      isFetchingInstances.value = true;
      try {
        const paginationQuery = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
        };
        const res = await eTLTaskService().getTaskInstances(eTLTask.value.id, paginationQuery);
        totalItems.value = Number(res.headers['x-total-count']);
        queryCount.value = totalItems.value;
        taskInstances.value = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isFetchingInstances.value = false;
      }
    };

    const handleSyncList = () => {
      retrieveTaskInstances();
    };

    // Log viewing
    const logContent = ref('');
    const isLogLoading = ref(false);
    const logEntity = ref<any>(null);
    const currentLogInstance: Ref<IJobInstance> = ref<IJobInstance>({});
    const logOffset = ref(0);
    const hasMoreLog = ref(false);

    const prepareViewLog = async (instance: IJobInstance) => {
      currentLogInstance.value = instance;
      logContent.value = '';
      logOffset.value = 0;
      hasMoreLog.value = false;

      try {
        await loadLogContent();
      } catch (error) {
        alertService.showHttpError(error.response);
      }

      logEntity.value.show();
    };

    const loadLogContent = async () => {
      if (!currentLogInstance.value) return;
      isLogLoading.value = true;
      try {
        const logData = await eTLTaskService().getTaskLog(
          currentLogInstance.value.jobCode,
          currentLogInstance.value.instanceCode,
          logOffset.value,
        );
        if (logData.success) {
          logContent.value += logData.content;
          logOffset.value = logData.newOffset;
          hasMoreLog.value = logData.hasMore;
        } else {
          alertService.showInfo(logData.message || '暂无日志', { variant: 'warning' });
        }
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isLogLoading.value = false;
      }
    };

    const loadMoreLog = async () => {
      await loadLogContent();
    };

    const closeLogDialog = () => {
      logEntity.value.hide();
      currentLogInstance.value = null;
      logContent.value = '';
      logOffset.value = 0;
      hasMoreLog.value = false;
    };

    const downloadLog = () => {
      if (!logContent.value) return;
      const blob = new Blob([logContent.value], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `job-log-${currentLogInstance.value.jobCode}-${currentLogInstance.value.instanceCode}.txt`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    };

    const formatDateTime = (timestamp: number | string): string => {
      if (!timestamp) return '';
      const timeValue = typeof timestamp === 'string' ? parseInt(timestamp, 10) : timestamp;
      if (isNaN(timeValue) || timeValue <= 0) return '';
      try {
        const date = new Date(timeValue);
        if (isNaN(date.getTime())) return '';
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');
        return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
      } catch (error) {
        return '';
      }
    };

    const getStatusBadgeClass = (status: string): string => {
      if (!status) return 'badge bg-secondary';
      const statusMap: Record<string, string> = {
        RUNNING: 'badge bg-primary',
        STARTING: 'badge bg-info',
        SUCCESS: 'badge bg-success',
        FAILED: 'badge bg-danger',
        KILLED: 'badge bg-warning text-dark',
        PENDING: 'badge bg-secondary',
      };
      return statusMap[status] || 'badge bg-secondary';
    };

    if (route.params?.eTLTaskId) {
      retrieveETLTask(route.params.eTLTaskId);
    }

    watch(
      () => eTLTask.value.id,
      async newId => {
        if (newId) {
          await retrieveTaskInstances();
        }
      },
    );

    watch([propOrder, reverse, itemsPerPage], async () => {
      if (page.value === 1) {
        await retrieveTaskInstances();
      } else {
        page.value = 1;
      }
    });

    watch(page, async () => {
      await retrieveTaskInstances();
    });

    return {
      ...dateFormat,
      alertService,
      eTLTask,
      previousState,

      taskInstances,
      isFetchingInstances,
      itemsPerPage,
      queryCount,
      page,
      totalItems,
      handleSyncList,

      logContent,
      isLogLoading,
      logEntity,
      currentLogInstance,
      hasMoreLog,
      prepareViewLog,
      loadMoreLog,
      closeLogDialog,
      downloadLog,
      formatDateTime,
      getStatusBadgeClass,
    };
  },
});
