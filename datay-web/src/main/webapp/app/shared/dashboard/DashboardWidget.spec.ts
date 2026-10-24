import { shallowMount } from '@vue/test-utils';

import DashboardWidget from './DashboardWidget.vue';
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
