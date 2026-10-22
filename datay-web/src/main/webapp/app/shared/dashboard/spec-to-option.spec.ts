import { applyDataRefRows, buildEchartsOption, resolveKpiValue } from './spec-to-option';
import type { DashboardWidget, DatasetResult } from './types';

const result: DatasetResult = {
  columns: ['region', 'amount', 'quantity', 'channel'],
  rows: [
    { region: '华东', amount: 120.5, quantity: 8, channel: '官网' },
    { region: '华南', amount: 80, quantity: 5, channel: 'APP' },
    { region: '华北', amount: 45.25, quantity: 3, channel: '门店' },
    { region: '华东', amount: 60, quantity: 4, channel: 'APP' },
    { region: '西南', amount: 70, quantity: 6, channel: '官网' },
  ],
};

const widget = (overrides: Partial<DashboardWidget>): DashboardWidget => ({
  id: 'widget1',
  title: '测试组件',
  type: 'echarts',
  layout: { x: 0, y: 0, w: 6, h: 4 },
  ...overrides,
});

describe('spec-to-option', () => {
  it('柱状图只使用配置的数值字段，不把其他数值列自动加为系列', () => {
    const option = buildEchartsOption(
      widget({
        type: 'echarts',
        echartsOption: { series: [{ type: 'bar' }] },
        dataRef: { categoryField: 'region', valueField: 'amount', limit: 100 },
      }),
      result,
    );

    expect(option.series).toHaveLength(1);
    expect(option.series[0].name).toBe('amount');
    expect(option.series[0].data).toEqual([120.5, 80, 45.25, 60, 70]);
    expect(option.xAxis).toMatchObject({ type: 'category' });
    expect(option.yAxis).toMatchObject({ type: 'value' });
  });

  it('分类字段 groupBy 后对数值字段求和', () => {
    const option = buildEchartsOption(
      widget({
        type: 'echarts',
        echartsOption: { series: [{ type: 'bar' }] },
        dataRef: {
          categoryField: 'region',
          valueField: 'amount',
          groupBy: true,
          limit: 100,
        },
      }),
      result,
    );

    expect(option.xAxis.data).toEqual(['华东', '华南', '华北', '西南']);
    expect(option.series).toHaveLength(1);
    expect(option.series[0].data).toEqual([180.5, 80, 45.25, 70]);
  });

  it('饼图 groupBy 后先聚合再按 limit 截断', () => {
    const option = buildEchartsOption(
      widget({
        type: 'echarts',
        echartsOption: { series: [{ type: 'pie' }] },
        dataRef: {
          categoryField: 'region',
          valueField: 'amount',
          groupBy: true,
          limit: 3,
        },
      }),
      result,
    );

    expect(option.series[0].data).toEqual([
      { name: '华东', value: 180.5 },
      { name: '华南', value: 80 },
      { name: '华北', value: 45.25 },
    ]);
  });

  it('多个数值字段生成多条系列，并各自按分类 groupBy', () => {
    const option = buildEchartsOption(
      widget({
        type: 'echarts',
        echartsOption: { series: [{ type: 'bar' }] },
        dataRef: {
          categoryField: 'region',
          valueField: 'amount',
          series: [
            { name: 'amount', field: 'amount' },
            { name: 'quantity', field: 'quantity' },
          ],
          groupBy: true,
          limit: 100,
        },
      }),
      result,
    );

    expect(option.xAxis.data).toEqual(['华东', '华南', '华北', '西南']);
    expect(option.series).toHaveLength(2);
    expect(option.series[0].data).toEqual([180.5, 80, 45.25, 70]);
    expect(option.series[1].data).toEqual([12, 5, 3, 6]);
  });

  it('系列字段优先按字段拆分多系列，并继续使用数值字段', () => {
    const option = buildEchartsOption(
      widget({
        type: 'echarts',
        echartsOption: { series: [{ type: 'bar' }] },
        dataRef: {
          categoryField: 'region',
          valueField: 'amount',
          seriesField: 'channel',
          limit: 100,
        },
      }),
      result,
    );

    expect(option.series).toHaveLength(3);
    expect(option.series.map((series: any) => series.name)).toEqual(['官网', 'APP', '门店']);
    expect(option.series[0].data).toEqual([120.5, null, null, 70]);
    expect(option.series[1].data).toEqual([60, 80, null, null]);
    expect(option.series[2].data).toEqual([null, null, 45.25, null]);
  });

  it('饼图按分类字段和数值字段组装数据', () => {
    const option = buildEchartsOption(
      widget({
        type: 'echarts',
        echartsOption: { series: [{ type: 'pie' }] },
        dataRef: { categoryField: 'region', valueField: 'amount', limit: 3 },
      }),
      result,
    );

    expect(option.series).toHaveLength(1);
    expect(option.series[0].data).toEqual([
      { name: '华东', value: 120.5 },
      { name: '华南', value: 80 },
      { name: '华北', value: 45.25 },
    ]);
  });

  it('明细表数据按 dataRef 排序并截断', () => {
    const rows = applyDataRefRows({ sortField: 'amount', sortOrder: 'desc', limit: 2 }, result.rows);

    expect(rows.map(row => row.region)).toEqual(['华东', '华南']);
    expect(rows.map(row => row.amount)).toEqual([120.5, 80]);
  });

  it('KPI 对指定数值字段求和', () => {
    const kpi = resolveKpiValue(
      widget({
        type: 'kpi',
        dataRef: { valueField: 'amount' },
      }),
      result,
    );

    expect(kpi.value).toBe(375.75);
  });
});
