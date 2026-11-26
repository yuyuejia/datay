import { mount } from '@vue/test-utils';
import { vi } from 'vitest';

import DashboardCanvas from './DashboardCanvas.vue';
import type { DashboardSpec } from './types';

vi.mock('./DashboardWidget.vue', () => ({
  default: {
    name: 'DashboardWidget',
    template: '<div class="dashboard-widget-stub"><slot name="container" /></div>',
  },
}));

const baseWidget = {
  id: 'w1',
  type: 'echarts' as const,
  title: '像素组件',
  layout: { x: 120, y: 60, w: 300, h: 200 },
  chart: { type: 'bar' as const },
};

describe('DashboardCanvas layout', () => {
  it('renders absolute items and z-index on a pixel canvas', () => {
    const spec: DashboardSpec = {
      version: 2,
      layout: {
        mode: 'screen',
        columns: 24,
        rowHeight: 40,
        gap: 12,
        screen: { width: 1920, height: 1080, canvas: { unit: 'px', padding: 0, gap: 8 } },
      },
      datasets: [],
      widgets: [{ ...baseWidget, zIndex: 10 }],
      filters: [],
    };

    const wrapper = mount(DashboardCanvas, { props: { spec } });
    const item = wrapper.find('.dashboard-canvas-item');

    expect(wrapper.find('.dashboard-canvas-grid').classes()).toContain('is-pixel');
    expect(wrapper.find('.dashboard-canvas').attributes('style')).toContain('padding: 0px');
    expect(item.attributes('style')).toContain('position: absolute');
    expect(item.attributes('style')).toContain('left: 120px');
    expect(item.attributes('style')).toContain('top: 60px');
    expect(item.attributes('style')).toContain('width: 300px');
    expect(item.attributes('style')).toContain('z-index: 10');
  });

  it('uses the configured padding and gap in grid mode', () => {
    const spec: DashboardSpec = {
      version: 2,
      layout: {
        mode: 'screen',
        columns: 24,
        rowHeight: 40,
        gap: 12,
        screen: { width: 1920, height: 1080, canvas: { unit: 'grid', padding: 4, gap: 6 } },
      },
      datasets: [],
      widgets: [baseWidget],
      filters: [],
    };

    const wrapper = mount(DashboardCanvas, { props: { spec } });
    const grid = wrapper.find('.dashboard-canvas-grid');

    expect(wrapper.find('.dashboard-canvas').attributes('style')).toContain('padding: 4px');
    expect(grid.attributes('style')).toContain('gap: 6px');
    expect(wrapper.find('.dashboard-canvas-item').attributes('style')).toContain('grid-column');
  });

  it('falls back to the grid when a standard dashboard stores px settings', () => {
    const spec: DashboardSpec = {
      version: 2,
      layout: {
        mode: 'standard',
        columns: 24,
        rowHeight: 40,
        gap: 12,
        screen: { width: 1920, height: 1080, canvas: { unit: 'px', padding: 0, gap: 8 } },
      },
      datasets: [],
      widgets: [baseWidget],
      filters: [],
    };

    const wrapper = mount(DashboardCanvas, { props: { spec } });

    expect(wrapper.find('.dashboard-canvas-grid').classes()).not.toContain('is-pixel');
    expect(wrapper.find('.dashboard-canvas-item').attributes('style')).toContain('grid-column');
  });

  it('applies theme tokens as CSS variables on the canvas', () => {
    const spec: DashboardSpec = {
      version: 2,
      layout: {
        mode: 'screen',
        columns: 24,
        rowHeight: 40,
        gap: 12,
        screen: {
          width: 1920,
          height: 1080,
          theme: 'dark',
          themePreset: 'tech-blue',
          tokens: { primary: '#ff0000', surface: 'rgba(9, 26, 48, 0.55)' },
        },
      },
      datasets: [],
      widgets: [baseWidget],
      filters: [],
    };

    const wrapper = mount(DashboardCanvas, { props: { spec } });
    const style = wrapper.find('.dashboard-canvas').attributes('style');

    expect(style).toContain('--dashboard-primary: #ff0000');
    expect(style).toContain('--dashboard-surface: rgba(9, 26, 48, 0.55)');
  });

  it('容器子组件在容器内部按 24 栅格渲染', () => {
    const spec: DashboardSpec = {
      version: 2,
      layout: {
        mode: 'screen',
        columns: 24,
        rowHeight: 40,
        gap: 12,
        screen: { width: 1920, height: 1080, canvas: { unit: 'px', padding: 0, gap: 8 } },
      },
      datasets: [],
      widgets: [
        {
          id: 'panel',
          type: 'container',
          title: '面板',
          layout: { x: 20, y: 20, w: 400, h: 300 },
        },
        {
          id: 'child',
          type: 'kpi',
          parentId: 'panel',
          layout: { x: 2, y: 1, w: 20, h: 4 },
        },
      ],
      filters: [],
    };

    const wrapper = mount(DashboardCanvas, { props: { spec } });
    const items = wrapper.findAll('.dashboard-canvas-item');
    expect(items).toHaveLength(1);
    expect(items[0].attributes('data-widget-id')).toBe('panel');

    const child = wrapper.find('.dashboard-container-grid > *');
    expect(child.exists()).toBe(true);
    expect(child.attributes('style')).toContain('grid-column: 3 / span 20');
    expect(child.attributes('style')).toContain('grid-row: 2 / span 4');
  });
});
