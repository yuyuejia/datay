import { mount } from '@vue/test-utils';
import { vi } from 'vitest';

import type { DashboardSpec } from '../types';
import DesignerCanvas from './DesignerCanvas.vue';

vi.mock('../DashboardWidget.vue', () => ({
  default: {
    name: 'DashboardWidget',
    template: '<div class="dashboard-widget-stub" />',
  },
}));

const spec: DashboardSpec = {
  version: 2,
  title: 'test',
  layout: { mode: 'standard', columns: 24, rowHeight: 40, gap: 12, screen: { width: 1920, height: 1080 } },
  datasets: [],
  widgets: [],
  filters: [],
};

function dragEvent(type: string, clientX: number, clientY: number): DragEvent {
  const event = new Event(type, { bubbles: true, cancelable: true });
  Object.defineProperties(event, {
    clientX: { value: clientX },
    clientY: { value: clientY },
    dataTransfer: { value: { dropEffect: '', getData: () => '' } },
  });
  return event as unknown as DragEvent;
}

function mountCanvas(scale = 1) {
  const wrapper = mount(DesignerCanvas, {
    props: {
      spec,
      scale,
      dragItem: { type: 'echarts', chartType: 'bar', w: 6, h: 4 },
    },
    global: {
      stubs: {
        'el-empty': true,
        'el-button': true,
        'font-awesome-icon': true,
      },
    },
  });

  const canvas = wrapper.find('.designer-canvas').element as HTMLElement;
  Object.defineProperty(canvas, 'clientWidth', { configurable: true, value: 1232 });
  canvas.getBoundingClientRect = () =>
    ({
      left: 0,
      top: 0,
      width: 1232,
      height: 600,
      right: 1232,
      bottom: 600,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    }) as DOMRect;
  return { wrapper, canvas };
}

describe('DesignerCanvas drop', () => {
  it('emits the widget payload and grid layout for a drop point', async () => {
    const { wrapper, canvas } = mountCanvas();

    // 内容宽 1200，单元格 38.5；x = 16 + 3 * (38.5 + 12)，y = 16 + 2 * (40 + 12)
    canvas.dispatchEvent(dragEvent('dragover', 168, 120));
    await wrapper.vm.$nextTick();

    expect(wrapper.find('.designer-drop-preview').exists()).toBe(true);
    expect(wrapper.find('.designer-drop-preview').attributes('style')).toContain('left: 167.5px');

    canvas.dispatchEvent(dragEvent('drop', 168, 120));
    await wrapper.vm.$nextTick();

    expect(wrapper.emitted('add-widget')?.[0]).toEqual(['echarts', 'bar', { x: 3, y: 2, w: 6, h: 4 }]);
    expect(wrapper.find('.designer-drop-preview').exists()).toBe(false);
  });

  it('restores logical coordinates when the canvas is scaled', async () => {
    const { wrapper, canvas } = mountCanvas(0.5);

    canvas.dispatchEvent(dragEvent('drop', 84, 60));
    await wrapper.vm.$nextTick();

    expect(wrapper.emitted('add-widget')?.[0]).toEqual(['echarts', 'bar', { x: 3, y: 2, w: 6, h: 4 }]);
  });
});

const pixelSpec: DashboardSpec = {
  version: 2,
  title: 'pixel',
  layout: {
    mode: 'screen',
    columns: 24,
    rowHeight: 40,
    gap: 12,
    screen: { width: 1200, height: 800, canvas: { unit: 'px', padding: 0, gap: 8 } },
  },
  datasets: [],
  widgets: [
    {
      id: 'w1',
      type: 'echarts',
      title: '像素组件',
      zIndex: 5,
      layout: { x: 120, y: 60, w: 300, h: 200 },
      chart: { type: 'bar' },
      encode: { dimensions: [{ field: 'name', role: 'x' }], measures: [{ field: 'value', agg: 'sum', role: 'y' }] },
    },
  ],
  filters: [],
};

function mountPixelCanvas() {
  const wrapper = mount(DesignerCanvas, {
    props: {
      spec: pixelSpec,
      scale: 1,
      dragItem: { type: 'echarts', chartType: 'bar', w: 6, h: 4 },
    },
    global: {
      stubs: { 'el-empty': true, 'el-button': true, 'font-awesome-icon': true },
    },
  });

  const canvas = wrapper.find('.designer-canvas').element as HTMLElement;
  Object.defineProperty(canvas, 'clientWidth', { configurable: true, value: 1200 });
  canvas.getBoundingClientRect = () =>
    ({
      left: 0,
      top: 0,
      width: 1200,
      height: 800,
      right: 1200,
      bottom: 800,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    }) as DOMRect;
  return { wrapper, canvas };
}

describe('DesignerCanvas pixel mode', () => {
  it('positions widgets absolutely and exposes layer controls', () => {
    const { wrapper } = mountPixelCanvas();
    const item = wrapper.find('.designer-canvas-item');

    expect(item.attributes('style')).toContain('left: 120px');
    expect(item.attributes('style')).toContain('top: 60px');
    expect(item.attributes('style')).toContain('width: 300px');
    expect(item.attributes('style')).toContain('height: 200px');
    expect(item.attributes('style')).toContain('z-index: 5');
    expect(wrapper.html()).toContain('上移一层');
    expect(wrapper.find('.designer-canvas').classes()).toContain('is-pixel');
  });

  it('does not show layer controls in grid mode', () => {
    const { wrapper } = mountCanvas();
    expect(wrapper.html()).not.toContain('上移一层');
  });

  it('converts palette grid sizes to pixel sizes on drop', async () => {
    const { wrapper, canvas } = mountPixelCanvas();

    canvas.dispatchEvent(dragEvent('drop', 137, 91));
    await wrapper.vm.$nextTick();

    // 6 列 -> 1200 * 6 / 24 = 300；4 行 -> 4 * (40 + 8) = 192
    expect(wrapper.emitted('add-widget')?.[0]).toEqual(['echarts', 'bar', { x: 137, y: 91, w: 300, h: 192 }]);
  });
});
