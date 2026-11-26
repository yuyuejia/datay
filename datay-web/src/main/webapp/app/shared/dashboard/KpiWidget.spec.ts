import { mount } from '@vue/test-utils';

import { vi } from 'vitest';

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

  it('按 style.kpi 展示标签、图标、独立单位与数字字体', () => {
    const wrapper = mount(KpiWidget, {
      props: {
        widget: widget({
          style: {
            kpi: {
              label: '学生总数',
              icon: 'users',
              layout: 'label-top',
              value: { fontSize: 56, color: '#eaf6ff', fontFamily: 'number-digital', bold: true, letterSpacing: 2 },
              unit: { text: '人', fontSize: 16, color: '#8fb6d6' },
            },
          },
        }),
        result,
      },
      global: { stubs: { 'font-awesome-icon': { template: '<i class="fa-stub" />' } } },
    });

    expect(wrapper.find('.kpi-widget-label-text').text()).toBe('学生总数');
    expect(wrapper.find('.kpi-widget-icon').exists()).toBe(true);
    expect(wrapper.find('.kpi-widget-value').text()).toBe('150');
    expect(wrapper.find('.kpi-widget-unit').text()).toBe('人');
    const style = wrapper.find('.kpi-widget-value').attributes('style');
    expect(style).toContain('font-size: 56px');
    expect(style).toContain('font-family');
    expect(style).toContain('letter-spacing: 2px');
  });

  it('仅数值布局隐藏标签，并按 decimals 格式化', () => {
    const wrapper = mount(KpiWidget, {
      props: {
        widget: widget({ style: { kpi: { layout: 'value-only', decimals: 1 } } }),
        result,
      },
    });

    expect(wrapper.find('.kpi-widget-label').exists()).toBe(false);
    expect(wrapper.find('.kpi-widget-value').text()).toBe('150.0');
  });

  it('标签在左时使用横向布局', () => {
    const wrapper = mount(KpiWidget, {
      props: { widget: widget({ style: { kpi: { layout: 'label-left' } } }), result },
    });

    expect(wrapper.find('.kpi-widget').classes()).toContain('is-label-left');
  });

  it('数字动画关闭时直接显示目标值', () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(KpiWidget, {
        props: { widget: widget({ style: { kpi: { animate: false } } }), result },
      });
      expect(wrapper.find('.kpi-widget-value').text()).toBe('150');
    } finally {
      vi.useRealTimers();
    }
  });
});
