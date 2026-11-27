import { flushPromises, mount } from '@vue/test-utils';

import DashboardDrillDialog from './DashboardDrillDialog.vue';
import type { DashboardWidget } from './types';

const AppModalStub = {
  name: 'AppModal',
  props: ['modelValue', 'size'],
  emits: ['update:modelValue'],
  template: '<div class="app-modal-stub"><slot /><slot name="modal-footer" /></div>',
};

const ButtonStub = {
  name: 'ElButton',
  props: ['disabled', 'size', 'type'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
};

const AlertStub = {
  name: 'ElAlert',
  props: ['title'],
  template: '<div class="alert-stub">{{ title }}</div>',
};

const widget: DashboardWidget = {
  id: 'w1',
  title: '区域销售',
  type: 'echarts',
  layout: { x: 0, y: 0, w: 12, h: 8 },
  datasetId: 'ds1',
  chart: { type: 'bar' },
  encode: {
    dimensions: [{ field: 'region', role: 'x' }],
    measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
    sortField: 'amount',
    sortOrder: 'desc',
  },
};

const mountDialog = (loader: (request: any) => Promise<any>, pageSize = 50) =>
  mount(DashboardDrillDialog, {
    props: {
      modelValue: false,
      widget,
      dimensions: [{ field: 'region', values: ['华东'] }],
      loader,
      pageSize,
    },
    global: {
      stubs: {
        AppModal: AppModalStub,
        ElButton: ButtonStub,
        ElAlert: AlertStub,
        ElTag: true,
        ElEmpty: true,
        ElTable: true,
        ElTableColumn: true,
      },
    },
  });

describe('DashboardDrillDialog', () => {
  it('打开时使用点击条件和组件排序加载第一页', async () => {
    const loader = vi.fn().mockResolvedValue({ columns: ['region'], rows: [{ region: '华东' }] });
    const wrapper = mountDialog(loader);

    await wrapper.setProps({ modelValue: true });
    await flushPromises();

    expect(loader).toHaveBeenCalledWith({
      widgetId: 'w1',
      datasetId: 'ds1',
      dimensions: [{ field: 'region', values: ['华东'] }],
      page: 1,
      pageSize: 50,
      sortField: 'amount',
      sortOrder: 'desc',
    });
  });

  it('首次挂载即打开时也会加载', async () => {
    const loader = vi.fn().mockResolvedValue({ columns: ['region'], rows: [{ region: '华东' }] });
    const wrapper = mount(DashboardDrillDialog, {
      props: {
        modelValue: true,
        widget,
        dimensions: [{ field: 'region', values: ['华东'] }],
        loader,
      },
      global: {
        stubs: {
          AppModal: AppModalStub,
          ElButton: ButtonStub,
          ElAlert: AlertStub,
          ElTag: true,
          ElEmpty: true,
          ElTable: true,
          ElTableColumn: true,
        },
      },
    });

    await flushPromises();

    expect(loader).toHaveBeenCalledTimes(1);
  });

  it('下一页按服务端 offset 对应的页码重新加载', async () => {
    const loader = vi.fn().mockResolvedValue({ columns: ['region'], rows: [{ region: '华东' }] });
    const wrapper = mountDialog(loader, 1);

    await wrapper.setProps({ modelValue: true });
    await flushPromises();

    const next = wrapper.findAll('button').find(button => button.text() === '下一页');
    expect(next).toBeTruthy();
    await next!.trigger('click');
    await flushPromises();

    expect(loader).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, pageSize: 1 }));
  });

  it('深色主题下弹窗使用暗色主题变量', async () => {
    const loader = vi.fn().mockResolvedValue({ columns: ['region'], rows: [{ region: '华东' }] });
    const wrapper = mount(DashboardDrillDialog, {
      props: {
        modelValue: true,
        widget,
        dimensions: [{ field: 'region', values: ['华东'] }],
        loader,
        theme: 'dark',
      },
      global: {
        stubs: {
          AppModal: AppModalStub,
          ElButton: ButtonStub,
          ElAlert: AlertStub,
          ElTag: true,
          ElEmpty: true,
          ElTable: true,
          ElTableColumn: true,
        },
      },
    });

    await flushPromises();

    expect(wrapper.find('.app-modal-stub').classes()).toContain('dashboard-theme-dark');
  });

  it('加载失败时展示服务端错误', async () => {
    const loader = vi.fn().mockRejectedValue({
      response: {
        data: {
          title: 'Bad Request',
          message: 'error.drillQueryError',
          detail: '400 BAD_REQUEST, ProblemDetailWithCause[...]',
          errorMessage: '数据集字段名不合法：store-name',
        },
      },
    });
    const wrapper = mountDialog(loader);

    await wrapper.setProps({ modelValue: true });
    await flushPromises();

    expect(wrapper.find('.alert-stub').text()).toContain('数据集字段名不合法：store-name');
  });
});
