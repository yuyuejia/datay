import { mount } from '@vue/test-utils';

import TitleWidget from './TitleWidget.vue';
import type { DashboardWidget } from './types';

const widget = (props: DashboardWidget['props'] = {}, style: DashboardWidget['style'] = {}): DashboardWidget => ({
  id: 'title1',
  type: 'title',
  layout: { x: 0, y: 0, w: 24, h: 2 },
  props,
  style,
});

describe('TitleWidget', () => {
  it('渲染主标题、副标题、对齐与边框', () => {
    const wrapper = mount(TitleWidget, {
      props: {
        widget: widget(
          { text: '领导驾驶舱', subText: '学生管理主题', align: 'center', frame: 'tech-frame' },
          { title: { fontSize: 32, color: '#cfeaff', letterSpacing: 4 } },
        ),
      },
    });

    expect(wrapper.find('.dashboard-title-main').text()).toBe('领导驾驶舱');
    expect(wrapper.find('.dashboard-title-sub').text()).toBe('学生管理主题');
    expect(wrapper.find('.dashboard-title-widget').classes()).toContain('align-center');
    expect(wrapper.find('.dashboard-title-widget').classes()).toContain('is-frame-tech');
    const style = wrapper.find('.dashboard-title-main').attributes('style');
    expect(style).toContain('font-size: 32px');
    expect(style).toContain('letter-spacing: 4px');
  });

  it('无标题内容时回落到默认文案，并支持自定义图标', () => {
    const wrapper = mount(TitleWidget, {
      props: {
        widget: widget({}, { title: { icon: 'chart-line' } }),
      },
      global: { stubs: { 'font-awesome-icon': { template: '<i class="fa-stub" />' } } },
    });

    expect(wrapper.find('.dashboard-title-main').text()).toContain('领导驾驶舱');
    expect(wrapper.find('.dashboard-title-icon').exists()).toBe(true);
  });

  it('左对齐与括号边框', () => {
    const wrapper = mount(TitleWidget, {
      props: { widget: widget({ text: '标题', align: 'left', frame: 'bracket' }) },
    });

    expect(wrapper.find('.dashboard-title-widget').classes()).toEqual(expect.arrayContaining(['align-left', 'is-frame-bracket']));
  });
});
