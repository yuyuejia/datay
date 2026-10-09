import { type Ref, computed, defineComponent, inject, ref, watch, onMounted, onUnmounted } from 'vue';
import { ElMessageBox } from 'element-plus';

import ETLTaskService from './etl-task.service';
import EtlTaskStateService from './etl-task-state.service';
import { type IETLTask } from '@/shared/model/etl-task.model';
import { type IJobInstance } from '@/shared/model/job-instance.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

interface IStateRow {
  key: string;
  value: string;
  originalValue: string;
}

export default defineComponent({
  name: 'ETLTask',
  setup() {
    const dateFormat = useDateFormat();
    const eTLTaskService = inject('eTLTaskService', () => new ETLTaskService());
    const etlTaskStateService = inject('etlTaskStateService', () => new EtlTaskStateService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref('id');
    const reverse = ref(false);
    const totalItems = ref(0);
    const search = ref('');

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
          search: search.value,
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

    let searchTimer: ReturnType<typeof setTimeout> | null = null;

    // Whenever the search keyword changes, reset the pagination and re-fetch (debounced)
    watch(search, () => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
      searchTimer = setTimeout(() => {
        if (page.value === 1) {
          // first page, retrieve new data
          retrieveETLTasks();
        } else {
          // reset the pagination
          clear();
        }
      }, 300);
    });

    onUnmounted(() => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
    });

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

    const removeId: Ref<string> = ref(null);
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

    const runETLTask = async (id: string) => {
      try {
        await eTLTaskService().run(id);
        const message = 'ETL Task has been triggered to run once';
        alertService.showInfo(message, { variant: 'success' });
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const onlineETLTask = async (id: string) => {
      try {
        await eTLTaskService().online(id);
        const message = 'ETL Task has been set to online';
        alertService.showInfo(message, { variant: 'success' });
        await retrieveETLTasks();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const offlineETLTask = async (id: string) => {
      try {
        await eTLTaskService().offline(id);
        const message = 'ETL Task has been set to offline';
        alertService.showInfo(message, { variant: 'success' });
        await retrieveETLTasks();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const stateModal = ref<any>(null);
    const currentStateTask: Ref<IETLTask> = ref<IETLTask>({});
    const stateJobCode = ref('');
    const stateNodeNames: Ref<Record<string, string>> = ref({});
    const stateRows: Ref<IStateRow[]> = ref([]);
    const deletedStateKeys: Ref<string[]> = ref([]);
    const isStateLoading = ref(false);
    const isStateSaving = ref(false);

    // 状态键形如 <节点id>.<状态项>，按第一段节点 id 分组展示
    const stateGroups = computed(() => {
      const map = new Map<string, IStateRow[]>();
      stateRows.value.forEach(row => {
        const index = row.key.indexOf('.');
        const nodeId = index > 0 ? row.key.substring(0, index) : '其他';
        const rows = map.get(nodeId);
        if (rows) {
          rows.push(row);
        } else {
          map.set(nodeId, [row]);
        }
      });
      return Array.from(map.entries()).map(([nodeId, rows]) => ({ nodeId, rows }));
    });

    const shortKey = (row: IStateRow): string => {
      const index = row.key.indexOf('.');
      return index > 0 ? row.key.substring(index + 1) : row.key;
    };

    const nodeDisplayName = (nodeId: string): string => stateNodeNames.value[nodeId] || nodeId;

    const valueToString = (value: any): string => {
      if (value === null || value === undefined) {
        return '';
      }
      return typeof value === 'string' ? value : JSON.stringify(value);
    };

    const applyStateResponse = (res: any) => {
      stateJobCode.value = res.jobCode || '';
      stateNodeNames.value = res.nodeNames || {};
      stateRows.value = Object.entries(res.state || {}).map(([key, value]) => {
        const display = valueToString(value);
        return { key, value: display, originalValue: display };
      });
      deletedStateKeys.value = [];
    };

    const reloadState = async () => {
      if (!currentStateTask.value?.id) return;
      isStateLoading.value = true;
      try {
        const res = await etlTaskStateService().getState(currentStateTask.value.id);
        applyStateResponse(res);
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isStateLoading.value = false;
      }
    };

    const openStateManager = async (task: IETLTask) => {
      currentStateTask.value = task;
      stateRows.value = [];
      deletedStateKeys.value = [];
      stateModal.value.show();
      await reloadState();
    };

    const removeStateRow = (row: IStateRow) => {
      const index = stateRows.value.indexOf(row);
      if (index >= 0) {
        stateRows.value.splice(index, 1);
      }
      deletedStateKeys.value.push(row.key);
    };

    const saveState = async () => {
      if (!currentStateTask.value?.id) return;
      const patch: Record<string, any> = {};
      deletedStateKeys.value.forEach(key => {
        patch[key] = null;
      });
      stateRows.value.forEach(row => {
        if (row.value !== row.originalValue) {
          patch[row.key] = row.value;
        }
      });
      if (Object.keys(patch).length === 0) {
        alertService.showInfo('没有需要保存的修改', { variant: 'info' });
        return;
      }
      isStateSaving.value = true;
      try {
        const res = await etlTaskStateService().patchState(currentStateTask.value.id, patch);
        applyStateResponse(res);
        alertService.showInfo('状态已更新', { variant: 'success' });
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isStateSaving.value = false;
      }
    };

    const clearState = async () => {
      if (!currentStateTask.value?.id) return;
      try {
        await ElMessageBox.confirm('确定要清空该任务的全部状态吗？清空后任务下次启动将从头开始读取。', '清空状态', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        });
      } catch {
        return;
      }
      isStateSaving.value = true;
      try {
        const res = await etlTaskStateService().deleteState(currentStateTask.value.id);
        applyStateResponse(res);
        alertService.showInfo('任务状态已清空', { variant: 'success' });
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isStateSaving.value = false;
      }
    };

    const closeStateModal = () => {
      stateModal.value?.hide();
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

    watch([propOrder, reverse, itemsPerPage], async () => {
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
      search,
      ...dateFormat,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removeETLTask,
      runETLTask,
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
      stateModal,
      currentStateTask,
      stateJobCode,
      stateRows,
      stateGroups,
      shortKey,
      nodeDisplayName,
      isStateLoading,
      isStateSaving,
      openStateManager,
      reloadState,
      removeStateRow,
      saveState,
      clearState,
      closeStateModal,
    };
  },
});
