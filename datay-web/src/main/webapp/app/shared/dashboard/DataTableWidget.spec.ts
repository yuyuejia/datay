import { defineComponent } from 'vue';
import { mount } from '@vue/test-utils';

import DataTableWidget from './DataTableWidget.vue';
import type { DashboardWidget, DatasetResult } from './types';

const ElTableStub = defineComponent({
  name: 'ElTableStub',
  props: {
    data: { type: Array, default: () => [] },
    cellStyle: { type: Function, default: undefined },
  },
  template: '<div class="el-table-stub"><slot /></div>',
});

const result: DatasetResult = {
  columns: ['region', 'amount'],
  columnLabels: { region: '区域', amount: '销售额' },
  rows: [
    { region: '华东', amount: 120.5 },
    { region: '华南', amount: 80 },
    { region: '华北', amount: 45.25 },
  ],
};

const widget = (overrides: Partial<DashboardWidget> = {}): DashboardWidget => ({
  id: 'table1',
  title: '明细',
  type: 'table',
  layout: { x: 0, y: 0, w: 12, h: 8 },
  encode: {
    limit: 2,
    columns: [
      { field: 'region', label: '区域' },
      { field: 'amount', label: '销售额', format: { precision: 1 } },
    ],
  },
  ...overrides,
});

const mountTable = (overrides: Partial<DashboardWidget> = {}) =>
  mount(DataTableWidget, {
    props: { widget: widget(overrides), result },
    global: {
      stubs: {
        'el-table': ElTableStub,
        'el-table-column': true,
        'el-pagination': true,
        'el-button': true,
      },
    },
  });

describe('DataTableWidget', () => {
  it('按 encode 排序与截断后传给表格', () => {
    const wrapper = mountTable({
      encode: {
        sortField: 'amount',
        sortOrder: 'desc',
        limit: 2,
        columns: [{ field: 'region' }, { field: 'amount' }],
      },
    });

    const table = wrapper.findComponent(ElTableStub);
    expect(table.props('data')).toEqual([
      { region: '华东', amount: 120.5 },
      { region: '华南', amount: 80 },
    ]);
  });

  it('条件格式通过 cell-style 作用到单元格', () => {
    const wrapper = mountTable({
      encode: { limit: 100, columns: [{ field: 'region' }, { field: 'amount' }] },
      formatRules: [{ field: 'amount', operator: 'gt', value: 100, background: '#fef0f0', color: '#f56c6c', bold: true }],
    });

    const cellStyle = wrapper.findComponent(ElTableStub).props('cellStyle') as (args: any) => Record<string, any>;
    expect(cellStyle({ row: { region: '华东', amount: 120.5 }, column: { property: 'amount' } })).toEqual({
      color: '#f56c6c',
      backgroundColor: '#fef0f0',
      fontWeight: 600,
    });
    expect(cellStyle({ row: { region: '华南', amount: 80 }, column: { property: 'amount' } })).toEqual({});
  });
});
