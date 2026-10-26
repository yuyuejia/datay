import { applyEncodeRows, buildEchartsOption, resolveKpiValue } from './spec-to-option';
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
  chart: { type: 'bar' },
  ...overrides,
});

describe('spec-to-option', () => {
  it('柱状图只使用配置的度量字段，不把其他数值列自动加为系列', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }] },
        chart: { type: 'bar' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
          groupBy: true,
          limit: 100,
        },
      }),
      result,
    );

    expect(option.series).toHaveLength(1);
    expect(option.series[0].name).toBe('amount');
    expect(option.series[0].data).toEqual([180.5, 80, 45.25, 70]);
    expect(option.xAxis).toMatchObject({ type: 'category' });
    expect(option.yAxis).toMatchObject({ type: 'value' });
  });

  it('图例默认使用字段描述而不是字段名', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }] },
        chart: { type: 'bar' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
          groupBy: true,
          limit: 100,
        },
      }),
      { ...result, columnLabels: { amount: '成交金额' } },
    );

    expect(option.series[0].name).toBe('成交金额');
  });

  it('分类字段 groupBy 后对度量字段求和', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }] },
        chart: { type: 'bar' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
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
        echartsOption: { series: [{ type: 'pie' }] },
        chart: { type: 'pie' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
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

  it('多个度量字段生成多条系列，并各自按分类 groupBy', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }] },
        chart: { type: 'bar' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [
            { name: 'amount', field: 'amount', agg: 'sum', role: 'y' },
            { name: 'quantity', field: 'quantity', agg: 'sum', role: 'y' },
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

  it('系列维度按字段拆分多系列，并继续使用度量字段', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }] },
        chart: { type: 'bar' },
        encode: {
          dimensions: [
            { field: 'region', role: 'x' },
            { field: 'channel', role: 'series' },
          ],
          measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
          groupBy: true,
          limit: 100,
        },
      }),
      result,
    );

    expect(option.series).toHaveLength(3);
    expect(option.series.map((series: any) => series.name)).toEqual(['amount · 官网', 'amount · APP', 'amount · 门店']);
    expect(option.series[0].data).toEqual([120.5, null, null, 70]);
    expect(option.series[1].data).toEqual([60, 80, null, null]);
    expect(option.series[2].data).toEqual([null, null, 45.25, null]);
  });

  it('饼图按分类字段和度量字段组装数据', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'pie' }] },
        chart: { type: 'pie' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 3,
        },
      }),
      result,
    );

    expect(option.series).toHaveLength(1);
    expect(option.series[0].data).toEqual([
      { name: '华东', value: 180.5 },
      { name: '华南', value: 80 },
      { name: '华北', value: 45.25 },
    ]);
  });

  it('环形图使用饼图系列并设置默认内外半径', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'pie' }] },
        chart: { type: 'donut' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 3,
        },
      }),
      result,
    );

    expect(option.series[0]).toMatchObject({ type: 'pie', radius: ['42%', '68%'] });
    expect(option.series[0].data).toHaveLength(3);
  });

  it('仪表盘对度量聚合后展示单值', () => {
    const option = buildEchartsOption(
      widget({
        title: '总销售额',
        echartsOption: { series: [{ type: 'gauge' }] },
        chart: { type: 'gauge' },
        encode: {
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: false,
        },
      }),
      result,
    );

    expect(option.series[0]).toMatchObject({ type: 'gauge' });
    expect(option.series[0].data).toEqual([{ value: 375.75, name: '总销售额' }]);
    expect(option.xAxis).toBeUndefined();
    expect(option.yAxis).toBeUndefined();
  });

  it('组合图按 chart.series 生成双轴系列', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: {
          series: [
            { type: 'bar', barMaxWidth: 36 },
            { type: 'line', smooth: true },
          ],
        },
        chart: {
          type: 'bar',
          subtype: 'grouped',
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
      result,
    );

    expect(option.series).toHaveLength(2);
    expect(option.series[0]).toMatchObject({ type: 'bar', data: [180.5, 80, 45.25, 70] });
    expect(option.series[1]).toMatchObject({ type: 'line', yAxisIndex: 1, data: [12, 5, 3, 6] });
    expect(option.yAxis).toHaveLength(2);
  });

  it('横向柱状图把分类放到 Y 轴、数值放到 X 轴', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }] },
        chart: { type: 'bar', orientation: 'horizontal' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
          groupBy: true,
          limit: 100,
        },
      }),
      result,
    );

    expect(option.yAxis).toMatchObject({ type: 'category' });
    expect(option.xAxis).toMatchObject({ type: 'value' });
    expect(option.yAxis.data).toEqual(['华东', '华南', '华北', '西南']);
  });

  it('百分比堆积按每个分类的合计换算为百分比', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }] },
        chart: { type: 'bar', subtype: 'stackedPercent' },
        encode: {
          dimensions: [
            { field: 'region', role: 'x' },
            { field: 'channel', role: 'series' },
          ],
          measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
          groupBy: true,
          limit: 100,
        },
      }),
      result,
    );

    expect(option.series[0].stack).toBe('total');
    expect(option.series[0].data[0]).toBeCloseTo(66.76, 2);
    expect(option.series[1].data[0]).toBeCloseTo(33.24, 2);
    expect(option.series[1].data[1]).toBe(100);
  });

  it('percentOfTotal 变换把饼图数值换算为占总比', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'pie' }] },
        chart: { type: 'pie' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          transform: [{ type: 'percentOfTotal' }],
          groupBy: true,
          limit: 100,
        },
      }),
      result,
    );

    const total = option.series[0].data.reduce((sum: number, item: any) => sum + item.value, 0);
    expect(total).toBeCloseTo(100, 1);
    expect(option.series[0].data[0]).toEqual({ name: '华东', value: 48.04 });
  });

  it('散点图按 x 维度和 y 度量生成数据点', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'scatter' }] },
        chart: { type: 'scatter' },
        encode: {
          dimensions: [{ field: 'amount', role: 'x' }],
          measures: [{ field: 'quantity', agg: 'sum', role: 'y' }],
          groupBy: false,
          limit: 100,
        },
      }),
      result,
    );

    expect(option.xAxis).toMatchObject({ type: 'value' });
    expect(option.yAxis).toMatchObject({ type: 'value' });
    expect(option.series).toHaveLength(1);
    expect(option.series[0]).toMatchObject({ type: 'scatter' });
    expect(option.series[0].data[0].value).toEqual([120.5, 8]);
  });

  it('散点图支持按 size 度量映射点大小', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'scatter' }] },
        chart: { type: 'scatter' },
        encode: {
          dimensions: [{ field: 'amount', role: 'x' }],
          measures: [
            { field: 'quantity', agg: 'sum', role: 'y' },
            { field: 'amount', agg: 'sum', role: 'size' },
          ],
          groupBy: false,
          limit: 100,
        },
      }),
      result,
    );

    expect(option.series[0].data[0].symbolSize).toBeGreaterThan(8);
    expect(option.series[0].data[4].symbolSize).toBeLessThanOrEqual(32);
  });

  it('雷达图按维度生成指标轴，每个度量生成一条系列', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'radar' }] },
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
      result,
    );

    expect(option.radar.indicator.map((item: any) => item.name)).toEqual(['华东', '华南', '华北', '西南']);
    expect(option.radar.indicator[0].max).toBeGreaterThanOrEqual(180.5);
    expect(option.series[0]).toMatchObject({ type: 'radar' });
    expect(option.series[0].data[0].value).toEqual([180.5, 80, 45.25, 70]);
    expect(option.series[0].data[1].value).toEqual([12, 5, 3, 6]);
  });

  it('漏斗图按分类和度量生成漏斗数据', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'funnel' }] },
        chart: { type: 'funnel' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 3,
        },
      }),
      result,
    );

    expect(option.series[0]).toMatchObject({ type: 'funnel' });
    expect(option.series[0].data).toEqual([
      { name: '华东', value: 180.5 },
      { name: '华南', value: 80 },
      { name: '华北', value: 45.25 },
    ]);
  });

  it('矩形树图按分类和度量生成矩形块', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'treemap' }] },
        chart: { type: 'treemap' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
          groupBy: true,
          limit: 3,
        },
      }),
      result,
    );

    expect(option.series[0]).toMatchObject({ type: 'treemap', leafDepth: 1 });
    expect(option.series[0].data).toHaveLength(3);
    expect(option.series[0].data[0]).toEqual({ name: '华东', value: 180.5 });
  });

  it('热力图使用 x 与系列维度作为两个分类轴', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'heatmap' }] },
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
      result,
    );

    expect(option.xAxis).toMatchObject({ type: 'category', data: ['华东', '华南', '华北', '西南'] });
    expect(option.yAxis).toMatchObject({ type: 'category', data: ['官网', 'APP', '门店'] });
    expect(option.series[0]).toMatchObject({ type: 'heatmap' });
    expect(option.series[0].data).toContainEqual([0, 0, 120.5]);
    expect(option.series[0].data).toContainEqual([1, 1, 80]);
    expect(option.visualMap.max).toBe(120.5);
  });

  it('瀑布图用透明基座和增减值叠加生成累计效果', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }] },
        chart: { type: 'waterfall' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
          groupBy: true,
          limit: 100,
        },
      }),
      result,
    );

    expect(option.series).toHaveLength(2);
    expect(option.series[0]).toMatchObject({ name: '累计基数', stack: 'waterfall' });
    expect(option.series[0].data).toEqual([0, 180.5, 260.5, 305.75]);
    expect(option.series[1].data.map((item: any) => item.value)).toEqual([180.5, 80, 45.25, 70]);
  });

  it('桑基图按源节点、目标节点和权重生成节点与连线', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'sankey' }] },
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
      result,
    );

    expect(option.series[0]).toMatchObject({ type: 'sankey' });
    expect(option.series[0].data.map((node: any) => node.name)).toEqual(['华东', '官网', '华南', 'APP', '华北', '门店', '西南']);
    expect(option.series[0].links).toContainEqual({ source: '华东', target: '官网', value: 120.5 });
    expect(option.series[0].links).toHaveLength(5);
  });

  it('关系图按源节点、目标节点和权重生成节点与连线', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'graph' }] },
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
      result,
    );

    expect(option.series[0]).toMatchObject({ type: 'graph', layout: 'force' });
    expect(option.series[0].links).toHaveLength(5);
    expect(option.series[0].data).toHaveLength(7);
  });

  it('箱线图按分类字段计算原始明细的五数概括', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'boxplot' }] },
        chart: { type: 'boxplot' },
        encode: {
          dimensions: [{ field: 'channel', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
          groupBy: false,
          limit: 100,
        },
      }),
      result,
    );

    expect(option.series[0]).toMatchObject({ type: 'boxplot' });
    expect(option.xAxis.data).toEqual(['官网', 'APP', '门店']);
    const firstBox = option.series[0].data[0];
    expect(firstBox[0]).toBe(70);
    expect(firstBox[2]).toBeCloseTo(95.25, 2);
    expect(firstBox[4]).toBe(120.5);
    const secondBox = option.series[0].data[1];
    expect(secondBox).toEqual([60, 65, 70, 75, 80]);
  });

  it('明细表数据按 encode 排序并截断', () => {
    const rows = applyEncodeRows({ sortField: 'amount', sortOrder: 'desc', limit: 2 }, result.rows);

    expect(rows.map(row => row.region)).toEqual(['华东', '华南']);
    expect(rows.map(row => row.amount)).toEqual([120.5, 80]);
  });

  it('KPI 按声明的聚合方式计算', () => {
    const avgKpi = resolveKpiValue(
      widget({
        type: 'kpi',
        chart: undefined,
        encode: { measures: [{ field: 'amount', agg: 'avg', role: 'value' }] },
      }),
      result,
    );
    expect(avgKpi.value).toBe(75.15);

    const countKpi = resolveKpiValue(
      widget({
        type: 'kpi',
        chart: undefined,
        encode: { measures: [{ field: 'amount', agg: 'count', role: 'value' }] },
      }),
      result,
    );
    expect(countKpi.value).toBe(5);
  });

  it('KPI 对指定度量字段求和', () => {
    const kpi = resolveKpiValue(
      widget({
        type: 'kpi',
        chart: undefined,
        encode: { measures: [{ field: 'amount', agg: 'sum', role: 'value' }] },
      }),
      result,
    );

    expect(kpi.value).toBe(375.75);
  });

  it('按 encode 声明的聚合方式聚合图表数据', () => {
    const avgOption = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }] },
        chart: { type: 'bar' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'avg', role: 'y' }],
          groupBy: true,
          limit: 100,
        },
      }),
      result,
    );

    expect(avgOption.series[0].data).toEqual([90.25, 80, 45.25, 70]);

    const countOption = buildEchartsOption(
      widget({
        echartsOption: { series: [{ type: 'bar' }] },
        chart: { type: 'bar' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'count', role: 'y' }],
          groupBy: true,
          limit: 100,
        },
      }),
      result,
    );

    expect(countOption.series[0].data).toEqual([2, 1, 1, 1]);
  });

  it('净化 AI option 中的 HTML formatter，保留 ECharts 模板占位符', () => {
    const option = buildEchartsOption(
      widget({
        echartsOption: {
          tooltip: { formatter: '<img src=x onerror="alert(1)">{b}: {c}' },
          xAxis: { axisLabel: { formatter: '<img src=x onerror="alert(1)">{value}' } },
          series: [{ type: 'bar' }],
        },
        chart: { type: 'bar' },
        encode: {
          dimensions: [{ field: 'region', role: 'x' }],
          measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
          groupBy: true,
          limit: 3,
        },
      }),
      result,
    );

    expect(option.tooltip.formatter).not.toContain('onerror');
    expect(option.tooltip.formatter).toContain('{b}: {c}');
    expect(option.xAxis.axisLabel.formatter).not.toContain('onerror');
    expect(option.xAxis.axisLabel.formatter).toContain('{value}');
  });
});
