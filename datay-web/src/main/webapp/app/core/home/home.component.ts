import { computed, type ComputedRef, defineComponent, inject, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useLoginModal } from '@/account/login-modal';
import DataSourceService from '@/entities/data-source/data-source.service';
import DataModelService from '@/entities/data-model/data-model.service';
import JobService from '@/entities/job/job.service';
import JobInstanceService from '@/entities/job-instance/job-instance.service';
import { dbTypes } from '@/entities/data-source/db-types';
import { type IDataSource } from '@/shared/model/data-source.model';
import { type IJobInstance } from '@/shared/model/job-instance.model';
import { type IDataModel } from '@/shared/model/data-model.model';
import { useDateFormat } from '@/shared/composables';

interface TypeDistributionItem {
  type: string;
  label: string;
  image?: string;
  count: number;
  percent: number;
  color: string;
}

const TYPE_COLORS = ['#1677ff', '#722ed1', '#00b42a', '#ff7d00', '#0fc6c2', '#eb2f96', '#2f54eb', '#f7ba1e', '#f53f3f'];

const STATUS_LABELS: Record<string, string> = {
  APPENDING: '等待中',
  RUNNING: '运行中',
  WAITING: '等待中',
  SUCCESSFUL: '成功',
  FAILED: '失败',
  TIMEOUT: '超时',
  INTERRUPTED: '已中断',
};

const RUNNING_STATUSES = ['RUNNING', 'APPENDING'];

export default defineComponent({
  compatConfig: { MODE: 3 },
  setup() {
    const { showLogin } = useLoginModal();
    const authenticated = inject<ComputedRef<boolean>>('authenticated');
    const username = inject<ComputedRef<string>>('currentUsername');
    const router = useRouter();
    const { formatDate } = useDateFormat();

    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const dataModelService = inject('dataModelService', () => new DataModelService());
    const jobService = inject('jobService', () => new JobService());
    const jobInstanceService = inject('jobInstanceService', () => new JobInstanceService());

    const loading = ref(false);

    const stats = ref({
      dataSourceCount: 0,
      dataModelCount: 0,
      taskCount: 0,
      runningCount: 0,
    });

    const typeDistribution = ref<TypeDistributionItem[]>([]);
    const runningInstances = ref<IJobInstance[]>([]);
    const failedInstances = ref<IJobInstance[]>([]);

    const buildTypeDistribution = (dataSources: IDataSource[]): TypeDistributionItem[] => {
      const counts = new Map<string, number>();
      for (const dataSource of dataSources) {
        const type = dataSource.type || 'UNKNOWN';
        counts.set(type, (counts.get(type) ?? 0) + 1);
      }
      const total = dataSources.length || 1;
      return Array.from(counts.entries())
        .sort((a, b) => b[1] - a[1])
        .map(([type, count], index) => {
          const meta = dbTypes.find(item => item.name === type);
          return {
            type,
            label: meta?.displayName ?? type,
            image: meta?.image,
            count,
            percent: Math.round((count / total) * 100),
            color: TYPE_COLORS[index % TYPE_COLORS.length],
          };
        });
    };

    const loadData = async () => {
      if (!authenticated?.value) return;
      loading.value = true;
      try {
        const [dsRes, modelRes, jobsRes, instanceRes] = await Promise.all([
          dataSourceService().retrieve({ page: 0, size: 1000, sort: ['type,asc'] }),
          dataModelService().retrieve(),
          jobService().retrieve({ page: 0, size: 1 }),
          jobInstanceService().retrieve({ page: 0, size: 200, sort: ['createTime,desc'] }),
        ]);

        const dataSources: IDataSource[] = dsRes.data ?? [];
        const dataModels: IDataModel[] = modelRes.data ?? [];
        const instances: IJobInstance[] = instanceRes.data ?? [];

        stats.value.dataSourceCount = Number(dsRes.headers['x-total-count']) || dataSources.length;
        stats.value.dataModelCount = dataModels.length;
        stats.value.taskCount = Number(jobsRes.headers['x-total-count']) || 0;

        typeDistribution.value = buildTypeDistribution(dataSources);

        runningInstances.value = instances.filter(instance => instance.status && RUNNING_STATUSES.includes(instance.status));
        stats.value.runningCount = runningInstances.value.length;

        const oneDayAgo = Date.now() - 24 * 60 * 60 * 1000;
        failedInstances.value = instances.filter(
          instance => instance.status === 'FAILED' && instance.createTime && new Date(instance.createTime).getTime() >= oneDayAgo,
        );
      } catch (e) {
        console.error('Failed to load dashboard data', e);
      } finally {
        loading.value = false;
      }
    };

    onMounted(() => {
      loadData();
    });

    watch(
      () => authenticated?.value,
      value => {
        if (value) {
          loadData();
        }
      },
    );

    const guideSteps = computed(() => [
      {
        title: '数据集成',
        desc: '接入数据源，配置数据同步与 ETL 采集流程',
        icon: 'database',
        color: '#1677ff',
        route: '/etl-task',
      },
      {
        title: '数据建模',
        desc: '设计数据模型与字段，沉淀标准化数据资产',
        icon: 'table',
        color: '#722ed1',
        route: '/data-model',
      },
      {
        title: '数据开发',
        desc: '编写 SQL / Shell 任务，完成数据加工与转换',
        icon: 'code',
        color: '#00b42a',
        route: '/sql-job',
      },
      {
        title: '任务编排',
        desc: '编排 DAG 调度流程，统一管理与监控任务运行',
        icon: 'project-diagram',
        color: '#ff7d00',
        route: '/dag-job',
      },
    ]);

    const goTo = (route: string) => {
      router.push(route);
    };

    const statusText = (status?: string | null) => (status ? STATUS_LABELS[status] ?? status : '-');

    const statusClass = (status?: string | null) => (status ? `status-${status.toLowerCase()}` : '');

    const formatTime = (value?: Date | string | null) => {
      if (!value) return '';
      const timeValue = typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value;
      return formatDate(timeValue);
    };

    return {
      authenticated,
      username,
      showLogin,
      loading,
      stats,
      typeDistribution,
      runningInstances,
      failedInstances,
      guideSteps,
      loadData,
      goTo,
      statusText,
      statusClass,
      formatTime,
    };
  },
});
