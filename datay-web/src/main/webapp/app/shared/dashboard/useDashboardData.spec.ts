import { ref } from 'vue';

import type { DashboardSpec, DatasetResult } from './types';
import { useDashboardData } from './useDashboardData';

const { queryWidgetsMock } = vi.hoisted(() => ({
  queryWidgetsMock: vi.fn(),
}));

vi.mock('./dashboard.service', () => ({
  default: class {
    queryWidgets = queryWidgetsMock;
    previewWidgets = vi.fn();
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
});
