import { buildServerAggregation, serverQueryLimit } from './server-aggregation';
import type { DashboardWidget } from './types';

const widget = (overrides: Partial<DashboardWidget>): DashboardWidget => ({
  id: 'w1',
  type: 'echarts',
  layout: { x: 0, y: 0, w: 12, h: 8 },
  datasetId: 'ds1',
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
  ...overrides,
});

describe('server-aggregation', () => {
  it('柱状图按 x + series 下推分组聚合', () => {
    expect(buildServerAggregation(widget({}))).toEqual({
      groupFields: ['region', 'channel'],
      aggregations: [{ field: 'amount', agg: 'sum' }],
    });
  });

  it('饼图只按 x 维度下推', () => {
    const result = buildServerAggregation(widget({ chart: { type: 'pie' } }));
    expect(result.groupFields).toEqual(['region']);
  });

  it('透视表按 row + column 下推，即使没有 groupBy', () => {
    const result = buildServerAggregation(
      widget({
        type: 'pivot',
        chart: undefined,
        encode: {
          dimensions: [
            { field: 'region', role: 'row' },
            { field: 'channel', role: 'column' },
          ],
          measures: [{ field: 'amount', agg: 'avg', role: 'value' }],
        },
      }),
      false,
    );

    expect(result).toEqual({
      groupFields: ['region', 'channel'],
      aggregations: [{ field: 'amount', agg: 'avg' }],
    });
  });

  it('表格/KPI 不启用聚合下推', () => {
    expect(buildServerAggregation(widget({ type: 'table' }))).toEqual({ groupFields: [], aggregations: [] });
    expect(buildServerAggregation(widget({ type: 'kpi' }))).toEqual({ groupFields: [], aggregations: [] });
  });

  it('groupBy=false 的图表不启用下推', () => {
    expect(
      buildServerAggregation(
        widget({
          encode: {
            dimensions: [{ field: 'region', role: 'x' }],
            measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
            groupBy: false,
          },
        }),
        false,
      ),
    ).toEqual({ groupFields: [], aggregations: [] });
  });
});

describe('serverQueryLimit', () => {
  it('percent 模式不推推行数限制', () => {
    expect(
      serverQueryLimit(
        widget({
          encode: {
            dimensions: [{ field: 'region', role: 'x' }],
            measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
            groupBy: true,
            limit: 10,
            transform: [{ type: 'percentOfTotal' }],
          },
        }),
        false,
      ),
    ).toBeUndefined();
    expect(serverQueryLimit(widget({ chart: { type: 'bar', subtype: 'stackedPercent' } }), false)).toBeUndefined();
  });

  it('普通图表与表格分页保留 limit', () => {
    expect(serverQueryLimit(widget({}), false)).toBe(100);
    expect(serverQueryLimit(widget({ type: 'table', encode: { pageSize: 20 } }), true)).toBe(20);
  });
});
