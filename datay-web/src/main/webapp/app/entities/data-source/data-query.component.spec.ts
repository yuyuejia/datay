import { beforeEach, describe, expect, it, vitest } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';

import DataQuery from './data-query.vue';
import type DataSourceService from './data-source.service';
import type DataQueryService from './data-query.service';

vitest.mock('vue-router', () => ({
  useRoute: () => ({ params: { dataSourceId: 'ds-1' } }),
  useRouter: () => ({ push: vitest.fn() }),
}));

const buildTables = (count: number) => Array.from({ length: count }, (_, i) => ({ table: `t_${String(i).padStart(5, '0')}` }));

const schemaNode = {
  id: 'schema:main',
  label: 'main',
  type: 'schema',
  name: 'main',
  isLeaf: false,
};

const flushPromises = async () => {
  await Promise.resolve();
  await nextTick();
};

describe('DataQuery 左侧表树分页与搜索', () => {
  const TOTAL_TABLES = 10000;

  let wrapper: VueWrapper<any>;
  let getTables: ReturnType<typeof vitest.fn>;

  const loadChildren = async (): Promise<any[]> => {
    let children: any[] = [];
    await wrapper.vm.loadTables({ data: schemaNode }, (nodes: any[]) => {
      children = nodes;
    });
    return children;
  };

  const lastGetTablesArgs = () => getTables.mock.calls[getTables.mock.calls.length - 1];

  beforeEach(async () => {
    getTables = vitest.fn(async (_id: string, _schema: string, limit?: number, search?: string) => {
      const all = buildTables(TOTAL_TABLES);
      const matched = search ? all.filter(t => t.table.includes(search)) : all;
      return { data: matched.slice(0, limit ?? matched.length) };
    });

    const dataSourceService = {
      find: vitest.fn().mockResolvedValue({ id: 'ds-1', name: 'Mock DS', type: 'MYSQL' }),
      getCatalogs: vitest.fn().mockResolvedValue({ data: [] }),
      getSchemas: vitest.fn().mockResolvedValue({ data: ['main'] }),
      getTables,
    } as unknown as DataSourceService;

    const dataQueryService = {
      executeQuery: vitest.fn(),
    } as unknown as DataQueryService;

    wrapper = mount(DataQuery, {
      global: {
        stubs: {
          'font-awesome-icon': true,
          DataQueryAi: true,
          TableLineage: true,
          'router-link': true,
        },
        provide: {
          dataSourceService: () => dataSourceService,
          dataQueryService: () => dataQueryService,
          alertService: {
            showHttpError: vitest.fn(),
            showError: vitest.fn(),
            showWarning: vitest.fn(),
            showSuccess: vitest.fn(),
          },
        },
      },
    });

    await flushPromises();
  });

  it('实际展开后 DOM 只创建 1000 个表节点，不会渲染全部 10000 张', async () => {
    await flushPromises();

    const tree = wrapper.findComponent({ name: 'ElTree' });
    const node = (tree.vm as any).getNode('schema:main');
    expect(node).toBeTruthy();

    node.expand();
    await flushPromises();
    await new Promise(resolve => setTimeout(resolve, 0));
    await flushPromises();

    expect(getTables).toHaveBeenCalledTimes(1);
    expect(wrapper.findAll('.custom-tree-node.is-table')).toHaveLength(1000);
    expect(wrapper.findAll('.tree-more-node')).toHaveLength(1);
  });

  it('展开 schema 只加载前 1000 张表，并追加“加载更多”节点', async () => {
    const children = await loadChildren();

    expect(getTables).toHaveBeenCalledTimes(1);
    // 多请求 1 条用于判断是否还有更多
    expect(lastGetTablesArgs()[2]).toBe(1001);
    expect(lastGetTablesArgs()[3]).toBeUndefined();

    expect(children).toHaveLength(1001);
    expect(children[0]).toMatchObject({ type: 'table', name: 't_00000' });
    expect(children[children.length - 1]).toMatchObject({ type: 'more', schemaNodeId: 'schema:main' });
    expect(children.filter(node => node.type === 'table')).toHaveLength(1000);
  });

  it('输入关键字后对已展开 schema 触发服务端搜索并更新树', async () => {
    await flushPromises();

    const tree = wrapper.findComponent({ name: 'ElTree' });
    const node = (tree.vm as any).getNode('schema:main');
    node.expand();
    await flushPromises();
    await new Promise(resolve => setTimeout(resolve, 0));
    await flushPromises();

    expect(wrapper.findAll('.custom-tree-node.is-table')).toHaveLength(1000);

    // 程序化 node.expand() 不经过树节点组件，不会触发 node-expand 事件，这里补登记
    wrapper.vm.handleNodeExpand(schemaNode);

    vitest.useFakeTimers();
    await wrapper.find('.sidebar-search input').setValue('t_0');
    await vitest.advanceTimersByTimeAsync(300);
    vitest.useRealTimers();
    await flushPromises();

    expect(lastGetTablesArgs()[3]).toBe('t_0');
    expect(wrapper.findAll('.custom-tree-node.is-table')).toHaveLength(1000);
    expect(wrapper.findAll('.tree-hint-node')).toHaveLength(1);
  });

  it('搜索时走服务端 search，最多展示 1000 条并提示细化关键字', async () => {
    wrapper.vm.tableSearch = 't_0';

    const children = await loadChildren();

    expect(lastGetTablesArgs()[2]).toBe(1001);
    expect(lastGetTablesArgs()[3]).toBe('t_0');

    const tableNodes = children.filter(node => node.type === 'table');
    expect(tableNodes).toHaveLength(1000);
    expect(tableNodes.every(node => node.name.includes('t_0'))).toBe(true);
    expect(children[children.length - 1]).toMatchObject({ type: 'hint' });
    expect(children[children.length - 1].label).toContain('仅显示前 1000 条匹配结果');
  });

  it('点击加载更多每次 +1000，最多到 5000 后改为搜索提示', async () => {
    wrapper.vm.handleNodeExpand(schemaNode);
    let children = await loadChildren();

    // 1000 -> 2000 -> 3000 -> 4000 -> 5000
    for (let expectedLimit = 2000; expectedLimit <= 5000; expectedLimit += 1000) {
      const moreNode = children[children.length - 1];
      expect(moreNode.type).toBe('more');
      await wrapper.vm.handleLoadMore(moreNode);
      await flushPromises();

      expect(lastGetTablesArgs()[2]).toBe(expectedLimit + 1);
      children = await loadChildren();
      expect(children.filter(node => node.type === 'table')).toHaveLength(expectedLimit);
    }

    // 到达 5000 后不再提供“加载更多”，只能搜索
    expect(children[children.length - 1]).toMatchObject({ type: 'hint' });
    expect(children[children.length - 1].label).toBe('仅显示前 5000 张表，请输入表名搜索');
  });
});
