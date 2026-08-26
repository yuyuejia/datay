import { type ComputedRef, defineComponent, inject, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useLoginModal } from '@/account/login-modal';

export default defineComponent({
  compatConfig: { MODE: 3 },
  setup() {
    const { showLogin } = useLoginModal();
    const authenticated = inject<ComputedRef<boolean>>('authenticated');
    const username = inject<ComputedRef<string>>('currentUsername');
    const router = useRouter();

    // 使用指南步骤
    const guideSteps = ref([
      {
        title: '添加数据源',
        desc: '支持 MySQL、PostgreSQL、Oracle、ClickHouse 等多种主流数据源的快速接入',
        button: '去添加',
        action: () => {
          router.push('/data-source');
        },
      },
      {
        title: '新建数据集成任务',
        desc: '通过可视化设计器配置数据抽取、转换和加载流程，快速完成数据集成',
        button: '去创建',
        action: () => {
          router.push('/etl-task');
        },
      },
      {
        title: '任务调度',
        desc: '配置定时调度策略，自动执行数据任务，实现数据的持续同步与更新',
        button: '去配置',
        action: () => {
          router.push('/job');
        },
      },
    ]);

    // 数据源类型数据
    // 数据源类型数据 - 使用实际的SVG图片
    const datasources = ref([
      { name: 'MySQL', image: '/content/images/mysql.svg' },
      { name: 'PostgreSQL', image: '/content/images/postgres.svg' },
      { name: 'Oracle', image: '/content/images/oracle.svg' },
      { name: 'SqlServer', image: '/content/images/sqlserver.svg' },
      { name: 'Greenplum', image: '/content/images/Greenplum.svg' },
      { name: 'Doris', image: '/content/images/doris.svg' },
      { name: 'ClickHouse', image: '/content/images/clickhouse.svg' },
    ]);

    // 数据源类型数据
    const components = ref([
      { name: '数据源输入', desc: '从外部数据源读取数据', image: '/content/images/datasource-input.svg' },
      { name: 'MySQL Binlog', desc: '读取 MySQL Binlog 事件', image: '/content/images/datasource-input.svg' },
      { name: 'SQL组件', desc: 'SQL组件', image: '/content/images/sql.svg' },
      { name: '数据源输出', desc: '将数据写入外部数据源', image: '/content/images/datasource-output.svg' },
      { name: 'DuckDBSql', desc: 'DuckDB SQL组件', image: '/content/images/sql.svg' },
      { name: '写入DuckDB', desc: '将数据写入DuckDB数据库', image: '/content/images/datasource-output.svg' },
      { name: '注册DuckDB', desc: '将数据库表注册到 DuckDB', image: '/content/images/register.svg' },
      { name: 'JAVA脚本', desc: 'JAVA脚本组件', image: '/content/images/java.svg' },
      { name: '数据生成', desc: '测试数据生成组件', image: '/content/images/datasource-input.svg' },
      { name: '日志输出', desc: '将数据打印到日志中', image: '/content/images/log.svg' },
      { name: 'Channel', desc: 'Channel组件', image: '/content/images/channel.svg' },
    ]);

    return {
      authenticated,
      username,
      showLogin,
      datasources,
      components,
      guideSteps,
    };
  },
});
