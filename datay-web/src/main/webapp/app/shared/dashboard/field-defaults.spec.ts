import {
  buildDashboardFields,
  createDefaultDataRef,
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

  it('默认分类字段避开代理键，默认数值字段优先常见度量字段', () => {
    const fields = buildDashboardFields(['region_id', 'region', 'id', 'amount', 'quantity'], rows);

    expect(pickCategoryField(fields)).toBe('region');
    expect(pickValueField(fields, 'region')).toBe('amount');
  });

  it('图表默认 dataRef 带分类字段、数值字段和 groupBy', () => {
    const fields = buildDashboardFields(['region', 'amount'], rows);
    const ref = createDefaultDataRef('echarts', 'bar', fields);

    expect(ref).toMatchObject({
      categoryField: 'region',
      valueField: 'amount',
      limit: 100,
      groupBy: true,
    });
  });

  it('表格不设置分类/数值字段，KPI 只设置数值字段', () => {
    const fields = buildDashboardFields(['region', 'amount'], rows);

    expect(createDefaultDataRef('table', undefined, fields)).toEqual({ limit: 100 });
    expect(createDefaultDataRef('kpi', undefined, fields)).toEqual({ valueField: 'amount' });
  });

  it('只有图表类型需要按分类字段 group by', () => {
    expect(shouldGroupByCategory('echarts', 'bar')).toBe(true);
    expect(shouldGroupByCategory('echarts', 'line')).toBe(true);
    expect(shouldGroupByCategory('echarts', 'area')).toBe(true);
    expect(shouldGroupByCategory('echarts', 'pie')).toBe(true);
    expect(shouldGroupByCategory('table', undefined)).toBe(false);
    expect(shouldGroupByCategory('kpi', undefined)).toBe(false);
  });

  it('数据到达后只补全空字段，并保持用户选择', () => {
    const item = widget({
      dataRef: { categoryField: '自定义分类', limit: 100, groupBy: true },
    });
    const fields = buildDashboardFields(['region', 'amount'], rows);

    ensureDefaultFields(item, fields);
    expect(item.dataRef?.categoryField).toBe('自定义分类');
    expect(item.dataRef?.valueField).toBe('amount');
    expect(hasCompleteDefaultFields(item)).toBe(true);

    const empty = widget({ dataRef: { categoryField: '', valueField: '', limit: 100 } });
    ensureDefaultFields(empty, fields);
    expect(empty.dataRef).toMatchObject({ categoryField: 'region', valueField: 'amount', groupBy: true });
  });

  it('从 echarts option 解析图表类型', () => {
    expect(resolveChartType({ series: [{ type: 'pie' }] })).toBe('pie');
    expect(resolveChartType({ series: [{ type: 'line', areaStyle: {} }] })).toBe('area');
    expect(resolveChartType({ series: [{ type: 'line' }] })).toBe('line');
    expect(resolveChartType(undefined)).toBe('bar');
  });
});
