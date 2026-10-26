import { mount } from '@vue/test-utils';

import KpiWidget from './KpiWidget.vue';
import type { DashboardWidget, DatasetResult } from './types';

const result: DatasetResult = {
  columns: ['amount'],
  rows: [{ amount: 120 }, { amount: 30 }],
};

const widget = (overrides: Partial<DashboardWidget> = {}): DashboardWidget => ({
  id: 'kpi1',
  title: '总销售额',
  type: 'kpi',
  layout: { x: 0, y: 0, w: 6, h: 4 },
  encode: { measures: [{ field: 'amount', agg: 'sum', role: 'value' }] },
  ...overrides,
});

describe('KpiWidget', () => {
  it('展示聚合后的 KPI 数值', () => {
    const wrapper = mount(KpiWidget, { props: { widget: widget(), result } });

    expect(wrapper.find('.kpi-widget-value').text()).toBe('150');
  });

  it('按 format 配置展示数值前缀、精度、单位和后缀', () => {
    const wrapper = mount(KpiWidget, {
      props: {
        widget: widget({
          format: { prefix: '$', precision: 2, unit: '万', suffix: '元' },
        }),
        result,
      },
    });

    expect(wrapper.find('.kpi-widget-value').text()).toBe('$150.00万元');
  });

  it('无数据时展示占位符', () => {
    const wrapper = mount(KpiWidget, { props: { widget: widget(), result: { columns: [], rows: [] } } });

    expect(wrapper.find('.kpi-widget-value').text()).toBe('--');
  });

  it('应用条件格式规则', () => {
    const wrapper = mount(KpiWidget, {
      props: {
        widget: widget({
          formatRules: [{ field: 'amount', operator: 'gt', value: 100, color: '#f56c6c', bold: true }],
        }),
        result,
      },
    });

    const style = wrapper.find('.kpi-widget-value').attributes('style');
    expect(style).toContain('color: #f56c6c');
    expect(style).toContain('font-weight: 600');
  });
});
