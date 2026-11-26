import { shallowMount } from '@vue/test-utils';
import { nextTick } from 'vue';

import ClockWidget from './ClockWidget.vue';
import DashboardDrillDialog from './DashboardDrillDialog.vue';
import DashboardWidget from './DashboardWidget.vue';
import EChart from './EChart.vue';
import TitleWidget from './TitleWidget.vue';
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

describe('DashboardWidget skin', () => {
  const styledWidget: Widget = {
    ...baseWidget,
    type: 'kpi',
    title: '学生总数',
    style: {
      card: {
        preset: 'tech-corner',
        fill: { type: 'color', color: '#0b1f33' },
        border: { width: 1, color: '#2fa8ff', radius: 4 },
        shadow: { x: 0, y: 0, blur: 12, color: 'rgba(0, 180, 255, 0.25)' },
      },
      title: { show: true, icon: 'chart-line', fontSize: 16, decoration: 'gradient-underline', align: 'center' },
      body: { padding: 4 },
    },
  };

  it('applies the card preset, title decoration and body padding', () => {
    const wrapper = shallowMount(DashboardWidget, {
      props: { widget: styledWidget },
      global: { stubs: { 'font-awesome-icon': { template: '<i class="fa-stub" />' } } },
    });

    const card = wrapper.find('.dashboard-widget-card');
    expect(card.classes()).toContain('dash-card--tech-corner');
    expect(card.attributes('style')).toContain('background');
    expect(wrapper.find('.dashboard-widget-header').classes()).toContain('is-deco-gradient-underline');
    expect(wrapper.find('.dashboard-widget-title-icon').exists()).toBe(true);
    expect(wrapper.find('.dashboard-widget-body').attributes('style')).toContain('padding: 4px');
  });

  it('hides the header when style.title.show is false', () => {
    const wrapper = shallowMount(DashboardWidget, {
      props: { widget: { ...styledWidget, style: { ...styledWidget.style, title: { show: false } } } },
    });

    expect(wrapper.find('.dashboard-widget-header').exists()).toBe(false);
    expect(wrapper.find('.dashboard-widget-card').classes()).toContain('is-headerless');
  });

  it('forces the header back on for a text widget', () => {
    const wrapper = shallowMount(DashboardWidget, {
      props: { widget: { ...baseWidget, style: { title: { show: true } } } },
    });

    expect(wrapper.find('.dashboard-widget-header').exists()).toBe(true);
  });

  it('把 style.chart 预设应用到最终 ECharts option', () => {
    const chartWidget: Widget = {
      id: 'chart-style',
      title: '样式图表',
      type: 'echarts',
      layout: { x: 0, y: 0, w: 12, h: 8 },
      datasetId: 'ds1',
      chart: { type: 'bar' },
      style: { chart: { preset: 'tech' } },
      encode: {
        dimensions: [{ field: 'region', role: 'x' }],
        measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
        groupBy: true,
      },
    };

    const wrapper = shallowMount(DashboardWidget, {
      props: {
        widget: chartWidget,
        theme: 'dark',
        palette: ['#2fa8ff'],
        result: { columns: ['region', 'amount'], rows: [{ region: '华东', amount: 10 }] },
      },
    });

    const option = wrapper.findComponent(EChart).props('option') as any;
    expect(option.series[0].itemStyle.color).toMatchObject({ type: 'linear' });
    expect(option.series[0].itemStyle.color.colorStops[0].color).toBe('#2fa8ff');
  });

  it('标题栏与日期时间组件不渲染通用 header', () => {
    const titleWrapper = shallowMount(DashboardWidget, {
      props: {
        widget: {
          id: 'title1',
          type: 'title',
          layout: { x: 0, y: 0, w: 24, h: 2 },
          props: { text: '领导驾驶舱', frame: 'tech-frame' },
        },
      },
    });
    expect(titleWrapper.findComponent(TitleWidget).exists()).toBe(true);
    expect(titleWrapper.find('.dashboard-widget-header').exists()).toBe(false);
    expect(titleWrapper.find('.dashboard-widget-card').classes()).toContain('is-plain');

    const clockWrapper = shallowMount(DashboardWidget, {
      props: {
        widget: { id: 'clock1', type: 'clock', layout: { x: 0, y: 0, w: 8, h: 1 }, props: { format: 'date-week' } },
      },
    });
    expect(clockWrapper.findComponent(ClockWidget).exists()).toBe(true);
    expect(clockWrapper.find('.dashboard-widget-header').exists()).toBe(false);
  });
});
