import { flushPromises, mount, shallowMount } from '@vue/test-utils';

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
      widgetData: ref({}),
      loadingDatasets: ref({}),
      datasetErrors: ref({}),
      widgetErrors: ref({}),
      serverPaginationWidgets: ref({}),
      dataVersion: ref(0),
      loading: ref(false),
      loadAll: vi.fn().mockResolvedValue(undefined),
      loadWidgetPage: vi.fn().mockResolvedValue(undefined),
    }),
  };
});

type WidgetLayout = { x: number; y: number; w: number; h: number };

type DesignerVm = {
  name: string;
  editingId?: string;
  spec: {
    title?: string;
    description?: string;
    layout?: {
      mode?: 'standard' | 'screen';
      columns?: number;
      rowHeight?: number;
      gap?: number;
      screen?: {
        width: number;
        height: number;
        fitMode?: 'contain' | 'stretch' | 'cover';
        theme?: 'light' | 'dark';
        autoRefreshSeconds?: number;
        background?: {
          type: 'color' | 'gradient' | 'image';
          color?: string;
          gradient?: { from: string; to: string; angle: number };
          imagePath?: string;
          imageContentType?: string;
        };
      };
    };
    widgets: Array<{
      id: string;
      title: string;
      type?: string;
      datasetId?: string;
      encode?: any;
      echartsOption?: any;
      html?: string;
      layout: WidgetLayout;
    }>;
    filters: Array<{ id: string; targets: any[] }>;
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
  isScreenMode: boolean;
  layoutMode: 'standard' | 'screen';
  dashboardSettingsVisible: boolean;
  openDashboardSettings: () => Promise<void>;
  screenWidth: number;
  screenHeight: number;
  screenFitMode: 'contain' | 'stretch' | 'cover';
  screenTheme: 'light' | 'dark';
  autoRefreshSeconds: number;
  backgroundType: 'color' | 'gradient' | 'image';
  backgroundColor: string;
  gradientFrom: string;
  gradientTo: string;
  gradientAngle: number;
  canvasZoom: 'fit' | number;
  zoomPercent: number;
  isCanvasOverflow: boolean;
  zoomIn: () => void;
  zoomOut: () => void;
  fitCanvas: () => void;
  onCanvasScaleChange: (scale: number) => void;
  $nextTick: () => Promise<void>;
};

describe('AnalysisDashboardDesigner history', () => {
  let wrapper: ReturnType<typeof shallowMount>;
  let vm: DesignerVm;

  beforeEach(async () => {
    vi.clearAllMocks();
    vi.spyOn(window.screen, 'width', 'get').mockReturnValue(2560);
    vi.spyOn(window.screen, 'height', 'get').mockReturnValue(1440);
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
    expect(pie.layout.w).toBe(8);
    expect(pie.layout.h).toBe(8);
    expect(pie.encode).toEqual({ categoryField: '', values: [{ field: '', agg: 'sum' }], limit: 100, groupBy: true });
    expect(pie.echartsOption.series[0].type).toBe('pie');
    expect(pie.echartsOption.xAxis).toBeUndefined();
    expect(pie.echartsOption.yAxis).toBeUndefined();

    expect(kpi.datasetId).toBe('ds1');
    expect(kpi.layout.w).toBe(6);
    expect(kpi.layout.h).toBe(4);
    expect(kpi.encode).toEqual({ values: [{ field: '', agg: 'sum' }] });

    expect(table.datasetId).toBe('ds1');
    expect(table.layout.w).toBe(12);
    expect(table.layout.h).toBe(8);
    expect(table.encode).toEqual({ limit: 100 });

    expect(html.datasetId).toBeUndefined();
    expect(html.layout.w).toBe(12);
    expect(html.layout.h).toBe(4);
    expect(html.html).toContain('HTML');
  });

  it('统一使用 24 列栅格并支持切换大屏分辨率', async () => {
    expect(vm.spec.layout?.columns).toBe(24);
    expect(vm.spec.layout?.mode).toBe('standard');

    vm.layoutMode = 'screen';
    await vm.$nextTick();

    expect(vm.isScreenMode).toBe(true);
    expect(vm.spec.layout?.mode).toBe('screen');
    expect(vm.screenFitMode).toBe('stretch');
    expect(vm.canvasZoom).toBe('fit');

    vm.screenWidth = 2560;
    vm.screenHeight = 1440;
    await vm.$nextTick();

    expect(vm.spec.layout?.screen).toMatchObject({ width: 2560, height: 1440 });

    vm.onCanvasScaleChange(0.5);
    await vm.$nextTick();
    expect(vm.zoomPercent).toBe(50);
    vm.zoomIn();
    await vm.$nextTick();
    expect(vm.zoomPercent).toBe(60);
    vm.fitCanvas();
    expect(vm.canvasZoom).toBe('fit');
  });

  it('大屏设置支持适配方式、主题、背景和自动刷新', async () => {
    vm.layoutMode = 'screen';
    await vm.$nextTick();

    vm.screenFitMode = 'cover';
    vm.screenTheme = 'dark';
    vm.autoRefreshSeconds = 30;
    vm.backgroundType = 'gradient';
    vm.gradientFrom = '#111111';
    vm.gradientTo = '#222222';
    vm.gradientAngle = 90;
    await vm.$nextTick();

    expect(vm.spec.layout?.screen).toMatchObject({
      fitMode: 'cover',
      theme: 'dark',
      autoRefreshSeconds: 30,
      background: { type: 'gradient', gradient: { from: '#111111', to: '#222222', angle: 90 } },
    });

    vm.backgroundType = 'color';
    vm.backgroundColor = '#123456';
    await vm.$nextTick();
    expect(vm.spec.layout?.screen?.background).toEqual({ type: 'color', color: '#123456' });

    vm.backgroundType = 'image';
    await vm.$nextTick();
    expect(vm.spec.layout?.screen?.background).toMatchObject({ type: 'image' });

    vm.autoRefreshSeconds = 0;
    await vm.$nextTick();
    expect(vm.spec.layout?.screen?.autoRefreshSeconds).toBe(0);
  });

  it('设置面板与组件配置互斥切换', async () => {
    expect(vm.dashboardSettingsVisible).toBe(false);
    await vm.openDashboardSettings();
    expect(vm.dashboardSettingsVisible).toBe(true);

    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    vm.addWidget('kpi');
    await vm.$nextTick();
    expect(vm.dashboardSettingsVisible).toBe(false);

    await vm.openDashboardSettings();
    expect(vm.dashboardSettingsVisible).toBe(true);
    await vm.openDashboardSettings();
    expect(vm.dashboardSettingsVisible).toBe(true);
  });

  it('大屏设置面板展示适配、主题、背景和自动刷新', async () => {
    await vm.openDashboardSettings();
    await vm.$nextTick();

    let text = wrapper.text();
    expect(text).toContain('主题');
    expect(text).toContain('背景');
    expect(text).toContain('自动刷新');
    expect(text).not.toContain('全屏适配方式');

    vm.layoutMode = 'screen';
    await vm.$nextTick();
    text = wrapper.text();
    expect(text).toContain('全屏适配方式');
    expect(text).toContain('主题');
    expect(text).toContain('背景');
    expect(text).toContain('自动刷新');
  });

  it('选中组件后点击看板设置仍能切换到设置面板', async () => {
    const fullWrapper = mount(AnalysisDashboardDesigner, {
      global: {
        provide: {
          alertService: { showError: vi.fn() },
        },
        stubs: {
          'font-awesome-icon': true,
          DesignerCanvas: true,
          DashboardStage: true,
          DashboardFilters: true,
          WidgetPaletteIcon: true,
        },
      },
    });
    await flushPromises();

    const fullVm = fullWrapper.vm as unknown as DesignerVm;
    fullVm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    fullVm.addWidget('kpi');
    await fullVm.$nextTick();
    expect(fullVm.spec.widgets).toHaveLength(1);

    const settingsButton = fullWrapper.find('button[title="看板设置：模式、分辨率与画布缩放"]');
    expect(settingsButton.exists()).toBe(true);
    await settingsButton.trigger('click');
    await fullVm.$nextTick();

    expect(fullVm.dashboardSettingsVisible).toBe(true);
    expect(fullWrapper.text()).toContain('看板设置');
    expect(fullWrapper.text()).not.toContain('组件配置');
    fullWrapper.unmount();
  });

  it('大屏模式下内容超出画布高度时给出提示', async () => {
    vm.layoutMode = 'screen';
    vm.spec.widgets = [
      {
        id: 'w1',
        title: '超高组件',
        type: 'table',
        layout: { x: 0, y: 0, w: 24, h: 40 },
      },
    ];
    await vm.$nextTick();
    expect(vm.isCanvasOverflow).toBe(true);
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

    expect(vm.spec.widgets[0].encode).toEqual({
      categoryField: 'region',
      values: [{ field: 'amount', agg: 'sum' }],
      limit: 100,
      groupBy: true,
    });
  });

  it('数据集结果稍后到达时补全默认分类字段和数值字段', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();

    expect(vm.spec.widgets[0].encode).toMatchObject({ categoryField: '', values: [{ field: '', agg: 'sum' }] });

    (vm as any).datasetData = {
      ds1: {
        columns: ['region', 'amount'],
        rows: [{ region: '华东', amount: 120.5 }],
      },
    };
    await flushPromises();
    await vm.$nextTick();

    expect(vm.spec.widgets[0].encode).toMatchObject({ categoryField: 'region', values: [{ field: 'amount', agg: 'sum' }] });
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

    expect(vm.spec.widgets[0].encode).toEqual({ values: [{ field: 'amount', agg: 'sum' }] });
    expect(vm.spec.widgets[1].encode).toEqual({ limit: 100 });
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

    expect(vm.spec.widgets[0].encode).toMatchObject({
      values: [
        { name: 'amount', field: 'amount', agg: 'sum' },
        { name: 'quantity', field: 'quantity', agg: 'sum' },
      ],
      seriesField: null,
    });

    vm.widgetValueFields = ['amount'];
    await vm.$nextTick();
    expect(vm.spec.widgets[0].encode.values).toHaveLength(1);
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
      [12, 8],
      [12, 8],
      [12, 8],
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

  it('新增筛选器统一生成 targets，SQL 数据集默认服务端下推', async () => {
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

    expect(vm.spec.filters[0].targets[0]).toMatchObject({ datasetId: 'ds1', field: 'region', mode: 'server' });
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
      layout: { mode: 'screen', columns: 12, rowHeight: 90, gap: 16, screen: { width: 1920, height: 1080 } },
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
    expect(vm.spec.description).toBe('AI 描述');
    expect(vm.spec.layout?.columns).toBe(24);
    expect(vm.spec.layout?.screen).toMatchObject({ width: 2560, height: 1440 });
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

  it('保存时写入大屏适配、主题、背景与刷新设置', async () => {
    createMock.mockResolvedValue({ id: 'db1', name: '大屏看板' });
    vm.name = '大屏看板';
    vm.layoutMode = 'screen';
    vm.screenFitMode = 'stretch';
    vm.screenTheme = 'dark';
    vm.autoRefreshSeconds = 15;
    vm.backgroundType = 'gradient';
    vm.gradientFrom = '#111111';
    vm.gradientTo = '#222222';
    vm.gradientAngle = 45;
    vm.addWidget('kpi');
    await vm.$nextTick();

    await vm.save();

    const payload = createMock.mock.calls[0][0];
    const savedSpec = JSON.parse(payload.spec);
    expect(savedSpec.layout.screen).toMatchObject({
      fitMode: 'stretch',
      theme: 'dark',
      autoRefreshSeconds: 15,
      background: { type: 'gradient', gradient: { from: '#111111', to: '#222222', angle: 45 } },
    });
  });

  it('在新页签打开当前设计的预览', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => ({}) as Window);
    vm.name = '未保存看板';
    vm.layoutMode = 'screen';
    vm.screenFitMode = 'stretch';
    vm.screenTheme = 'dark';
    vm.autoRefreshSeconds = 30;
    vm.backgroundType = 'color';
    vm.backgroundColor = '#123456';
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
    expect(draft?.spec.layout?.screen).toMatchObject({
      fitMode: 'stretch',
      theme: 'dark',
      autoRefreshSeconds: 30,
      background: { type: 'color', color: '#123456' },
    });
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
