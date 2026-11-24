import { isDrillableWidget, resolveDrillDimensions } from './drill-utils';
import type { DashboardWidget } from './types';

const widget = (chartType: string, overrides: Partial<DashboardWidget> = {}): DashboardWidget => ({
  id: 'w1',
  type: 'echarts',
  layout: { x: 0, y: 0, w: 12, h: 8 },
  datasetId: 'ds1',
  chart: { type: chartType as any },
  encode: {
    dimensions: [{ field: 'region', role: 'x' }],
    measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
  },
  ...overrides,
});

describe('resolveDrillDimensions', () => {
  it('优先使用 series 下钻元数据', () => {
    const option = {
      series: [
        {
          __datayDrill: [
            [
              { field: 'region', values: ['华东'] },
              { field: 'channel', values: ['官网'] },
            ],
            null,
          ],
        },
      ],
    };

    expect(resolveDrillDimensions({ seriesIndex: 0, dataIndex: 0 }, widget('bar'), option)).toEqual([
      { field: 'region', values: ['华东'] },
      { field: 'channel', values: ['官网'] },
    ]);
    expect(resolveDrillDimensions({ seriesIndex: 0, dataIndex: 1 }, widget('bar'), option)).toBeNull();
  });

  it('桑基图/关系图读取边元数据', () => {
    const option = {
      series: [
        {
          __datayDrillLinks: [
            [
              { field: 'source', values: ['官网'] },
              { field: 'target', values: ['华东'] },
            ],
          ],
        },
      ],
    };

    expect(resolveDrillDimensions({ seriesIndex: 0, dataIndex: 0, dataType: 'edge' }, widget('sankey'), option)).toEqual([
      { field: 'source', values: ['官网'] },
      { field: 'target', values: ['华东'] },
    ]);
  });

  it('分类图缺少元数据时按 params.name 兜底', () => {
    expect(resolveDrillDimensions({ name: '华南' }, widget('bar'), { series: [{}] })).toEqual([{ field: 'region', values: ['华南'] }]);
  });

  it('地图和仪表盘等无维度图表不兜底', () => {
    expect(resolveDrillDimensions({ name: '华东' }, widget('gauge'))).toBeNull();
    expect(resolveDrillDimensions({ name: '华东' }, widget('map'))).toBeNull();
  });

  it('过滤空值并去重字段', () => {
    const option = {
      series: [
        {
          __datayDrill: [
            [
              { field: 'region', values: ['华东'] },
              { field: 'region', values: ['华南'] },
              { field: 'channel', values: [''] },
            ],
          ],
        },
      ],
    };

    expect(resolveDrillDimensions({ seriesIndex: 0, dataIndex: 0 }, widget('bar'), option)).toEqual([
      { field: 'region', values: ['华东'] },
    ]);
  });
});

describe('isDrillableWidget', () => {
  it('排除仪表盘和雷达图', () => {
    expect(isDrillableWidget(widget('bar'))).toBe(true);
    expect(isDrillableWidget(widget('map'))).toBe(true);
    expect(isDrillableWidget(widget('gauge'))).toBe(false);
    expect(isDrillableWidget(widget('radar'))).toBe(false);
    expect(isDrillableWidget({ ...widget('bar'), type: 'table' })).toBe(false);
  });
});
