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
      formatRules?: any[];
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
  chartType: string;
  chartSubtype: string;
  chartOrientation: string;
  chartCombo: boolean;
  hasChartAxes: boolean;
  setChartType: (type: string) => void;
  chartLegendVisible: boolean;
  chartDataLabelVisible: boolean;
  chartAxesVisible: boolean;
  chartGridLinesVisible: boolean;
  addWidget: (type: string, chartType?: string) => void;
  addFormatRule: () => void;
  removeFormatRule: (index: number) => void;
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
    expect(pie.encode).toEqual({
      dimensions: [{ field: '', role: 'x' }],
      measures: [{ field: '', agg: 'sum', role: 'value' }],
      limit: 100,
      groupBy: true,
    });
    expect(pie.echartsOption.series[0].type).toBe('pie');
    expect(pie.echartsOption.xAxis).toBeUndefined();
    expect(pie.echartsOption.yAxis).toBeUndefined();

    expect(kpi.datasetId).toBe('ds1');
    expect(kpi.layout.w).toBe(6);
    expect(kpi.layout.h).toBe(4);
    expect(kpi.encode).toEqual({ measures: [{ field: '', agg: 'sum', role: 'value' }] });

    expect(table.datasetId).toBe('ds1');
    expect(table.layout.w).toBe(12);
    expect(table.layout.h).toBe(8);
    expect(table.encode).toEqual({ limit: 100 });

    expect(html.datasetId).toBeUndefined();
    expect(html.layout.w).toBe(12);
    expect(html.layout.h).toBe(4);
    expect(html.html).toContain('HTML');
  });

  it('图表选项可控制图例、数据标签、坐标轴和网格线', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();

    vm.chartLegendVisible = false;
    vm.chartDataLabelVisible = true;
    vm.chartAxesVisible = false;
    vm.chartGridLinesVisible = false;
    await vm.$nextTick();

    const option = vm.spec.widgets[0].echartsOption;
    expect(option.legend.show).toBe(false);
    expect(option.series[0].label.show).toBe(true);
    expect(option.xAxis.show).toBe(false);
    expect(option.yAxis.show).toBe(false);
    expect(option.xAxis.splitLine.show).toBe(false);
    expect(option.yAxis.splitLine.show).toBe(false);
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
      dimensions: [{ field: 'region', role: 'x' }],
      measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
      limit: 100,
      groupBy: true,
    });
  });

  it('数据集结果稍后到达时补全默认分类字段和数值字段', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();

    expect(vm.spec.widgets[0].encode).toMatchObject({
      dimensions: [{ field: '', role: 'x' }],
      measures: [{ field: '', agg: 'sum', role: 'y' }],
    });

    (vm as any).datasetData = {
      ds1: {
        columns: ['region', 'amount'],
        rows: [{ region: '华东', amount: 120.5 }],
      },
    };
    await flushPromises();
    await vm.$nextTick();

    expect(vm.spec.widgets[0].encode).toMatchObject({
      dimensions: [{ field: 'region', role: 'x' }],
      measures: [{ field: 'amount', agg: 'sum', role: 'y' }],
    });
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

    expect(vm.spec.widgets[0].encode).toEqual({
      measures: [{ field: 'amount', agg: 'sum', role: 'value' }],
    });
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
      measures: [
        { name: 'amount', field: 'amount', agg: 'sum', role: 'y' },
        { name: 'quantity', field: 'quantity', agg: 'sum', role: 'y' },
      ],
    });

    vm.widgetValueFields = ['amount'];
    await vm.$nextTick();
    expect(vm.spec.widgets[0].encode.measures).toHaveLength(1);
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

  it('支持环形图、仪表盘以及横向和百分比堆积配置', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    (vm as any).datasetData = {
      ds1: {
        columns: ['region', 'amount'],
        rows: [{ region: '华东', amount: 120.5 }],
      },
    };
    await vm.$nextTick();
    vm.addWidget('echarts', 'bar');
    await vm.$nextTick();

    expect(vm.chartType).toBe('bar');
    expect(vm.chartSubtype).toBe('grouped');
    expect(vm.chartOrientation).toBe('vertical');

    vm.chartSubtype = 'stackedPercent';
    vm.chartOrientation = 'horizontal';
    await vm.$nextTick();
    expect(vm.spec.widgets[0].chart?.subtype).toBe('stackedPercent');
    expect(vm.spec.widgets[0].chart?.orientation).toBe('horizontal');

    vm.setChartType('donut');
    await vm.$nextTick();
    expect(vm.spec.widgets[0].chart?.type).toBe('donut');
    expect(vm.spec.widgets[0].encode?.measures?.[0]?.role).toBe('value');
    expect(vm.spec.widgets[0].echartsOption?.series?.[0]?.radius).toEqual(['42%', '68%']);
    expect(vm.hasChartAxes).toBe(false);

    vm.setChartType('gauge');
    await vm.$nextTick();
    expect(vm.spec.widgets[0].chart?.type).toBe('gauge');
    expect(vm.spec.widgets[0].encode?.dimensions).toBeUndefined();
    expect(vm.spec.widgets[0].encode?.groupBy).toBe(false);
    expect(vm.spec.widgets[0].echartsOption?.series?.[0]?.type).toBe('gauge');
    expect(vm.hasChartAxes).toBe(false);
  });

  it('双轴组合按度量生成柱状和折线系列', async () => {
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

    vm.chartCombo = true;
    await vm.$nextTick();

    expect(vm.spec.widgets[0].chart?.series).toHaveLength(2);
    expect(vm.spec.widgets[0].chart?.series?.[0]).toMatchObject({ measure: 'amount', type: 'bar', axis: 'left' });
    expect(vm.spec.widgets[0].chart?.series?.[1]).toMatchObject({ measure: 'quantity', type: 'line', axis: 'right' });

    vm.chartCombo = false;
    await vm.$nextTick();
    expect(vm.spec.widgets[0].chart?.series).toBeUndefined();
  });

  it('支持高级图表和透视表默认配置', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    (vm as any).datasetData = {
      ds1: {
        columns: ['region', 'channel', 'amount'],
        rows: [
          { region: '华东', channel: '官网', amount: 120.5 },
          { region: '华东', channel: 'APP', amount: 60 },
        ],
      },
    };
    await vm.$nextTick();

    vm.addWidget('echarts', 'scatter');
    vm.addWidget('echarts', 'heatmap');
    vm.addWidget('echarts', 'waterfall');
    vm.addWidget('echarts', 'sankey');
    vm.addWidget('echarts', 'graph');
    vm.addWidget('echarts', 'boxplot');
    vm.addWidget('pivot');
    await vm.$nextTick();

    const [scatter, heatmap, waterfall, sankey, graph, boxplot, pivot] = vm.spec.widgets;
    expect(scatter.chart).toMatchObject({ type: 'scatter' });
    expect(scatter.echartsOption?.series?.[0]?.type).toBe('scatter');
    expect(scatter.echartsOption?.xAxis).toMatchObject({ type: 'value' });

    expect(heatmap.chart).toMatchObject({ type: 'heatmap' });
    expect(heatmap.encode?.dimensions).toEqual([
      { field: 'region', role: 'x' },
      { field: 'channel', role: 'series' },
    ]);
    expect(heatmap.echartsOption?.series?.[0]?.type).toBe('heatmap');

    expect(pivot.type).toBe('pivot');
    expect(pivot.layout).toMatchObject({ w: 12, h: 8 });
    expect(pivot.encode?.dimensions).toEqual([
      { field: 'region', role: 'row' },
      { field: 'channel', role: 'column' },
    ]);
    expect(pivot.encode?.measures).toEqual([{ field: 'amount', agg: 'sum', role: 'value' }]);

    expect(waterfall.chart).toMatchObject({ type: 'waterfall' });
    expect(waterfall.encode?.groupBy).toBe(true);
    expect(sankey.chart).toMatchObject({ type: 'sankey' });
    expect(sankey.encode?.dimensions).toEqual([
      { field: 'region', role: 'x' },
      { field: 'channel', role: 'series' },
    ]);
    expect(graph.chart).toMatchObject({ type: 'graph' });
    expect(boxplot.chart).toMatchObject({ type: 'boxplot' });
    expect(boxplot.encode?.groupBy).toBe(false);

    vm.addFormatRule();
    await vm.$nextTick();
    expect(pivot.formatRules).toHaveLength(1);
    vm.removeFormatRule(0);
    await vm.$nextTick();
    expect(pivot.formatRules).toHaveLength(0);
  });

  it('新建柱状图始终展示双轴组合选项，少于两个度量时禁用', async () => {
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
    fullVm.addWidget('echarts', 'bar');
    await fullVm.$nextTick();

    const findCombo = () => fullWrapper.findAll('.el-checkbox').find(item => item.text().includes('双轴组合'));
    expect(findCombo()).toBeTruthy();
    expect(findCombo()!.classes()).toContain('is-disabled');

    fullVm.widgetValueFields = ['amount', 'quantity'];
    await fullVm.$nextTick();
    expect(findCombo()!.classes()).not.toContain('is-disabled');

    fullWrapper.unmount();
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

  it('SQL 数据集字段下拉支持 SQL 参数选项', async () => {
    vm.spec.datasets = [{ id: 'ds1', type: 'SQL' }];
    vm.addFilter();
    await vm.$nextTick();

    const target = vm.spec.filters[0].targets[0];
    (vm as any).onFilterTargetFieldSelect(target, (vm as any).SQL_PARAM_OPTION);
    expect(target.pushdown).toMatchObject({ kind: 'sqlParam' });
    expect(target.field).toBeUndefined();

    (vm as any).onFilterTargetFieldSelect(target, 'region');
    expect(target.field).toBe('region');
    expect(target.pushdown).toBeUndefined();
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
