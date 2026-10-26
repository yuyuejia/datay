import {
  buildDashboardFields,
  createDefaultEncode,
  ensureDefaultFields,
  hasCompleteDefaultFields,
  pickCategoryField,
  pickValueField,
  resolveChartType,
  shouldGroupByCategory,
} from './field-defaults';
import type { DashboardWidget } from './types';

const rows = [
  { region: '华东', amount: 120.5, quantity: 8, region_id: 'r1' },
  { region: '华南', amount: 80, quantity: 5, region_id: 'r2' },
];

const widget = (overrides: Partial<DashboardWidget>): DashboardWidget => ({
  id: 'widget1',
  title: '测试',
  type: 'echarts',
  layout: { x: 0, y: 0, w: 6, h: 4 },
  echartsOption: { series: [{ type: 'bar' }] },
  chart: { type: 'bar' },
  ...overrides,
});

describe('field-defaults', () => {
  it('从结果行推断数值字段类型', () => {
    const fields = buildDashboardFields(['region', 'amount', 'quantity'], rows);

    expect(fields.map(field => [field.name, field.type, field.numeric])).toEqual([
      ['region', 'string', false],
      ['amount', 'number', true],
      ['quantity', 'number', true],
    ]);
  });

  it('优先使用数据集缓存中的字段类型', () => {
    const fields = buildDashboardFields(
      ['region', 'amount'],
      [],
      [
        { name: 'region', type: 'VARCHAR' },
        { name: 'amount', type: 'DECIMAL' },
      ],
    );

    expect(fields.find(field => field.name === 'amount')?.numeric).toBe(true);
    expect(fields.find(field => field.name === 'region')?.numeric).toBe(false);
  });

  it('字段描述优先作为属性面板展示名称', () => {
    const fields = buildDashboardFields(['amount'], [], [{ name: 'amount', description: '成交金额', type: 'number' }]);

    expect(fields[0]).toMatchObject({ name: 'amount', label: '成交金额', type: 'number' });
  });

  it('字段没有声明描述时使用查询结果列标签', () => {
    const fields = buildDashboardFields(['sales_amount'], [], [], { sales_amount: '销售额' });

    expect(fields[0]).toMatchObject({ name: 'sales_amount', label: '销售额' });
  });

  it('默认分类字段避开代理键，默认数值字段优先常见度量字段', () => {
    const fields = buildDashboardFields(['region_id', 'region', 'id', 'amount', 'quantity'], rows);

    expect(pickCategoryField(fields)).toBe('region');
    expect(pickValueField(fields, 'region')).toBe('amount');
  });

  it('图表默认 encode 带 x 维度和 y 度量', () => {
    const fields = buildDashboardFields(['region', 'amount'], rows);
    const ref = createDefaultEncode('echarts', 'bar', fields);

    expect(ref).toMatchObject({
      dimensions: [{ field: 'region', role: 'x' }],
      measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
      limit: 100,
      groupBy: true,
    });
  });

  it('表格不设置维度/度量，KPI 只设置度量', () => {
    const fields = buildDashboardFields(['region', 'amount'], rows);

    expect(createDefaultEncode('table', undefined, fields)).toEqual({ limit: 100 });
    expect(createDefaultEncode('kpi', undefined, fields)).toEqual({
      measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
    });
  });

  it('仪表盘默认 encode 不设置维度', () => {
    const fields = buildDashboardFields(['region', 'amount'], rows);

    expect(createDefaultEncode('echarts', 'gauge', fields)).toMatchObject({
      measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
      groupBy: false,
    });
  });

  it('只有图表类型需要按分类字段 group by', () => {
    expect(shouldGroupByCategory('echarts', 'bar')).toBe(true);
    expect(shouldGroupByCategory('echarts', 'line')).toBe(true);
    expect(shouldGroupByCategory('echarts', 'area')).toBe(true);
    expect(shouldGroupByCategory('echarts', 'pie')).toBe(true);
    expect(shouldGroupByCategory('echarts', 'donut')).toBe(true);
    expect(shouldGroupByCategory('echarts', 'gauge')).toBe(false);
    expect(shouldGroupByCategory('echarts', 'scatter')).toBe(false);
    expect(shouldGroupByCategory('echarts', 'boxplot')).toBe(false);
    expect(shouldGroupByCategory('table', undefined)).toBe(false);
    expect(shouldGroupByCategory('kpi', undefined)).toBe(false);
  });

  it('热力图默认带 x 与 series 维度，透视表默认带 row 与 column 维度', () => {
    const fields = buildDashboardFields(['region', 'channel', 'amount'], rows);

    expect(createDefaultEncode('echarts', 'heatmap', fields)).toMatchObject({
      dimensions: [
        { field: 'region', role: 'x' },
        { field: 'channel', role: 'series' },
      ],
    });
    expect(createDefaultEncode('echarts', 'sankey', fields)).toMatchObject({
      dimensions: [
        { field: 'region', role: 'x' },
        { field: 'channel', role: 'series' },
      ],
      measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
    });
    expect(createDefaultEncode('echarts', 'boxplot', fields)).toMatchObject({ groupBy: false });

    const pivot = createDefaultEncode('pivot', undefined, fields);
    expect(pivot).toMatchObject({
      dimensions: [
        { field: 'region', role: 'row' },
        { field: 'channel', role: 'column' },
      ],
      measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
    });
  });

  it('数据到达后只补全空字段，并保持用户选择', () => {
    const item = widget({
      encode: { dimensions: [{ field: '自定义分类', role: 'x' }], limit: 100, groupBy: true },
    });
    const fields = buildDashboardFields(['region', 'amount'], rows);

    ensureDefaultFields(item, fields);
    expect(item.encode?.dimensions?.[0]?.field).toBe('自定义分类');
    expect(item.encode?.measures?.[0]?.field).toBe('amount');
    expect(hasCompleteDefaultFields(item)).toBe(true);

    const empty = widget({ encode: { limit: 100 } });
    ensureDefaultFields(empty, fields);
    expect(empty.encode).toMatchObject({
      dimensions: [{ field: 'region', role: 'x' }],
      measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
      groupBy: true,
    });
  });

  it('从组件或 echarts option 解析图表类型', () => {
    expect(resolveChartType(widget({ chart: { type: 'donut' } }))).toBe('donut');
    expect(resolveChartType(widget({ chart: { type: 'gauge' } }))).toBe('gauge');
    expect(resolveChartType({ series: [{ type: 'pie' }] })).toBe('pie');
    expect(resolveChartType({ series: [{ type: 'line', areaStyle: {} }] })).toBe('area');
    expect(resolveChartType({ series: [{ type: 'line' }] })).toBe('line');
    expect(resolveChartType({ series: [{ type: 'scatter' }] })).toBe('scatter');
    expect(resolveChartType({ series: [{ type: 'radar' }] })).toBe('radar');
    expect(resolveChartType({ series: [{ type: 'heatmap' }] })).toBe('heatmap');
    expect(resolveChartType({ series: [{ type: 'waterfall' }] })).toBe('waterfall');
    expect(resolveChartType({ series: [{ type: 'sankey' }] })).toBe('sankey');
    expect(resolveChartType({ series: [{ type: 'graph' }] })).toBe('graph');
    expect(resolveChartType({ series: [{ type: 'boxplot' }] })).toBe('boxplot');
    expect(resolveChartType(undefined)).toBe('bar');
  });
});
