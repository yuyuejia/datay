import { type Ref, computed, defineComponent, inject, onActivated, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import { useAlertService } from '@/shared/alert/alert.service';
import { useDateFormat } from '@/shared/composables';
import CronExpressionSelector from '@/components/CronExpressionSelector.vue';
import DataSourceSelector from '@/components/DataSourceSelector.vue';
import DatasetService from '@/shared/dataset/dataset.service';
import DataApiService from '@/entities/data-api/data-api.service';
import type { AnalysisDataset, DatasetPersistWriteMode } from '@/shared/dataset/types';

interface DataSourceSelection {
  dataSourceId?: string;
  schema?: string;
}

interface PersistFormState {
  persistEnabled: boolean;
  persistTargetDataSourceId?: string;
  persistTargetSchema?: string;
  persistTargetTable?: string;
  persistWriteMode: DatasetPersistWriteMode;
  persistCron?: string;
}

/**
 * 分析数据集列表：独立管理可被多个看板复用的指标 / SQL 数据集。
 */
export default defineComponent({
  name: 'AnalysisDataset',
  components: { CronExpressionSelector, DataSourceSelector },
  setup() {
    const router = useRouter();
    const alertService = inject('alertService', () => useAlertService(), true);
    const service = new DatasetService();
    const dataApiService = new DataApiService();
    const dateFormat = useDateFormat();

    const datasets: Ref<AnalysisDataset[]> = ref([]);
    const isFetching = ref(false);
    const search = ref('');
    const removeId: Ref<string | null> = ref(null);
    const removeEntity = ref<any>(null);

    const previewVisible = ref(false);
    const previewLoading = ref(false);
    const previewColumns = ref<string[]>([]);
    const previewRows = ref<Record<string, any>[]>([]);
    const previewColumnLabels = ref<Record<string, string>>({});
    const previewDataset = ref<AnalysisDataset | null>(null);
    const previewError = ref('');

    const persistVisible = ref(false);
    const persistLoading = ref(false);
    const persistSaving = ref(false);
    const persistDataset = ref<AnalysisDataset | null>(null);
    const persistForm = ref<PersistFormState>({
      persistEnabled: false,
      persistWriteMode: 'REPLACE',
      persistCron: '',
    });

    const retrieve = async () => {
      isFetching.value = true;
      try {
        datasets.value = await service.list({ page: 0, size: 200, search: search.value });
      } catch (err: any) {
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
      searchTimer = setTimeout(retrieve, 300);
    });

    let skipFirstActivation = true;
    onMounted(retrieve);
    onActivated(() => {
      // 首次挂载由 onMounted 负责取数；之后从编辑页返回时刷新，避免 keep-alive 列表停留在旧数据。
      if (skipFirstActivation) {
        skipFirstActivation = false;
        return;
      }
      retrieve();
    });
    onUnmounted(() => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
    });

    const create = () =>
      router.push({
        name: 'AnalysisDatasetEdit',
        query: {},
      });

    const edit = (row: AnalysisDataset) =>
      router.push({
        name: 'AnalysisDatasetEdit',
        query: { id: row.id },
      });

    const openPreview = async (row: AnalysisDataset) => {
      previewDataset.value = row;
      previewVisible.value = true;
      previewLoading.value = true;
      previewError.value = '';
      previewColumns.value = [];
      previewRows.value = [];
      previewColumnLabels.value = {};
      try {
        const result = await service.preview(row.id as string);
        previewColumns.value = result.columns ?? [];
        previewRows.value = result.rows ?? [];
        previewColumnLabels.value = result.columnLabels ?? {};
      } catch (err: any) {
        previewError.value =
          err?.response?.data?.errorMessage || err?.response?.data?.title || err?.response?.data?.detail || err?.message || '预览失败';
      } finally {
        previewLoading.value = false;
      }
    };

    const previewColumnLabel = (column: string): string => previewColumnLabels.value[column] || column;

    const prepareRemove = (row: AnalysisDataset) => {
      removeId.value = row.id ?? null;
      removeEntity.value.show();
    };

    const closeDialog = () => removeEntity.value.hide();

    const remove = async () => {
      try {
        await service.remove(removeId.value as string);
        ElMessage.success('数据集已删除');
        closeDialog();
        await retrieve();
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    const copyCode = async (row: AnalysisDataset) => {
      if (!row.code) {
        return;
      }
      try {
        await navigator.clipboard.writeText(row.code);
        ElMessage.success('编码已复制');
      } catch {
        ElMessage.warning('复制失败，请手动复制');
      }
    };

    const typeLabel = (type?: string) => (type === 'SQL' ? 'SQL 数据集' : '指标数据集');

    const persistStatusLabel = (status?: string) => {
      switch (status) {
        case 'READY':
          return '结果可用';
        case 'RUNNING':
          return '执行中';
        case 'FAILED':
          return '执行失败';
        case 'STALE':
          return '结果过期';
        case 'PENDING':
          return '待执行';
        default:
          return '未启用';
      }
    };

    const persistStatusType = (status?: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' => {
      switch (status) {
        case 'READY':
          return 'success';
        case 'FAILED':
          return 'danger';
        case 'STALE':
        case 'RUNNING':
          return 'warning';
        case 'PENDING':
          return 'primary';
        default:
          return 'info';
      }
    };

    const persistMessage = computed(() => {
      if (!persistForm.value.persistEnabled) {
        return '';
      }
      if (!persistDataset.value?.persistEnabled) {
        return '保存配置后将生成「数据集任务」，可配置 Cron 或手动执行。';
      }
      switch (persistDataset.value.persistStatus) {
        case 'READY':
          return '持久化结果可用，数据集查询优先读取结果集。';
        case 'RUNNING':
          return '持久化任务执行中，查询暂回退实时结果。';
        case 'FAILED':
          return '最近一次执行失败，查询自动回退实时结果。';
        case 'STALE':
          return '数据集定义或目标配置已变化，持久化结果已过期，请重新执行。';
        default:
          return '尚未生成可用结果，查询暂时使用实时数据。';
      }
    });

    const persistMessageType = computed(() => {
      switch (persistDataset.value?.persistStatus) {
        case 'READY':
          return 'success';
        case 'FAILED':
          return 'error';
        case 'STALE':
        case 'RUNNING':
          return 'warning';
        default:
          return 'info';
      }
    });

    const persistDefinitionHasParams = computed(() => {
      if (persistDataset.value?.type !== 'SQL') {
        return false;
      }
      const definition = DatasetService.parseDefinition(persistDataset.value);
      const sql = typeof definition.sql === 'string' ? definition.sql : '';
      return sql.includes('${');
    });

    const defaultPersistTable = (dataset: AnalysisDataset | null): string => {
      const base = (dataset?.code || dataset?.name || 'dataset')
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9_]+/g, '_')
        .replace(/^_+|_+$/g, '');
      const safeBase = /^[a-z_]/.test(base) ? base : `ds_${base || 'dataset'}`;
      return `${safeBase.slice(0, 48)}_result`;
    };

    const applyPersistStatus = (status: Record<string, any>) => {
      if (!persistDataset.value) {
        return;
      }
      persistDataset.value = {
        ...persistDataset.value,
        persistStatus: status.status ?? persistDataset.value.persistStatus,
        persistLastRunTime: status.lastRunTime ?? persistDataset.value.persistLastRunTime,
        persistLastSuccessTime: status.lastSuccessTime ?? persistDataset.value.persistLastSuccessTime,
        persistLastError: status.lastError || undefined,
        persistRowCount: status.rowCount ?? persistDataset.value.persistRowCount,
        persistDefinitionHash: status.definitionHash ?? persistDataset.value.persistDefinitionHash,
        persistEtlTaskId: status.etlTaskId ?? persistDataset.value.persistEtlTaskId,
        persistJobId: status.jobId ?? persistDataset.value.persistJobId,
      };
    };

    const openPersist = async (row: AnalysisDataset) => {
      if (!row.id) {
        return;
      }
      persistVisible.value = true;
      persistLoading.value = true;
      persistDataset.value = row;
      try {
        const dataset = await service.get(row.id);
        persistDataset.value = dataset;
        persistForm.value = {
          persistEnabled: !!dataset.persistEnabled,
          persistTargetDataSourceId: dataset.persistTargetDataSourceId,
          persistTargetSchema: dataset.persistTargetSchema,
          persistTargetTable: dataset.persistTargetTable,
          persistWriteMode: dataset.persistWriteMode || 'REPLACE',
          persistCron: dataset.persistCron || '',
        };
      } catch (err: any) {
        persistVisible.value = false;
        alertService.showHttpError(err.response);
      } finally {
        persistLoading.value = false;
      }
    };

    const onPersistToggle = (enabled: boolean) => {
      if (!enabled) {
        return;
      }
      persistForm.value.persistWriteMode = persistForm.value.persistWriteMode || 'REPLACE';
      if (!persistForm.value.persistTargetTable) {
        persistForm.value.persistTargetTable = defaultPersistTable(persistDataset.value);
      }
    };

    const handlePersistDataSourceSelected = (selection: DataSourceSelection) => {
      persistForm.value.persistTargetDataSourceId = selection.dataSourceId || undefined;
      persistForm.value.persistTargetSchema = selection.schema || undefined;
    };

    const savePersist = async () => {
      if (!persistDataset.value?.id) {
        return;
      }
      if (persistForm.value.persistEnabled) {
        if (persistDefinitionHasParams.value) {
          ElMessage.error('当前 SQL 数据集包含 ${param} 占位符，暂不支持启用持久化，请先移除参数');
          return;
        }
        if (!persistForm.value.persistTargetDataSourceId) {
          ElMessage.error('请选择持久化目标数据源');
          return;
        }
        if (!persistForm.value.persistTargetTable?.trim()) {
          ElMessage.error('请填写持久化目标表');
          return;
        }
      }
      persistSaving.value = true;
      try {
        const updated = await service.update(persistDataset.value.id, {
          ...persistDataset.value,
          persistEnabled: persistForm.value.persistEnabled,
          persistTargetDataSourceId: persistForm.value.persistTargetDataSourceId || undefined,
          persistTargetSchema: persistForm.value.persistTargetSchema?.trim() || undefined,
          persistTargetTable: persistForm.value.persistTargetTable?.trim() || undefined,
          persistWriteMode: persistForm.value.persistWriteMode,
          persistCron: persistForm.value.persistCron?.trim() || '',
        });
        persistDataset.value = updated;
        persistForm.value = {
          persistEnabled: !!updated.persistEnabled,
          persistTargetDataSourceId: updated.persistTargetDataSourceId,
          persistTargetSchema: updated.persistTargetSchema,
          persistTargetTable: updated.persistTargetTable,
          persistWriteMode: updated.persistWriteMode || 'REPLACE',
          persistCron: updated.persistCron || '',
        };
        ElMessage.success('持久化配置已保存');
        await retrieve();
      } catch (err: any) {
        alertService.showHttpError(err.response);
      } finally {
        persistSaving.value = false;
      }
    };

    const loadPersistStatus = async () => {
      if (!persistDataset.value?.id) {
        return;
      }
      persistLoading.value = true;
      try {
        applyPersistStatus(await service.persistStatus(persistDataset.value.id));
        await retrieve();
      } catch (err: any) {
        alertService.showHttpError(err.response);
      } finally {
        persistLoading.value = false;
      }
    };

    const syncPersist = async () => {
      if (!persistDataset.value?.id) {
        return;
      }
      persistLoading.value = true;
      try {
        const updated = await service.syncPersist(persistDataset.value.id);
        persistDataset.value = { ...persistDataset.value, ...updated };
        ElMessage.success('持久化任务已同步');
        await retrieve();
      } catch (err: any) {
        alertService.showHttpError(err.response);
      } finally {
        persistLoading.value = false;
      }
    };

    const runPersist = async () => {
      if (!persistDataset.value?.id) {
        return;
      }
      persistLoading.value = true;
      try {
        applyPersistStatus(await service.runPersist(persistDataset.value.id));
        ElMessage.success('持久化任务已提交执行');
        await retrieve();
      } catch (err: any) {
        alertService.showHttpError(err.response);
      } finally {
        persistLoading.value = false;
      }
    };

    const handleRowCommand = (command: string, row: AnalysisDataset) => {
      switch (command) {
        case 'persist':
          void openPersist(row);
          break;
        case 'delete':
          prepareRemove(row);
          break;
        default:
          break;
      }
    };

    const publishApi = async (row: AnalysisDataset) => {
      try {
        const existing = await dataApiService.findByDataset(row.id as string);
        if (existing?.id) {
          router.push({ name: 'DataApiEdit', params: { dataApiId: existing.id } });
          return;
        }
        router.push({
          name: 'DataApiCreate',
          query: {
            sourceType: 'DATASET',
            datasetId: row.id,
            name: row.name,
            code: row.code || '',
          },
        });
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    return {
      datasets,
      isFetching,
      search,
      removeId,
      removeEntity,
      previewVisible,
      previewLoading,
      previewColumns,
      previewRows,
      previewDataset,
      previewError,
      previewColumnLabel,
      retrieve,
      create,
      edit,
      openPreview,
      prepareRemove,
      closeDialog,
      remove,
      copyCode,
      typeLabel,
      persistStatusLabel,
      persistStatusType,
      publishApi,
      persistVisible,
      persistLoading,
      persistSaving,
      persistDataset,
      persistForm,
      persistMessage,
      persistMessageType,
      persistDefinitionHasParams,
      openPersist,
      onPersistToggle,
      handlePersistDataSourceSelected,
      savePersist,
      loadPersistStatus,
      syncPersist,
      runPersist,
      handleRowCommand,
      ...dateFormat,
    };
  },
});
