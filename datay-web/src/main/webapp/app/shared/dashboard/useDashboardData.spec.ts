import { flushPromises } from '@vue/test-utils';
import { nextTick, ref } from 'vue';

import type { DashboardSpec, DashboardWidget, DatasetResult } from './types';
import { useDashboardData } from './useDashboardData';

const { queryWidgetsMock, drillMock } = vi.hoisted(() => ({
  queryWidgetsMock: vi.fn(),
  drillMock: vi.fn(),
}));

vi.mock('./dashboard.service', () => ({
  default: class {
    queryWidgets = queryWidgetsMock;
    previewWidgets = vi.fn();
    drill = drillMock;
    previewDrill = vi.fn();
  },
}));

const serverResult: DatasetResult = {
  columns: ['region', 'amount'],
  rows: [
    { region: '华东', amount: 1 },
    { region: '华南', amount: 2 },
  ],
};

const spec = ref<DashboardSpec>({
  version: 2,
  datasets: [{ id: 'ds1', type: 'SQL' }],
  widgets: [
    {
      id: 'w1',
      type: 'table',
      layout: { x: 0, y: 0, w: 12, h: 8 },
      datasetId: 'ds1',
      encode: { limit: 100 },
    },
  ],
  filters: [
    {
      id: 'region',
      type: 'select',
      targets: [{ datasetId: 'ds1', field: 'region', mode: 'server' }],
    },
  ],
});

describe('useDashboardData server-side filters', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    queryWidgetsMock.mockResolvedValue({
      datasets: { ds1: serverResult },
      widgets: { q1: serverResult },
    });
  });

  it('所有筛选器值都发给服务端，前端不再二次过滤', async () => {
    const dashboardId = ref('dashboard-1');
    const data = useDashboardData({ dashboardId, spec });

    await data.loadAll({ region: '华东' });

    expect(queryWidgetsMock).toHaveBeenCalledTimes(1);
    expect(queryWidgetsMock.mock.calls[0][3]).toEqual({ region: '华东' });
    expect(data.datasetData.value.ds1.rows).toEqual(serverResult.rows);
    expect(data.widgetData.value.w1.rows).toEqual(serverResult.rows);
  });

  it('筛选值变化会重新请求服务端', async () => {
    const dashboardId = ref('dashboard-1');
    const data = useDashboardData({ dashboardId, spec });

    await data.loadAll({ region: '华东' });
    await data.loadAll({ region: '华南' });

    expect(queryWidgetsMock).toHaveBeenCalledTimes(2);
    expect(queryWidgetsMock.mock.calls[1][3]).toEqual({ region: '华南' });
    expect(data.datasetData.value.ds1.rows).toEqual(serverResult.rows);
  });

  it('KPI 全局聚合同样通过服务端查询下推', async () => {
    queryWidgetsMock.mockResolvedValue({
      datasets: { ds1: serverResult },
      widgets: { q1: { ...serverResult, aggregated: true } },
    });
    const kpiSpec = ref<DashboardSpec>({
      version: 2,
      datasets: [{ id: 'ds1', type: 'SQL' }],
      widgets: [
        {
          id: 'kpi1',
          type: 'kpi',
          layout: { x: 0, y: 0, w: 6, h: 4 },
          datasetId: 'ds1',
          encode: { measures: [{ field: 'amount', agg: 'avg', role: 'value' }] },
        },
      ],
    });
    const dashboardId = ref('dashboard-1');
    const data = useDashboardData({ dashboardId, spec: kpiSpec });

    await data.loadAll({});

    const queries = queryWidgetsMock.mock.calls[0][2];
    expect(queries).toHaveLength(1);
    expect(queries[0]).toMatchObject({
      datasetId: 'ds1',
      groupFields: [],
      aggregations: [{ field: 'amount', agg: 'avg' }],
    });
    expect(data.widgetData.value.kpi1.aggregated).toBe(true);
  });

  it('明细下钻携带当前筛选值与分页参数', async () => {
    drillMock.mockResolvedValue({ columns: ['region'], rows: [{ region: '华东' }] });
    const dashboardId = ref('dashboard-1');
    const data = useDashboardData({ dashboardId, spec });

    await data.loadAll({ region: '华东' });
    const result = await data.loadDrill({
      datasetId: 'ds1',
      dimensions: [{ field: 'region', values: ['华东'] }],
      page: 2,
      pageSize: 50,
      sortField: 'amount',
      sortOrder: 'desc',
    });

    expect(result.rows).toEqual([{ region: '华东' }]);
    expect(drillMock).toHaveBeenCalledWith('dashboard-1', {
      datasetId: 'ds1',
      dimensions: [{ field: 'region', values: ['华东'] }],
      filterValues: { region: '华东' },
      limit: 50,
      offset: 50,
      sortField: 'amount',
      sortOrder: 'desc',
    });
  });
});

const sourceWidget = (): DashboardWidget => ({
  id: 'w1',
  type: 'echarts',
  datasetId: 'ds1',
  layout: { x: 0, y: 0, w: 12, h: 8 },
  chart: { type: 'bar' },
  encode: {
    dimensions: [{ field: 'region', role: 'x' }],
    measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
    groupBy: true,
  },
});

const targetWidget = (datasetId: string): DashboardWidget => ({
  id: 'w2',
  type: 'echarts',
  datasetId,
  layout: { x: 12, y: 0, w: 12, h: 8 },
  chart: { type: 'line' },
  encode: {
    dimensions: [{ field: 'month', role: 'x' }],
    measures: [{ field: 'total', agg: 'sum', role: 'y' }],
    groupBy: true,
  },
});

const buildLinkageSpec = (targetDatasetId = 'ds1'): DashboardSpec => {
  const source = sourceWidget();
  source.interaction = { clickAction: 'linkage', targets: [{ widgetId: 'w2' }] };
  return {
    version: 2,
    datasets:
      targetDatasetId === 'ds1'
        ? [{ id: 'ds1', type: 'SQL' }]
        : [
            { id: 'ds1', type: 'SQL' },
            { id: 'ds2', type: 'SQL' },
          ],
    widgets: [source, targetWidget(targetDatasetId)],
  };
};

const latestQueries = () => queryWidgetsMock.mock.calls.at(-1)?.[2] as Array<Record<string, any>>;

describe('useDashboardData 图表联动', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    queryWidgetsMock.mockResolvedValue({ datasets: {}, widgets: {} });
  });

  it('同数据集联动直接把点击字段下推到目标组件查询', async () => {
    const dashboardId = ref('dashboard-1');
    const linkageSpec = ref<DashboardSpec>(buildLinkageSpec());
    const data = useDashboardData({ dashboardId, spec: linkageSpec });
    await data.loadAll({});
    expect(latestQueries().every(query => query.conditions === undefined)).toBe(true);

    expect(data.applyLinkage('w1', [{ field: 'region', values: ['华东'] }])).toBe(true);
    await nextTick();
    await flushPromises();

    expect(data.linkage.value?.label).toBe('region = 华东');
    expect(data.linkageWidgetIds.value).toEqual({ w2: true });
    expect(latestQueries()).toContainEqual(
      expect.objectContaining({ datasetId: 'ds1', conditions: [{ field: 'region', values: ['华东'] }] }),
    );

    // 再次点击相同取值取消联动，并恢复不带条件的查询。
    expect(data.applyLinkage('w1', [{ field: 'region', values: ['华东'] }])).toBe(true);
    await nextTick();
    await flushPromises();
    expect(data.linkage.value).toBeNull();
    expect(data.linkageWidgetIds.value).toEqual({});
    expect(latestQueries().every(query => query.conditions === undefined)).toBe(true);
  });

  it('联动只刷新目标组件，不触发整块看板重载', async () => {
    const dashboardId = ref('dashboard-1');
    const linkageSpec = ref<DashboardSpec>(buildLinkageSpec());
    const data = useDashboardData({ dashboardId, spec: linkageSpec });
    let resolveRefresh: ((value: any) => void) | undefined;
    let call = 0;
    queryWidgetsMock.mockImplementation(async (_id: string, datasetIds: string[], queries: Array<Record<string, any>>) => {
      call += 1;
      if (call > 1) {
        return new Promise(resolve => {
          resolveRefresh = resolve;
        });
      }
      const widgets: Record<string, any> = {};
      for (const query of queries) {
        widgets[query.id] = { columns: ['region'], rows: [{ region: `call${call}` }] };
      }
      return { datasets: {}, widgets };
    });

    await data.loadAll({});
    expect(data.widgetData.value.w1.rows).toEqual([{ region: 'call1' }]);
    expect(queryWidgetsMock).toHaveBeenCalledTimes(1);

    data.applyLinkage('w1', [{ field: 'region', values: ['华东'] }]);
    await nextTick();

    // 局部刷新：不开启全量 loading，只标记目标组件加载中。
    expect(data.loading.value).toBe(false);
    expect(data.loadingWidgets.value.w2).toBe(true);
    expect(data.loadingWidgets.value.w1).toBeUndefined();

    resolveRefresh?.({
      datasets: {},
      widgets: { [`${latestQueries()[0].id}`]: { columns: ['region'], rows: [{ region: 'linked' }] } },
    });
    await flushPromises();

    // 第二个请求只包含一个目标查询，且不再请求任何数据集。
    expect(queryWidgetsMock).toHaveBeenCalledTimes(2);
    expect(queryWidgetsMock.mock.calls[1][1]).toEqual([]);
    expect(queryWidgetsMock.mock.calls[1][2]).toHaveLength(1);
    // 源组件保持原数据，只有目标组件被替换。
    expect(data.widgetData.value.w1.rows).toEqual([{ region: 'call1' }]);
    expect(data.widgetData.value.w2.rows).toEqual([{ region: 'linked' }]);
    expect(data.loadingWidgets.value.w2).toBeUndefined();
  });

  it('只切换选中数据项时不重复查询目标组件', async () => {
    const dashboardId = ref('dashboard-1');
    const linkageSpec = ref<DashboardSpec>(buildLinkageSpec());
    const data = useDashboardData({ dashboardId, spec: linkageSpec });
    queryWidgetsMock.mockResolvedValue({ datasets: {}, widgets: {} });

    await data.loadAll({});
    data.applyLinkage('w1', [{ field: 'region', values: ['华东'] }], { seriesIndex: 0, dataIndex: 0 });
    await nextTick();
    await flushPromises();
    expect(queryWidgetsMock).toHaveBeenCalledTimes(2);

    // 同一分类、不同系列点位：联动条件不变，只更新源图表高亮，不重新查询。
    data.applyLinkage('w1', [{ field: 'region', values: ['华东'] }], { seriesIndex: 1, dataIndex: 0 });
    await nextTick();
    await flushPromises();
    expect(queryWidgetsMock).toHaveBeenCalledTimes(2);
    expect(data.linkage.value?.selection).toMatchObject({ seriesIndex: 1, dataIndex: 0 });

    // 再次点击同一数据项取消联动，目标组件恢复查询。
    data.applyLinkage('w1', [{ field: 'region', values: ['华东'] }], { seriesIndex: 1, dataIndex: 0 });
    await nextTick();
    await flushPromises();
    expect(data.linkage.value).toBeNull();
    expect(queryWidgetsMock).toHaveBeenCalledTimes(3);
  });

  it('跨数据集按字段关联转换目标字段', async () => {
    const dashboardId = ref('dashboard-1');
    const linkageSpec = ref<DashboardSpec>(buildLinkageSpec('ds2'));
    linkageSpec.value.widgets[0].interaction = {
      clickAction: 'linkage',
      targets: [{ widgetId: 'w2', fieldMap: [{ sourceField: 'region', targetField: 'province' }] }],
    };
    const data = useDashboardData({ dashboardId, spec: linkageSpec });
    await data.loadAll({});

    data.applyLinkage('w1', [{ field: 'region', values: ['华东'] }]);
    await nextTick();
    await flushPromises();

    expect(latestQueries()).toContainEqual(
      expect.objectContaining({ datasetId: 'ds2', conditions: [{ field: 'province', values: ['华东'] }] }),
    );
  });

  it('跨数据集缺少字段关联时不建立联动', async () => {
    const dashboardId = ref('dashboard-1');
    const linkageSpec = ref<DashboardSpec>(buildLinkageSpec('ds2'));
    linkageSpec.value.widgets[0].interaction = { clickAction: 'linkage', targets: [{ widgetId: 'w2' }] };
    const data = useDashboardData({ dashboardId, spec: linkageSpec });
    await data.loadAll({});

    expect(data.applyLinkage('w1', [{ field: 'region', values: ['华东'] }])).toBe(false);
    expect(data.linkage.value).toBeNull();
    expect(data.linkageWidgetIds.value).toEqual({});
  });
});
