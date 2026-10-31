import { computed, defineComponent, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';
import { ElMessage, ElMessageBox } from 'element-plus';
import DataCompareService, { type IDataCompareResult, type IDataCompareTableResult, type IDataCompareTask } from './data-compare.service';
import DataSourceService from '@/entities/data-source/data-source.service';

const STATUS_LABELS: Record<string, string> = {
  APPENDING: '排队中',
  RUNNING: '运行中',
  SUCCESSFUL: '成功',
  FAILED: '失败',
  INTERRUPTED: '已停止',
  TIMEOUT: '超时',
  OFFLINE: '离线',
  ONLINE: '在线',
};

export default defineComponent({
  name: 'DataCompare',
  setup() {
    const router = useRouter();
    const service = new DataCompareService();
    const dataSourceService = new DataSourceService();

    const tasks = ref<IDataCompareTask[]>([]);
    const search = ref('');
    const page = ref(1);
    const size = ref(20);
    const total = ref(0);
    const isFetching = ref(false);
    const executingId = ref<string | null>(null);
    const dataSourceNames = ref<Record<string, string>>({});

    const instanceQueryId = ref('');
    const resultsDialogVisible = ref(false);
    const selectedTask = ref<IDataCompareTask | null>(null);
    const results = ref<IDataCompareResult[]>([]);
    const activeResult = ref<IDataCompareResult | null>(null);
    const latestResultId = ref<string | null>(null);
    const historyVisible = ref(false);
    const selectedTableIndex = ref(0);

    const selectedTable = computed<IDataCompareTableResult | null>(
      () => activeResult.value?.tables?.[selectedTableIndex.value] || activeResult.value?.tables?.[0] || null,
    );

    const detailLoading = ref(false);
    const detailColumns = ref<string[]>([]);
    const detailRows = ref<Record<string, any>[]>([]);
    const detailTruncated = ref(false);
    const detailHint = ref('');
    let detailRequestSeq = 0;

    const logDialogVisible = ref(false);
    const logContent = ref('');
    const currentLogResult = ref<IDataCompareResult | null>(null);

    let pollTimer: number | null = null;

    const resultsDialogTitle = computed(() => `比对结果 - ${selectedTask.value?.taskName || ''}`);
    const isLatestResult = computed(() => Boolean(activeResult.value?.id) && activeResult.value?.id === latestResultId.value);

    const statusText = (status?: string | null) => (status ? (STATUS_LABELS[status] ?? status) : '-');

    const DETAIL_TYPE_LABELS: Record<string, string> = {
      VALUE_DIFF: '字段值差异',
      ONLY_LEFT: '仅左侧存在',
      ONLY_RIGHT: '仅右侧存在',
      SAME: '一致',
    };

    const detailColumnLabel = (column: string): string => {
      if (column === 'diff_type') return '差异类型';
      if (column.startsWith('key__')) return `主键（${column.substring(5)}）`;
      if (column.startsWith('left__')) return `左侧（${column.substring(6)}）`;
      if (column.startsWith('right__')) return `右侧（${column.substring(7)}）`;
      return column;
    };

    const detailCellValue = (column: string, value: any): any => {
      if (column === 'diff_type' && value != null) {
        return DETAIL_TYPE_LABELS[value] ?? value;
      }
      return value;
    };

    const formatDate = (value?: string | number | null): string => {
      if (!value) return '';
      const date = typeof value === 'number' ? new Date(value) : new Date(value);
      if (Number.isNaN(date.getTime())) return '';
      const pad = (n: number) => String(n).padStart(2, '0');
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(
        date.getSeconds(),
      )}`;
    };

    const dataSourceLabel = (id?: string | null) => (id ? dataSourceNames.value[id] || id : '-');

    const isTaskRunning = (task: IDataCompareTask) =>
      task.lastStatus === 'APPENDING' ||
      task.lastStatus === 'RUNNING' ||
      task.lastResult?.status === 'APPENDING' ||
      task.lastResult?.status === 'RUNNING';

    const loadDataSources = async () => {
      try {
        const res = await dataSourceService.retrieve({ page: 0, size: 2000, sort: ['id,asc'] });
        const names: Record<string, string> = {};
        (res.data || []).forEach((item: any) => {
          if (item.id) names[item.id] = item.name || item.id;
        });
        dataSourceNames.value = names;
      } catch {
        // 数据源名称加载失败不影响任务列表
      }
    };

    const retrieveTasks = async () => {
      isFetching.value = true;
      try {
        const res = await service.retrieve({
          page: page.value - 1,
          size: size.value,
          sort: ['updateTime,desc'],
          search: search.value || undefined,
        });
        tasks.value = res.data || [];
        total.value = Number(res.headers['x-total-count'] || 0);
      } catch (e: any) {
        ElMessage.error(e?.response?.data?.detail || e?.message || '加载数据比对任务失败');
      } finally {
        isFetching.value = false;
      }
    };

    const handlePageChange = (value: number) => {
      page.value = value;
      retrieveTasks();
    };

    const handleSizeChange = (value: number) => {
      size.value = value;
      page.value = 1;
      retrieveTasks();
    };

    const goEdit = (task: IDataCompareTask) => {
      router.push({ name: 'DataCompareEdit', params: { id: task.id } });
    };

    const executeTask = async (task: IDataCompareTask) => {
      if (!task.id) return;
      try {
        await ElMessageBox.confirm(`确定立即执行数据比对任务「${task.taskName}」吗？`, '立即执行', { type: 'warning' });
      } catch {
        return;
      }
      executingId.value = task.id;
      try {
        const result = await service.execute(task.id);
        ElMessage.success('数据比对任务已提交');
        selectedTask.value = { ...task, lastStatus: result.status, lastResult: result };
        activeResult.value = result;
        latestResultId.value = result.id || null;
        selectedTableIndex.value = 0;
        results.value = [result];
        historyVisible.value = false;
        resultsDialogVisible.value = true;
        ensurePolling();
        await retrieveTasks();
      } catch (e: any) {
        ElMessage.error(e?.response?.data?.detail || e?.response?.data?.message || e?.message || '执行失败');
      } finally {
        executingId.value = null;
      }
    };

    const deleteTask = async (task: IDataCompareTask) => {
      if (!task.id) return;
      try {
        await ElMessageBox.confirm(`确定删除数据比对任务「${task.taskName}」吗？`, '删除确认', { type: 'warning' });
      } catch {
        return;
      }
      try {
        await service.delete(task.id);
        ElMessage.success('删除成功');
        retrieveTasks();
      } catch (e: any) {
        ElMessage.error(e?.response?.data?.detail || e?.message || '删除失败');
      }
    };

    const loadResults = async () => {
      if (!selectedTask.value?.id) return;
      try {
        const res = await service.getResults(selectedTask.value.id, 0, 100);
        results.value = res.data || [];
        if (results.value.length > 0) {
          latestResultId.value = results.value[0].id || null;
        }
        const currentId = activeResult.value?.id;
        const matched = currentId ? results.value.find(item => item.id === currentId) : null;
        activeResult.value = matched || results.value[0] || activeResult.value || null;
      } catch (e: any) {
        ElMessage.error(e?.response?.data?.detail || e?.message || '加载比对结果失败');
      }
    };

    const openResults = async (task: IDataCompareTask) => {
      selectedTask.value = task;
      activeResult.value = task.lastResult || null;
      latestResultId.value = task.lastResult?.id || null;
      selectedTableIndex.value = 0;
      results.value = [];
      historyVisible.value = false;
      resultsDialogVisible.value = true;
      await loadResults();
      ensurePolling();
    };

    const selectHistoryResult = (row: IDataCompareResult) => {
      activeResult.value = row;
      selectedTableIndex.value = 0;
      historyVisible.value = false;
    };

    const selectTable = (index: number) => {
      selectedTableIndex.value = index;
      loadDetail(activeResult.value);
    };

    const queryByInstanceId = async () => {
      const id = instanceQueryId.value.trim();
      if (!id) return;
      try {
        const result = await service.getResultByInstanceId(id);
        try {
          const task = await service.find(result.taskId!);
          selectedTask.value = task;
          latestResultId.value = task.lastResult?.id || null;
        } catch {
          selectedTask.value = { id: result.taskId, taskName: result.taskName || result.taskId };
          latestResultId.value = result.id || null;
        }
        activeResult.value = result;
        selectedTableIndex.value = 0;
        results.value = [result];
        historyVisible.value = false;
        resultsDialogVisible.value = true;
        ensurePolling();
      } catch (e: any) {
        if (e?.response?.status === 404) {
          ElMessage.warning('未找到该执行实例 ID 对应的比对结果');
        } else {
          ElMessage.error(e?.response?.data?.detail || e?.message || '查询失败');
        }
      }
    };

    const ensurePolling = () => {
      clearPolling();
      pollTimer = window.setInterval(async () => {
        const status = activeResult.value?.status;
        if (!resultsDialogVisible.value || (status !== 'APPENDING' && status !== 'RUNNING')) {
          clearPolling();
          return;
        }
        if (selectedTask.value?.id) {
          await loadResults();
        } else if (activeResult.value?.jobInstanceId) {
          try {
            activeResult.value = await service.getResultByInstanceId(activeResult.value.jobInstanceId);
          } catch {
            // ignore polling failure
          }
        }
      }, 3000);
    };

    const clearPolling = () => {
      if (pollTimer != null) {
        window.clearInterval(pollTimer);
        pollTimer = null;
      }
    };

    const downloadResult = async (result: IDataCompareResult, table?: IDataCompareTableResult) => {
      try {
        await service.downloadResultFile(result, table || selectedTable.value || undefined);
      } catch (e: any) {
        ElMessage.error(e?.response?.data?.detail || e?.message || '下载差异文件失败');
      }
    };

    const downloadSummary = async (result: IDataCompareResult, table?: IDataCompareTableResult) => {
      if (!result.jobInstanceId) return;
      try {
        await service.downloadSummaryFile(result.jobInstanceId, table || selectedTable.value || undefined);
      } catch (e: any) {
        ElMessage.error(e?.response?.data?.detail || e?.message || '下载汇总文件失败');
      }
    };

    /**
     * 结果详情默认展示当前结果的前 100 行差异；历史实例切换后重新加载。
     */
    const loadDetail = async (result: IDataCompareResult | null) => {
      const requestSeq = ++detailRequestSeq;
      detailColumns.value = [];
      detailRows.value = [];
      detailTruncated.value = false;
      detailHint.value = '';
      detailLoading.value = false;
      if (!result) {
        detailHint.value = '暂无比对结果';
        return;
      }
      if (result.status !== 'SUCCESSFUL') {
        detailHint.value =
          result.status === 'FAILED' || result.status === 'TIMEOUT' ? '执行未成功，无差异明细' : '任务执行完成后可查看差异明细';
        return;
      }
      if (result.compareDetail === false) {
        detailHint.value = '本次仅比对记录数，未生成差异明细';
        return;
      }
      if (!selectedTable.value) {
        detailHint.value = '无表比对结果';
        return;
      }
      const tableFormat = selectedTable.value.resultFormat || result.resultFormat;
      if ((tableFormat || '').toUpperCase() !== 'CSV') {
        detailHint.value = 'Parquet 格式请下载结果文件查看';
        return;
      }
      if (!result.id) {
        detailHint.value = '结果文件不存在';
        return;
      }
      detailLoading.value = true;
      try {
        const preview = await service.previewResult(result.id, 100, selectedTable.value.index ?? 0);
        if (requestSeq !== detailRequestSeq) {
          return;
        }
        detailColumns.value = preview.columns || [];
        detailRows.value = preview.rows || [];
        detailTruncated.value = Boolean(preview.truncated);
        if (detailRows.value.length === 0) {
          detailHint.value = '无差异数据';
        }
      } catch (e: any) {
        if (requestSeq === detailRequestSeq) {
          detailHint.value = e?.response?.data?.detail || e?.message || '加载差异明细失败';
        }
      } finally {
        if (requestSeq === detailRequestSeq) {
          detailLoading.value = false;
        }
      }
    };

    watch(activeResult, result => {
      loadDetail(result);
    });

    const loadLog = async () => {
      const result = currentLogResult.value;
      if (!result?.jobId || !result?.instanceCode) return;
      try {
        const res = await axios.get('api/worker/log', {
          params: {
            jobCode: result.jobId,
            jobInstanceCode: result.instanceCode,
            offset: 0,
            maxSize: 10 * 1024 * 1024,
          },
        });
        logContent.value = res.data?.success ? res.data.content || '' : res.data?.message || '加载日志失败';
      } catch (e: any) {
        logContent.value = e?.response?.data?.message || e?.message || '加载日志失败';
      }
    };

    const openLog = (result: IDataCompareResult) => {
      currentLogResult.value = result;
      logContent.value = '';
      logDialogVisible.value = true;
      loadLog();
    };

    const refreshLog = () => loadLog();

    const copyText = async (value?: string | null) => {
      if (!value) return;
      try {
        await navigator.clipboard.writeText(value);
        ElMessage.success('已复制');
      } catch {
        ElMessage.warning('复制失败，请手动复制');
      }
    };

    onMounted(async () => {
      await Promise.all([retrieveTasks(), loadDataSources()]);
    });

    onUnmounted(clearPolling);

    return {
      tasks,
      search,
      page,
      size,
      total,
      isFetching,
      executingId,
      instanceQueryId,
      resultsDialogVisible,
      selectedTask,
      results,
      activeResult,
      resultsDialogTitle,
      isLatestResult,
      historyVisible,
      selectedTableIndex,
      selectedTable,
      detailLoading,
      detailColumns,
      detailRows,
      detailTruncated,
      detailHint,
      detailColumnLabel,
      detailCellValue,
      logDialogVisible,
      logContent,
      dataSourceLabel,
      statusText,
      formatDate,
      isTaskRunning,
      retrieveTasks,
      handlePageChange,
      handleSizeChange,
      goEdit,
      executeTask,
      deleteTask,
      openResults,
      selectHistoryResult,
      selectTable,
      queryByInstanceId,
      downloadResult,
      downloadSummary,
      openLog,
      refreshLog,
      copyText,
    };
  },
});
