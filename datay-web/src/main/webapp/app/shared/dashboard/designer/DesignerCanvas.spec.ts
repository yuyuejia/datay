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
