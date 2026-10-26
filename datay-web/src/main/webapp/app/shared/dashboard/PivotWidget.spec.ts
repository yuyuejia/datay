import { mount } from '@vue/test-utils';

import PivotWidget from './PivotWidget.vue';
import type { DashboardWidget, DatasetResult } from './types';

const result: DatasetResult = {
  columns: ['region', 'channel', 'amount'],
  columnLabels: { region: '区域', channel: '渠道', amount: '销售额' },
  rows: [
    { region: '华东', channel: '官网', amount: 120.5 },
    { region: '华东', channel: 'APP', amount: 60 },
    { region: '华南', channel: 'APP', amount: 80 },
    { region: '华北', channel: '门店', amount: 45.25 },
  ],
};

const widget = (overrides: Partial<DashboardWidget> = {}): DashboardWidget => ({
  id: 'pivot1',
  title: '区域渠道透视',
  type: 'pivot',
  layout: { x: 0, y: 0, w: 12, h: 8 },
  encode: {
    dimensions: [
      { field: 'region', role: 'row' },
      { field: 'channel', role: 'column' },
    ],
    measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
    limit: 100,
  },
  ...overrides,
});

describe('PivotWidget', () => {
  it('按行维度和列维度渲染交叉表', () => {
    const wrapper = mount(PivotWidget, { props: { widget: widget(), result } });

    const headers = wrapper.findAll('thead th').map(item => item.text());
    expect(headers).toEqual(['区域', '官网', 'APP', '门店']);

    const rows = wrapper.findAll('tbody tr');
    expect(rows).toHaveLength(3);
    expect(rows[0].findAll('th').at(0)?.text()).toBe('华东');
    expect(rows[0].findAll('td').map(item => item.text())).toEqual(['120.5', '60', '—']);
    expect(rows[1].findAll('td').map(item => item.text())).toEqual(['—', '80', '—']);
  });

  it('没有列维度时按度量展示普通汇总表', () => {
    const simple = widget({
      encode: {
        dimensions: [{ field: 'region', role: 'row' }],
        measures: [{ field: 'amount', agg: 'sum', role: 'value', name: '销售额' }],
        limit: 100,
      },
    });
    const wrapper = mount(PivotWidget, { props: { widget: simple, result } });

    expect(wrapper.findAll('thead th').map(item => item.text())).toEqual(['区域', '销售额']);
    const rows = wrapper.findAll('tbody tr');
    expect(rows[0].findAll('td').map(item => item.text())).toEqual(['180.5']);
    expect(rows[1].findAll('td').map(item => item.text())).toEqual(['80']);
  });

  it('按条件格式规则给单元格着色', () => {
    const formatted = widget({
      formatRules: [{ field: 'amount', operator: 'gt', value: 100, background: '#fef0f0', color: '#f56c6c', bold: true }],
    });
    const wrapper = mount(PivotWidget, { props: { widget: formatted, result } });

    const firstCell = wrapper.findAll('tbody tr')[0].findAll('td')[0];
    expect(firstCell.attributes('style')).toContain('background-color: #fef0f0');
    expect(firstCell.attributes('style')).toContain('color: #f56c6c');
    expect(firstCell.attributes('style')).toContain('font-weight: 600');
  });

  it('多度量时列标题带度量名称', () => {
    const multi = widget({
      encode: {
        dimensions: [
          { field: 'region', role: 'row' },
          { field: 'channel', role: 'column' },
        ],
        measures: [
          { field: 'amount', agg: 'sum', role: 'value', name: '销售额' },
          { field: 'amount', agg: 'count', role: 'value', name: '订单数' },
        ],
        limit: 100,
      },
    });
    const wrapper = mount(PivotWidget, { props: { widget: multi, result } });

    expect(wrapper.findAll('thead th').map(item => item.text())).toEqual([
      '区域',
      '官网 · 销售额',
      '官网 · 订单数',
      'APP · 销售额',
      'APP · 订单数',
      '门店 · 销售额',
      '门店 · 订单数',
    ]);
  });

  it('服务端已聚合的 count 结果不会再次计数', () => {
    const aggregated = widget({
      encode: {
        dimensions: [
          { field: 'region', role: 'row' },
          { field: 'channel', role: 'column' },
        ],
        measures: [{ field: 'amount', agg: 'count', role: 'value' }],
        limit: 100,
      },
    });
    const wrapper = mount(PivotWidget, {
      props: {
        widget: aggregated,
        result: {
          columns: ['region', 'channel', 'amount'],
          rows: [
            { region: '华东', channel: '官网', amount: 1 },
            { region: '华东', channel: 'APP', amount: 2 },
          ],
          aggregated: true,
        },
      },
    });

    expect(wrapper.findAll('tbody tr')[0].findAll('td')[1].text()).toBe('2');
  });

  it('无数据时展示空状态', () => {
    const wrapper = mount(PivotWidget, { props: { widget: widget(), result: { columns: [], rows: [] } } });

    expect(wrapper.find('.pivot-widget-empty').exists()).toBe(true);
  });
});
