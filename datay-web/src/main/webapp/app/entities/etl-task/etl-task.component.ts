import { type Ref, defineComponent, inject, ref, watch, onMounted } from 'vue';

import ETLTaskService from './etl-task.service';
import { type IETLTask } from '@/shared/model/etl-task.model';
import { type IJobInstance } from '@/shared/model/job-instance.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'ETLTask',
  setup() {
    const dateFormat = useDateFormat();
    const eTLTaskService = inject('eTLTaskService', () => new ETLTaskService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref('id');
    const reverse = ref(false);
    const totalItems = ref(0);

    const eTLTasks: Ref<IETLTask[]> = ref([]);

    const isFetching = ref(false);

    const currentTask: Ref<IETLTask> = ref<IETLTask>({});
    const instancesModal = ref<any>(null);
    const isInstancesLoading = ref(false);
    const taskInstances: Ref<IJobInstance[]> = ref([]);

    const logModal = ref<any>(null);
    const currentLogInstance: Ref<IJobInstance> = ref<IJobInstance>({});
    const logContent = ref('');
    const isLogLoading = ref(false);

    const formatDateTime = (timestamp: number | string): string => {
      if (!timestamp) return '';
      const timeValue = typeof timestamp === 'string' ? parseInt(timestamp, 10) : timestamp;
      if (isNaN(timeValue) || timeValue <= 0) return '';
      const date = new Date(timeValue);
      if (isNaN(date.getTime())) return '';
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
    };

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

    const retrieveETLTasks = async () => {
      isFetching.value = true;
      try {
        const paginationQuery = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
        };
        const res = await eTLTaskService().retrieve(paginationQuery);
        totalItems.value = Number(res.headers['x-total-count']);
        queryCount.value = totalItems.value;
        eTLTasks.value = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isFetching.value = false;
      }
    };

    const handleSyncList = () => {
      retrieveETLTasks();
    };

    const prepareViewInstances = async (task: IETLTask) => {
      currentTask.value = task;
      isInstancesLoading.value = true;
      taskInstances.value = [];
      try {
        const res = await eTLTaskService().getTaskInstances(task.id, {
          page: 0,
          size: 10,
          sort: ['createTime,desc', 'id,desc'],
        });
        taskInstances.value = res.data || [];
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isInstancesLoading.value = false;
      }
      instancesModal.value.show();
    };

    const closeInstancesModal = () => {
      instancesModal.value.hide();
      taskInstances.value = [];
    };

    const prepareViewLog = async (instance: IJobInstance) => {
      currentLogInstance.value = instance;
      logContent.value = '';
      isLogLoading.value = true;
      try {
        const logData = await eTLTaskService().getTaskLog(instance.jobCode, instance.instanceCode, 0);
        if (logData.success) {
          logContent.value = logData.content || '';
        } else {
          logContent.value = logData.message || '加载日志失败';
        }
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isLogLoading.value = false;
      }
      logModal.value.show();
    };

    const refreshLog = async () => {
      if (!currentLogInstance.value?.instanceCode) return;
      isLogLoading.value = true;
      logContent.value = '';
      try {
        const logData = await eTLTaskService().getTaskLog(currentLogInstance.value.jobCode, currentLogInstance.value.instanceCode, 0);
        if (logData.success) {
          logContent.value = logData.content || '';
        } else {
          logContent.value = logData.message || '加载日志失败';
        }
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isLogLoading.value = false;
      }
    };

    const closeLogModal = () => {
      logModal.value.hide();
      logContent.value = '';
      currentLogInstance.value = null;
    };

    const removeId: Ref<number> = ref(null);
    const removeEntity = ref<any>(null);
    const prepareRemove = (instance: IETLTask) => {
      removeId.value = instance.id;
      removeEntity.value.show();
    };
    const closeDialog = () => {
      removeEntity.value.hide();
    };
    const removeETLTask = async () => {
      try {
        await eTLTaskService().delete(removeId.value);
        const message = `A ETLTask is deleted with identifier ${removeId.value}`;
        alertService.showInfo(message, { variant: 'danger' });
        removeId.value = null;
        retrieveETLTasks();
        closeDialog();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const onlineETLTask = async (id: number) => {
      try {
        await eTLTaskService().online(id);
        const message = 'ETL Task has been set to online';
        alertService.showInfo(message, { variant: 'success' });
        await retrieveETLTasks();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const offlineETLTask = async (id: number) => {
      try {
        await eTLTaskService().offline(id);
        const message = 'ETL Task has been set to offline';
        alertService.showInfo(message, { variant: 'success' });
        await retrieveETLTasks();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const handleSortChange = (column: { prop: string; order: 'ascending' | 'descending' | null }) => {
      if (column.prop && column.order) {
        if (column.order === 'ascending') {
          reverse.value = false;
        } else {
          reverse.value = true;
        }
        propOrder.value = column.prop;
      }
    };

    watch([propOrder, reverse], async () => {
      if (page.value === 1) {
        await retrieveETLTasks();
      } else {
        clear();
      }
    });

    watch(page, async () => {
      await retrieveETLTasks();
    });

    onMounted(async () => {
      await retrieveETLTasks();
    });

    return {
      eTLTasks,
      handleSyncList,
      isFetching,
      retrieveETLTasks,
      clear,
      ...dateFormat,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removeETLTask,
      onlineETLTask,
      offlineETLTask,
      itemsPerPage,
      queryCount,
      page,
      propOrder,
      reverse,
      totalItems,
      handleSortChange,
      currentTask,
      instancesModal,
      isInstancesLoading,
      taskInstances,
      prepareViewInstances,
      closeInstancesModal,
      logModal,
      currentLogInstance,
      logContent,
      isLogLoading,
      prepareViewLog,
      refreshLog,
      closeLogModal,
      formatDateTime,
    };
  },
});
