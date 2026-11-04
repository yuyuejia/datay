import { type Ref, computed, defineComponent, inject, nextTick, ref, watch, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessageBox } from 'element-plus';

import ETLTaskService from './etl-task.service';
import EtlTaskStateService from './etl-task-state.service';
import { type IETLTask, type IETLTaskImportResult, type IETLTaskTransfer } from '@/shared/model/etl-task.model';
import { type IJobInstance } from '@/shared/model/job-instance.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';
import CronExpressionSelector from '@/components/CronExpressionSelector.vue';

interface IStateRow {
  key: string;
  value: string;
  originalValue: string;
}

export default defineComponent({
  name: 'ETLTask',
  components: {
    CronExpressionSelector,
  },
  setup() {
    const route = useRoute();
    const dateFormat = useDateFormat();
    const eTLTaskService = inject('eTLTaskService', () => new ETLTaskService());
    const etlTaskStateService = inject('etlTaskStateService', () => new EtlTaskStateService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref('updateTime');
    const reverse = ref(true);
    const totalItems = ref(0);
    const search = ref('');

    /** 当前菜单对应的任务类型：批量任务 / 实时任务。 */
    const taskKind = computed<'BATCH' | 'STREAM'>(() => (route.meta.taskKind === 'STREAM' ? 'STREAM' : 'BATCH'));
    const isRealtimeTask = computed(() => taskKind.value === 'STREAM');
    const pageTitle = computed(() => (isRealtimeTask.value ? '实时任务' : '批量任务'));
    const createButtonText = computed(() => (isRealtimeTask.value ? '创建实时任务' : '创建批量任务'));
    const createRouteName = computed(() => (isRealtimeTask.value ? 'ETLTaskRealtimeDesignNew' : 'ETLTaskDesignNew'));
    const editRouteName = computed(() => (isRealtimeTask.value ? 'ETLTaskRealtimeDesign' : 'ETLTaskDesign'));

    /** 实时任务展示最近一次实例状态，批量任务仍展示任务自身状态。 */
    const rowStatus = (task: IETLTask): string => ((isRealtimeTask.value ? task.lastStatus : task.status) || '-') as string;
    const rowStatusClass = (task: IETLTask) => {
      const status = rowStatus(task);
      return ['status-badge', status === '-' ? '' : `status-${status.toLowerCase()}`];
    };

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

    // 实时任务日志：直接展示最近一次实例，默认滚动到最新并定时增量刷新
    const LOG_MAX_LINES = 5000;
    const LOG_REFRESH_INTERVAL_MS = 3000;
    const logContentRef = ref<HTMLElement | null>(null);
    const logOffset = ref(0);
    const logAutoRefresh = ref(false);
    const isRealtimeLog = ref(false);
    let logAutoRefreshTimer: ReturnType<typeof setInterval> | null = null;
    let logStickToBottom = true;
    let logRefreshing = false;

    const isLogNearBottom = () => {
      const el = logContentRef.value;
      if (!el) {
        return true;
      }
      return el.scrollHeight - el.scrollTop - el.clientHeight < 40;
    };

    const scrollLogToBottom = async (force = false) => {
      await nextTick();
      const el = logContentRef.value;
      if (el && (force || logStickToBottom)) {
        el.scrollTop = el.scrollHeight;
      }
    };

    const handleLogScroll = () => {
      logStickToBottom = isLogNearBottom();
    };

    const limitLogLines = (content: string): string => {
      if (!content) {
        return '';
      }
      const lines = content.split(/\r?\n/);
      if (lines.length <= LOG_MAX_LINES) {
        return content;
      }
      return lines.slice(lines.length - LOG_MAX_LINES).join('\n');
    };

    const appendLogContent = (chunk: string) => {
      if (!chunk) {
        return;
      }
      logContent.value = limitLogLines(`${logContent.value}${chunk}`);
    };

    const stopLogAutoRefresh = () => {
      if (logAutoRefreshTimer) {
        clearInterval(logAutoRefreshTimer);
        logAutoRefreshTimer = null;
      }
      logAutoRefresh.value = false;
    };

    const refreshRealtimeLog = async (silent = false) => {
      const instance = currentLogInstance.value;
      if (!instance?.instanceCode || !instance?.jobCode || logRefreshing) {
        return;
      }
      logRefreshing = true;
      if (!silent) {
        isLogLoading.value = true;
      }
      try {
        const stickToBottom = isLogNearBottom();
        const logData = await eTLTaskService().getTaskLog(
          instance.jobCode,
          instance.instanceCode,
          logOffset.value,
          10485760,
          logOffset.value === 0 ? LOG_MAX_LINES : 0,
        );
        if (logData.success) {
          logOffset.value = Number(logData.newOffset ?? logOffset.value);
          if (logData.content) {
            appendLogContent(logData.content);
            logStickToBottom = stickToBottom;
            await scrollLogToBottom();
          }
        } else if (!silent) {
          alertService.showError(logData.message || '加载日志失败');
        }
      } catch (err) {
        if (!silent) {
          alertService.showHttpError(err.response);
        }
      } finally {
        logRefreshing = false;
        if (!silent) {
          isLogLoading.value = false;
        }
      }
    };

    const startLogAutoRefresh = () => {
      stopLogAutoRefresh();
      logAutoRefresh.value = true;
      logAutoRefreshTimer = setInterval(() => {
        void refreshRealtimeLog(true);
      }, LOG_REFRESH_INTERVAL_MS);
    };

    const handleLogAutoRefreshChange = (enabled: boolean) => {
      if (enabled) {
        startLogAutoRefresh();
      } else {
        stopLogAutoRefresh();
      }
    };

    const openLatestRealtimeLog = async (task: IETLTask) => {
      currentTask.value = task;
      currentLogInstance.value = {};
      logContent.value = '';
      logOffset.value = 0;
      isRealtimeLog.value = true;
      isLogLoading.value = true;
      try {
        const res = await eTLTaskService().getTaskInstances(task.id, {
          page: 0,
          size: 1,
          sort: ['createTime,desc', 'id,desc'],
        });
        const latestInstance = (res.data || [])[0];
        if (!latestInstance) {
          alertService.showInfo('当前任务还没有执行实例', { variant: 'info' });
          return;
        }
        currentLogInstance.value = latestInstance;
        logModal.value.show();
        const logData = await eTLTaskService().getTaskLog(latestInstance.jobCode, latestInstance.instanceCode, 0, 10485760, LOG_MAX_LINES);
        if (logData.success) {
          logContent.value = limitLogLines(logData.content || '');
          logOffset.value = Number(logData.newOffset || 0);
          logStickToBottom = true;
        } else {
          logContent.value = logData.message || '暂无日志内容';
        }
        startLogAutoRefresh();
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isLogLoading.value = false;
        await scrollLogToBottom(true);
      }
    };

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
          type: taskKind.value,
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
      stopLogAutoRefresh();
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
      stopLogAutoRefresh();
      isRealtimeLog.value = false;
      logOffset.value = 0;
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

    const handleLogRefresh = async () => {
      if (isRealtimeLog.value) {
        await refreshRealtimeLog(false);
        return;
      }
      await refreshLog();
    };

    const closeLogModal = () => {
      stopLogAutoRefresh();
      isRealtimeLog.value = false;
      logOffset.value = 0;
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
        // 刷新列表以展示最新一次实例的执行状态
        await retrieveETLTasks();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    // ---------------- 终止运行中的实时任务 ----------------

    /** 可终止的实例状态：与任务实例页面保持一致。 */
    const TERMINATABLE_STATUSES = ['RUNNING', 'STARTING'];
    const terminatingTaskId: Ref<string | null> = ref(null);

    const terminateETLTask = async (task: IETLTask) => {
      if (!task?.id || terminatingTaskId.value) {
        return;
      }
      try {
        await ElMessageBox.confirm(`确定要终止任务「${task.taskName || task.id}」当前运行中的实例吗？`, '终止任务', {
          confirmButtonText: '终止',
          cancelButtonText: '取消',
          type: 'warning',
        });
      } catch {
        // 用户取消终止
        return;
      }
      terminatingTaskId.value = task.id;
      try {
        const res = await eTLTaskService().getTaskInstances(task.id, {
          page: 0,
          size: 50,
          sort: ['createTime,desc'],
        });
        const runningInstances = (res.data || []).filter((instance: IJobInstance) => {
          const status = (instance.status || '').toUpperCase();
          return TERMINATABLE_STATUSES.includes(status);
        });
        if (runningInstances.length === 0) {
          alertService.showInfo('当前任务没有运行中的实例', { variant: 'info' });
          return;
        }
        const results = await Promise.allSettled(
          runningInstances.map((instance: IJobInstance) => eTLTaskService().stopTaskInstance(instance.instanceCode)),
        );
        const failedCount = results.filter(result => result.status === 'rejected').length;
        const stoppedCount = runningInstances.length - failedCount;
        if (failedCount === 0) {
          alertService.showSuccess(`已终止 ${stoppedCount} 个运行中的实例`);
        } else if (stoppedCount > 0) {
          alertService.showWarning(`已终止 ${stoppedCount} 个实例，${failedCount} 个实例终止失败`);
        } else {
          alertService.showError('终止失败，请稍后重试');
        }
        await retrieveETLTasks();
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        terminatingTaskId.value = null;
      }
    };

    // ---------------- 上线（确认调度周期） ----------------

    const onlineModal = ref<any>(null);
    const onlineTask: Ref<IETLTask | null> = ref(null);
    const onlineCron = ref('');
    const onlineRetryCount = ref(0);
    const onlineTimeoutSeconds = ref(0);
    const onlinePriority = ref(2);
    const isOnlineSubmitting = ref(false);

    /** Cron 表达式的基础校验：至少包含 6 段（秒 分 时 日 月 周）或 7 段。 */
    const onlineCronValid = computed(() => {
      const parts = (onlineCron.value || '').trim().split(/\s+/).filter(Boolean);
      return parts.length === 6 || parts.length === 7;
    });

    const onlinePolicyValid = computed(
      () =>
        Number.isInteger(onlineRetryCount.value) &&
        onlineRetryCount.value >= 0 &&
        onlineRetryCount.value <= 10 &&
        Number.isInteger(onlineTimeoutSeconds.value) &&
        onlineTimeoutSeconds.value >= 0 &&
        onlineTimeoutSeconds.value <= 604800 &&
        Number.isInteger(onlinePriority.value) &&
        onlinePriority.value >= 1 &&
        onlinePriority.value <= 3,
    );

    const onlineETLTask = (task: IETLTask) => {
      onlineTask.value = task;
      onlineCron.value = task.cron || '';
      onlineRetryCount.value = task.retryCount ?? 0;
      onlineTimeoutSeconds.value = task.timeoutSeconds ?? 0;
      onlinePriority.value = task.priority ?? 2;
      onlineModal.value?.show();
    };

    const closeOnlineModal = () => {
      onlineModal.value?.hide();
      onlineTask.value = null;
      onlineCron.value = '';
      onlineRetryCount.value = 0;
      onlineTimeoutSeconds.value = 0;
      onlinePriority.value = 2;
    };

    const confirmOnline = async () => {
      if (!onlineTask.value?.id) {
        return;
      }
      if (!onlineCron.value?.trim()) {
        alertService.showError('当前任务尚未设置调度周期，请先设置 Cron 表达式');
        return;
      }
      if (!onlineCronValid.value) {
        alertService.showError('调度周期格式不正确，请填写 6 段或 7 段 Cron 表达式');
        return;
      }
      if (!onlinePolicyValid.value) {
        alertService.showError('重试次数需在 0-10 之间，超时时间需在 0-604800 秒之间，优先级需在 1-3 之间');
        return;
      }
      isOnlineSubmitting.value = true;
      try {
        await eTLTaskService().online(
          onlineTask.value.id,
          onlineCron.value.trim(),
          onlineRetryCount.value,
          onlineTimeoutSeconds.value,
          onlinePriority.value,
        );
        alertService.showInfo(`ETL 任务 ${onlineTask.value.taskName || ''} 已上线调度`, { variant: 'success' });
        closeOnlineModal();
        await retrieveETLTasks();
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isOnlineSubmitting.value = false;
      }
    };

    const offlineETLTask = async (task: IETLTask) => {
      if (!task?.id) {
        return;
      }
      try {
        await ElMessageBox.confirm(`确定要将任务「${task.taskName || task.id}」下线吗？下线后将取消定时调度。`, '下线确认', {
          confirmButtonText: '确认下线',
          cancelButtonText: '取消',
          type: 'warning',
        });
      } catch {
        // 用户取消下线
        return;
      }
      try {
        await eTLTaskService().offline(task.id);
        alertService.showInfo(`ETL 任务 ${task.taskName || ''} 已下线`, { variant: 'success' });
        await retrieveETLTasks();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    // ---------------- 导出 / 导入 ----------------

    const fileInput = ref<HTMLInputElement | null>(null);
    const importResultVisible = ref(false);
    const importResult: Ref<IETLTaskImportResult | null> = ref(null);

    const exportETLTask = async (task: IETLTask) => {
      try {
        const data = await eTLTaskService().exportTask(task.id);
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${task.taskCode || 'etl-task'}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        alertService.showInfo('任务已导出', { variant: 'success' });
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const triggerImport = () => {
      fileInput.value?.click();
    };

    const onFileSelected = async (event: Event) => {
      const input = event.target as HTMLInputElement;
      const file = input?.files?.[0];
      input.value = '';
      if (!file) {
        return;
      }
      const text = await file.text();
      let transfer: IETLTaskTransfer;
      try {
        transfer = JSON.parse(text);
      } catch {
        alertService.showError('所选文件不是合法的 JSON 任务文件');
        return;
      }
      if (!transfer || !transfer.task) {
        alertService.showError('所选文件不是合法的 ETL 任务文件');
        return;
      }
      // 按当前菜单入口归类，保证导入后出现在对应任务列表
      transfer.task.type = taskKind.value;
      try {
        const result = await eTLTaskService().importTask(transfer);
        importResult.value = result;
        importResultVisible.value = true;
        alertService.showInfo('ETL 任务导入成功', { variant: 'success' });
        await retrieveETLTasks();
      } catch (error: any) {
        alertService.showError(error.response?.data?.detail || error.response?.data?.message || 'ETL 任务导入失败');
      }
    };

    const closeImportResult = () => {
      importResultVisible.value = false;
    };

    const importUnmatchedRefs = (result: IETLTaskImportResult | null): string[] => {
      if (!result) {
        return [];
      }
      const items = [...(result.dataSources || []), ...(result.models || [])];
      return items.filter(item => !item.matched).map(item => item.label || item.oldId);
    };

    // 操作列「更多」下拉：日志 / 状态 / 导出 / 删除
    const handleRowCommand = (command: string, task: IETLTask) => {
      switch (command) {
        case 'logs':
          if (isRealtimeTask.value) {
            openLatestRealtimeLog(task);
          } else {
            prepareViewInstances(task);
          }
          break;
        case 'state':
          openStateManager(task);
          break;
        case 'export':
          exportETLTask(task);
          break;
        case 'delete':
          prepareRemove(task);
          break;
        default:
          break;
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
      taskKind,
      isRealtimeTask,
      pageTitle,
      createButtonText,
      createRouteName,
      editRouteName,
      rowStatus,
      rowStatusClass,
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
      terminateETLTask,
      terminatingTaskId,
      onlineETLTask,
      onlineModal,
      onlineTask,
      onlineCron,
      onlineRetryCount,
      onlineTimeoutSeconds,
      onlinePriority,
      onlineCronValid,
      onlinePolicyValid,
      isOnlineSubmitting,
      closeOnlineModal,
      confirmOnline,
      offlineETLTask,
      fileInput,
      importResultVisible,
      importResult,
      exportETLTask,
      triggerImport,
      onFileSelected,
      closeImportResult,
      importUnmatchedRefs,
      handleRowCommand,
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
      logContentRef,
      logAutoRefresh,
      isRealtimeLog,
      logMaxLines: LOG_MAX_LINES,
      prepareViewLog,
      refreshLog,
      handleLogRefresh,
      handleLogScroll,
      handleLogAutoRefreshChange,
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
