import { shallowMount } from '@vue/test-utils';
import { nextTick } from 'vue';

import DashboardDrillDialog from './DashboardDrillDialog.vue';
import DashboardWidget from './DashboardWidget.vue';
import EChart from './EChart.vue';
import type { DashboardWidget as Widget } from './types';

const baseWidget: Widget = {
  id: 'html1',
  type: 'html',
  layout: { x: 0, y: 0, w: 12, h: 4 },
  html: '<p>说明内容</p>',
};

describe('DashboardWidget header', () => {
  it('hides the header for a text widget without a title', () => {
    const wrapper = shallowMount(DashboardWidget, { props: { widget: baseWidget } });

    expect(wrapper.find('.dashboard-widget-header').exists()).toBe(false);
    expect(wrapper.find('.dashboard-widget-card').classes()).toContain('is-headerless');
  });

  it('shows the header when a text widget has a title', () => {
    const wrapper = shallowMount(DashboardWidget, { props: { widget: { ...baseWidget, title: '说明文本' } } });

    expect(wrapper.find('.dashboard-widget-header').exists()).toBe(true);
    expect(wrapper.find('.dashboard-widget-title').text()).toBe('说明文本');
    expect(wrapper.find('.dashboard-widget-card').classes()).not.toContain('is-headerless');
  });

  it('keeps the header for non-text widgets without a title', () => {
    const wrapper = shallowMount(DashboardWidget, { props: { widget: { ...baseWidget, type: 'kpi' } } });

    expect(wrapper.find('.dashboard-widget-header').exists()).toBe(true);
  });
});

describe('DashboardWidget drill-down', () => {
  const chartWidget: Widget = {
    id: 'chart1',
    title: '区域销售',
    type: 'echarts',
    layout: { x: 0, y: 0, w: 12, h: 8 },
    datasetId: 'ds1',
    chart: { type: 'bar' },
    encode: {
      dimensions: [{ field: 'region', role: 'x' }],
      measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
      groupBy: true,
    },
  };

  it('点击图表数据时解析条件并打开明细弹窗', async () => {
    const wrapper = shallowMount(DashboardWidget, {
      props: {
        widget: chartWidget,
        result: { columns: ['region', 'amount'], rows: [{ region: '华东', amount: 10 }] },
        drillLoader: vi.fn(),
      },
    });

    wrapper.findComponent(EChart).vm.$emit('click', { seriesIndex: 0, dataIndex: 0 });
    await nextTick();

    const dialog = wrapper.findComponent(DashboardDrillDialog);
    expect(dialog.props('modelValue')).toBe(true);
    expect(dialog.props('dimensions')).toEqual([{ field: 'region', values: ['华东'] }]);
  });

  it('没有下钻加载器时不打开弹窗', async () => {
    const wrapper = shallowMount(DashboardWidget, {
      props: {
        widget: chartWidget,
        result: { columns: ['region', 'amount'], rows: [{ region: '华东', amount: 10 }] },
      },
    });

    wrapper.findComponent(EChart).vm.$emit('click', { seriesIndex: 0, dataIndex: 0 });
    await nextTick();

    expect(wrapper.findComponent(DashboardDrillDialog).exists()).toBe(false);
  });
});
