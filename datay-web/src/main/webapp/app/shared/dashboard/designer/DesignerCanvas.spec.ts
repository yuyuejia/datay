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
  layout: { columns: 12, rowHeight: 90, gap: 16 },
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

describe('DesignerCanvas drop', () => {
  it('emits the widget payload and grid layout for a drop point', async () => {
    const wrapper = mount(DesignerCanvas, {
      props: {
        spec,
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

    // x = 12 + 3 * (86 + 16), y = 12 + 2 * (90 + 16)
    canvas.dispatchEvent(dragEvent('dragover', 318, 224));
    await wrapper.vm.$nextTick();

    expect(wrapper.find('.designer-drop-preview').exists()).toBe(true);
    expect(wrapper.find('.designer-drop-preview').attributes('style')).toContain('left: 318px');

    canvas.dispatchEvent(dragEvent('drop', 318, 224));
    await wrapper.vm.$nextTick();

    expect(wrapper.emitted('add-widget')?.[0]).toEqual(['echarts', 'bar', { x: 3, y: 2, w: 6, h: 4 }]);
    expect(wrapper.find('.designer-drop-preview').exists()).toBe(false);
  });
});
