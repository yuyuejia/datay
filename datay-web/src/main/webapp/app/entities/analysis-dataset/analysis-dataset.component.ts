import { type Ref, defineComponent, inject, onActivated, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';

import { useAlertService } from '@/shared/alert/alert.service';
import { useDateFormat } from '@/shared/composables';
import DatasetService from '@/shared/dataset/dataset.service';
import type { AnalysisDataset } from '@/shared/dataset/types';

/**
 * 分析数据集列表：独立管理可被多个看板复用的指标 / SQL 数据集。
 */
export default defineComponent({
  name: 'AnalysisDataset',
  setup() {
    const router = useRouter();
    const alertService = inject('alertService', () => useAlertService(), true);
    const service = new DatasetService();
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
      ...dateFormat,
    };
  },
});
