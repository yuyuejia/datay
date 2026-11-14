import { type Ref, computed, defineComponent, inject, onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { ElMessageBox } from 'element-plus';

import EChart from '@/shared/dashboard/EChart.vue';
import DataApiService from './data-api.service';
import { type IDataApiCallLog, type IDataApiCallStats } from '@/shared/model/data-api.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

const rangeForDays = (days: number): [string, string] => [
  dayjs()
    .subtract(days - 1, 'day')
    .format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
];

export default defineComponent({
  name: 'DataApiStats',
  components: { EChart },
  setup() {
    const route = useRoute();
    const router = useRouter();
    const dataApiService = inject('dataApiService', () => new DataApiService());
    const alertService = inject('alertService', () => useAlertService(), true);
    const dateFormat = useDateFormat();

    const dataApiId = String(route.params.dataApiId || '');
    const stats: Ref<IDataApiCallStats | null> = ref(null);
    const statsLoading = ref(false);
    const logsLoading = ref(false);
    const dateRange: Ref<[string, string]> = ref(rangeForDays(7));

    const logs: Ref<IDataApiCallLog[]> = ref([]);
    const page = ref(1);
    const itemsPerPage = ref(20);
    const totalItems = ref(0);
    const statusFilter = ref('');

    const detailVisible = ref(false);
    const currentLog: Ref<IDataApiCallLog | null> = ref(null);

    const apiTitle = computed(() => {
      if (!stats.value) {
        return 'API 服务调用统计';
      }
      return stats.value.apiName || stats.value.apiCode || 'API 服务调用统计';
    });

    const apiCode = computed(() => stats.value?.apiCode || '');

    const chartOption = computed(() => {
      const trend = stats.value?.trend || [];
      return {
        tooltip: { trigger: 'axis' },
        legend: { data: ['总调用', '失败', '平均耗时'] },
        grid: { left: 56, right: 64, top: 44, bottom: 36 },
        xAxis: {
          type: 'category',
          data: trend.map(item => item.date),
          axisLabel: { formatter: (value: string) => value?.slice(5) },
        },
        yAxis: [
          { type: 'value', name: '调用次数', minInterval: 1 },
          { type: 'value', name: '耗时(ms)' },
        ],
        series: [
          {
            name: '总调用',
            type: 'bar',
            barMaxWidth: 28,
            itemStyle: { color: '#409eff' },
            data: trend.map(item => item.totalCount),
          },
          {
            name: '失败',
            type: 'bar',
            barMaxWidth: 28,
            itemStyle: { color: '#f56c6c' },
            data: trend.map(item => item.failureCount),
          },
          {
            name: '平均耗时',
            type: 'line',
            yAxisIndex: 1,
            smooth: true,
            itemStyle: { color: '#e6a23c' },
            data: trend.map(item => item.avgDurationMs),
          },
        ],
      };
    });

    const loadStats = async () => {
      if (!dataApiId) {
        return;
      }
      statsLoading.value = true;
      try {
        stats.value = await dataApiService().retrieveCallStats(dataApiId, dateRange.value?.[0], dateRange.value?.[1]);
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        statsLoading.value = false;
      }
    };

    const loadLogs = async () => {
      if (!dataApiId) {
        return;
      }
      logsLoading.value = true;
      try {
        // 不要携带空值：分页参数会被原样拼进 query string，undefined 会变成字面量 "undefined"
        const query: Record<string, any> = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: ['createTime,desc', 'id,desc'],
        };
        if (statusFilter.value) {
          query.status = statusFilter.value;
        }
        if (dateRange.value?.[0]) {
          query.startDate = dateRange.value[0];
        }
        if (dateRange.value?.[1]) {
          query.endDate = dateRange.value[1];
        }
        const res = await dataApiService().retrieveCallLogs(dataApiId, query);
        totalItems.value = Number(res.headers['x-total-count'] || 0);
        logs.value = res.data || [];
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        logsLoading.value = false;
      }
    };

    const refresh = async () => {
      await Promise.all([loadStats(), loadLogs()]);
    };

    const applyQuickRange = async (days: number) => {
      dateRange.value = rangeForDays(days);
      page.value = 1;
      await refresh();
    };

    const handleRangeChange = async () => {
      if (!dateRange.value) {
        dateRange.value = rangeForDays(7);
      }
      page.value = 1;
      await refresh();
    };

    const handleStatusChange = async () => {
      page.value = 1;
      await loadLogs();
    };

    const openDetail = (log: IDataApiCallLog) => {
      currentLog.value = log;
      detailVisible.value = true;
    };

    const closeDetail = () => {
      detailVisible.value = false;
      currentLog.value = null;
    };

    const clearLogs = async () => {
      try {
        await ElMessageBox.confirm('确定要清空当前服务的全部调用日志吗？清空后不可恢复。', '清空调用日志', {
          confirmButtonText: '清空',
          cancelButtonText: '取消',
          type: 'warning',
        });
      } catch {
        return;
      }
      try {
        await dataApiService().clearCallLogs(dataApiId);
        alertService.showSuccess('调用日志已清空');
        page.value = 1;
        await refresh();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const backToList = () => {
      router.push({ name: 'DataApi' });
    };

    const sourceLabel = (source?: string | null) => {
      switch (source) {
        case 'OPEN_API':
          return '对外 API';
        case 'MCP':
          return 'MCP';
        case 'INTERNAL':
          return '内部';
        default:
          return source || '-';
      }
    };

    const statusLabel = (status?: string | null) => (status === 'SUCCESS' ? '成功' : status === 'FAILED' ? '失败' : status || '-');

    const statusTagType = (status?: string | null) => (status === 'SUCCESS' ? 'success' : 'danger');

    const formatParams = (params?: string | null) => {
      if (!params) {
        return '-';
      }
      try {
        return JSON.stringify(JSON.parse(params), null, 2);
      } catch {
        return params;
      }
    };

    const formatDuration = (duration?: number | null) => {
      if (duration === null || duration === undefined) {
        return '-';
      }
      return `${duration} ms`;
    };

    /** 调用日志展示到秒，并按浏览器本地时区渲染。 */
    const formatDateTime = (value?: string | Date | null) => {
      if (!value) {
        return '-';
      }
      const parsed = dayjs(value);
      return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : String(value);
    };

    watch(page, async () => {
      await loadLogs();
    });

    watch(itemsPerPage, async () => {
      page.value = 1;
      await loadLogs();
    });

    let activatedOnce = false;

    onMounted(async () => {
      await refresh();
    });

    onActivated(async () => {
      if (!activatedOnce) {
        activatedOnce = true;
        return;
      }
      await refresh();
    });

    return {
      dataApiId,
      stats,
      statsLoading,
      logsLoading,
      dateRange,
      logs,
      page,
      itemsPerPage,
      totalItems,
      statusFilter,
      detailVisible,
      currentLog,
      apiTitle,
      apiCode,
      chartOption,
      refresh,
      applyQuickRange,
      handleRangeChange,
      handleStatusChange,
      openDetail,
      closeDetail,
      clearLogs,
      backToList,
      sourceLabel,
      statusLabel,
      statusTagType,
      ...dateFormat,
      formatParams,
      formatDuration,
      formatDateTime,
    };
  },
});
