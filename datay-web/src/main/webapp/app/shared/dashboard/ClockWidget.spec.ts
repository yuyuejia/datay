import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ClockWidget from './ClockWidget.vue';
import type { ClockFormat, DashboardWidget } from './types';

const widget = (format: ClockFormat): DashboardWidget => ({
  id: 'clock1',
  type: 'clock',
  layout: { x: 0, y: 0, w: 8, h: 1 },
  props: { format },
});

describe('ClockWidget', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-26T09:30:05'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('按 date-week 渲染日期与星期', () => {
    const wrapper = mount(ClockWidget, { props: { widget: widget('date-week') } });
    expect(wrapper.find('.dashboard-clock').text()).toBe('2026-11-26 星期四');
    wrapper.unmount();
  });

  it('按 time 渲染并每秒刷新', async () => {
    const wrapper = mount(ClockWidget, { props: { widget: widget('time') } });
    expect(wrapper.find('.dashboard-clock').text()).toBe('09:30:05');

    vi.advanceTimersByTime(1000);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.dashboard-clock').text()).toBe('09:30:06');
    wrapper.unmount();
  });

  it('应用 style.title 的字体样式', () => {
    const wrapper = mount(ClockWidget, {
      props: {
        widget: {
          ...widget('date'),
          style: { title: { color: '#7fe3ff', fontSize: 18, letterSpacing: 2 } },
        } as DashboardWidget,
      },
    });
    const style = wrapper.find('.dashboard-clock').attributes('style');
    expect(style).toContain('color: #7fe3ff');
    expect(style).toContain('font-size: 18px');
    wrapper.unmount();
  });
});
