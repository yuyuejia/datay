import { isDrillableWidget, resolveDrillDimensions } from './drill-utils';
import { buildEchartsOption } from './spec-to-option';
import type { ChartType, DashboardWidget, DatasetResult, WidgetEncode } from './types';

/**
 * 逐个图表类型验证“点击数据 -> 下钻条件”的映射。
 *
 * <p>这里走完整链路：buildEchartsOption 写入 series 元数据，
 * 再用 ECharts 点击事件形状的参数调用 resolveDrillDimensions。</p>
 */

const result: DatasetResult = {
  columns: ['region', 'channel', 'city', 'province', 'month_name', 'amount'],
  rows: [
    { region: '华东', channel: '官网', city: '上海', province: '上海市', month_name: '2025-01', amount: 120 },
    { region: '华东', channel: 'APP', city: '上海', province: '上海市', month_name: '2025-02', amount: 80 },
    { region: '华南', channel: '门店', city: '广州', province: '广东省', month_name: '2025-01', amount: 45 },
    { region: '华南', channel: '官网', city: '广州', province: '广东', month_name: '2025-02', amount: 70 },
  ],
};

const widget = (chartType: ChartType, encode: WidgetEncode): DashboardWidget => ({
  id: `w-${chartType}`,
  type: 'echarts',
  layout: { x: 0, y: 0, w: 12, h: 8 },
  datasetId: 'ds1',
  chart: { type: chartType },
  encode,
  echartsOption: { series: [{ type: chartType === 'area' ? 'line' : chartType }] },
});

const xRegion: WidgetEncode = {
  dimensions: [{ field: 'region', role: 'x' }],
  measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
  groupBy: true,
  limit: 100,
};

const xRegionValue: WidgetEncode = {
  dimensions: [{ field: 'region', role: 'x' }],
  measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
  groupBy: true,
  limit: 100,
};

const xRegionSeriesChannel: WidgetEncode = {
  dimensions: [
    { field: 'region', role: 'x' },
    { field: 'channel', role: 'series' },
  ],
  measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
  groupBy: true,
  limit: 100,
};

function drill(chartType: ChartType, encode: WidgetEncode, params: Record<string, any>) {
  const w = widget(chartType, encode);
  const option = buildEchartsOption(w, result);
  return resolveDrillDimensions(params, w, option);
}

describe('各图表类型下钻条件', () => {
  it('bar / line / area 按分类和系列拆分下钻', () => {
    for (const type of ['bar', 'line', 'area'] as ChartType[]) {
      expect(drill(type, xRegion, { seriesIndex: 0, dataIndex: 0 })).toEqual([{ field: 'region', values: ['华东'] }]);
      expect(drill(type, xRegionSeriesChannel, { seriesIndex: 0, dataIndex: 0 })).toEqual([
        { field: 'region', values: ['华东'] },
        { field: 'channel', values: ['官网'] },
      ]);
    }
  });

  it('pie / donut / funnel / treemap 按扇区或分类下钻', () => {
    for (const type of ['pie', 'donut', 'funnel', 'treemap'] as ChartType[]) {
      expect(drill(type, xRegionValue, { seriesIndex: 0, dataIndex: 0 })).toEqual([{ field: 'region', values: ['华东'] }]);
    }
  });

  it('heatmap 记录 x 与 series 两个维度', () => {
    expect(drill('heatmap', xRegionSeriesChannel, { seriesIndex: 0, dataIndex: 0 })).toEqual([
      { field: 'region', values: ['华东'] },
      { field: 'channel', values: ['官网'] },
    ]);
  });

  it('map 使用原始省份写法并支持多种写法 IN 过滤', () => {
    const encode: WidgetEncode = {
      dimensions: [{ field: 'province', role: 'x' }],
      measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
      groupBy: true,
      limit: 100,
    };
    expect(drill('map', encode, { seriesIndex: 0, dataIndex: 0 })).toEqual([{ field: 'province', values: ['上海市'] }]);
    expect(drill('map', encode, { seriesIndex: 0, dataIndex: 1 })).toEqual([{ field: 'province', values: ['广东省', '广东'] }]);
  });

  it('scatter 按点所属分类与系列下钻', () => {
    expect(drill('scatter', xRegionSeriesChannel, { seriesIndex: 0, dataIndex: 1 })).toEqual([
      { field: 'region', values: ['华南'] },
      { field: 'channel', values: ['官网'] },
    ]);
  });

  it('waterfall / boxplot 按分类下钻', () => {
    const encode: WidgetEncode = {
      dimensions: [{ field: 'city', role: 'x' }],
      measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
      groupBy: true,
      limit: 100,
    };
    expect(drill('waterfall', encode, { seriesIndex: 1, dataIndex: 0 })).toEqual([{ field: 'city', values: ['上海'] }]);

    const boxplot = drill('boxplot', { ...encode, groupBy: false }, { seriesIndex: 0, dataIndex: 1 });
    expect(boxplot).toEqual([{ field: 'city', values: ['广州'] }]);
  });

  it('sankey / graph 点击边时按源节点和目标节点下钻', () => {
    const encode: WidgetEncode = {
      dimensions: [
        { field: 'channel', role: 'x' },
        { field: 'region', role: 'series' },
      ],
      measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
      groupBy: true,
      limit: 100,
    };
    for (const type of ['sankey', 'graph'] as ChartType[]) {
      expect(drill(type, encode, { seriesIndex: 0, dataIndex: 0, dataType: 'edge' })).toEqual([
        { field: 'channel', values: ['官网'] },
        { field: 'region', values: ['华东'] },
      ]);
    }
  });

  it('chart.type 缺省时按 echartsOption.series 推断图表类型', () => {
    const w: DashboardWidget = {
      ...widget('bar', xRegionValue),
      chart: undefined,
      echartsOption: { series: [{ type: 'pie' }] },
    };
    const option = buildEchartsOption(w, result);
    expect(isDrillableWidget(w)).toBe(true);
    expect(resolveDrillDimensions({ seriesIndex: 0, dataIndex: 0 }, w, option)).toEqual([{ field: 'region', values: ['华东'] }]);
  });

  it('gauge / radar 不启用下钻', () => {
    const gauge = widget('gauge', { measures: [{ field: 'amount', agg: 'sum', role: 'value' }] });
    expect(resolveDrillDimensions({ seriesIndex: 0, dataIndex: 0 }, gauge, buildEchartsOption(gauge, result))).toBeNull();
    expect(isDrillableWidget(gauge)).toBe(false);

    const radar = widget('radar', {
      dimensions: [{ field: 'region', role: 'x' }],
      measures: [
        { field: 'amount', agg: 'sum', role: 'y' },
        { field: 'amount', agg: 'avg', role: 'y' },
      ],
      groupBy: true,
    });
    expect(resolveDrillDimensions({ seriesIndex: 0, dataIndex: 0, name: 'amount' }, radar, buildEchartsOption(radar, result))).toBeNull();
    expect(isDrillableWidget(radar)).toBe(false);
  });
});
