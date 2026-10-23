import { flushPromises, shallowMount } from '@vue/test-utils';

import { readDashboardPreviewDraft } from '@/shared/dashboard/preview-draft';
import AnalysisDashboardDesigner from './analysis-dashboard-designer.vue';

const { push, createMock, updateMock } = vi.hoisted(() => ({
  push: vi.fn(),
  createMock: vi.fn(),
  updateMock: vi.fn(),
}));

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push }),
}));

vi.mock('@/shared/dataset/dataset.service', () => ({
  default: class {
    all = vi.fn().mockResolvedValue([]);
  },
}));

vi.mock('@/shared/dashboard/dashboard.service', () => ({
  default: class {
    get = vi.fn();
    validateSpec = vi.fn(async (spec: unknown) => spec);
    create = createMock;
    update = updateMock;
  },
}));

vi.mock('@/entities/data-source/ai-assistant.service', () => ({
  default: class {
    listAssistants = vi.fn().mockResolvedValue([]);
    generate = vi.fn();
  },
}));

vi.mock('@/shared/dashboard/useDashboardData', async () => {
  const { ref } = await import('vue');
  return {
    useDashboardData: () => ({
      datasetData: ref({}),
      rawDatasetData: ref({}),
      loadingDatasets: ref({}),
      datasetErrors: ref({}),
      loading: ref(false),
      loadAll: vi.fn().mockResolvedValue(undefined),
    }),
  };
});

type WidgetLayout = { x: number; y: number; w: number; h: number };

type DesignerVm = {
  name: string;
  description: string;
  editingId?: string;
  spec: {
    title?: string;
    widgets: Array<{
      id: string;
      title: string;
      type?: string;
      datasetId?: string;
      dataRef?: any;
      echartsOption?: any;
      html?: string;
      layout: WidgetLayout;
    }>;
    filters: Array<{ id: string; datasetId?: string; field?: string; bindings?: any[] }>;
    datasets: Array<{ id: string; type?: string }>;
  };
  canUndo: boolean;
  canRedo: boolean;
  aiPanelVisible: boolean;
  propertyTab: string;
  customOptionText: string;
  customOptionError: string;
  widgetValueFields: string[];
  addWidget: (type: string, chartType?: string) => void;
  applyCustomOption: () => void;
  resetCustomOption: () => void;
  addFilter: () => void;
  onLayoutChange: (id: string, layout: WidgetLayout) => void;
  applyAiSpec: (spec: any) => Promise<boolean>;
  openPreview: () => void;
  save: () => Promise<void>;
  undo: () => void;
  redo: () => void;
  $nextTick: () => Promise<void>;
};

describe('AnalysisDashboardDesigner history', () => {
  let wrapper: ReturnType<typeof shallowMount>;
  let vm: DesignerVm;

  beforeEach(async () => {
    vi.clearAllMocks();
    localStorage.clear();
    wrapper = shallowMount(AnalysisDashboardDesigner, {
      global: {
        provide: {
          alertService: { showError: vi.fn() },
        },
        stubs: {
          'font-awesome-icon': true,
        },
      },
    });
    vm = wrapper.vm as unknown as DesignerVm;
    await flushPromises();
    await vm.$nextTick();
  });

  afterEach(() => {
    wrapper.unmount();
    vi.restoreAllMocks();
  });

  it('组件库图标创建正确的默认配置', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];

    vm.addWidget('echarts', 'pie');
    vm.addWidget('kpi');
    vm.addWidget('table');
    vm.addWidget('html');
    await vm.$nextTick();

    const [pie, kpi, table, html] = vm.spec.widgets;

    expect(pie.datasetId).toBe('ds1');
    expect(pie.layout.w).toBe(4);
    expect(pie.layout.h).toBe(4);
    expect(pie.dataRef).toEqual({ categoryField: '', valueField: '', limit: 100, groupBy: true });
    expect(pie.echartsOption.series[0].type).toBe('pie');
    expect(pie.echartsOption.xAxis).toBeUndefined();
    expect(pie.echartsOption.yAxis).toBeUndefined();

    expect(kpi.datasetId).toBe('ds1');
    expect(kpi.layout.w).toBe(3);
    expect(kpi.layout.h).toBe(2);
    expect(kpi.dataRef).toEqual({ valueField: '' });

    expect(table.datasetId).toBe('ds1');
    expect(table.layout.w).toBe(6);
    expect(table.layout.h).toBe(4);
    expect(table.dataRef).toEqual({ limit: 100 });

    expect(html.datasetId).toBeUndefined();
    expect(html.layout.w).toBe(6);
    expect(html.layout.h).toBe(3);
    expect(html.html).toContain('HTML');
  });

  it('添加图表时按数据集字段设置默认分类字段和数值字段', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    (vm as any).datasetData = {
      ds1: {
        columns: ['region', 'amount', 'quantity'],
        rows: [
          { region: '华东', amount: 120.5, quantity: 8 },
          { region: '华南', amount: 80, quantity: 5 },
        ],
      },
    };
    await vm.$nextTick();

    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();

    expect(vm.spec.widgets[0].dataRef).toEqual({
      categoryField: 'region',
      valueField: 'amount',
      limit: 100,
      groupBy: true,
    });
  });

  it('数据集结果稍后到达时补全默认分类字段和数值字段', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();

    expect(vm.spec.widgets[0].dataRef).toMatchObject({ categoryField: '', valueField: '' });

    (vm as any).datasetData = {
      ds1: {
        columns: ['region', 'amount'],
        rows: [{ region: '华东', amount: 120.5 }],
      },
    };
    await flushPromises();
    await vm.$nextTick();

    expect(vm.spec.widgets[0].dataRef).toMatchObject({ categoryField: 'region', valueField: 'amount' });
  });

  it('KPI 使用默认数值字段，表格不强加分类字段', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    (vm as any).datasetData = {
      ds1: {
        columns: ['region', 'amount'],
        rows: [{ region: '华东', amount: 120.5 }],
      },
    };
    await vm.$nextTick();

    vm.addWidget('kpi');
    vm.addWidget('table');
    await vm.$nextTick();

    expect(vm.spec.widgets[0].dataRef).toEqual({ valueField: 'amount' });
    expect(vm.spec.widgets[1].dataRef).toEqual({ limit: 100 });
  });

  it('纵轴支持多个数值字段并生成多条系列', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    (vm as any).datasetData = {
      ds1: {
        columns: ['region', 'amount', 'quantity'],
        rows: [{ region: '华东', amount: 120.5, quantity: 8 }],
      },
    };
    await vm.$nextTick();
    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();

    vm.widgetValueFields = ['amount', 'quantity'];
    await vm.$nextTick();

    expect(vm.spec.widgets[0].dataRef).toMatchObject({
      valueField: 'amount',
      series: [
        { name: 'amount', field: 'amount' },
        { name: 'quantity', field: 'quantity' },
      ],
      seriesField: null,
    });

    vm.widgetValueFields = ['amount'];
    await vm.$nextTick();
    expect(vm.spec.widgets[0].dataRef.valueField).toBe('amount');
    expect(vm.spec.widgets[0].dataRef.series).toBeUndefined();
  });

  it('自定义设置可应用和恢复 ECharts 配置', async () => {
    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();

    vm.customOptionText = JSON.stringify({
      tooltip: { trigger: 'axis' },
      series: [{ type: 'bar', name: '自定义' }],
    });
    vm.applyCustomOption();
    await vm.$nextTick();

    expect(vm.customOptionError).toBe('');
    expect(vm.spec.widgets[0].echartsOption).toMatchObject({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category' },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', name: '自定义' }],
    });

    vm.customOptionText = '{ not-json';
    vm.applyCustomOption();
    await vm.$nextTick();
    expect(vm.customOptionError).not.toBe('');
    expect(vm.spec.widgets[0].echartsOption.series[0].name).toBe('自定义');

    vm.resetCustomOption();
    await vm.$nextTick();
    expect(vm.spec.widgets[0].echartsOption.series[0]).toMatchObject({ type: 'bar' });
    expect(vm.spec.widgets[0].echartsOption.series[0].name).toBeUndefined();
  });

  it('柱状图、折线图和面积图使用对应的默认图表类型和尺寸', async () => {
    vm.addWidget('echarts', 'bar');
    vm.addWidget('echarts', 'line');
    vm.addWidget('echarts', 'area');
    await vm.$nextTick();

    const [bar, line, area] = vm.spec.widgets;

    expect([bar, line, area].map(item => [item.layout.w, item.layout.h])).toEqual([
      [6, 4],
      [6, 4],
      [6, 4],
    ]);
    expect(bar.echartsOption.series[0]).toMatchObject({ type: 'bar' });
    expect(line.echartsOption.series[0]).toMatchObject({ type: 'line' });
    expect(area.echartsOption.series[0]).toMatchObject({ type: 'line' });
    expect(area.echartsOption.series[0].areaStyle).toBeDefined();
  });

  it('撤销和重做组件的添加', async () => {
    expect(vm.canUndo).toBe(false);
    expect(vm.canRedo).toBe(false);

    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();
    expect(vm.spec.widgets).toHaveLength(1);
    expect(vm.canUndo).toBe(true);

    vm.undo();
    await vm.$nextTick();
    expect(vm.spec.widgets).toHaveLength(0);
    expect(vm.canUndo).toBe(false);
    expect(vm.canRedo).toBe(true);

    vm.redo();
    await vm.$nextTick();
    expect(vm.spec.widgets).toHaveLength(1);
    expect(vm.spec.widgets[0].title).toBe('柱状图');
    expect(vm.canRedo).toBe(false);
  });

  it('首次编辑后立即撤销可恢复原值', async () => {
    expect(vm.canUndo).toBe(false);

    vm.name = '未保存名称';
    await vm.$nextTick();
    expect(vm.canUndo).toBe(true);

    vm.undo();
    await vm.$nextTick();
    expect(vm.name).toBe('');
    expect(vm.canUndo).toBe(false);
    expect(vm.canRedo).toBe(true);

    vm.redo();
    await vm.$nextTick();
    expect(vm.name).toBe('未保存名称');
  });

  it('撤销和重做组件布局调整', async () => {
    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();
    const widget = vm.spec.widgets[0];
    const original = { ...widget.layout };

    vm.onLayoutChange(widget.id, { ...original, x: original.x + 1 });
    await vm.$nextTick();
    expect(vm.spec.widgets[0].layout.x).toBe(original.x + 1);

    vm.undo();
    await vm.$nextTick();
    expect(vm.spec.widgets[0].layout).toEqual(original);

    vm.redo();
    await vm.$nextTick();
    expect(vm.spec.widgets[0].layout.x).toBe(original.x + 1);
  });

  it('撤销和重做筛选器的新增', async () => {
    vm.addFilter();
    await vm.$nextTick();
    expect(vm.spec.filters).toHaveLength(1);

    vm.undo();
    await vm.$nextTick();
    expect(vm.spec.filters).toHaveLength(0);

    vm.redo();
    await vm.$nextTick();
    expect(vm.spec.filters).toHaveLength(1);
  });

  it('新增筛选器直接配置数据集和字段，不再生成绑定', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    (vm as any).datasetData = {
      ds1: {
        columns: ['region', 'amount'],
        rows: [{ region: '华东', amount: 120 }],
      },
    };
    await vm.$nextTick();

    vm.addFilter();
    await vm.$nextTick();

    expect(vm.spec.filters[0]).toMatchObject({ datasetId: 'ds1', field: 'region' });
    expect(vm.spec.filters[0].bindings).toBeUndefined();
  });

  it('AI 模式复用设计器并默认打开助手', async () => {
    const aiWrapper = shallowMount(AnalysisDashboardDesigner, {
      props: { aiMode: true },
      global: {
        provide: {
          alertService: { showError: vi.fn() },
        },
        stubs: {
          'font-awesome-icon': true,
        },
      },
    });
    await flushPromises();
    await (aiWrapper.vm as unknown as DesignerVm).$nextTick();

    expect((aiWrapper.vm as unknown as DesignerVm).aiPanelVisible).toBe(true);
    expect((aiWrapper.vm as unknown as DesignerVm).spec.widgets).toHaveLength(0);
    aiWrapper.unmount();
  });

  it('AI 生成结果直接应用到手动设计器并支持撤销', async () => {
    await vm.applyAiSpec({
      title: 'AI 销售看板',
      description: 'AI 描述',
      layout: { columns: 12, rowHeight: 90, gap: 16 },
      datasets: [{ id: 'ds1', refId: 'ref1', name: '销售', type: 'SQL' }],
      widgets: [
        {
          id: 'widget1',
          title: 'AI 图表',
          type: 'echarts',
          layout: { x: 0, y: 0, w: 6, h: 4 },
          datasetId: 'ds1',
        },
      ],
      filters: [],
    });
    await vm.$nextTick();

    expect(vm.name).toBe('AI 销售看板');
    expect(vm.description).toBe('AI 描述');
    expect(vm.spec.widgets).toHaveLength(1);
    expect(vm.spec.widgets[0].title).toBe('AI 图表');
    expect(vm.canUndo).toBe(true);

    vm.undo();
    await vm.$nextTick();
    expect(vm.spec.widgets).toHaveLength(0);
  });

  it('保存看板后停留在当前页面，并继续以编辑模式保存', async () => {
    createMock.mockResolvedValue({ id: 'db1', name: '测试看板' });
    updateMock.mockResolvedValue({ id: 'db1', name: '测试看板' });
    vm.name = '测试看板';
    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();

    await vm.save();

    expect(createMock).toHaveBeenCalledTimes(1);
    expect(push).not.toHaveBeenCalled();
    expect(vm.editingId).toBe('db1');

    await vm.save();

    expect(updateMock).toHaveBeenCalledTimes(1);
    expect(updateMock.mock.calls[0][0]).toBe('db1');
    expect(createMock).toHaveBeenCalledTimes(1);
    expect(push).not.toHaveBeenCalled();
  });

  it('在新页签打开当前设计的预览', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => ({}) as Window);
    vm.name = '未保存看板';
    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();

    vm.openPreview();

    expect(openSpy).toHaveBeenCalledTimes(1);
    const url = openSpy.mock.calls[0][0] as string;
    expect(url).toContain('/dashboard.html?draft=');
    const draftId = new URL(url, 'http://localhost').searchParams.get('draft');
    const draft = readDashboardPreviewDraft(draftId as string);
    expect(draft?.name).toBe('未保存看板');
    expect(draft?.spec.title).toBe('未保存看板');
    expect(draft?.spec.widgets).toHaveLength(1);
  });

  it('支持快捷键撤销重做，且输入框内不拦截', async () => {
    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();
    expect(vm.spec.widgets).toHaveLength(1);

    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true, cancelable: true }));
    await vm.$nextTick();
    expect(vm.spec.widgets).toHaveLength(0);

    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Z', ctrlKey: true, shiftKey: true, bubbles: true, cancelable: true }));
    await vm.$nextTick();
    expect(vm.spec.widgets).toHaveLength(1);

    const input = document.createElement('input');
    document.body.appendChild(input);
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true, cancelable: true }));
    await vm.$nextTick();
    expect(vm.spec.widgets).toHaveLength(1);
    input.remove();
  });
});
