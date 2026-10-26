import { buildEchartsOption, resolveKpiValue } from './spec-to-option';
import type { Aggregation, DashboardWidget, DatasetResult } from './types';

const baseResult: DatasetResult = {
  columns: ['region', 'channel', 'amount', 'quantity', 'target'],
  rows: [
    { region: '华东', channel: '官网', amount: 120.5, quantity: 8, target: 100 },
    { region: '华南', channel: 'APP', amount: 80, quantity: 5, target: 90 },
    { region: '华北', channel: '门店', amount: 45.25, quantity: 3, target: 60 },
    { region: '华东', channel: 'APP', amount: 60, quantity: 4, target: 80 },
    { region: '西南', channel: '官网', amount: 70, quantity: 6, target: 75 },
  ],
};

const widget = (overrides: Partial<DashboardWidget>): DashboardWidget => ({
  id: 'chart1',
  title: '测试图表',
  type: 'echarts',
  layout: { x: 0, y: 0, w: 12, h: 8 },
  chart: { type: 'bar' },
  encode: {
    dimensions: [{ field: 'region', role: 'x' }],
    measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
    groupBy: true,
    limit: 100,
  },
  ...overrides,
});

const barOption = (agg: Aggregation) =>
  buildEchartsOption(
    widget({
      encode: {
        dimensions: [{ field: 'region', role: 'x' }],
        measures: [{ field: 'amount', agg, role: 'y' }],
        groupBy: true,
        limit: 100,
      },
    }),
    baseResult,
  );

describe('chart configuration matrix', () => {
  it.each([
    ['sum', [180.5, 80, 45.25, 70]],
    ['avg', [90.25, 80, 45.25, 70]],
    ['min', [60, 80, 45.25, 70]],
    ['max', [120.5, 80, 45.25, 70]],
    ['count', [2, 1, 1, 1]],
    ['none', [120.5, 80, 45.25, 70]],
  ])('聚合方式 %s 计算正确', (agg, expected) => {
    expect(barOption(agg as Aggregation).series[0].data).toEqual(expected);
  });

  it('堆积柱状图设置 stack', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'bar', subtype: 'stacked' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [
            { field: 'amount', agg: 'sum', role: 'y' },
            { field: 'quantity', agg: 'sum', role: 'y' },
          ],
          groupBy: true,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.series).toHaveLength(2);
    expect(option.series.every((item: any) => item.stack === 'total')).toBe(true);
  });

  it('百分比堆积按分类换算并设置 100 轴', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'bar', subtype: 'stackedPercent' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [
            { field: 'amount', agg: 'sum', role: 'y' },
            { field: 'quantity', agg: 'sum', role: 'y' },
          ],
          groupBy: true,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.series[0].data[0] + option.series[1].data[0]).toBeCloseTo(100, 2);
    expect(option.yAxis).toMatchObject({ max: 100, axisLabel: { formatter: '{value}%' } });
    expect(option.series[0].stack).toBe('total');
  });

  it('横向柱状图交换坐标轴', () => {
    const option = buildEchartsOption(widget({ chart: { type: 'bar', orientation: 'horizontal' } }), baseResult);

    expect(option.yAxis).toMatchObject({ type: 'category', data: ['华东', '华南', '华北', '西南'] });
    expect(option.xAxis).toMatchObject({ type: 'value' });
  });

  it('双轴组合图保留左右轴与不同系列类型', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }, { type: 'line' }] },
        chart: {
          type: 'bar',
          series: [
            { measure: 'amount', type: 'bar', axis: 'left' },
            { measure: 'quantity', type: 'line', axis: 'right' },
          ],
        },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [
            { field: 'amount', agg: 'sum', role: 'y' },
            { field: 'quantity', agg: 'sum', role: 'y' },
          ],
          groupBy: true,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.yAxis).toHaveLength(2);
    expect(option.series[0]).toMatchObject({ type: 'bar' });
    expect(option.series[1]).toMatchObject({ type: 'line', yAxisIndex: 1 });
  });

  it('横向双轴组合图使用双 X 轴', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }, { type: 'line' }] },
        chart: {
          type: 'bar',
          orientation: 'horizontal',
          series: [
            { measure: 'amount', type: 'bar', axis: 'left' },
            { measure: 'quantity', type: 'line', axis: 'right' },
          ],
        },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [
            { field: 'amount', agg: 'sum', role: 'y' },
            { field: 'quantity', agg: 'sum', role: 'y' },
          ],
          groupBy: true,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.yAxis).toMatchObject({ type: 'category' });
    expect(option.xAxis).toHaveLength(2);
    expect(option.series[1]).toMatchObject({ type: 'line', xAxisIndex: 1 });
  });

  it('折线平滑、阶梯和面积样式生效', () => {
    const smooth = buildEchartsOption(widget({ chart: { type: 'line', subtype: 'smooth' } }), baseResult);
    expect(smooth.series[0]).toMatchObject({ type: 'line', smooth: true });

    const step = buildEchartsOption(widget({ chart: { type: 'line', subtype: 'step' } }), baseResult);
    expect(step.series[0]).toMatchObject({ type: 'line', step: true, smooth: false });

    const area = buildEchartsOption(widget({ chart: { type: 'area', subtype: 'smooth' } }), baseResult);
    expect(area.series[0]).toMatchObject({ type: 'line', smooth: true });
    expect(area.series[0].areaStyle).toBeDefined();
  });

  it('饼图 limit 与占总比按完整聚合结果计算', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'pie' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 2,
          sortField: 'amount',
          sortOrder: 'desc',
          transform: [{ type: 'percentOfTotal' }],
        },
      }),
      baseResult,
    );

    expect(option.series[0].data.map((item: any) => item.name)).toEqual(['华东', '华南']);
    expect(option.series[0].data[0].value).toBeCloseTo(48.04, 2);
    expect(option.series[0].data[1].value).toBeCloseTo(21.29, 2);
  });

  it('漏斗图按 encode 排序方向展示', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'funnel' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 100,
          sortField: 'amount',
          sortOrder: 'asc',
        },
      }),
      baseResult,
    );

    expect(option.series[0].data.map((item: any) => item.name)).toEqual(['华北', '西南', '华南', '华东']);
    expect(option.series[0].sort).toBe('none');
  });

  it('漏斗图未配置排序时默认按值降序', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'funnel' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.series[0].sort).toBe('descending');
  });

  it('矩形树图使用树图配置', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'treemap' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.series[0]).toMatchObject({ type: 'treemap', leafDepth: 1, roam: false });
    expect(option.series[0].breadcrumb).toEqual({ show: false });
  });

  it('散点图支持数值 X 轴与点大小映射', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'scatter' },
        encode: {
          dimensions: [{ field: 'amount', role: 'x' }],
          measures: [
            { field: 'quantity', agg: 'sum', role: 'y' },
            { field: 'target', agg: 'sum', role: 'size' },
          ],
          groupBy: false,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.xAxis).toMatchObject({ type: 'value' });
    expect(option.yAxis).toMatchObject({ type: 'value' });
    expect(option.series).toHaveLength(1);
    expect(option.series[0].data[0].value).toEqual([120.5, 8]);
    expect(option.series[0].data[0].symbolSize).toBeGreaterThanOrEqual(8);
  });

  it('散点图分类 X 轴与多度量系列生效', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'scatter' },
        encode: {
          dimensions: [
            { field: 'region', role: 'x' },
            { field: 'channel', role: 'series' },
          ],
          measures: [
            { field: 'amount', agg: 'sum', role: 'y' },
            { field: 'quantity', agg: 'sum', role: 'y' },
          ],
          groupBy: false,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.xAxis).toMatchObject({ type: 'category', data: ['华东', '华南', '华北', '西南'] });
    expect(option.series.length).toBe(6);
    expect(option.series.map((item: any) => item.name)).toEqual([
      'amount · 官网',
      'amount · APP',
      'amount · 门店',
      'quantity · 官网',
      'quantity · APP',
      'quantity · 门店',
    ]);
  });

  it('雷达图生成指标轴和系列值', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'radar' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [
            { field: 'amount', agg: 'sum', role: 'y' },
            { field: 'quantity', agg: 'sum', role: 'y' },
          ],
          groupBy: true,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.radar.indicator.map((item: any) => item.name)).toEqual(['华东', '华南', '华北', '西南']);
    expect(option.series[0].data[0].value).toEqual([180.5, 80, 45.25, 70]);
    expect(option.series[0].data[1].value).toEqual([12, 5, 3, 6]);
  });

  it('热力图生成二维分类与 visualMap', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'heatmap' },
        encode: {
          dimensions: [
            { field: 'region', role: 'x' },
            { field: 'channel', role: 'series' },
          ],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.xAxis.data).toEqual(['华东', '华南', '华北', '西南']);
    expect(option.yAxis.data).toEqual(['官网', 'APP', '门店']);
    expect(option.series[0].data).toContainEqual([0, 0, 120.5]);
    expect(option.visualMap.max).toBe(120.5);
  });

  it('瀑布图正确计算累计基座与正负颜色', () => {
    const result: DatasetResult = {
      columns: ['stage', 'amount'],
      rows: [
        { stage: '期初', amount: 100 },
        { stage: '减少', amount: -30 },
        { stage: '增长', amount: 50 },
      ],
    };
    const option = buildEchartsOption(
      widget({
        chart: { type: 'waterfall' },
        encode: {
          dimensions: [{ field: 'stage', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
          groupBy: true,
          limit: 100,
        },
      }),
      result,
    );

    expect(option.series[0].data).toEqual([0, 70, 70]);
    expect(option.series[1].data.map((item: any) => item.value)).toEqual([100, 30, 50]);
    expect(option.series[1].data[1].itemStyle.color).toBe('#f56c6c');
    expect(option.series[1].data[2].itemStyle.color).toBe('#67c23a');
  });

  it('桑基图与关系图生成节点和连线', () => {
    const sankey = buildEchartsOption(
      widget({
        chart: { type: 'sankey' },
        encode: {
          dimensions: [
            { field: 'region', role: 'x' },
            { field: 'channel', role: 'series' },
          ],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 100,
        },
      }),
      baseResult,
    );
    expect(sankey.series[0].data).toHaveLength(7);
    expect(sankey.series[0].links).toHaveLength(5);

    const graph = buildEchartsOption(
      widget({
        chart: { type: 'graph' },
        encode: {
          dimensions: [
            { field: 'region', role: 'x' },
            { field: 'channel', role: 'series' },
          ],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 100,
        },
      }),
      baseResult,
    );
    expect(graph.series[0]).toMatchObject({ type: 'graph', layout: 'force' });
    expect(graph.series[0].links).toHaveLength(5);
  });

  it('箱线图计算五数概括', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'boxplot' },
        encode: {
          dimensions: [{ field: 'channel', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
          groupBy: false,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.xAxis.data).toEqual(['官网', 'APP', '门店']);
    expect(option.series[0].data[0][0]).toBe(70);
    expect(option.series[0].data[0][4]).toBe(120.5);
    expect(option.series[0].data[1]).toEqual([60, 65, 70, 75, 80]);
  });

  it('服务端聚合结果不会被二次 count', () => {
    const option = buildEchartsOption(
      widget({
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'count', role: 'y' }],
          groupBy: true,
          limit: 100,
        },
      }),
      {
        columns: ['region', 'amount'],
        rows: [{ region: '华东', amount: 2 }],
        aggregated: true,
      },
    );

    expect(option.series[0].data).toEqual([2]);
  });

  it('KPI 聚合与条件展示数据正确', () => {
    expect(resolveKpiValue(widget({ type: 'kpi', chart: undefined }), baseResult).value).toBe(375.75);
    expect(
      resolveKpiValue(
        widget({
          type: 'kpi',
          chart: undefined,
          encode: { measures: [{ field: 'amount', agg: 'count', role: 'value' }] },
        }),
        baseResult,
      ).value,
    ).toBe(5);
  });

  it('图例、坐标轴、网格线、数据标签配置保留', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: {
          legend: { show: false },
          xAxis: { show: false, splitLine: { show: false } },
          yAxis: { show: false, splitLine: { show: false } },
          series: [{ type: 'bar', label: { show: true } }],
        },
      }),
      baseResult,
    );

    expect(option.legend.show).toBe(false);
    expect(option.xAxis.show).toBe(false);
    expect(option.xAxis.splitLine.show).toBe(false);
    expect(option.yAxis.show).toBe(false);
    expect(option.yAxis.splitLine.show).toBe(false);
    expect(option.series[0].label.show).toBe(true);
  });

  it('chart.type 优先于 echartsOption.series.type', () => {
    const option = buildEchartsOption(
      widget({
        chart: { type: 'pie' },
        echartsOption: { series: [{ type: 'bar' }] },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 100,
        },
      }),
      baseResult,
    );

    expect(option.series[0].type).toBe('pie');
  });
});
