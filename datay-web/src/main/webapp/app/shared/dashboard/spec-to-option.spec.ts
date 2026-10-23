import { applyDataRefRows, buildEchartsOption, buildKpiGaugeOption, formatKpiValue, resolveKpiValue } from './spec-to-option';
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

  it('图例默认使用字段描述而不是字段名', () => {
    const option = buildEchartsOption(
      widget({
        type: 'echarts',
        echartsOption: { series: [{ type: 'bar' }] },
        dataRef: { categoryField: 'region', valueField: 'amount', limit: 100 },
      }),
      { ...result, columnLabels: { amount: '成交金额' } },
    );

    expect(option.series[0].name).toBe('成交金额');
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

  it('指标卡对指定数值字段求和', () => {
    const kpi = resolveKpiValue(
      widget({
        type: 'kpi',
        dataRef: { valueField: 'amount' },
      }),
      result,
    );

    expect(kpi.value).toBe(375.75);
  });

  it('指标卡使用 ECharts gauge 展示汇总值', () => {
    const option = buildKpiGaugeOption(
      widget({
        type: 'kpi',
        dataRef: { valueField: 'amount' },
      }),
      result,
    );
    const series = option.series[0];

    expect(series.type).toBe('gauge');
    expect(series.min).toBe(0);
    expect(series.max).toBe(500);
    expect(series.data).toMatchObject([{ value: 375.75 }]);
    expect(series.title.show).toBe(false);
    expect(series.detail.formatter()).toBe('375.75');
  });

  it('指标卡无数据时显示占位符', () => {
    const option = buildKpiGaugeOption(widget({ type: 'kpi' }), undefined);
    const series = option.series[0];

    expect(series.data).toMatchObject([{ value: 0 }]);
    expect(series.max).toBe(100);
    expect(series.detail.formatter()).toBe('--');
  });

  it('指标卡负值使用反向仪表盘', () => {
    const option = buildKpiGaugeOption(
      widget({
        type: 'kpi',
        dataRef: { valueField: 'amount' },
      }),
      { columns: ['amount'], rows: [{ amount: -100 }] },
    );
    const series = option.series[0];

    expect(series.min).toBe(-200);
    expect(series.max).toBe(0);
    expect(series.data).toMatchObject([{ value: -100 }]);
    expect(series.detail.formatter()).toBe('-100');
  });

  it('指标卡没有标题时在图表内展示自动识别的字段名', () => {
    const option = buildKpiGaugeOption(widget({ type: 'kpi', title: undefined }), result);
    const series = option.series[0];

    expect(series.title.show).toBe(true);
    expect(series.data[0].name).toBe('amount');
  });

  it('指标卡数值格式化保留两位小数', () => {
    expect(formatKpiValue(375.756)).toMatch(/^375[.,]76$/);
    expect(formatKpiValue(null)).toBe('--');
  });

  it('指标卡长金额自动缩小字号避免压住环形进度', () => {
    const option = buildKpiGaugeOption(
      widget({
        type: 'kpi',
        dataRef: { valueField: 'amount' },
      }),
      { columns: ['amount'], rows: [{ amount: 1234567.89 }] },
    );
    const series = option.series[0];

    expect(series.detail.fontSize).toBe(15);
    expect(series.detail.formatter()).toMatch(/^1[.,]234[.,]567[.,]89$/);
  });
});
