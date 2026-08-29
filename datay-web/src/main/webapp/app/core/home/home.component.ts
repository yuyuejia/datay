import { computed, type ComputedRef, defineComponent, inject, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useLoginModal } from '@/account/login-modal';
import DataSourceService from '@/entities/data-source/data-source.service';
import ETLTaskService from '@/entities/etl-task/etl-task.service';
import DataSyncService from '@/entities/data-sync/data-sync.service';
import JobInstanceService from '@/entities/job-instance/job-instance.service';
import DataSourceModal from '@/entities/data-source/data-source-modal.vue';

export default defineComponent({
  compatConfig: { MODE: 3 },
  components: { DataSourceModal },
  setup() {
    const { showLogin } = useLoginModal();
    const authenticated = inject<ComputedRef<boolean>>('authenticated');
    const username = inject<ComputedRef<string>>('currentUsername');
    const router = useRouter();

    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const etlTaskService = inject('etlTaskService', () => new ETLTaskService());
    const dataSyncService = inject('dataSyncService', () => new DataSyncService());
    const jobInstanceService = inject('jobInstanceService', () => new JobInstanceService());

    const stats = ref({
      dataSourceCount: 0,
      etlTaskCount: 0,
      dataSyncCount: 0,
      runningInstanceCount: 0,
    });

    const loadingStats = ref(false);

    const loadStats = async () => {
      if (!authenticated.value) return;
      loadingStats.value = true;
      try {
        const [dsRes, taskRes, syncRes, instanceRes] = await Promise.all([
          dataSourceService().retrieve({ page: 0, size: 1 }),
          etlTaskService().retrieve({ page: 0, size: 1 }),
          dataSyncService().retrieve({ page: 0, size: 1 }),
          jobInstanceService().retrieve({ page: 0, size: 1 }),
        ]);
        stats.value.dataSourceCount = Number(dsRes.headers['x-total-count']) || 0;
        stats.value.etlTaskCount = Number(taskRes.headers['x-total-count']) || 0;
        stats.value.dataSyncCount = Number(syncRes.headers['x-total-count']) || 0;
        stats.value.runningInstanceCount = Number(instanceRes.headers['x-total-count']) || 0;
      } catch (e) {
        console.error('Failed to load stats', e);
      } finally {
        loadingStats.value = false;
      }
    };

    onMounted(() => {
      loadStats();
    });

    watch(authenticated, val => {
      if (val) {
        loadStats();
      }
    });

    const dataSourceModalShow = ref(false);

    const openAddDataSourceModal = () => {
      if (!authenticated.value) {
        showLogin();
        return;
      }
      dataSourceModalShow.value = true;
    };

    const onDataSourceModalSaved = () => {
      loadStats();
    };

    const heroActions = computed(() => [
      { label: '立即登录', style: 'primary', action: () => showLogin() }
    ]);

    const guideSteps = ref([
      {
        title: '添加数据源',
        desc: '支持 MySQL、PostgreSQL、Oracle、SQLServer 等多种主流数据库，一键连接，快速接入',
        button: '去添加',
        icon: 'database',
        color: '#4e8cff',
        action: () => {
          openAddDataSourceModal();
        },
      },
      {
        title: '设计数据集成',
        desc: '通过可视化拖拽设计器，配置数据抽取、转换、加载流程，轻松构建数据管道',
        button: '去设计',
        icon: 'project-diagram',
        color: '#6f42c1',
        action: () => {
          router.push('/etl-task/design-new');
        },
      },
      {
        title: '运行与调度',
        desc: '支持手动触发与定时调度，实时监控任务运行状态，保障数据持续同步',
        button: '去调度',
        icon: 'clock',
        color: '#28a745',
        action: () => {
          router.push('/job');
        },
      },
    ]);

    const datasources = ref([
      { name: 'MySQL', image: '/content/images/mysql.svg' },
      { name: 'PostgreSQL', image: '/content/images/postgres.svg' },
      { name: 'Oracle', image: '/content/images/oracle.svg' },
      { name: 'SQLServer', image: '/content/images/sqlserver.svg' },
      { name: 'Greenplum', image: '/content/images/Greenplum.svg' },
      { name: 'Doris', image: '/content/images/doris.svg' },
      { name: 'ClickHouse', image: '/content/images/clickhouse.svg' },
    ]);

    const components = ref([
      { name: '数据源输入', desc: '从外部数据源读取数据', icon: 'sign-in-alt', color: '#4e8cff' },
      { name: 'MySQL Binlog', desc: '实时读取 MySQL Binlog 事件', icon: 'database', color: '#00758f' },
      { name: 'SQL 组件', desc: '通过 SQL 进行数据查询与转换', icon: 'code', color: '#28a745' },
      { name: '数据源输出', desc: '将数据写入外部数据源', icon: 'sign-out-alt', color: '#fd7e14' },
      { name: 'DuckDB SQL', desc: '基于 DuckDB 的高性能 SQL 处理', icon: 'file-code', color: '#f7b500' },
      { name: 'DuckDB 写入', desc: '将数据写入 DuckDB 数据库', icon: 'database', color: '#6f42c1' },
      { name: 'DuckDB 注册', desc: '将数据库表注册到 DuckDB', icon: 'table', color: '#20c997' },
      { name: 'JavaScript 脚本', desc: '使用 JS 脚本进行自定义转换', icon: 'code', color: '#e83e8c' },
      { name: '数据生成', desc: '测试数据生成组件', icon: 'random', color: '#17a2b8' },
      { name: '日志输出', desc: '将数据打印到日志中', icon: 'list', color: '#6c757d' },
      { name: 'HTTP 监听', desc: '监听 HTTP 请求作为数据源', icon: 'globe', color: '#0d6efd' },
      { name: 'Kafka 消费', desc: '从 Kafka 消费实时消息', icon: 'sync', color: '#dc3545' },
    ]);

    return {
      authenticated,
      username,
      showLogin,
      stats,
      loadingStats,
      heroActions,
      datasources,
      components,
      guideSteps,
      dataSourceModalShow,
      openAddDataSourceModal,
      onDataSourceModalSaved,
    };
  },
});