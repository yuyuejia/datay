import { ref } from 'vue';

import type { DashboardSpec, DatasetResult } from './types';
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
