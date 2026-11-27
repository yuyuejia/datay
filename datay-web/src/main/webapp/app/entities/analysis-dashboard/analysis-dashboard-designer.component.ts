import { type Ref, computed, defineComponent, inject, nextTick, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';

import AiAssistantService from '@/entities/data-source/ai-assistant.service';
import type { AiHistoryMessage, AiToolTrace } from '@/entities/data-source/ai-assistant.service';
import { useAlertService } from '@/shared/alert/alert.service';
import DatasetService from '@/shared/dataset/dataset.service';
import type { AnalysisDataset } from '@/shared/dataset/types';
import DashboardService from '@/shared/dashboard/dashboard.service';
import type { AnalysisDashboard } from '@/shared/dashboard/dashboard.service';
import { initFilterValues } from '@/shared/dashboard/filter-utils';
import {
  DARK_SCREEN_BACKGROUND,
  DEFAULT_CANVAS_GAP,
  DEFAULT_CANVAS_PADDING,
  DEFAULT_SCREEN_HEIGHT,
  DEFAULT_SCREEN_WIDTH,
  getSystemScreenSize,
  LIGHT_SCREEN_BACKGROUND,
  migrateLegacyRowHeight,
  normalizeDashboardLayout,
  normalizeDashboardSpec,
  normalizeFitMode,
  normalizeTheme,
  resolveScreenBackgroundStyle,
  resolveScreenTheme,
  SCREEN_FIT_MODES,
} from '@/shared/dashboard/layout-defaults';
import { DEFAULT_CHILD_LAYOUT, fitChildLayout, normalizeChildLayout } from '@/shared/dashboard/container-layout';
import DashboardBackgroundService from '@/shared/dashboard/dashboard-background.service';
import { useDashboardBackgroundImage } from '@/shared/dashboard/useDashboardBackgroundImage';
import {
  buildDashboardFields,
  createDefaultEncode,
  ensureDefaultFields,
  hasCompleteDefaultFields,
  resolveChartType,
} from '@/shared/dashboard/field-defaults';
import type { DashboardField } from '@/shared/dashboard/field-defaults';
import { createDashboardPreviewDraft } from '@/shared/dashboard/preview-draft';
import { resolveThemeTokens, THEME_PRESET_OPTIONS } from '@/shared/dashboard/theme-presets';
import type {
  Aggregation,
  CanvasUnit,
  CardPreset,
  ChartStylePreset,
  ChartSeries,
  ClockFormat,
  ChartType,
  DashboardBackgroundType,
  DashboardDataset,
  DashboardFilter,
  DashboardMode,
  DashboardScreen,
  DashboardSpec,
  DashboardTheme,
  DashboardWidget,
  FilterValues,
  ScreenCanvas,
  ScreenFitMode,
  ThemePreset,
  KpiLayout,
  LegendPosition,
  LineStyleType,
  MapLayerSpec,
  ThemeTokens,
  TitleFrame,
  WidgetBodyStyle,
  WidgetCardStyle,
  WidgetChartAxisStyle,
  WidgetChartStyle,
  WidgetClickAction,
  WidgetLinkageTarget,
  WidgetFill,
  WidgetFormatRule,
  WidgetKpiStyle,
  WidgetLayout,
  WidgetPalettePayload,
  WidgetProps,
  WidgetStyle,
  WidgetTitleStyle,
  WidgetType,
} from '@/shared/dashboard/types';
import { WIDGET_PALETTE_MIME } from '@/shared/dashboard/types';
import { useDashboardData } from '@/shared/dashboard/useDashboardData';
import DashboardFilters from '@/shared/dashboard/DashboardFilters.vue';
import DashboardStage from '@/shared/dashboard/DashboardStage.vue';
import DesignerCanvas from '@/shared/dashboard/designer/DesignerCanvas.vue';
import WidgetPaletteIcon from '@/shared/dashboard/designer/WidgetPaletteIcon.vue';
import {
  canvasContentWidth,
  clamp,
  clampPixelLayout,
  compactLayouts,
  DEFAULT_ROW_HEIGHT,
  GRID_COLUMNS,
  layoutContentHeight,
  moveLayout,
  nextLayout,
  nextPixelLayout,
  normalizeLayout,
  pixelContentHeight,
  resizeLayout,
  resolveCanvasMetrics,
} from '@/shared/dashboard/designer/grid-layout';

type PaletteCategory = 'charts' | 'kpi' | 'table' | 'text' | 'other';

interface PaletteItem extends WidgetPalettePayload {
  key: string;
  label: string;
  icon: string;
  category: PaletteCategory;
}

interface DesignerSnapshot {
  name: string;
  description: string;
  status: string;
  spec: DashboardSpec;
}

interface AiChatMessage {
  role: 'user' | 'assistant';
  content: string;
  loading?: boolean;
  statusText?: string;
  explanation?: string;
  toolCalls?: AiToolTrace[];
  hasSpec?: boolean;
}

const DATASETLESS_TYPES = new Set<WidgetType>(['html', 'title', 'clock', 'container']);

const TITLE_ICON_OPTIONS = [
  'chart-line',
  'tachometer-alt',
  'table',
  'list',
  'database',
  'globe',
  'users',
  'user',
  'clock',
  'cubes',
  'box-open',
  'bell',
  'flag',
  'heart',
  'rocket',
  'bolt',
  'book',
  'code',
  'folder',
  'home',
  'project-diagram',
  'tasks',
  'cloud',
  'eye',
];

const AI_ASSISTANT_ID = 'dashboard-design';
const HISTORY_LIMIT = 60;
const HISTORY_COMMIT_DELAY = 400;

const CHART_LABELS: Record<ChartType, string> = {
  bar: '柱状图',
  line: '折线图',
  area: '面积图',
  pie: '饼图',
  donut: '环形图',
  gauge: '仪表盘',
  scatter: '散点图',
  radar: '雷达图',
  funnel: '漏斗图',
  heatmap: '热力图',
  treemap: '矩形树图',
  waterfall: '瀑布图',
  sankey: '桑基图',
  graph: '关系图',
  boxplot: '箱线图',
  map: '中国地图',
};

const PALETTE_CATEGORIES: Array<{ key: PaletteCategory; label: string; icon: string }> = [
  { key: 'charts', label: '图表', icon: 'bar' },
  { key: 'kpi', label: 'KPI', icon: 'kpi' },
  { key: 'table', label: '表格', icon: 'table' },
  { key: 'text', label: '文本', icon: 'text' },
  { key: 'other', label: '其他', icon: 'other' },
];

const PALETTE_ITEMS: PaletteItem[] = [
  { key: 'bar', label: '柱状图', icon: 'bar', category: 'charts', type: 'echarts', chartType: 'bar', w: 12, h: 8 },
  { key: 'line', label: '折线图', icon: 'line', category: 'charts', type: 'echarts', chartType: 'line', w: 12, h: 8 },
  { key: 'pie', label: '饼图', icon: 'pie', category: 'charts', type: 'echarts', chartType: 'pie', w: 8, h: 8 },
  { key: 'donut', label: '环形图', icon: 'pie', category: 'charts', type: 'echarts', chartType: 'donut', w: 8, h: 8 },
  { key: 'area', label: '面积图', icon: 'area', category: 'charts', type: 'echarts', chartType: 'area', w: 12, h: 8 },
  { key: 'gauge', label: '仪表盘', icon: 'kpi', category: 'charts', type: 'echarts', chartType: 'gauge', w: 6, h: 8 },
  { key: 'scatter', label: '散点图', icon: 'area', category: 'charts', type: 'echarts', chartType: 'scatter', w: 12, h: 8 },
  { key: 'radar', label: '雷达图', icon: 'kpi', category: 'charts', type: 'echarts', chartType: 'radar', w: 12, h: 8 },
  { key: 'funnel', label: '漏斗图', icon: 'pie', category: 'charts', type: 'echarts', chartType: 'funnel', w: 8, h: 8 },
  { key: 'heatmap', label: '热力图', icon: 'table', category: 'charts', type: 'echarts', chartType: 'heatmap', w: 12, h: 8 },
  { key: 'treemap', label: '矩形树图', icon: 'table', category: 'charts', type: 'echarts', chartType: 'treemap', w: 12, h: 8 },
  { key: 'waterfall', label: '瀑布图', icon: 'bar', category: 'charts', type: 'echarts', chartType: 'waterfall', w: 12, h: 8 },
  { key: 'sankey', label: '桑基图', icon: 'area', category: 'charts', type: 'echarts', chartType: 'sankey', w: 12, h: 8 },
  { key: 'graph', label: '关系图', icon: 'area', category: 'charts', type: 'echarts', chartType: 'graph', w: 12, h: 8 },
  { key: 'boxplot', label: '箱线图', icon: 'bar', category: 'charts', type: 'echarts', chartType: 'boxplot', w: 12, h: 8 },
  { key: 'map', label: '中国地图', icon: 'map', category: 'charts', type: 'echarts', chartType: 'map', w: 12, h: 8 },
  { key: 'kpi', label: 'KPI 指标', icon: 'kpi', category: 'kpi', type: 'kpi', w: 6, h: 4 },
  { key: 'table', label: '明细表', icon: 'table', category: 'table', type: 'table', w: 12, h: 8 },
  { key: 'pivot', label: '透视表', icon: 'table', category: 'table', type: 'pivot', w: 12, h: 8 },
  { key: 'html', label: '说明文本', icon: 'text', category: 'text', type: 'html', w: 12, h: 4 },
  { key: 'title', label: '标题栏', icon: 'title', category: 'other', type: 'title', w: 24, h: 2 },
  { key: 'clock', label: '日期时间', icon: 'clock', category: 'other', type: 'clock', w: 4, h: 1 },
  { key: 'container', label: '容器', icon: 'container', category: 'other', type: 'container', w: 12, h: 8 },
];

const SCREEN_PRESETS = [
  { key: '1280x720', label: '1280 × 720', width: 1280, height: 720 },
  { key: '1920x1080', label: '1920 × 1080', width: 1920, height: 1080 },
  { key: '2560x1440', label: '2560 × 1440', width: 2560, height: 1440 },
  { key: '3840x2160', label: '3840 × 2160', width: 3840, height: 2160 },
];

const ZOOM_MIN = 0.25;
const ZOOM_MAX = 2;
const ZOOM_STEP = 0.1;

const DEFAULT_ECHARTS_OPTION = (type: ChartType) => {
  const seriesType = type === 'area' ? 'line' : type === 'donut' ? 'pie' : type === 'waterfall' ? 'bar' : type;
  const isPie = seriesType === 'pie';
  const isGauge = seriesType === 'gauge';
  const isSingleValue = isPie || seriesType === 'funnel' || seriesType === 'treemap';
  const isRadar = seriesType === 'radar';
  const isScatter = seriesType === 'scatter';
  const isHeatmap = seriesType === 'heatmap';
  const isNodeLink = seriesType === 'sankey' || seriesType === 'graph';
  const isBoxplot = seriesType === 'boxplot';
  const isWaterfall = type === 'waterfall';
  const isMap = type === 'map';
  const option: Record<string, any> = {
    tooltip: {
      trigger: isSingleValue || isGauge || isRadar || isScatter || isHeatmap || isNodeLink || isBoxplot || isMap ? 'item' : 'axis',
    },
    ...(isMap ? {} : { legend: { top: 0 } }),
    ...(isMap ? { visualMap: { calculable: true, orient: 'horizontal', left: 'center', bottom: 0 } } : {}),
    series: [
      {
        type: seriesType,
        smooth: seriesType === 'line',
        areaStyle: type === 'area' ? {} : undefined,
        radius: type === 'donut' ? ['42%', '68%'] : undefined,
        emphasis: isMap ? { label: { show: true } } : { focus: 'series' },
        ...(isGauge
          ? {
              progress: { show: true },
              detail: { valueAnimation: true, formatter: '{value}' },
              data: [{ value: 0, name: '' }],
            }
          : {}),
        ...(isHeatmap ? { label: { show: true } } : {}),
        ...(isBoxplot ? { boxWidth: [8, 32] } : {}),
        ...(isNodeLink ? { layout: seriesType === 'graph' ? 'force' : undefined, roam: true } : {}),
        ...(isMap ? { map: 'china', roam: false, label: { show: false } } : {}),
      },
    ],
  };
  if (isGauge || isSingleValue || isRadar || isNodeLink || isMap) {
    return option;
  }
  option.grid = { left: 48, right: 24, top: 40, bottom: 36 };
  if (isScatter) {
    option.xAxis = { type: 'value' };
    option.yAxis = { type: 'value' };
  } else if (isHeatmap) {
    option.xAxis = { type: 'category' };
    option.yAxis = { type: 'category' };
  } else {
    option.xAxis = { type: 'category', boundaryGap: seriesType === 'bar' || isWaterfall };
    option.yAxis = { type: 'value' };
  }
  if (isHeatmap) {
    option.visualMap = { min: 0, max: 100, calculable: true, orient: 'horizontal', left: 'center', bottom: 0 };
  }
  return option;
};

/** 合并自定义图表配置：对象递归合并，数组整体替换，避免缺少坐标轴等必需结构。 */
const mergeEchartsOption = (base: Record<string, any>, override: Record<string, any>): Record<string, any> => {
  const result: Record<string, any> = { ...base };
  for (const [key, value] of Object.entries(override)) {
    if (
      value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      result[key] &&
      typeof result[key] === 'object' &&
      !Array.isArray(result[key])
    ) {
      result[key] = mergeEchartsOption(result[key], value);
    } else {
      result[key] = value;
    }
  }
  return result;
};

const errorMessage = (err: any, fallback: string): string => {
  const detail = err?.response?.data?.errors?.[0];
  if (detail?.message) {
    return detail.path ? `${detail.path}: ${detail.message}` : detail.message;
  }
  return (
    err?.response?.data?.errorMessage ||
    err?.response?.data?.detail ||
    err?.response?.data?.title ||
    err?.response?.data?.message ||
    err?.message ||
    fallback
  );
};

/**
 * 可视化看板设计器：数据集引用、24 列拖拽布局、图表配置、全局筛选器与实时预览。
 */
export default defineComponent({
  name: 'AnalysisDashboardDesigner',
  components: { DashboardFilters, DashboardStage, DesignerCanvas, WidgetPaletteIcon },
  props: {
    aiMode: {
      type: Boolean,
      default: false,
    },
  },
  setup(props) {
    const route = useRoute();
    const router = useRouter();
    const alertService = inject('alertService', () => useAlertService(), true);
    const dashboardService = new DashboardService();
    const datasetService = new DatasetService();
    const aiService = new AiAssistantService();

    const editingId: Ref<string | undefined> = ref(undefined);
    const loading = ref(false);
    const saving = ref(false);
    const name = ref('');
    const description = ref('');
    const status = ref('ENABLED');
    const activePanel = ref<'datasets' | 'widgets' | 'filters'>('datasets');
    const propertyTab = ref<'basic' | 'custom' | 'interaction'>('basic');
    const dashboardSettingsVisible = ref(false);
    const propertiesRef = ref<HTMLElement | null>(null);
    const customOptionText = ref('');
    const customOptionError = ref('');
    const paletteCategory = ref<PaletteCategory>('charts');
    const paletteDragItem = ref<PaletteItem | null>(null);
    const selectedWidgetId = ref('');
    const selectedFilterId = ref('');
    const datasetToAdd = ref('');
    const availableDatasets = ref<AnalysisDataset[]>([]);

    const aiPanelVisible = ref(false);
    const aiInput = ref('');
    const aiGenerating = ref(false);
    const aiMessages: Ref<AiChatMessage[]> = ref([]);
    const aiMessageListRef = ref<HTMLElement | null>(null);
    const aiToolsExpanded = ref<Record<number, boolean>>({});
    const aiSamples = ref<string[]>([]);
    const aiAssistantLoaded = ref(false);

    const paletteCategories = PALETTE_CATEGORIES;
    const activePaletteItems = computed(() => PALETTE_ITEMS.filter(item => item.category === paletteCategory.value));

    const spec = ref<DashboardSpec>({
      version: 2,
      title: '',
      description: '',
      layout: normalizeDashboardLayout({ rowHeight: 20 }),
      datasets: [],
      widgets: [],
      filters: [],
    });
    const filterValues = ref<FilterValues>({});

    const {
      datasetData,
      widgetData,
      loadingDatasets,
      loadingWidgets,
      widgetResetKeys,
      datasetErrors,
      widgetErrors,
      serverPaginationWidgets,
      dataVersion,
      loading: loadingData,
      loadAll,
      loadWidgetPage,
      loadDrill,
      linkage,
      linkageWidgetIds,
      applyLinkage,
      clearLinkage,
    } = useDashboardData({ spec });

    const selectedWidget = computed(() => spec.value.widgets.find(widget => widget.id === selectedWidgetId.value));
    const selectedFilter = computed(() => (spec.value.filters ?? []).find(filter => filter.id === selectedFilterId.value));
    const selectedWidgetDatasetLabel = computed(() => {
      const datasetId = selectedWidget.value?.datasetId;
      if (!datasetId) {
        return '未绑定数据集';
      }
      const dataset = spec.value.datasets.find(item => item.id === datasetId);
      return dataset?.name || datasetId;
    });

    // ---------------------------------------------------------------- 组件皮肤

    const ensureSelectedStyle = (): WidgetStyle | undefined => {
      const widget = selectedWidget.value;
      if (!widget) {
        return undefined;
      }
      if (!widget.style) {
        widget.style = {};
      }
      return widget.style;
    };

    const updateCard = (patch: (card: WidgetCardStyle) => void) => {
      const style = ensureSelectedStyle();
      if (!style) {
        return;
      }
      if (!style.card) {
        style.card = {};
      }
      patch(style.card);
    };

    const updateTitle = (patch: (title: WidgetTitleStyle) => void) => {
      const style = ensureSelectedStyle();
      if (!style) {
        return;
      }
      if (!style.title) {
        style.title = {};
      }
      patch(style.title);
    };

    const updateBody = (patch: (body: WidgetBodyStyle) => void) => {
      const style = ensureSelectedStyle();
      if (!style) {
        return;
      }
      if (!style.body) {
        style.body = {};
      }
      patch(style.body);
    };

    const updateKpi = (patch: (kpi: WidgetKpiStyle) => void) => {
      const style = ensureSelectedStyle();
      if (!style) {
        return;
      }
      if (!style.kpi) {
        style.kpi = {};
      }
      patch(style.kpi);
    };

    const cardPreset = computed<CardPreset>({
      get: () => selectedWidget.value?.style?.card?.preset ?? 'none',
      set: value => updateCard(card => (card.preset = value)),
    });

    const cardFillType = computed<'none' | 'color' | 'gradient'>({
      get: () => selectedWidget.value?.style?.card?.fill?.type ?? 'none',
      set: type => {
        updateCard(card => {
          if (type === 'none') {
            card.fill = { type: 'none' };
          } else if (type === 'color') {
            card.fill = { type: 'color', color: card.fill?.color ?? '#0b1f33' };
          } else {
            card.fill = {
              type: 'gradient',
              gradient: card.fill?.gradient ?? { from: '#0a1b2e', to: '#0d2643', angle: 180 },
            };
          }
        });
      },
    });

    const cardFillColor = computed<string>({
      get: () => selectedWidget.value?.style?.card?.fill?.color ?? '#0b1f33',
      set: value => updateCard(card => (card.fill = { type: 'color', color: value || '#0b1f33' })),
    });

    const ensureCardGradient = (): NonNullable<WidgetFill['gradient']> => {
      let gradient: NonNullable<WidgetFill['gradient']> = { from: '#0a1b2e', to: '#0d2643', angle: 180 };
      updateCard(card => {
        if (card.fill?.type !== 'gradient' || !card.fill.gradient) {
          card.fill = { type: 'gradient', gradient };
        }
        gradient = card.fill.gradient;
      });
      return gradient;
    };

    const cardGradientFrom = computed<string>({
      get: () => selectedWidget.value?.style?.card?.fill?.gradient?.from ?? '#0a1b2e',
      set: value => {
        const gradient = ensureCardGradient();
        gradient.from = value || '#0a1b2e';
      },
    });
    const cardGradientTo = computed<string>({
      get: () => selectedWidget.value?.style?.card?.fill?.gradient?.to ?? '#0d2643',
      set: value => {
        const gradient = ensureCardGradient();
        gradient.to = value || '#0d2643';
      },
    });
    const cardGradientAngle = computed<number>({
      get: () => selectedWidget.value?.style?.card?.fill?.gradient?.angle ?? 180,
      set: value => {
        const gradient = ensureCardGradient();
        gradient.angle = clamp(Math.round(value || 0), 0, 360);
      },
    });

    const cardBorderWidth = computed<number>({
      get: () => selectedWidget.value?.style?.card?.border?.width ?? 0,
      set: value => {
        updateCard(card => {
          if (!card.border) {
            card.border = {};
          }
          card.border.width = clamp(Math.round(value || 0), 0, 8);
          if (card.border.width > 0) {
            card.border.style = card.border.style ?? 'solid';
            card.border.color = card.border.color ?? '#2fa8ff';
          }
        });
      },
    });
    const cardBorderStyle = computed<'solid' | 'dashed' | 'dotted'>({
      get: () => selectedWidget.value?.style?.card?.border?.style ?? 'solid',
      set: value =>
        updateCard(card => {
          if (!card.border) {
            card.border = {};
          }
          card.border.style = value;
        }),
    });
    const cardBorderColor = computed<string>({
      get: () => selectedWidget.value?.style?.card?.border?.color ?? '#2fa8ff',
      set: value =>
        updateCard(card => {
          if (!card.border) {
            card.border = {};
          }
          card.border.color = value || '#2fa8ff';
        }),
    });
    const cardBorderRadius = computed<number>({
      get: () => selectedWidget.value?.style?.card?.border?.radius ?? 6,
      set: value =>
        updateCard(card => {
          if (!card.border) {
            card.border = {};
          }
          card.border.radius = clamp(Math.round(value || 0), 0, 32);
        }),
    });
    const cardPadding = computed<number>({
      get: () => selectedWidget.value?.style?.card?.padding ?? 0,
      set: value => updateCard(card => (card.padding = clamp(Math.round(value || 0), 0, 48))),
    });
    const cardOpacity = computed<number>({
      get: () => selectedWidget.value?.style?.card?.opacity ?? 1,
      set: value => updateCard(card => (card.opacity = Math.max(0, Math.min(1, Number(value) || 0)))),
    });
    const cardBackdropBlur = computed<number>({
      get: () => selectedWidget.value?.style?.card?.backdropBlur ?? 0,
      set: value => updateCard(card => (card.backdropBlur = clamp(Math.round(value || 0), 0, 20))),
    });
    const cardShadowBlur = computed<number>({
      get: () => selectedWidget.value?.style?.card?.shadow?.blur ?? 0,
      set: value =>
        updateCard(card => {
          if (!card.shadow) {
            card.shadow = { color: 'rgba(0, 0, 0, 0.35)' };
          }
          card.shadow.blur = clamp(Math.round(value || 0), 0, 100);
        }),
    });
    const cardShadowColor = computed<string>({
      get: () => selectedWidget.value?.style?.card?.shadow?.color ?? 'rgba(0, 0, 0, 0.35)',
      set: value =>
        updateCard(card => {
          if (!card.shadow) {
            card.shadow = {};
          }
          card.shadow.color = value || 'rgba(0, 0, 0, 0.35)';
        }),
    });

    const titleShow = computed<boolean>({
      get: () => {
        const explicit = selectedWidget.value?.style?.title?.show;
        if (explicit !== undefined) {
          return explicit;
        }
        return selectedWidget.value?.type !== 'html' || !!selectedWidget.value?.title?.trim();
      },
      set: value => updateTitle(title => (title.show = value)),
    });
    const titleIcon = computed<string>({
      get: () => selectedWidget.value?.style?.title?.icon ?? '',
      set: value =>
        updateTitle(title => {
          const icon = value.trim();
          if (icon) {
            title.icon = icon;
          } else {
            delete title.icon;
          }
        }),
    });
    const titleFontSize = computed<number>({
      get: () => selectedWidget.value?.style?.title?.fontSize ?? 14,
      set: value => updateTitle(title => (title.fontSize = clamp(Math.round(value || 14), 10, 48))),
    });
    const titleColor = computed<string>({
      get: () => selectedWidget.value?.style?.title?.color ?? '#cfeaff',
      set: value => updateTitle(title => (title.color = value || '#cfeaff')),
    });
    const titleAlign = computed<'left' | 'center' | 'right'>({
      get: () => selectedWidget.value?.style?.title?.align ?? 'left',
      set: value => updateTitle(title => (title.align = value)),
    });
    const titleHeight = computed<number>({
      get: () => selectedWidget.value?.style?.title?.height ?? 0,
      set: value => updateTitle(title => (title.height = clamp(Math.round(value || 0), 0, 80))),
    });
    const titleLetterSpacing = computed<number>({
      get: () => selectedWidget.value?.style?.title?.letterSpacing ?? 0,
      set: value => updateTitle(title => (title.letterSpacing = clamp(Math.round(value || 0), 0, 8))),
    });
    const titleDecoration = computed<'none' | 'underline' | 'gradient-underline' | 'left-bar'>({
      get: () => selectedWidget.value?.style?.title?.decoration ?? 'none',
      set: value => updateTitle(title => (title.decoration = value)),
    });
    const titleBackground = computed<string>({
      get: () => selectedWidget.value?.style?.title?.background ?? '#0c2238',
      set: value => updateTitle(title => (title.background = value || '#0c2238')),
    });

    const bodyPadding = computed<number>({
      get: () => selectedWidget.value?.style?.body?.padding ?? 8,
      set: value => updateBody(body => (body.padding = clamp(Math.round(value || 0), 0, 32))),
    });

    const resetWidgetStyle = () => {
      const widget = selectedWidget.value;
      if (!widget) {
        return;
      }
      widget.style = undefined;
    };

    const kpiLabel = computed<string>({
      get: () => selectedWidget.value?.style?.kpi?.label ?? '',
      set: value =>
        updateKpi(kpi => {
          const label = value.trim();
          if (label) {
            kpi.label = label;
          } else {
            delete kpi.label;
          }
        }),
    });
    const kpiIcon = computed<string>({
      get: () => selectedWidget.value?.style?.kpi?.icon ?? '',
      set: value =>
        updateKpi(kpi => {
          const icon = value.trim();
          if (icon) {
            kpi.icon = icon;
          } else {
            delete kpi.icon;
          }
        }),
    });
    const kpiLayout = computed<KpiLayout>({
      get: () => selectedWidget.value?.style?.kpi?.layout ?? 'label-top',
      set: value => updateKpi(kpi => (kpi.layout = value)),
    });
    const kpiValueFontSize = computed<number>({
      get: () => selectedWidget.value?.style?.kpi?.value?.fontSize ?? 32,
      set: value =>
        updateKpi(kpi => {
          if (!kpi.value) {
            kpi.value = {};
          }
          kpi.value.fontSize = clamp(Math.round(value || 32), 12, 120);
        }),
    });
    const kpiValueColor = computed<string>({
      get: () => selectedWidget.value?.style?.kpi?.value?.color ?? '#eaf6ff',
      set: value =>
        updateKpi(kpi => {
          if (!kpi.value) {
            kpi.value = {};
          }
          kpi.value.color = value || '#eaf6ff';
        }),
    });
    const kpiValueFontFamily = computed<'system' | 'number-digital' | 'number-condensed'>({
      get: () => selectedWidget.value?.style?.kpi?.value?.fontFamily ?? 'system',
      set: value =>
        updateKpi(kpi => {
          if (!kpi.value) {
            kpi.value = {};
          }
          kpi.value.fontFamily = value;
        }),
    });
    const kpiValueBold = computed<boolean>({
      get: () => selectedWidget.value?.style?.kpi?.value?.bold ?? true,
      set: value =>
        updateKpi(kpi => {
          if (!kpi.value) {
            kpi.value = {};
          }
          kpi.value.bold = value;
        }),
    });
    const kpiValueLetterSpacing = computed<number>({
      get: () => selectedWidget.value?.style?.kpi?.value?.letterSpacing ?? 0,
      set: value =>
        updateKpi(kpi => {
          if (!kpi.value) {
            kpi.value = {};
          }
          kpi.value.letterSpacing = clamp(Math.round(value || 0), 0, 8);
        }),
    });
    const ensureKpiUnit = (kpi: WidgetKpiStyle) => {
      if (!kpi.unit) {
        kpi.unit = {};
      }
      return kpi.unit;
    };
    const kpiUnitText = computed<string>({
      get: () => selectedWidget.value?.style?.kpi?.unit?.text ?? '',
      set: value =>
        updateKpi(kpi => {
          const unit = ensureKpiUnit(kpi);
          const text = value.trim();
          if (text) {
            unit.text = text;
          } else {
            delete unit.text;
          }
        }),
    });
    const kpiUnitFontSize = computed<number>({
      get: () => selectedWidget.value?.style?.kpi?.unit?.fontSize ?? 14,
      set: value => updateKpi(kpi => (ensureKpiUnit(kpi).fontSize = clamp(Math.round(value || 14), 10, 40))),
    });
    const kpiUnitColor = computed<string>({
      get: () => selectedWidget.value?.style?.kpi?.unit?.color ?? '#8fb6d6',
      set: value => updateKpi(kpi => (ensureKpiUnit(kpi).color = value || '#8fb6d6')),
    });
    const kpiAnimate = computed<boolean>({
      get: () => selectedWidget.value?.style?.kpi?.animate === true,
      set: value => updateKpi(kpi => (kpi.animate = value)),
    });
    const kpiDecimals = computed<number>({
      get: () => selectedWidget.value?.style?.kpi?.decimals ?? selectedWidget.value?.format?.precision ?? 0,
      set: value => updateKpi(kpi => (kpi.decimals = clamp(Math.round(value || 0), 0, 6))),
    });

    const updateChartStyle = (patch: (chart: WidgetChartStyle) => void) => {
      const style = ensureSelectedStyle();
      if (!style) {
        return;
      }
      if (!style.chart) {
        style.chart = {};
      }
      patch(style.chart);
    };

    const chartStylePreset = computed<'default' | ChartStylePreset>({
      get: () => selectedWidget.value?.style?.chart?.preset ?? 'default',
      set: value => {
        const style = ensureSelectedStyle();
        if (!style) {
          return;
        }
        if (value === 'default') {
          // 只清掉预设本身，保留用户单独配置的轴/图例/系列样式。
          if (style.chart) {
            delete style.chart.preset;
            if (Object.keys(style.chart).length === 0) {
              delete style.chart;
            }
          }
          return;
        }
        if (!style.chart) {
          style.chart = {};
        }
        style.chart.preset = value;
      },
    });
    const chartStyleGradient = computed<boolean>({
      get: () => selectedWidget.value?.style?.chart?.gradient ?? (selectedWidget.value?.style?.chart?.preset ?? 'tech') !== 'minimal',
      set: value => updateChartStyle(chart => (chart.gradient = value)),
    });
    const chartStyleGlow = computed<boolean>({
      get: () => selectedWidget.value?.style?.chart?.glow ?? (selectedWidget.value?.style?.chart?.preset ?? 'tech') !== 'minimal',
      set: value => updateChartStyle(chart => (chart.glow = value)),
    });

    /** 把图表样式里已清空的轴 / 预设对象一并移除，避免空对象触发预设皮肤。 */
    const pruneChartStyle = () => {
      const widget = selectedWidget.value;
      const style = widget?.style;
      const chart = style?.chart;
      if (!widget || !style || !chart) {
        return;
      }
      for (const key of ['xAxis', 'yAxis'] as const) {
        const axis = chart[key];
        if (axis && Object.keys(axis).length === 0) {
          delete chart[key];
        }
      }
      if (Object.keys(chart).length === 0) {
        delete style.chart;
        if (Object.keys(style).length === 0) {
          delete widget.style;
        }
      }
    };

    const updateChartAxis = (key: 'xAxis' | 'yAxis', patch: (axis: WidgetChartAxisStyle) => void) => {
      const style = ensureSelectedStyle();
      if (!style) {
        return;
      }
      if (!style.chart) {
        style.chart = {};
      }
      if (!style.chart[key]) {
        style.chart[key] = {};
      }
      patch(style.chart[key] as WidgetChartAxisStyle);
    };

    const chartLegendPosition = computed<LegendPosition>({
      get: () => selectedWidget.value?.style?.chart?.legendPosition ?? 'top',
      set: value => updateChartStyle(chart => (chart.legendPosition = value)),
    });
    const chartLegendFontSize = computed<number>({
      get: () => selectedWidget.value?.style?.chart?.legendFontSize ?? 11,
      set: value => updateChartStyle(chart => (chart.legendFontSize = clamp(Math.round(value || 0), 8, 24))),
    });
    const chartAxisTitleFontSize = computed<number>({
      get: () => selectedWidget.value?.style?.chart?.axisTitleFontSize ?? 12,
      set: value => updateChartStyle(chart => (chart.axisTitleFontSize = clamp(Math.round(value || 0), 8, 24))),
    });
    const chartAxisTitleColor = computed<string>({
      // 空值表示跟随图表风格预设；控件上给一个中性灰作为占位，避免出现空色块。
      get: () => selectedWidget.value?.style?.chart?.axisTitleColor ?? '#909399',
      set: value => updateChartStyle(chart => (chart.axisTitleColor = value || '#909399')),
    });
    /** 读取 echartsOption 里已有的轴标题，作为样式覆盖为空时的展示值。 */
    const chartOptionAxisName = (key: 'xAxis' | 'yAxis'): string => {
      const axis = selectedWidget.value?.echartsOption?.[key];
      const first = Array.isArray(axis) ? axis[0] : axis;
      return typeof first?.name === 'string' ? first.name : '';
    };
    const chartXAxisTitle = computed<string>({
      get: () => selectedWidget.value?.style?.chart?.xAxis?.title ?? chartOptionAxisName('xAxis'),
      set: value => {
        const title = value.trim();
        updateChartAxis('xAxis', axis => {
          if (title) {
            axis.title = title;
          } else {
            delete axis.title;
          }
        });
        if (!title) {
          pruneChartStyle();
        }
      },
    });
    const chartYAxisTitle = computed<string>({
      get: () => selectedWidget.value?.style?.chart?.yAxis?.title ?? chartOptionAxisName('yAxis'),
      set: value => {
        const title = value.trim();
        updateChartAxis('yAxis', axis => {
          if (title) {
            axis.title = title;
          } else {
            delete axis.title;
          }
        });
        if (!title) {
          pruneChartStyle();
        }
      },
    });
    const chartXAxisVisible = computed<boolean>({
      get: () => {
        const widget = selectedWidget.value;
        const chart = widget?.style?.chart;
        if (chart?.xAxis?.show !== undefined) {
          return chart.xAxis.show;
        }
        if (chart?.showAxis !== undefined) {
          return chart.showAxis;
        }
        const axis = widget?.echartsOption?.xAxis;
        return Array.isArray(axis) ? axis.every((item: any) => item?.show !== false) : axis?.show !== false;
      },
      set: value => updateChartAxis('xAxis', axis => (axis.show = value)),
    });
    const chartYAxisVisible = computed<boolean>({
      get: () => {
        const widget = selectedWidget.value;
        const chart = widget?.style?.chart;
        if (chart?.yAxis?.show !== undefined) {
          return chart.yAxis.show;
        }
        if (chart?.showAxis !== undefined) {
          return chart.showAxis;
        }
        const axis = widget?.echartsOption?.yAxis;
        return Array.isArray(axis) ? axis.every((item: any) => item?.show !== false) : axis?.show !== false;
      },
      set: value => updateChartAxis('yAxis', axis => (axis.show = value)),
    });
    const chartXAxisLabelFontSize = computed<number>({
      get: () => selectedWidget.value?.style?.chart?.xAxis?.labelFontSize ?? 11,
      set: value => updateChartAxis('xAxis', axis => (axis.labelFontSize = clamp(Math.round(value || 0), 8, 24))),
    });
    const chartYAxisLabelFontSize = computed<number>({
      get: () => selectedWidget.value?.style?.chart?.yAxis?.labelFontSize ?? 11,
      set: value => updateChartAxis('yAxis', axis => (axis.labelFontSize = clamp(Math.round(value || 0), 8, 24))),
    });
    const chartGridLineStyle = computed<LineStyleType>({
      get: () => selectedWidget.value?.style?.chart?.yAxis?.gridStyle ?? 'dashed',
      set: value => updateChartAxis('yAxis', axis => (axis.gridStyle = value)),
    });
    const chartAxisTick = computed<boolean>({
      get: () => selectedWidget.value?.style?.chart?.xAxis?.tick ?? selectedWidget.value?.style?.chart?.yAxis?.tick ?? false,
      set: value => {
        updateChartAxis('xAxis', axis => (axis.tick = value));
        updateChartAxis('yAxis', axis => (axis.tick = value));
      },
    });
    const chartAxisLineColor = computed<string>({
      get: () => selectedWidget.value?.style?.chart?.xAxis?.lineColor ?? selectedWidget.value?.style?.chart?.yAxis?.lineColor ?? '#c0c4cc',
      set: value => {
        const apply = (axis: WidgetChartAxisStyle) => {
          if (value) {
            axis.lineColor = value;
          } else {
            delete axis.lineColor;
          }
        };
        updateChartAxis('xAxis', apply);
        updateChartAxis('yAxis', apply);
        if (!value) {
          pruneChartStyle();
        }
      },
    });
    const chartBarRadius = computed<number>({
      get: () => selectedWidget.value?.style?.chart?.barRadius ?? 3,
      set: value => updateChartStyle(chart => (chart.barRadius = clamp(Math.round(value || 0), 0, 16))),
    });
    const chartLabelFontSize = computed<number>({
      get: () => selectedWidget.value?.style?.chart?.labelFontSize ?? 11,
      set: value => updateChartStyle(chart => (chart.labelFontSize = clamp(Math.round(value || 0), 8, 24))),
    });
    /** 占总比：由服务端下推聚合时按维度分组换算百分比。 */
    const chartPercentOfTotal = computed<boolean>({
      get: () => (selectedWidget.value?.encode?.transform ?? []).some(transform => transform.type === 'percentOfTotal'),
      set: value => {
        const encode = selectedWidget.value?.encode;
        if (!encode) {
          return;
        }
        const rest = (encode.transform ?? []).filter(transform => transform.type !== 'percentOfTotal');
        if (value) {
          rest.push({ type: 'percentOfTotal' });
        }
        if (rest.length > 0) {
          encode.transform = rest;
        } else {
          delete encode.transform;
        }
      },
    });
    /** 自定义配置面板手风琴展开的分组。 */
    const customSections = ref<string[]>(['background']);

    const canvasZoom = ref<'fit' | number>('fit');
    const canvasScale = ref(1);
    const systemScreen = getSystemScreenSize();
    const screenPresets = computed(() => {
      if (SCREEN_PRESETS.some(preset => preset.width === systemScreen.width && preset.height === systemScreen.height)) {
        return SCREEN_PRESETS;
      }
      return [
        {
          key: 'system',
          label: `当前屏幕 ${systemScreen.width} × ${systemScreen.height}`,
          width: systemScreen.width,
          height: systemScreen.height,
        },
        ...SCREEN_PRESETS,
      ];
    });

    const isScreenMode = computed(() => spec.value.layout?.mode === 'screen');
    const canvasMetrics = computed(() => resolveCanvasMetrics(spec.value.layout));
    /** px 画布：组件按像素自由定位且允许叠放。 */
    const isPixelCanvas = computed(() => canvasMetrics.value.unit === 'px');

    const ensureScreenSettings = (): DashboardScreen => {
      if (!spec.value.layout) {
        spec.value.layout = normalizeDashboardLayout();
      }
      if (!spec.value.layout.screen) {
        const system = getSystemScreenSize();
        spec.value.layout.screen = {
          width: system.width,
          height: system.height,
          fitMode: 'stretch',
          theme: 'light',
          canvas: { unit: 'grid', padding: DEFAULT_CANVAS_PADDING, gap: DEFAULT_CANVAS_GAP },
          autoRefreshSeconds: 0,
        };
      }
      return spec.value.layout.screen;
    };

    const ensureCanvasSettings = (): ScreenCanvas => {
      const screen = ensureScreenSettings();
      if (!screen.canvas) {
        screen.canvas = { unit: 'grid', padding: DEFAULT_CANVAS_PADDING, gap: DEFAULT_CANVAS_GAP };
      }
      return screen.canvas;
    };

    /** 在栅格与像素坐标系之间换算已有组件布局，避免切换后组件尺寸失真。 */
    const scaleLayouts = (from: CanvasUnit, to: CanvasUnit) => {
      if (from === to) {
        return;
      }
      const metrics = canvasMetrics.value;
      const contentWidth = canvasContentWidth(metrics);
      const rowStep = metrics.rowHeight + metrics.gap;
      spec.value.widgets.forEach(widget => {
        const layout = widget.layout;
        if (!layout) {
          return;
        }
        if (to === 'px') {
          widget.layout = clampPixelLayout(
            {
              x: Math.round((layout.x / metrics.columns) * contentWidth),
              y: Math.round(layout.y * rowStep),
              w: Math.round((layout.w / metrics.columns) * contentWidth),
              h: Math.round(layout.h * rowStep),
            },
            metrics,
          );
        } else {
          widget.layout = normalizeLayout(
            {
              x: Math.round((layout.x / contentWidth) * metrics.columns),
              y: Math.round(layout.y / rowStep),
              w: Math.max(1, Math.round((layout.w / contentWidth) * metrics.columns)),
              h: Math.max(1, Math.round(layout.h / rowStep)),
            },
            metrics.columns,
          );
        }
      });
    };

    const canvasUnit = computed<CanvasUnit>({
      get: () => (isScreenMode.value ? (spec.value.layout?.screen?.canvas?.unit ?? 'grid') : 'grid'),
      set: unit => {
        const next = unit === 'px' ? 'px' : 'grid';
        const current = spec.value.layout?.screen?.canvas?.unit ?? 'grid';
        scaleLayouts(current, next);
        ensureCanvasSettings().unit = next;
        canvasZoom.value = 'fit';
      },
    });

    const canvasPadding = computed<number>({
      get: () => spec.value.layout?.screen?.canvas?.padding ?? DEFAULT_CANVAS_PADDING,
      set: value => {
        const padding = clamp(Math.round(value || 0), 0, 64);
        ensureCanvasSettings().padding = padding;
        if (isPixelCanvas.value) {
          const metrics = { ...canvasMetrics.value, padding };
          spec.value.widgets.forEach(widget => {
            widget.layout = clampPixelLayout(widget.layout, metrics);
          });
        }
      },
    });

    const canvasGap = computed<number>({
      get: () => spec.value.layout?.screen?.canvas?.gap ?? DEFAULT_CANVAS_GAP,
      set: value => {
        ensureCanvasSettings().gap = clamp(Math.round(value || 0), 0, 48);
      },
    });

    // ---------------------------------------------------------------- 主题 token

    const themePresetOptions = THEME_PRESET_OPTIONS;
    const themePreset = computed<ThemePreset>({
      get: () => spec.value.layout?.screen?.themePreset ?? 'custom',
      set: value => {
        const screen = ensureScreenSettings();
        screen.themePreset = value;
        if (value !== 'custom') {
          // 预设均为深色科技风，切换时同步底层明暗主题。
          screen.theme = 'dark';
        }
      },
    });
    const resolvedThemeTokens = computed(() =>
      resolveThemeTokens(spec.value.layout?.screen?.themePreset, spec.value.layout?.screen?.tokens),
    );
    const ensureThemeTokens = (): ThemeTokens => {
      const screen = ensureScreenSettings();
      if (!screen.tokens) {
        screen.tokens = {};
      }
      return screen.tokens;
    };
    const setTokenColor = (key: 'primary' | 'secondary' | 'accent' | 'surface' | 'border' | 'text', value: string | null) => {
      const tokens = ensureThemeTokens();
      if (value) {
        tokens[key] = value;
      } else {
        delete tokens[key];
      }
    };
    const themePrimary = computed<string>({
      get: () => resolvedThemeTokens.value.primary ?? '#2fa8ff',
      set: value => setTokenColor('primary', value),
    });
    const themeSecondary = computed<string>({
      get: () => resolvedThemeTokens.value.secondary ?? '#1de9b6',
      set: value => setTokenColor('secondary', value),
    });
    const themeAccent = computed<string>({
      get: () => resolvedThemeTokens.value.accent ?? '#ffd54f',
      set: value => setTokenColor('accent', value),
    });
    const themeSurface = computed<string>({
      get: () => resolvedThemeTokens.value.surface ?? 'rgba(9, 26, 48, 0.55)',
      set: value => setTokenColor('surface', value),
    });
    const themeBorder = computed<string>({
      get: () => resolvedThemeTokens.value.border ?? 'rgba(47, 168, 255, 0.35)',
      set: value => setTokenColor('border', value),
    });
    const themeText = computed<string>({
      get: () => resolvedThemeTokens.value.text ?? '#d8ecff',
      set: value => setTokenColor('text', value),
    });
    const resetThemeTokens = () => {
      const screen = ensureScreenSettings();
      delete screen.tokens;
    };

    const updateScreenResolution = (width: number, height: number) => {
      const screen = ensureScreenSettings();
      screen.width = Math.min(7680, Math.max(640, Math.round(width || DEFAULT_SCREEN_WIDTH)));
      screen.height = Math.min(4320, Math.max(480, Math.round(height || DEFAULT_SCREEN_HEIGHT)));
      if (isPixelCanvas.value) {
        const metrics = resolveCanvasMetrics(spec.value.layout);
        spec.value.widgets.forEach(widget => {
          widget.layout = clampPixelLayout(widget.layout, metrics);
        });
      }
      canvasZoom.value = 'fit';
    };

    const layoutMode = computed<DashboardMode>({
      get: () => spec.value.layout?.mode ?? 'standard',
      set: mode => {
        if (!spec.value.layout) {
          spec.value.layout = normalizeDashboardLayout();
        }
        const current = spec.value.layout.mode ?? 'standard';
        if (current === mode) {
          return;
        }
        const unit = spec.value.layout.screen?.canvas?.unit ?? 'grid';
        if (unit === 'px') {
          // 像素画布切到标准模式前先换算回栅格，避免把像素值当成列号渲染。
          if (mode === 'standard') {
            scaleLayouts('px', 'grid');
          }
          spec.value.layout.mode = mode;
          if (mode === 'screen') {
            scaleLayouts('grid', 'px');
          }
        } else {
          spec.value.layout.mode = mode;
        }
        canvasZoom.value = 'fit';
      },
    });

    const screenWidth = computed<number>({
      get: () => spec.value.layout?.screen?.width ?? DEFAULT_SCREEN_WIDTH,
      set: value => updateScreenResolution(value, spec.value.layout?.screen?.height ?? DEFAULT_SCREEN_HEIGHT),
    });

    const screenHeight = computed<number>({
      get: () => spec.value.layout?.screen?.height ?? DEFAULT_SCREEN_HEIGHT,
      set: value => updateScreenResolution(spec.value.layout?.screen?.width ?? DEFAULT_SCREEN_WIDTH, value),
    });

    const screenFitMode = computed<ScreenFitMode>({
      get: () => normalizeFitMode(spec.value.layout?.screen?.fitMode),
      set: value => {
        ensureScreenSettings().fitMode = normalizeFitMode(value);
      },
    });

    const screenTheme = computed<DashboardTheme>({
      get: () => normalizeTheme(spec.value.layout?.screen?.theme),
      set: value => {
        ensureScreenSettings().theme = normalizeTheme(value);
      },
    });

    const screenThemeClass = computed(() => (screenTheme.value === 'dark' ? 'dashboard-theme-dark' : 'dashboard-theme-light'));
    const fitModeOptions = SCREEN_FIT_MODES;
    const fitModeHint = computed(() => SCREEN_FIT_MODES.find(option => option.value === screenFitMode.value)?.hint ?? '');

    const backgroundService = new DashboardBackgroundService();
    const backgroundUploading = ref(false);
    const backgroundUploadRef = ref<{ clearFiles?: () => void } | null>(null);
    const screenBackground = computed(() => spec.value.layout?.screen?.background);
    const gradientDefaults = () => (screenTheme.value === 'dark' ? { from: '#0f172a', to: '#1e293b' } : { from: '#eef2ff', to: '#dbeafe' });

    const backgroundType = computed<DashboardBackgroundType>({
      get: () => screenBackground.value?.type ?? 'color',
      set: type => {
        const current = screenBackground.value;
        const defaultColor = screenTheme.value === 'dark' ? DARK_SCREEN_BACKGROUND : LIGHT_SCREEN_BACKGROUND;
        if (type === 'image') {
          ensureScreenSettings().background = {
            type: 'image',
            imagePath: current?.imagePath,
            imageContentType: current?.imageContentType,
          };
        } else if (type === 'gradient') {
          const defaults = gradientDefaults();
          ensureScreenSettings().background = {
            type: 'gradient',
            gradient: current?.gradient ?? { ...defaults, angle: 135 },
          };
        } else {
          ensureScreenSettings().background = { type: 'color', color: current?.color ?? defaultColor };
        }
      },
    });

    const backgroundColor = computed<string>({
      get: () => {
        const defaultColor = screenTheme.value === 'dark' ? DARK_SCREEN_BACKGROUND : LIGHT_SCREEN_BACKGROUND;
        return screenBackground.value?.type === 'color' ? (screenBackground.value.color ?? defaultColor) : defaultColor;
      },
      set: color => {
        const defaultColor = screenTheme.value === 'dark' ? DARK_SCREEN_BACKGROUND : LIGHT_SCREEN_BACKGROUND;
        ensureScreenSettings().background = { type: 'color', color: color || defaultColor };
      },
    });

    const updateGradient = (patch: Partial<{ from: string; to: string; angle: number }>) => {
      const current = screenBackground.value?.type === 'gradient' ? screenBackground.value.gradient : undefined;
      const defaults = gradientDefaults();
      ensureScreenSettings().background = {
        type: 'gradient',
        gradient: {
          from: current?.from ?? defaults.from,
          to: current?.to ?? defaults.to,
          angle: current?.angle ?? 135,
          ...patch,
        },
      };
    };

    const gradientFrom = computed<string>({
      get: () =>
        screenBackground.value?.type === 'gradient'
          ? (screenBackground.value.gradient?.from ?? gradientDefaults().from)
          : gradientDefaults().from,
      set: from => updateGradient({ from }),
    });
    const gradientTo = computed<string>({
      get: () =>
        screenBackground.value?.type === 'gradient'
          ? (screenBackground.value.gradient?.to ?? gradientDefaults().to)
          : gradientDefaults().to,
      set: to => updateGradient({ to }),
    });
    const gradientAngle = computed<number>({
      get: () => (screenBackground.value?.type === 'gradient' ? (screenBackground.value.gradient?.angle ?? 135) : 135),
      set: angle => updateGradient({ angle: Math.min(360, Math.max(0, Math.round(angle || 0))) }),
    });

    const backgroundImagePath = computed(() => (screenBackground.value?.type === 'image' ? (screenBackground.value.imagePath ?? '') : ''));
    const { imageUrl: backgroundImageUrl } = useDashboardBackgroundImage(backgroundImagePath);
    const screenBackgroundStyle = computed(() => resolveScreenBackgroundStyle(spec.value.layout, backgroundImageUrl.value));

    const onBackgroundFileChange = async (uploadFile: any) => {
      const file: File | undefined = uploadFile?.raw;
      if (!file) {
        return;
      }
      const allowedTypes = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];
      if (!allowedTypes.includes(file.type)) {
        ElMessage.warning('仅支持 PNG、JPEG、WebP、GIF 图片');
        return;
      }
      if (file.size > 5 * 1024 * 1024) {
        ElMessage.warning('背景图不能超过 5MB');
        return;
      }
      backgroundUploading.value = true;
      try {
        const result = await backgroundService.upload(file);
        ensureScreenSettings().background = {
          type: 'image',
          imagePath: result.path,
          imageContentType: result.contentType,
        };
        backgroundUploadRef.value?.clearFiles?.();
        ElMessage.success('背景图已上传');
      } catch (err: any) {
        ElMessage.error(errorMessage(err, '背景图上传失败'));
      } finally {
        backgroundUploading.value = false;
      }
    };

    const removeBackgroundImage = () => {
      // 只解除看板与图片的引用，避免撤销/重做时文件已被删除导致背景失效。
      ensureScreenSettings().background = undefined;
    };

    const autoRefreshSeconds = computed<number>({
      get: () => spec.value.layout?.screen?.autoRefreshSeconds ?? 0,
      set: value => {
        ensureScreenSettings().autoRefreshSeconds = Math.min(86400, Math.max(0, Math.round(value || 0)));
      },
    });

    const screenPresetValue = computed(() => {
      const screen = spec.value.layout?.screen;
      return screenPresets.value.find(item => item.width === screen?.width && item.height === screen?.height)?.key ?? 'custom';
    });

    const currentScale = computed(() => (typeof canvasZoom.value === 'number' ? canvasZoom.value : canvasScale.value));
    const zoomPercent = computed(() => Math.round(currentScale.value * 100));

    const applyScreenPreset = (key: string) => {
      const preset = screenPresets.value.find(item => item.key === key);
      if (!preset) {
        return;
      }
      updateScreenResolution(preset.width, preset.height);
    };

    const setZoom = (value: number) => {
      canvasZoom.value = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, Math.round(value * 100) / 100));
    };
    const zoomIn = () => setZoom(currentScale.value + ZOOM_STEP);
    const zoomOut = () => setZoom(currentScale.value - ZOOM_STEP);
    const fitCanvas = () => {
      canvasZoom.value = 'fit';
    };
    const onCanvasScaleChange = (value: number) => {
      canvasScale.value = value;
    };

    const isCanvasOverflow = computed(() => {
      const layout = spec.value.layout;
      const screen = layout?.screen;
      if (!isScreenMode.value || !screen) {
        return false;
      }
      const topLevelWidgets = spec.value.widgets.filter(widget => !widget.parentId);
      if (isPixelCanvas.value) {
        return pixelContentHeight(topLevelWidgets, canvasMetrics.value.padding) > screen.height;
      }
      return (
        layoutContentHeight(topLevelWidgets, layout.rowHeight ?? DEFAULT_ROW_HEIGHT, canvasMetrics.value.gap, canvasMetrics.value.padding) >
        screen.height
      );
    });

    const canvasBindings = computed(() => ({
      spec: spec.value,
      datasetData: datasetData.value,
      widgetData: widgetData.value,
      loadingDatasets: loadingDatasets.value,
      loadingWidgets: loadingWidgets.value,
      widgetResetKeys: widgetResetKeys.value,
      datasetErrors: datasetErrors.value,
      widgetErrors: widgetErrors.value,
      serverPaginationWidgets: serverPaginationWidgets.value,
      loadWidgetPage,
      loadDrill,
      linkage: linkage.value,
      linkageWidgetIds: linkageWidgetIds.value,
      applyLinkage,
      clearLinkage,
      dataVersion: dataVersion.value,
      selectedId: selectedWidgetId.value,
      dragItem: paletteDragItem.value,
    }));

    const hasDesignContents = computed(
      () => spec.value.widgets.length > 0 || spec.value.datasets.length > 0 || (spec.value.filters?.length ?? 0) > 0,
    );
    const aiPlaceholder = computed(() =>
      hasDesignContents.value ? '继续描述调整要求，例如：把销售趋势改成柱状图，并按区域拆分' : '例如：帮我做一个电商销售分析看板',
    );

    /**
     * 快照式撤销/重做：属性编辑防抖合并，结构性操作立即入栈。
     * historyPending 表示已有尚未写入历史栈的防抖编辑，此时也应允许撤销。
     */
    const history = shallowRef<DesignerSnapshot[]>([]);
    const historyIndex = ref(-1);
    const historyPending = ref(false);
    const isRestoring = ref(false);
    let historyTimer: ReturnType<typeof setTimeout> | null = null;
    let historyInitialized = false;

    const snapshotState = (): DesignerSnapshot => ({
      name: name.value,
      description: description.value,
      status: status.value,
      spec: JSON.parse(JSON.stringify(spec.value)) as DashboardSpec,
    });

    const commitHistory = () => {
      if (!historyInitialized || isRestoring.value) {
        return;
      }
      if (historyTimer) {
        clearTimeout(historyTimer);
        historyTimer = null;
      }
      historyPending.value = false;
      const snapshot = snapshotState();
      const key = JSON.stringify(snapshot);
      const current = history.value[historyIndex.value];
      if (current && JSON.stringify(current) === key) {
        return;
      }
      const next = history.value.slice(0, historyIndex.value + 1);
      next.push(snapshot);
      if (next.length > HISTORY_LIMIT) {
        next.shift();
      }
      history.value = next;
      historyIndex.value = next.length - 1;
    };

    const scheduleHistoryCommit = () => {
      if (!historyInitialized || isRestoring.value) {
        return;
      }
      if (historyTimer) {
        clearTimeout(historyTimer);
      }
      historyPending.value = true;
      historyTimer = setTimeout(() => {
        historyTimer = null;
        commitHistory();
      }, HISTORY_COMMIT_DELAY);
    };

    const resetHistory = () => {
      if (historyTimer) {
        clearTimeout(historyTimer);
        historyTimer = null;
      }
      history.value = [snapshotState()];
      historyIndex.value = 0;
      historyPending.value = false;
      historyInitialized = true;
    };

    const canUndo = computed(() => historyIndex.value > 0 || historyPending.value);
    const canRedo = computed(() => !historyPending.value && historyIndex.value >= 0 && historyIndex.value < history.value.length - 1);

    const applySnapshot = (snapshot: DesignerSnapshot) => {
      const previousFilters = new Map<string, DashboardFilter>((spec.value.filters ?? []).map(filter => [filter.id, filter] as const));
      name.value = snapshot.name;
      description.value = snapshot.description;
      status.value = snapshot.status;
      spec.value = JSON.parse(JSON.stringify(snapshot.spec)) as DashboardSpec;

      const defaults = initFilterValues(spec.value.filters ?? []);
      const nextValues: FilterValues = {};
      for (const filter of spec.value.filters ?? []) {
        const previous = previousFilters.get(filter.id);
        nextValues[filter.id] =
          previous && previous.type === filter.type && Object.prototype.hasOwnProperty.call(filterValues.value, filter.id)
            ? filterValues.value[filter.id]
            : defaults[filter.id];
      }
      filterValues.value = nextValues;

      if (!spec.value.widgets.some(widget => widget.id === selectedWidgetId.value)) {
        selectedWidgetId.value = '';
      }
      if (!(spec.value.filters ?? []).some(filter => filter.id === selectedFilterId.value)) {
        selectedFilterId.value = '';
      }
      historyPending.value = false;
    };

    const undo = () => {
      if (saving.value || loading.value) {
        return;
      }
      commitHistory();
      if (historyIndex.value <= 0) {
        return;
      }
      isRestoring.value = true;
      historyIndex.value -= 1;
      applySnapshot(history.value[historyIndex.value]);
      nextTick(() => {
        isRestoring.value = false;
      });
    };

    const redo = () => {
      if (saving.value || loading.value) {
        return;
      }
      commitHistory();
      if (historyIndex.value < 0 || historyIndex.value >= history.value.length - 1) {
        return;
      }
      isRestoring.value = true;
      historyIndex.value += 1;
      applySnapshot(history.value[historyIndex.value]);
      nextTick(() => {
        isRestoring.value = false;
      });
    };

    const onHistoryKeydown = (event: KeyboardEvent) => {
      if (!(event.metaKey || event.ctrlKey)) {
        return;
      }
      const target = event.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT' || target.isContentEditable)
      ) {
        return;
      }
      const key = event.key.toLowerCase();
      if (key === 'z') {
        event.preventDefault();
        if (event.shiftKey) {
          redo();
        } else {
          undo();
        }
      } else if (key === 'y') {
        event.preventDefault();
        redo();
      }
    };

    watch([name, description, status], () => scheduleHistoryCommit());
    watch(spec, () => scheduleHistoryCommit(), { deep: true });
    watch([dashboardSettingsVisible, selectedWidgetId, selectedFilterId], async () => {
      await nextTick();
      if (propertiesRef.value) {
        propertiesRef.value.scrollTop = 0;
      }
    });

    const pendingFieldDefaults = new Set<string>();

    /**
     * 解析组件数据集当前可用的字段：优先使用预览结果类型，其次使用数据集字段缓存。
     */
    const fieldCandidates = (datasetId?: string): DashboardField[] => {
      if (!datasetId) {
        return [];
      }
      const result = datasetData.value[datasetId];
      const dashboardDataset = spec.value.datasets.find(dataset => dataset.id === datasetId);
      const available = dashboardDataset?.refId
        ? availableDatasets.value.find(dataset => dataset.id === dashboardDataset.refId)
        : undefined;
      const declared = available ? DatasetService.columns(DatasetService.parseFields(available)) : [];
      const columns = result?.columns?.length ? result.columns : declared.map(column => column.name);
      return buildDashboardFields(columns, result?.rows ?? [], declared, result?.columnLabels ?? {});
    };

    // ---------------------------------------------------------------- 地图图层

    const mapLayers = computed<MapLayerSpec[]>(() => selectedWidget.value?.chart?.layers ?? []);
    const findMapLayer = (kind: 'effectScatter' | 'lines') => mapLayers.value.find(layer => layer.kind === kind);

    const updateMapLayers = (mutate: (layers: MapLayerSpec[]) => void) => {
      const widget = selectedWidget.value;
      if (!widget) {
        return;
      }
      const layers = (widget.chart?.layers ?? []).map(layer => ({ ...layer }));
      mutate(layers);
      const chart = { ...(widget.chart ?? { type: 'map' as const }) };
      if (layers.length > 0) {
        chart.layers = layers;
      } else {
        delete chart.layers;
      }
      widget.chart = chart;
    };

    const updateMapLayer = (kind: 'effectScatter' | 'lines', patch: Partial<MapLayerSpec>) => {
      updateMapLayers(layers => {
        const index = layers.findIndex(layer => layer.kind === kind);
        if (index >= 0) {
          layers[index] = { ...layers[index], ...patch };
        }
      });
    };

    const removeMapLayer = (kind: 'effectScatter' | 'lines') => {
      updateMapLayers(layers => {
        const index = layers.findIndex(layer => layer.kind === kind);
        if (index >= 0) {
          layers.splice(index, 1);
        }
      });
    };

    const firstNumericField = (fields: DashboardField[]): string | undefined =>
      fields.find(field => field.numeric)?.name ?? fields[0]?.name;
    const guessField = (fields: DashboardField[], patterns: string[]): string | undefined =>
      fields.find(field => patterns.some(pattern => field.name.toLowerCase().includes(pattern)))?.name;

    /** 新建图层时按字段名猜测经纬度/名称，避免用户面对一堆空下拉。 */
    const buildDefaultMapLayer = (kind: 'effectScatter' | 'lines'): MapLayerSpec => {
      const datasetId = selectedWidget.value?.datasetId ?? spec.value.datasets[0]?.id;
      const fields = fieldCandidates(datasetId);
      const base: MapLayerSpec = { kind, ...(datasetId ? { datasetId } : {}) };
      if (kind === 'effectScatter') {
        const lngField = guessField(fields, ['lng', 'lon', '经度']);
        const latField = guessField(fields, ['lat', '纬度']);
        const nameField = guessField(fields, ['name', 'city', '名称', '城市', '省份']);
        const valueField = firstNumericField(fields);
        return {
          ...base,
          ...(lngField ? { lngField } : {}),
          ...(latField ? { latField } : {}),
          ...(nameField ? { nameField } : {}),
          ...(valueField ? { valueField } : {}),
          color: '#ffd54f',
          symbolSize: [12],
        };
      }
      const fromLngField = guessField(fields, ['from_lng', 'fromlon', 'from_lon', '起点经度']);
      const fromLatField = guessField(fields, ['from_lat', '起点纬度']);
      const toLngField = guessField(fields, ['to_lng', 'tolon', 'to_lon', '终点经度']);
      const toLatField = guessField(fields, ['to_lat', '终点纬度']);
      const valueField = firstNumericField(fields);
      return {
        ...base,
        ...(fromLngField ? { fromLngField } : {}),
        ...(fromLatField ? { fromLatField } : {}),
        ...(toLngField ? { toLngField } : {}),
        ...(toLatField ? { toLatField } : {}),
        ...(valueField ? { valueField } : {}),
        color: '#ffd54f',
        curveness: 0.2,
        effect: { show: true, period: 4, trailLength: 0.3 },
      };
    };

    const mapScatterEnabled = computed<boolean>({
      get: () => !!findMapLayer('effectScatter'),
      set: enabled => {
        if (enabled) {
          updateMapLayers(layers => {
            if (!layers.some(layer => layer.kind === 'effectScatter')) {
              layers.push(buildDefaultMapLayer('effectScatter'));
            }
          });
        } else {
          removeMapLayer('effectScatter');
        }
      },
    });
    const mapLinesEnabled = computed<boolean>({
      get: () => !!findMapLayer('lines'),
      set: enabled => {
        if (enabled) {
          updateMapLayers(layers => {
            if (!layers.some(layer => layer.kind === 'lines')) {
              layers.push(buildDefaultMapLayer('lines'));
            }
          });
        } else {
          removeMapLayer('lines');
        }
      },
    });

    const mapLayerField = (kind: 'effectScatter' | 'lines', key: keyof MapLayerSpec) =>
      computed<string>({
        get: () => {
          const value = findMapLayer(kind)?.[key];
          return typeof value === 'string' ? value : '';
        },
        set: value => updateMapLayer(kind, { [key]: value || undefined } as Partial<MapLayerSpec>),
      });

    const mapScatterDatasetId = computed<string>({
      get: () => findMapLayer('effectScatter')?.datasetId ?? '',
      set: value => {
        const fields = fieldCandidates(value);
        updateMapLayer('effectScatter', {
          datasetId: value || undefined,
          lngField: guessField(fields, ['lng', 'lon', '经度']),
          latField: guessField(fields, ['lat', '纬度']),
          nameField: guessField(fields, ['name', 'city', '名称', '城市', '省份']),
          valueField: firstNumericField(fields),
        });
      },
    });
    const mapLinesDatasetId = computed<string>({
      get: () => findMapLayer('lines')?.datasetId ?? '',
      set: value => {
        const fields = fieldCandidates(value);
        updateMapLayer('lines', {
          datasetId: value || undefined,
          fromLngField: guessField(fields, ['from_lng', 'fromlon', 'from_lon', '起点经度']),
          fromLatField: guessField(fields, ['from_lat', '起点纬度']),
          toLngField: guessField(fields, ['to_lng', 'tolon', 'to_lon', '终点经度']),
          toLatField: guessField(fields, ['to_lat', '终点纬度']),
          valueField: firstNumericField(fields),
        });
      },
    });

    const mapScatterLngField = mapLayerField('effectScatter', 'lngField');
    const mapScatterLatField = mapLayerField('effectScatter', 'latField');
    const mapScatterNameField = mapLayerField('effectScatter', 'nameField');
    const mapScatterValueField = mapLayerField('effectScatter', 'valueField');
    const mapLinesFromLngField = mapLayerField('lines', 'fromLngField');
    const mapLinesFromLatField = mapLayerField('lines', 'fromLatField');
    const mapLinesToLngField = mapLayerField('lines', 'toLngField');
    const mapLinesToLatField = mapLayerField('lines', 'toLatField');
    const mapLinesValueField = mapLayerField('lines', 'valueField');

    const mapScatterColor = computed<string>({
      get: () => findMapLayer('effectScatter')?.color ?? '#ffd54f',
      set: value => updateMapLayer('effectScatter', { color: value || '#ffd54f' }),
    });
    const mapScatterSize = computed<number>({
      get: () => findMapLayer('effectScatter')?.symbolSize?.[0] ?? 12,
      set: value => updateMapLayer('effectScatter', { symbolSize: [clamp(Math.round(value || 12), 2, 80)] }),
    });
    const mapLinesColor = computed<string>({
      get: () => findMapLayer('lines')?.color ?? '#ffd54f',
      set: value => updateMapLayer('lines', { color: value || '#ffd54f' }),
    });
    const mapLinesCurveness = computed<number>({
      get: () => findMapLayer('lines')?.curveness ?? 0.2,
      set: value => updateMapLayer('lines', { curveness: Math.max(0, Math.min(1, Number(value) || 0)) }),
    });

    // ---------------------------------------------------------------- 标题栏 / 日期时间

    const updateWidgetProps = (patch: (props: WidgetProps) => void) => {
      const widget = selectedWidget.value;
      if (!widget) {
        return;
      }
      if (!widget.props) {
        widget.props = {};
      }
      patch(widget.props);
    };

    const titleWidgetText = computed<string>({
      get: () => selectedWidget.value?.props?.text ?? '',
      set: value => updateWidgetProps(props => (props.text = value)),
    });
    const titleWidgetSubText = computed<string>({
      get: () => selectedWidget.value?.props?.subText ?? '',
      set: value => updateWidgetProps(props => (props.subText = value)),
    });
    const titleWidgetAlign = computed<'left' | 'center' | 'right'>({
      get: () => selectedWidget.value?.props?.align ?? 'center',
      set: value => updateWidgetProps(props => (props.align = value)),
    });
    const titleWidgetFrame = computed<TitleFrame>({
      get: () => selectedWidget.value?.props?.frame ?? 'none',
      set: value => updateWidgetProps(props => (props.frame = value)),
    });
    const clockFormat = computed<ClockFormat>({
      get: () => selectedWidget.value?.props?.format ?? 'date-week',
      set: value => updateWidgetProps(props => (props.format = value)),
    });

    /** 保存/预览前剔除字段不全的图层，避免校验失败。 */
    const pruneIncompleteMapLayers = <T extends DashboardSpec>(target: T): T => {
      target.widgets.forEach(widget => {
        const layers = widget.chart?.layers;
        if (!layers?.length || !widget.chart) {
          return;
        }
        const valid = layers.filter(layer => {
          if (layer.kind === 'effectScatter') {
            return !!layer.lngField && !!layer.latField;
          }
          if (layer.kind === 'lines') {
            return !!(layer.fromLngField && layer.fromLatField && layer.toLngField && layer.toLatField);
          }
          return true;
        });
        if (valid.length > 0) {
          widget.chart.layers = valid;
        } else {
          delete widget.chart.layers;
        }
      });
      return target;
    };

    /** 初始化或补全组件的 encode，并在字段尚未就绪时等待数据集结果。 */
    const setupWidgetEncode = (widget: DashboardWidget, fields = fieldCandidates(widget.datasetId)) => {
      if (!widget.encode) {
        widget.encode = createDefaultEncode(widget.type, resolveChartType(widget), fields);
      }
      if (widget.encode) {
        ensureDefaultFields(widget, fields);
      }
      if ((widget.type === 'echarts' || widget.type === 'kpi' || widget.type === 'pivot') && !hasCompleteDefaultFields(widget)) {
        pendingFieldDefaults.add(widget.id);
      } else {
        pendingFieldDefaults.delete(widget.id);
      }
    };

    const openDashboardSettings = async () => {
      dashboardSettingsVisible.value = true;
      await nextTick();
      if (propertiesRef.value) {
        propertiesRef.value.scrollTop = 0;
      }
    };

    const selectWidget = (id: string) => {
      dashboardSettingsVisible.value = false;
      selectedWidgetId.value = id;
      selectedFilterId.value = '';
      const widget = spec.value.widgets.find(item => item.id === id);
      if (widget && !widget.encode) {
        setupWidgetEncode(widget);
      }
      propertyTab.value = 'basic';
      activePanel.value = 'widgets';
    };

    const selectFilter = (id: string) => {
      dashboardSettingsVisible.value = false;
      selectedFilterId.value = id;
      selectedWidgetId.value = '';
      activePanel.value = 'filters';
    };

    const openFiltersPanel = () => {
      dashboardSettingsVisible.value = false;
      activePanel.value = 'filters';
    };

    const widgetFields = computed(() => fieldCandidates(selectedWidget.value?.datasetId));
    const widgetValueFieldOptions = computed(() => {
      const numeric = widgetFields.value.filter(field => field.numeric);
      return numeric.length > 0 ? numeric : widgetFields.value;
    });

    // ---------------------------------------------------------------- 组件交互

    /** 可作为联动目标的数据绑定组件类型。 */
    const LINKABLE_WIDGET_TYPES = new Set<WidgetType>(['echarts', 'kpi', 'table', 'pivot']);

    const linkageCandidateWidgets = computed(() =>
      spec.value.widgets.filter(widget => widget.id !== selectedWidget.value?.id && LINKABLE_WIDGET_TYPES.has(widget.type)),
    );

    const linkageCandidateLabel = (widget: DashboardWidget): string => {
      const dataset = spec.value.datasets.find(item => item.id === widget.datasetId);
      return `${widget.title || widget.id} · ${dataset?.name || widget.datasetId || '未绑定数据集'}`;
    };

    const linkageTargetSameDataset = (widget: DashboardWidget): boolean =>
      !!selectedWidget.value?.datasetId && selectedWidget.value.datasetId === widget.datasetId;

    /** 只有图表维度字段会出现在点击条件里，字段关联来源因此限定为维度字段。 */
    const linkageSourceFields = computed(() => {
      const dimensionFields = new Set((selectedWidget.value?.encode?.dimensions ?? []).map(dimension => dimension.field));
      if (dimensionFields.size === 0) {
        return widgetFields.value;
      }
      const matched = widgetFields.value.filter(field => dimensionFields.has(field.name));
      return matched.length > 0 ? matched : widgetFields.value;
    });
    const linkageTargetFields = (widget: DashboardWidget) => fieldCandidates(widget.datasetId);

    /** 移除空交互配置，避免把默认行为写进 spec。 */
    const pruneInteraction = (widget: DashboardWidget) => {
      const interaction = widget.interaction;
      if (!interaction) {
        return;
      }
      interaction.targets = (interaction.targets ?? []).filter(target => spec.value.widgets.some(item => item.id === target.widgetId));
      if (interaction.targets.length === 0) {
        delete interaction.targets;
      }
      if (!interaction.clickAction && !interaction.targets) {
        delete widget.interaction;
      }
    };

    const interactionClickAction = computed<WidgetClickAction>({
      get: () => selectedWidget.value?.interaction?.clickAction ?? 'detail',
      set: value => {
        const widget = selectedWidget.value;
        if (!widget) {
          return;
        }
        if (!widget.interaction) {
          if (value === 'detail') {
            return;
          }
          widget.interaction = {};
        }
        if (value === 'detail') {
          delete widget.interaction.clickAction;
        } else {
          widget.interaction.clickAction = value;
        }
        pruneInteraction(widget);
      },
    });

    /** 新增联动目标时，跨数据集按同名字段自动预填关联关系。 */
    const createLinkageTarget = (targetId: string): WidgetLinkageTarget => {
      const source = selectedWidget.value;
      const target = spec.value.widgets.find(widget => widget.id === targetId);
      const config: WidgetLinkageTarget = { widgetId: targetId };
      if (source && target && !linkageTargetSameDataset(target)) {
        const targetFields = new Set(fieldCandidates(target.datasetId).map(field => field.name));
        config.fieldMap = (source.encode?.dimensions ?? [])
          .filter(dimension => targetFields.has(dimension.field))
          .map(dimension => ({ sourceField: dimension.field, targetField: dimension.field }));
      }
      return config;
    };

    const interactionTargetIds = computed<string[]>({
      get: () =>
        (selectedWidget.value?.interaction?.targets ?? [])
          .map(target => target.widgetId)
          .filter(id => linkageCandidateWidgets.value.some(widget => widget.id === id)),
      set: ids => {
        const widget = selectedWidget.value;
        if (!widget) {
          return;
        }
        if (!widget.interaction) {
          widget.interaction = {};
        }
        const existing = new Map((widget.interaction.targets ?? []).map(target => [target.widgetId, target]));
        widget.interaction.targets = ids
          .filter(id => linkageCandidateWidgets.value.some(item => item.id === id))
          .map(id => existing.get(id) ?? createLinkageTarget(id));
        pruneInteraction(widget);
      },
    });

    const interactionTargets = computed(() => {
      const widgets = new Map(spec.value.widgets.map(widget => [widget.id, widget]));
      return (selectedWidget.value?.interaction?.targets ?? [])
        .map(config => ({ config, widget: widgets.get(config.widgetId) }))
        .filter((item): item is { config: WidgetLinkageTarget; widget: DashboardWidget } => !!item.widget);
    });

    const addLinkageMapping = (target: WidgetLinkageTarget) => {
      if (!target.fieldMap) {
        target.fieldMap = [];
      }
      target.fieldMap.push({ sourceField: '', targetField: '' });
    };

    const removeLinkageMapping = (target: WidgetLinkageTarget, index: number) => {
      target.fieldMap?.splice(index, 1);
    };

    const removeLinkageTarget = (targetId: string) => {
      interactionTargetIds.value = interactionTargetIds.value.filter(id => id !== targetId);
    };

    const widgetChartType = computed<ChartType>(() => resolveChartType(selectedWidget.value));

    const widgetDimensionField = (role: 'x' | 'series' | 'row' | 'column'): string =>
      selectedWidget.value?.encode?.dimensions?.find(dimension => dimension.role === role)?.field ?? '';

    const setWidgetDimensionField = (role: 'x' | 'series' | 'row' | 'column', field: string) => {
      const encode = selectedWidget.value?.encode;
      if (!encode) {
        return;
      }
      const dimensions = [...(encode.dimensions ?? [])];
      const index = dimensions.findIndex(dimension => dimension.role === role);
      if (!field) {
        if (index >= 0) {
          dimensions.splice(index, 1);
        }
      } else if (index >= 0) {
        dimensions[index] = { ...dimensions[index], field };
      } else {
        dimensions.push({ field, role });
      }
      if (dimensions.length > 0) {
        encode.dimensions = dimensions;
      } else {
        delete encode.dimensions;
      }
    };

    const widgetCategoryField = computed<string>({
      get: () => widgetDimensionField('x'),
      set: field => setWidgetDimensionField('x', field),
    });

    const widgetSeriesField = computed<string>({
      get: () => widgetDimensionField('series'),
      set: field => setWidgetDimensionField('series', field),
    });

    const widgetRowField = computed<string>({
      get: () => widgetDimensionField('row'),
      set: field => setWidgetDimensionField('row', field),
    });

    const widgetColumnField = computed<string>({
      get: () => widgetDimensionField('column'),
      set: field => setWidgetDimensionField('column', field),
    });

    const valueMeasureRole = (): 'y' | 'value' => {
      const widget = selectedWidget.value;
      const chartType = widgetChartType.value;
      if (
        widget?.type === 'kpi' ||
        widget?.type === 'pivot' ||
        chartType === 'gauge' ||
        chartType === 'pie' ||
        chartType === 'donut' ||
        chartType === 'funnel' ||
        chartType === 'treemap' ||
        chartType === 'heatmap' ||
        chartType === 'sankey' ||
        chartType === 'graph' ||
        chartType === 'map'
      ) {
        return 'value';
      }
      return 'y';
    };

    /**
     * 纵轴/数值字段统一写入 encode.measures；散点大小字段单独维护。
     */
    const widgetValueFields = computed<string[]>({
      get: () =>
        selectedWidget.value?.encode?.measures
          ?.filter(measure => measure.role !== 'size')
          .map(measure => measure.field)
          .filter(Boolean) ?? [],
      set: fields => {
        const widget = selectedWidget.value;
        const encode = widget?.encode;
        if (!encode) {
          return;
        }
        const unique = Array.from(new Set(fields.filter(Boolean)));
        const sizeMeasure = encode.measures?.find(measure => measure.role === 'size');
        if (unique.length === 0) {
          encode.measures = sizeMeasure ? [sizeMeasure] : [];
          if (widget?.chart?.series) {
            delete widget.chart.series;
          }
          return;
        }
        const role = valueMeasureRole();
        const nextMeasures = unique.map(field => {
          const existing = encode.measures?.find(measure => measure.field === field && measure.role !== 'size');
          return { field, name: existing?.name ?? field, agg: existing?.agg ?? 'sum', role: existing?.role ?? role };
        });
        encode.measures = sizeMeasure ? [...nextMeasures, sizeMeasure] : nextMeasures;
        if (widget && widget.chart?.series) {
          const declaredSeries = widget.chart.series;
          const seriesType: 'bar' | 'line' | 'area' =
            widgetChartType.value === 'line' ? 'line' : widgetChartType.value === 'area' ? 'area' : 'bar';
          widget.chart.series = unique.map(field => {
            const existing = declaredSeries.find(series => series.measure === field);
            return existing ?? { measure: field, type: seriesType, axis: 'left' };
          });
        }
      },
    });

    const widgetSizeField = computed<string>({
      get: () => selectedWidget.value?.encode?.measures?.find(measure => measure.role === 'size')?.field ?? '',
      set: field => {
        const encode = selectedWidget.value?.encode;
        if (!encode) {
          return;
        }
        const measures = (encode.measures ?? []).filter(measure => measure.role !== 'size' && measure.field !== field);
        if (field) {
          const existing = encode.measures?.find(measure => measure.field === field && measure.role === 'size');
          measures.push({ field, name: existing?.name ?? field, agg: existing?.agg ?? 'sum', role: 'size' });
        }
        encode.measures = measures;
      },
    });

    const widgetAggregation = computed<Aggregation>({
      get: () => selectedWidget.value?.encode?.measures?.find(measure => measure.role !== 'size')?.agg ?? 'sum',
      set: agg => {
        const encode = selectedWidget.value?.encode;
        if (!encode?.measures?.length) {
          return;
        }
        encode.measures = encode.measures.map(measure => ({ ...measure, agg }));
      },
    });

    const widgetTableColumns = computed<string[]>({
      get: () => selectedWidget.value?.encode?.columns?.map(column => column.field) ?? [],
      set: fields => {
        const encode = selectedWidget.value?.encode;
        if (!encode) {
          return;
        }
        const unique = Array.from(new Set(fields.filter(Boolean)));
        if (unique.length === 0) {
          delete encode.columns;
          return;
        }
        encode.columns = unique.map(field => {
          const existing = encode.columns?.find(column => column.field === field);
          return existing ?? { field, label: field };
        });
      },
    });

    const widgetPrimaryValueField = computed<string>({
      get: () => selectedWidget.value?.encode?.measures?.find(measure => measure.role !== 'size')?.field ?? '',
      set: field => {
        const widget = selectedWidget.value;
        const encode = widget?.encode;
        if (!encode) {
          return;
        }
        const sizeMeasure = encode.measures?.find(measure => measure.role === 'size');
        if (!field) {
          encode.measures = sizeMeasure ? [sizeMeasure] : [];
          if (widget?.chart?.series) {
            delete widget.chart.series;
          }
          return;
        }
        const existing = encode.measures?.find(measure => measure.field === field && measure.role !== 'size');
        const role = valueMeasureRole();
        const nextMeasure = { field, name: existing?.name ?? field, agg: existing?.agg ?? 'sum', role };
        encode.measures = sizeMeasure ? [nextMeasure, sizeMeasure] : [nextMeasure];
        if (widget && widget.chart?.series) {
          const existingSeries = widget.chart.series.find(series => series.measure === field);
          const seriesType: 'bar' | 'line' | 'area' =
            widgetChartType.value === 'line' ? 'line' : widgetChartType.value === 'area' ? 'area' : 'bar';
          widget.chart.series = [existingSeries ?? { measure: field, type: seriesType, axis: 'left' }];
        }
      },
    });

    const addFormatRule = () => {
      const widget = selectedWidget.value;
      if (!widget) {
        return;
      }
      if (!widget.formatRules) {
        widget.formatRules = [];
      }
      const rule: WidgetFormatRule = {
        id: `rule-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        field: widget.type === 'kpi' ? undefined : '',
        operator: 'gt',
        value: 0,
        color: '#67c23a',
      };
      widget.formatRules.push(rule);
      commitHistory();
    };

    const removeFormatRule = (index: number) => {
      const rules = selectedWidget.value?.formatRules;
      if (!rules || index < 0 || index >= rules.length) {
        return;
      }
      rules.splice(index, 1);
      commitHistory();
    };

    const loadAvailableDatasets = async () => {
      try {
        availableDatasets.value = await datasetService.all();
      } catch (err: any) {
        alertService.showError(errorMessage(err, '数据集列表加载失败'));
      }
    };

    const ensureSpecShape = (raw: DashboardSpec): DashboardSpec => normalizeDashboardSpec(raw);

    const loadExisting = async (id: string) => {
      loading.value = true;
      suppressSpecReload += 1;
      try {
        const dashboard = await dashboardService.get(id);
        editingId.value = id;
        name.value = dashboard.name || '';
        description.value = dashboard.description || '';
        status.value = dashboard.status || 'ENABLED';
        spec.value = ensureSpecShape(DashboardService.parseSpec(dashboard));
        spec.value.title = name.value;
        filterValues.value = initFilterValues(spec.value.filters);
        await refreshData();
      } catch (err: any) {
        alertService.showError(errorMessage(err, '看板加载失败'));
      } finally {
        await nextTick();
        suppressSpecReload = Math.max(0, suppressSpecReload - 1);
        loading.value = false;
      }
    };

    const scrollAiToBottom = async () => {
      await nextTick();
      const el = aiMessageListRef.value;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    };

    const loadAiAssistant = async () => {
      if (aiAssistantLoaded.value) {
        return;
      }
      try {
        const assistants = await aiService.listAssistants();
        const assistant = assistants.find(item => item.id === AI_ASSISTANT_ID);
        aiSamples.value = assistant?.samplePrompts ?? [];
      } catch {
        aiSamples.value = [];
      } finally {
        aiAssistantLoaded.value = true;
      }
    };

    const openAiAssistant = async () => {
      aiPanelVisible.value = true;
      await loadAiAssistant();
      await scrollAiToBottom();
    };

    const buildAiHistory = (): AiHistoryMessage[] =>
      aiMessages.value
        .filter(message => !message.loading && (message.content || message.explanation))
        .slice(-8)
        .map(message => ({
          role: message.role,
          content: message.role === 'assistant' ? (message.explanation as string) : message.content,
        }));

    const cleanAiExplanation = (text?: string): string => (text ? text.replace(/```[\s\S]*?```/g, '').trim() : '');

    const applySpecDefinition = async (rawSpec: DashboardSpec, successMessage?: string): Promise<boolean> => {
      if (!rawSpec || typeof rawSpec !== 'object' || Array.isArray(rawSpec)) {
        alertService.showError('看板定义格式无效');
        return false;
      }
      let normalized = rawSpec;
      let valid = true;
      try {
        normalized = await dashboardService.validateSpec(rawSpec);
      } catch (err: any) {
        valid = false;
        alertService.showError(errorMessage(err, '看板定义校验失败'));
      }
      if (!editingId.value && rawSpec.layout?.rowHeight == null && normalized.layout) {
        // 新看板默认使用 20px 行高；历史看板没有该字段时保持 40px 兼容。
        normalized = {
          ...normalized,
          layout: { ...normalized.layout, rowHeight: 20 },
        };
      }
      if (!name.value.trim() && normalized.title) {
        name.value = normalized.title;
      }
      if (!description.value.trim() && normalized.description) {
        description.value = normalized.description;
      }
      if (!editingId.value) {
        // 新建看板：大屏分辨率默认跟随当前系统屏幕，除非明确指定了非默认分辨率。
        const screen = normalized.layout?.screen;
        const usesDefaultScreen = !screen || (screen.width === DEFAULT_SCREEN_WIDTH && screen.height === DEFAULT_SCREEN_HEIGHT);
        if (normalized.layout?.mode !== 'screen' || usesDefaultScreen) {
          const systemScreen = getSystemScreenSize();
          normalized = {
            ...normalized,
            layout: {
              ...(normalized.layout ?? {}),
              screen: {
                ...(normalized.layout?.screen ?? {}),
                width: systemScreen.width,
                height: systemScreen.height,
              },
            },
          };
        }
      }
      suppressSpecReload += 1;
      try {
        spec.value = ensureSpecShape(normalized);
        spec.value.title = name.value.trim() || spec.value.title || '';
        spec.value.description = description.value;
        filterValues.value = initFilterValues(spec.value.filters ?? []);
        if (!spec.value.widgets.some(widget => widget.id === selectedWidgetId.value)) {
          selectedWidgetId.value = '';
        }
        if (!(spec.value.filters ?? []).some(filter => filter.id === selectedFilterId.value)) {
          selectedFilterId.value = '';
        }
        activePanel.value = spec.value.widgets.length ? 'widgets' : 'datasets';
        commitHistory();
        // AI 可能刚创建了新的分析数据集，刷新数据集库下拉列表。
        await loadAvailableDatasets();
        await refreshData();
      } finally {
        await nextTick();
        suppressSpecReload = Math.max(0, suppressSpecReload - 1);
      }
      if (valid && successMessage) {
        ElMessage.success(successMessage);
      }
      return valid;
    };

    const applyAiSpec = async (rawSpec: DashboardSpec): Promise<boolean> =>
      applySpecDefinition(rawSpec, 'AI 已更新当前设计，可继续手动调整或保存');

    const sendAiMessage = async () => {
      const text = aiInput.value.trim();
      if (!text || aiGenerating.value || loading.value || saving.value) {
        return;
      }
      const history = buildAiHistory();
      aiMessages.value.push({ role: 'user', content: text });
      aiInput.value = '';
      aiGenerating.value = true;

      const placeholderIndex =
        aiMessages.value.push({
          role: 'assistant',
          content: '',
          loading: true,
          statusText: '正在确定指标数据集…',
        }) - 1;
      const placeholder = aiMessages.value[placeholderIndex];
      await scrollAiToBottom();

      const slowTimer = window.setTimeout(() => {
        placeholder.statusText = '正在基于数据集设计看板布局与图表…';
      }, 3000);

      try {
        const contextData = hasDesignContents.value ? { currentSpec: spec.value } : undefined;
        const result = await aiService.generate(text, undefined, history, AI_ASSISTANT_ID, contextData);
        placeholder.loading = false;
        placeholder.explanation = cleanAiExplanation(result.explanation);
        placeholder.toolCalls = result.toolCalls || [];
        if (result.dashboardSpec) {
          placeholder.hasSpec = true;
          await applyAiSpec(result.dashboardSpec as unknown as DashboardSpec);
        } else {
          placeholder.explanation = `${placeholder.explanation}\n\n未能解析到看板定义，请调整描述后重试。`;
          alertService.showError('未能解析到看板定义，请调整描述后重试');
        }
      } catch (err: any) {
        placeholder.loading = false;
        placeholder.explanation = errorMessage(err, '生成失败，请稍后重试');
      } finally {
        window.clearTimeout(slowTimer);
        aiGenerating.value = false;
        await scrollAiToBottom();
      }
    };

    const onAiKeydown = (event: KeyboardEvent) => {
      if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
        event.preventDefault();
        sendAiMessage();
      }
    };

    const onAiSample = (sample: string) => {
      aiInput.value = sample;
    };

    const formatAiToolArgs = (args?: Record<string, any>): string => {
      if (!args || Object.keys(args).length === 0) {
        return '';
      }
      try {
        return JSON.stringify(args);
      } catch {
        return '';
      }
    };

    const toggleAiTools = (index: number) => {
      aiToolsExpanded.value[index] = !aiToolsExpanded.value[index];
    };

    watch(
      () => props.aiMode,
      aiMode => {
        if (aiMode) {
          aiPanelVisible.value = true;
          void loadAiAssistant();
        } else {
          aiPanelVisible.value = false;
        }
      },
    );

    onMounted(async () => {
      if (props.aiMode) {
        aiPanelVisible.value = true;
        void loadAiAssistant();
      }
      await loadAvailableDatasets();
      const id = route.query.id ? String(route.query.id) : undefined;
      if (id) {
        await loadExisting(id);
      }
      resetHistory();
    });

    onMounted(() => {
      window.addEventListener('keydown', onHistoryKeydown);
    });

    onUnmounted(() => {
      window.removeEventListener('keydown', onHistoryKeydown);
      if (historyTimer) {
        clearTimeout(historyTimer);
        historyTimer = null;
      }
    });

    let reloadTimer: ReturnType<typeof setTimeout> | null = null;
    /** 加载/应用整份 spec 时抑制 watcher 的二次取数，避免首屏重复刷新。 */
    let suppressSpecReload = 0;
    const scheduleReload = () => {
      if (reloadTimer) {
        clearTimeout(reloadTimer);
      }
      reloadTimer = setTimeout(() => {
        refreshData();
      }, 500);
    };

    watch(
      () =>
        JSON.stringify({
          datasets: spec.value.datasets,
          filters: spec.value.filters,
          widgets: spec.value.widgets.map(widget => ({
            id: widget.id,
            datasetId: widget.datasetId,
            encode: widget.encode,
          })),
        }),
      () => {
        if (suppressSpecReload > 0) {
          return;
        }
        scheduleReload();
      },
    );

    // 新建图表时数据集可能尚未完成取数；结果到达后补全默认分类/数值字段。
    watch(
      datasetData,
      () => {
        if (!pendingFieldDefaults.size) {
          return;
        }
        for (const widget of spec.value.widgets) {
          if (!pendingFieldDefaults.has(widget.id) || !widget.encode) {
            continue;
          }
          const fields = fieldCandidates(widget.datasetId);
          if (!fields.length) {
            continue;
          }
          ensureDefaultFields(widget, fields);
          if (hasCompleteDefaultFields(widget)) {
            pendingFieldDefaults.delete(widget.id);
          }
        }
      },
      { deep: true },
    );

    async function refreshData() {
      await loadAll(filterValues.value, { force: true });
    }

    const onFilterSearch = async (values: FilterValues) => {
      filterValues.value = values;
      await refreshData();
    };

    const addDataset = () => {
      const source = availableDatasets.value.find(dataset => dataset.id === datasetToAdd.value);
      if (!source) {
        alertService.showError('请选择要添加的数据集');
        return;
      }
      let index = spec.value.datasets.length + 1;
      while (spec.value.datasets.some(dataset => dataset.id === `ds${index}`)) {
        index += 1;
      }
      const dataset: DashboardDataset = {
        id: `ds${index}`,
        refId: source.id,
        name: source.name,
        type: source.type,
      };
      spec.value.datasets.push(dataset);
      datasetToAdd.value = '';
      commitHistory();
      ElMessage.success(`已添加数据集「${source.name}」`);
    };

    const removeDataset = async (dataset: DashboardDataset) => {
      const usedBy = spec.value.widgets.filter(widget => widget.datasetId === dataset.id);
      if (usedBy.length) {
        try {
          await ElMessageBox.confirm(`该数据集被 ${usedBy.length} 个组件使用，删除后这些组件将无法取数。`, '确认删除', {
            type: 'warning',
          });
        } catch {
          return;
        }
      }
      spec.value.datasets = spec.value.datasets.filter(item => item.id !== dataset.id);
      spec.value.widgets.forEach(widget => {
        if (widget.datasetId === dataset.id) {
          widget.datasetId = undefined;
        }
      });
      spec.value.filters = (spec.value.filters ?? [])
        .map(filter => ({
          ...filter,
          targets: (filter.targets ?? []).filter(target => target.datasetId !== dataset.id),
        }))
        .filter(filter => filter.targets.length > 0);
      commitHistory();
    };

    const widgetLabel = (widget: DashboardWidget): string => {
      if (widget.title) {
        return widget.title;
      }
      if (widget.type === 'html') {
        return '说明文本';
      }
      if (widget.type === 'title') {
        return '标题栏';
      }
      if (widget.type === 'clock') {
        return '日期时间';
      }
      return widget.id;
    };

    const nextWidgetId = (type: WidgetType) => {
      let index = spec.value.widgets.length + 1;
      while (spec.value.widgets.some(widget => widget.id === `${type}${index}`)) {
        index += 1;
      }
      return `${type}${index}`;
    };

    const widgetZIndex = computed<number>({
      get: () => selectedWidget.value?.zIndex ?? 0,
      set: value => {
        const widget = selectedWidget.value;
        if (!widget) {
          return;
        }
        widget.zIndex = clamp(Math.round(value || 0), 0, 999);
      },
    });

    const containerOptions = computed(() =>
      spec.value.widgets.filter(widget => widget.type === 'container' && widget.id !== selectedWidget.value?.id),
    );
    const widgetContainerId = computed<string>({
      get: () => selectedWidget.value?.parentId ?? '',
      set: value => {
        const widget = selectedWidget.value;
        if (!widget) {
          return;
        }
        if (value) {
          const container = spec.value.widgets.find(item => item.id === value && item.type === 'container');
          if (!container) {
            return;
          }
          widget.parentId = value;
          // 旧版绝对坐标会回落默认值；超出容器行数的子组件会被收进可见区域。
          widget.layout = fitChildLayout(widget.layout, container.layout, isPixelCanvas.value);
        } else {
          delete widget.parentId;
          const topLevel = spec.value.widgets.filter(item => !item.parentId && item.id !== widget.id);
          widget.layout = isPixelCanvas.value ? nextPixelLayout(topLevel, canvasMetrics.value, 12, 8) : nextLayout(topLevel, 12, 8);
        }
      },
    });

    const addWidget = (type: WidgetType, chartType: ChartType = 'bar', layout?: WidgetLayout, parentId?: string) => {
      const id = nextWidgetId(type);
      const title =
        type === 'html' || type === 'title' || type === 'clock'
          ? ''
          : type === 'container'
            ? '数据面板'
            : type === 'kpi'
              ? 'KPI 指标'
              : type === 'table'
                ? '明细表'
                : type === 'pivot'
                  ? '透视表'
                  : CHART_LABELS[chartType] || '图表';
      const defaultWidth =
        type === 'title'
          ? 24
          : type === 'clock'
            ? 4
            : type === 'container'
              ? 12
              : type === 'kpi'
                ? 6
                : type === 'echarts' && (chartType === 'pie' || chartType === 'donut' || chartType === 'funnel')
                  ? 8
                  : type === 'echarts' && chartType === 'gauge'
                    ? 6
                    : 12;
      const defaultHeight =
        type === 'title' ? 2 : type === 'clock' ? 1 : type === 'container' ? 8 : type === 'kpi' ? 4 : type === 'html' ? 4 : 8;
      const parentContainer =
        parentId && type !== 'container' ? spec.value.widgets.find(item => item.id === parentId && item.type === 'container') : undefined;
      const widget: DashboardWidget = {
        id,
        title,
        type,
        layout: parentContainer
          ? fitChildLayout(layout ?? DEFAULT_CHILD_LAYOUT, parentContainer.layout, isPixelCanvas.value)
          : layout
            ? isPixelCanvas.value
              ? clampPixelLayout(layout, canvasMetrics.value)
              : { ...layout }
            : isPixelCanvas.value
              ? nextPixelLayout(spec.value.widgets, canvasMetrics.value, defaultWidth, defaultHeight)
              : nextLayout(spec.value.widgets, defaultWidth, defaultHeight),
      };
      if (parentContainer) {
        widget.parentId = parentContainer.id;
      }
      if (!DATASETLESS_TYPES.has(type)) {
        widget.datasetId = spec.value.datasets[0]?.id;
      }
      if (type === 'echarts') {
        widget.echartsOption = DEFAULT_ECHARTS_OPTION(chartType);
        const subtype = chartType === 'bar' ? 'grouped' : chartType === 'line' || chartType === 'area' ? 'smooth' : undefined;
        widget.chart = {
          type: chartType,
          ...(subtype ? { subtype } : {}),
          ...(chartType === 'bar' ? { orientation: 'vertical' as const } : {}),
          ...(chartType === 'map' ? { map: 'china' as const } : {}),
        };
      } else if (type === 'html') {
        widget.html = '<div>在此编写 HTML 说明</div>';
      } else if (type === 'title') {
        widget.props = {
          text: name.value.trim() || '领导驾驶舱',
          subText: '',
          align: 'center',
          frame: 'tech-frame',
        };
      } else if (type === 'clock') {
        widget.props = { format: 'date-week' };
      } else if (type === 'container') {
        widget.parentId = undefined;
      }
      const fields = DATASETLESS_TYPES.has(type) ? [] : fieldCandidates(widget.datasetId);
      if (!DATASETLESS_TYPES.has(type)) {
        widget.encode = createDefaultEncode(type, chartType, fields);
      }
      spec.value.widgets.push(widget);
      if (!DATASETLESS_TYPES.has(type)) {
        setupWidgetEncode(widget, fields);
      }
      if (layout && !isPixelCanvas.value && !widget.parentId) {
        applyLayouts(
          compactLayouts(
            spec.value.widgets.filter(item => !item.parentId).map(item => ({ id: item.id, layout: item.layout })),
            spec.value.layout?.columns ?? GRID_COLUMNS,
            id,
          ),
        );
      }
      selectedWidgetId.value = id;
      dashboardSettingsVisible.value = false;
      propertyTab.value = 'basic';
      activePanel.value = 'widgets';
      commitHistory();
    };

    const onPaletteDragStart = (event: DragEvent, item: PaletteItem) => {
      paletteDragItem.value = item;
      if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'copy';
        event.dataTransfer.setData(
          WIDGET_PALETTE_MIME,
          JSON.stringify({ type: item.type, chartType: item.chartType, w: item.w, h: item.h }),
        );
        event.dataTransfer.setData('text/plain', item.label);
      }
    };

    const onPaletteDragEnd = () => {
      paletteDragItem.value = null;
    };

    const addPaletteWidget = (item: PaletteItem) => {
      // 点击添加不传 layout，避免被误判为“拖入某个容器”。
      addWidget(item.type, item.chartType);
    };

    const onCanvasAddWidget = (type: WidgetType, chartType: ChartType | undefined, layout: WidgetLayout, parentId?: string) => {
      addWidget(type, chartType, layout, parentId);
      paletteDragItem.value = null;
    };

    const removeWidget = (id: string) => {
      // 组件删除后同步清理其它组件的联动目标，避免留下悬空引用。
      spec.value.widgets.forEach(widget => {
        if (widget.interaction?.targets?.some(target => target.widgetId === id)) {
          widget.interaction.targets = widget.interaction.targets.filter(target => target.widgetId !== id);
          if (widget.interaction.targets.length === 0) {
            delete widget.interaction.targets;
          }
          pruneInteraction(widget);
        }
      });
      const target = spec.value.widgets.find(widget => widget.id === id);
      const childIds = new Set(
        target?.type === 'container' ? spec.value.widgets.filter(widget => widget.parentId === id).map(widget => widget.id) : [],
      );
      pendingFieldDefaults.delete(id);
      childIds.forEach(childId => pendingFieldDefaults.delete(childId));
      spec.value.widgets = spec.value.widgets.filter(widget => widget.id !== id && !childIds.has(widget.id));
      // 删除联动源或目标时立即取消联动，避免目标组件停留在过滤状态。
      if (linkage.value?.sourceWidgetId === id || (linkage.value?.targetWidgetIds ?? []).some(widgetId => widgetId === id)) {
        clearLinkage();
      }
      if (selectedWidgetId.value === id || childIds.has(selectedWidgetId.value)) {
        selectedWidgetId.value = '';
      }
      commitHistory();
    };

    const onLayoutChange = (id: string, layout: WidgetLayout) => {
      const widget = spec.value.widgets.find(item => item.id === id);
      if (isPixelCanvas.value) {
        if (widget) {
          widget.layout = clampPixelLayout(layout, canvasMetrics.value);
        }
        commitHistory();
        return;
      }
      // 只对顶层组件做栅格压实；容器子组件在容器内部独立布局。
      const items = spec.value.widgets.filter(item => !item.parentId).map(item => ({ id: item.id, layout: item.layout }));
      const updated = moveLayout(items, id, layout.x, layout.y, spec.value.layout?.columns ?? GRID_COLUMNS);
      applyLayouts(updated);
      commitHistory();
    };

    const onResize = (id: string, layout: WidgetLayout) => {
      if (isPixelCanvas.value) {
        const widget = spec.value.widgets.find(item => item.id === id);
        if (widget) {
          widget.layout = clampPixelLayout(layout, canvasMetrics.value);
        }
        commitHistory();
        return;
      }
      const items = spec.value.widgets.filter(item => !item.parentId).map(item => ({ id: item.id, layout: item.layout }));
      const updated = resizeLayout(items, id, layout.w, layout.h, spec.value.layout?.columns ?? GRID_COLUMNS);
      applyLayouts(updated);
      commitHistory();
    };

    /** 容器子组件始终按容器内 24 栅格保存。 */
    const onChildLayoutChange = (containerId: string, id: string, layout: WidgetLayout) => {
      const child = spec.value.widgets.find(widget => widget.id === id && widget.parentId === containerId);
      if (!child) {
        return;
      }
      child.layout = normalizeChildLayout(layout);
      commitHistory();
    };

    const onChildResize = (containerId: string, id: string, layout: WidgetLayout) => {
      const child = spec.value.widgets.find(widget => widget.id === id && widget.parentId === containerId);
      if (!child) {
        return;
      }
      child.layout = normalizeChildLayout(layout);
      commitHistory();
    };

    /** 上移/下移一层：按 zIndex 排序后交换相邻项并重新编号，保证层级稳定。 */
    const moveWidgetLayer = (id: string, direction: 'up' | 'down') => {
      // 点箭头时同步选中该组件，属性面板的「层级」才会显示刚调整后的数值。
      selectedWidgetId.value = id;
      const ordered = [...spec.value.widgets].sort((left, right) => (left.zIndex ?? 0) - (right.zIndex ?? 0));
      const index = ordered.findIndex(widget => widget.id === id);
      const target = direction === 'up' ? index + 1 : index - 1;
      if (index < 0 || target < 0 || target >= ordered.length) {
        return;
      }
      [ordered[index], ordered[target]] = [ordered[target], ordered[index]];
      ordered.forEach((widget, position) => {
        widget.zIndex = position === 0 ? 0 : position * 5;
      });
      commitHistory();
    };

    const applyLayouts = (layouts: Array<{ id: string; layout: WidgetLayout }>) => {
      const map = new Map(layouts.map(item => [item.id, item.layout]));
      spec.value.widgets.forEach(widget => {
        const layout = map.get(widget.id);
        if (layout) {
          widget.layout = layout;
        }
      });
    };

    const chartType = computed<ChartType>(() => resolveChartType(selectedWidget.value));
    const chartTypeLabel = computed(() => CHART_LABELS[chartType.value] ?? '图表');
    const hasChartAxes = computed(() => !['pie', 'donut', 'gauge', 'funnel', 'radar', 'treemap', 'map'].includes(chartType.value));

    const chartSubtypeOptions = computed<Array<{ value: string; label: string }>>(() => {
      if (chartType.value === 'bar') {
        return [
          { value: 'grouped', label: '分组' },
          { value: 'stacked', label: '堆积' },
          { value: 'stackedPercent', label: '百分比堆积' },
        ];
      }
      if (chartType.value === 'line' || chartType.value === 'area') {
        return [
          { value: 'smooth', label: '平滑' },
          { value: 'step', label: '阶梯' },
        ];
      }
      return [];
    });

    const chartSubtype = computed<string>({
      get: () => selectedWidget.value?.chart?.subtype ?? chartSubtypeOptions.value[0]?.value ?? '',
      set: value => {
        const widget = selectedWidget.value;
        if (!widget) {
          return;
        }
        widget.chart = { ...(widget.chart ?? { type: chartType.value }) };
        if (value) {
          widget.chart.subtype = value as any;
        } else {
          delete widget.chart.subtype;
        }
      },
    });

    const chartOrientation = computed<'vertical' | 'horizontal'>({
      get: () => selectedWidget.value?.chart?.orientation ?? 'vertical',
      set: value => {
        const widget = selectedWidget.value;
        if (!widget) {
          return;
        }
        widget.chart = { ...(widget.chart ?? { type: chartType.value }), orientation: value };
      },
    });

    const chartCombo = computed<boolean>({
      get: () => selectedWidget.value?.chart?.series?.some(series => series.type === 'line' || series.axis === 'right') ?? false,
      set: value => {
        const widget = selectedWidget.value;
        if (!widget) {
          return;
        }
        widget.chart = { ...(widget.chart ?? { type: chartType.value }) };
        if (!value) {
          delete widget.chart.series;
          return;
        }
        const measures = (widget.encode?.measures ?? []).filter(measure => measure.role !== 'size');
        if (measures.length < 2) {
          delete widget.chart.series;
          return;
        }
        widget.chart.type = 'bar';
        const series: ChartSeries[] = measures.map((measure, index) => ({
          measure: measure.field,
          type: index === 0 ? 'bar' : 'line',
          axis: index === 0 ? 'left' : 'right',
        }));
        widget.chart.series = series;
      },
    });

    const chartSeries = (option: Record<string, any>): Record<string, any>[] => {
      if (Array.isArray(option.series)) {
        return option.series.filter((item): item is Record<string, any> => !!item && typeof item === 'object');
      }
      return option.series && typeof option.series === 'object' ? [option.series] : [];
    };

    const chartAxes = (option: Record<string, any>): Record<string, any>[] => {
      const result: Record<string, any>[] = [];
      for (const key of ['xAxis', 'yAxis']) {
        const axis = option[key];
        if (Array.isArray(axis)) {
          result.push(...axis.filter((item): item is Record<string, any> => !!item && typeof item === 'object'));
        } else if (axis && typeof axis === 'object') {
          result.push(axis);
        }
      }
      return result;
    };

    const updateChartAxes = (updater: (axis: Record<string, any>, key: 'xAxis' | 'yAxis') => void) => {
      const option = selectedWidget.value?.echartsOption;
      if (!option) {
        return;
      }
      for (const key of ['xAxis', 'yAxis'] as const) {
        const axis = option[key];
        if (Array.isArray(axis)) {
          option[key] = axis.map(item => {
            if (!item || typeof item !== 'object') {
              return item;
            }
            const next = { ...item };
            updater(next, key);
            return next;
          });
        } else if (axis && typeof axis === 'object') {
          const next = { ...axis };
          updater(next, key);
          option[key] = next;
        }
      }
    };

    const chartLegendVisible = computed<boolean>({
      get: () => !!selectedWidget.value?.echartsOption?.legend && selectedWidget.value.echartsOption.legend.show !== false,
      set: value => {
        const option = selectedWidget.value?.echartsOption;
        if (option) {
          option.legend = { ...(option.legend ?? {}), show: value };
        }
        // 自定义配置中显式操作后，移除样式预设里的同名覆盖，避免两个入口互相打架。
        if (selectedWidget.value?.style?.chart) {
          delete selectedWidget.value.style.chart.showLegend;
        }
      },
    });

    const chartDataLabelVisible = computed<boolean>({
      get: () => {
        const option = selectedWidget.value?.echartsOption;
        return option ? chartSeries(option).some(series => series.label?.show === true) : false;
      },
      set: value => {
        const option = selectedWidget.value?.echartsOption;
        if (!option) {
          return;
        }
        const series = chartSeries(option);
        for (const item of series) {
          item.label = { ...(item.label ?? {}), show: value };
        }
        if (selectedWidget.value?.style?.chart) {
          delete selectedWidget.value.style.chart.showLabel;
        }
      },
    });

    const chartMapRoam = computed<boolean>({
      get: () => {
        const option = selectedWidget.value?.echartsOption;
        return option ? chartSeries(option)[0]?.roam === true : false;
      },
      set: value => {
        const option = selectedWidget.value?.echartsOption;
        if (!option) {
          return;
        }
        const series = chartSeries(option);
        if (series[0]) {
          series[0].roam = value;
        }
      },
    });

    const chartAxesVisible = computed<boolean>({
      get: () => {
        const option = selectedWidget.value?.echartsOption;
        return option ? chartAxes(option).some(axis => axis.show !== false) : true;
      },
      set: value => {
        updateChartAxes(axis => (axis.show = value));
        if (selectedWidget.value?.style?.chart) {
          delete selectedWidget.value.style.chart.showAxis;
        }
      },
    });

    const chartGridLinesVisible = computed<boolean>({
      get: () => {
        const option = selectedWidget.value?.echartsOption;
        if (!option) {
          return true;
        }
        const axes = chartAxes(option);
        return axes.length === 0 || axes.some(axis => axis.splitLine?.show !== false);
      },
      set: value =>
        updateChartAxes(axis => {
          axis.splitLine = { ...(axis.splitLine ?? {}), show: value };
        }),
    });

    const syncCustomOption = () => {
      customOptionError.value = '';
      customOptionText.value = JSON.stringify(selectedWidget.value?.echartsOption ?? {}, null, 2);
    };

    watch(propertyTab, tab => {
      if (tab === 'custom') {
        syncCustomOption();
      }
    });

    watch(selectedWidgetId, () => {
      if (propertyTab.value === 'custom') {
        syncCustomOption();
      }
    });

    watch(
      () => selectedWidget.value?.echartsOption,
      () => {
        if (propertyTab.value === 'custom') {
          syncCustomOption();
        }
      },
      { deep: true },
    );

    const setChartType = (type: ChartType) => {
      const widget = selectedWidget.value;
      if (!widget) {
        return;
      }
      widget.echartsOption = DEFAULT_ECHARTS_OPTION(type);
      const subtype = type === 'bar' ? 'grouped' : type === 'line' || type === 'area' ? 'smooth' : undefined;
      widget.chart = {
        type,
        ...(subtype ? { subtype } : {}),
        ...(type === 'bar' ? { orientation: 'vertical' as const } : {}),
        ...(type === 'map' ? { map: 'china' as const } : {}),
      };
      if (widget.encode) {
        const singleValue = type === 'pie' || type === 'donut' || type === 'funnel' || type === 'treemap' || type === 'map';
        const nodeLink = type === 'sankey' || type === 'graph';
        if (type === 'gauge') {
          delete widget.encode.dimensions;
          widget.encode.groupBy = false;
        } else if (singleValue) {
          widget.encode.dimensions = (widget.encode.dimensions ?? []).filter(dimension => dimension.role === 'x');
        } else if (type === 'heatmap' || nodeLink) {
          widget.encode.dimensions = (widget.encode.dimensions ?? []).filter(
            dimension => dimension.role === 'x' || dimension.role === 'series',
          );
        } else if (type === 'boxplot' || type === 'waterfall') {
          widget.encode.dimensions = (widget.encode.dimensions ?? []).filter(dimension => dimension.role === 'x');
        }
        if (singleValue || type === 'gauge' || nodeLink) {
          const first = widget.encode.measures?.find(measure => measure.role !== 'size');
          widget.encode.measures = first?.field ? [{ ...first, role: 'value' }] : [];
        } else if (type === 'heatmap' || type === 'scatter') {
          widget.encode.measures = (widget.encode.measures ?? []).map(measure =>
            measure.role === 'size' ? measure : { ...measure, role: type === 'heatmap' ? 'value' : 'y' },
          );
        } else if (widget.encode.measures?.length) {
          widget.encode.measures = widget.encode.measures.map(measure => (measure.role === 'size' ? measure : { ...measure, role: 'y' }));
        }
        widget.encode.groupBy = !['gauge', 'scatter', 'boxplot'].includes(type);
      }
      setupWidgetEncode(widget);
      if (propertyTab.value === 'custom') {
        syncCustomOption();
      }
      commitHistory();
    };

    const applyCustomOption = () => {
      const widget = selectedWidget.value;
      if (!widget || widget.type !== 'echarts') {
        return;
      }
      let parsed: unknown;
      try {
        parsed = JSON.parse(customOptionText.value);
      } catch (error: any) {
        customOptionError.value = error?.message || 'JSON 解析失败';
        return;
      }
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        customOptionError.value = '图表配置必须是 JSON 对象';
        return;
      }
      widget.echartsOption = mergeEchartsOption(DEFAULT_ECHARTS_OPTION(chartType.value), parsed as Record<string, any>);
      customOptionError.value = '';
      customOptionText.value = JSON.stringify(widget.echartsOption, null, 2);
      commitHistory();
      ElMessage.success('图表配置已应用');
    };

    const resetCustomOption = () => {
      const widget = selectedWidget.value;
      if (!widget || widget.type !== 'echarts') {
        return;
      }
      widget.echartsOption = DEFAULT_ECHARTS_OPTION(chartType.value);
      syncCustomOption();
      commitHistory();
      ElMessage.success('已恢复默认图表配置');
    };

    const SQL_PARAM_OPTION = '__sql_param__';
    const METRIC_TIME_OPTION = '__metric_time__';

    /** 所有筛选目标统一服务端执行；保留函数便于集中维护目标默认值。 */
    const filterTargetMode = (_datasetId: string | undefined): 'server' => 'server';

    const isSqlDataset = (datasetId: string | undefined): boolean =>
      spec.value.datasets.some(item => item.id === datasetId && item.type === 'SQL');

    const isMetricDateRangeTarget = (target: DashboardFilter['targets'][number]): boolean =>
      selectedFilter.value?.type === 'dateRange' &&
      spec.value.datasets.some(item => item.id === target.datasetId && item.type === 'METRIC') &&
      (!target.pushdown || target.pushdown.kind === 'metricTime');

    const filterTargetFieldValue = (target: DashboardFilter['targets'][number]): string => {
      if (target.pushdown?.kind === 'sqlParam') {
        return SQL_PARAM_OPTION;
      }
      if (target.pushdown?.kind === 'metricTime') {
        return METRIC_TIME_OPTION;
      }
      return target.field ?? '';
    };

    const onFilterTargetFieldSelect = (target: DashboardFilter['targets'][number], value: string) => {
      if (value === METRIC_TIME_OPTION) {
        target.mode = 'server';
        target.pushdown = { kind: 'metricTime' };
        delete target.field;
      } else if (value === SQL_PARAM_OPTION) {
        const param = target.pushdown?.param || target.field || '';
        target.mode = 'server';
        target.pushdown = { kind: 'sqlParam', ...(param ? { param } : {}) };
        delete target.field;
      } else {
        target.field = value;
        target.mode = filterTargetMode(target.datasetId);
        delete target.pushdown;
      }
      commitHistory();
    };

    const defaultFilterField = (datasetId: string | undefined, type: DashboardFilter['type']): string => {
      const fields = fieldCandidates(datasetId);
      if (fields.length === 0) {
        return '';
      }
      if (type === 'numberRange') {
        return fields.find(field => field.numeric)?.name ?? fields[0].name;
      }
      if (type === 'dateRange') {
        return (
          fields.find(field => field.type === 'date' || /(date|time|month|year|day)/i.test(field.name))?.name ??
          fields.find(field => !field.numeric)?.name ??
          fields[0].name
        );
      }
      return fields.find(field => !field.numeric)?.name ?? fields[0].name;
    };

    const configureFilterTarget = (filter: DashboardFilter, target: DashboardFilter['targets'][number]) => {
      const dataset = spec.value.datasets.find(item => item.id === target.datasetId);
      if (filter.type === 'dateRange' && dataset?.type === 'METRIC') {
        target.mode = 'server';
        target.pushdown = { kind: 'metricTime' };
        delete target.field;
        return;
      }
      target.mode = filterTargetMode(target.datasetId);
      target.field = defaultFilterField(target.datasetId, filter.type);
      delete target.pushdown;
    };

    const addFilterTarget = () => {
      const filter = selectedFilter.value;
      if (!filter) {
        return;
      }
      const target = { datasetId: spec.value.datasets[0]?.id ?? '', field: '', mode: 'server' as const };
      filter.targets = [...(filter.targets ?? []), target];
      configureFilterTarget(filter, target);
      commitHistory();
    };

    const removeFilterTarget = (index: number) => {
      const filter = selectedFilter.value;
      if (!filter || (filter.targets?.length ?? 0) <= 1) {
        return;
      }
      filter.targets.splice(index, 1);
      commitHistory();
    };

    const onFilterTargetDatasetChange = (target: DashboardFilter['targets'][number]) => {
      const filter = selectedFilter.value;
      if (!filter) {
        return;
      }
      configureFilterTarget(filter, target);
      commitHistory();
    };

    const onFilterTargetFieldChange = (target: DashboardFilter['targets'][number]) => {
      if (!selectedFilter.value) {
        return;
      }
      target.mode = filterTargetMode(target.datasetId);
      delete target.pushdown;
      commitHistory();
    };

    const onFilterTypeChange = () => {
      const filter = selectedFilter.value;
      if (!filter) {
        return;
      }
      // 类型切换后旧默认值（如文本/数组/区间对象）形态不再匹配，清空避免保存校验失败。
      delete filter.defaultValue;
      filterValues.value[filter.id] = undefined;
      for (const target of filter.targets ?? []) {
        configureFilterTarget(filter, target);
      }
      commitHistory();
    };

    const pushdownLabel = (kind?: string): string => {
      switch (kind) {
        case 'sqlParam':
          return 'SQL 参数';
        case 'metricTime':
          return '指标时间范围';
        case 'metricDimension':
          return '指标维度';
        case 'metricFactField':
          return '指标事实字段';
        default:
          return kind || '服务端下推';
      }
    };

    const pushdownDetail = (target: DashboardFilter['targets'][number]): string => {
      const pushdown = target.pushdown;
      if (!pushdown) {
        return '未配置字段';
      }
      switch (pushdown.kind) {
        case 'sqlParam':
          return `${pushdownLabel(pushdown.kind)}：${pushdown.param || '未填写参数名'}`;
        case 'metricTime':
          return pushdownLabel(pushdown.kind);
        case 'metricDimension':
          return `${pushdownLabel(pushdown.kind)}：${pushdown.dimensionFieldName || pushdown.dimensionModelCode || '未配置维度'}`;
        case 'metricFactField':
          return `${pushdownLabel(pushdown.kind)}：${pushdown.factFieldName || '未配置事实字段'}`;
        default:
          return pushdownLabel(pushdown.kind);
      }
    };

    const filterTargetLabel = (filter: DashboardFilter): string => {
      const target = filter.targets?.[0];
      const dataset = spec.value.datasets.find(item => item.id === target?.datasetId);
      if (!dataset) {
        return '未配置数据集/字段';
      }
      const field = target?.field || (target?.pushdown ? pushdownDetail(target) : '未选字段');
      const suffix = (filter.targets?.length ?? 0) > 1 ? ` 等 ${filter.targets.length} 个目标` : '';
      return `${dataset.name || dataset.id}.${field}${suffix}`;
    };

    const addFilter = () => {
      let index = (spec.value.filters?.length ?? 0) + 1;
      while (spec.value.filters?.some(filter => filter.id === `filter${index}`)) {
        index += 1;
      }
      const dataset = spec.value.datasets[0];
      const filter: DashboardFilter = {
        id: `filter${index}`,
        type: 'select',
        label: '筛选器',
        options: { values: [] },
        targets: [
          {
            datasetId: dataset?.id ?? '',
            field: dataset ? defaultFilterField(dataset.id, 'select') : '',
            mode: filterTargetMode(dataset?.id),
          },
        ],
      };
      spec.value.filters = [...(spec.value.filters ?? []), filter];
      filterValues.value[filter.id] = undefined;
      dashboardSettingsVisible.value = false;
      selectedWidgetId.value = '';
      selectedFilterId.value = filter.id;
      activePanel.value = 'filters';
      commitHistory();
    };

    const removeFilter = (id: string) => {
      spec.value.filters = (spec.value.filters ?? []).filter(filter => filter.id !== id);
      delete filterValues.value[id];
      if (selectedFilterId.value === id) {
        selectedFilterId.value = '';
      }
      commitHistory();
    };

    const filterOptionsText = computed({
      get: () => (selectedFilter.value?.options?.values ?? []).join(','),
      set: (value: string) => {
        if (!selectedFilter.value) {
          return;
        }
        selectedFilter.value.options = {
          ...(selectedFilter.value.options ?? {}),
          values: value
            .split(',')
            .map(item => item.trim())
            .filter(Boolean),
        };
      },
    });

    /** 区间默认值读取：兼容 {start,end}/{min,max} 对象与 [a,b] 数组。 */
    const rangeBound = (value: any, key: 'start' | 'end' | 'min' | 'max'): string => {
      const index = key === 'start' || key === 'min' ? 0 : 1;
      if (Array.isArray(value)) {
        return value[index] === undefined || value[index] === null ? '' : String(value[index]);
      }
      if (value && typeof value === 'object') {
        const raw = value[key];
        return raw === undefined || raw === null ? '' : String(raw);
      }
      return '';
    };

    const parseNumberOrDefault = (raw?: string): number | undefined => {
      if (raw === undefined || raw === '') {
        return undefined;
      }
      const num = Number(raw);
      return Number.isFinite(num) ? num : undefined;
    };

    const filterDefaultText = computed({
      get: () => {
        const filter = selectedFilter.value;
        const value = filter?.defaultValue;
        if (value === undefined || value === null) {
          return '';
        }
        if (filter?.type === 'dateRange') {
          return [rangeBound(value, 'start'), rangeBound(value, 'end')].filter(Boolean).join(',');
        }
        if (filter?.type === 'numberRange') {
          return [rangeBound(value, 'min'), rangeBound(value, 'max')].filter(Boolean).join(',');
        }
        if (Array.isArray(value)) {
          return value.join(',');
        }
        return String(value);
      },
      set: (value: string) => {
        const filter = selectedFilter.value;
        if (!filter) {
          return;
        }
        const rangeType = filter.type === 'dateRange' || filter.type === 'numberRange';
        const separator = rangeType && value.includes('~') && !value.includes(',') ? '~' : ',';
        const parts = value
          .split(separator)
          .map(item => item.trim())
          .filter(Boolean);
        if (filter.type === 'multiSelect') {
          filter.defaultValue = parts;
        } else if (filter.type === 'dateRange') {
          // 后端要求日期范围默认值必须是 {start,end} 对象。
          filter.defaultValue = parts.length
            ? { ...(parts[0] ? { start: parts[0] } : {}), ...(parts[1] ? { end: parts[1] } : {}) }
            : undefined;
        } else if (filter.type === 'numberRange') {
          // 后端要求数值范围默认值必须是 {min,max} 对象。
          const min = parseNumberOrDefault(parts[0]);
          const max = parseNumberOrDefault(parts[1]);
          filter.defaultValue =
            min === undefined && max === undefined
              ? undefined
              : { ...(min !== undefined ? { min } : {}), ...(max !== undefined ? { max } : {}) };
        } else {
          filter.defaultValue = parts[0];
        }
        filterValues.value[filter.id] = filter.defaultValue;
      },
    });

    const filterDefaultHint = computed(() => {
      switch (selectedFilter.value?.type) {
        case 'dateRange':
          return { label: '默认值（开始日期,结束日期）', placeholder: '2026-01-01,2026-12-31' };
        case 'numberRange':
          return { label: '默认值（最小值,最大值）', placeholder: '0,100' };
        default:
          return { label: '默认值（多值用逗号分隔）', placeholder: '可选' };
      }
    });

    /**
     * 默认值输入草稿：输入过程中保留原文（如结尾逗号），
     * 只在 change/blur 时解析成 spec 需要的结构，避免刚输入的分隔符被立即吞掉。
     */
    const filterDefaultDraft = ref('');

    const syncFilterDefaultDraft = () => {
      filterDefaultDraft.value = filterDefaultText.value;
    };

    watch(() => [selectedFilterId.value, selectedFilter.value?.type, selectedFilter.value?.defaultValue] as const, syncFilterDefaultDraft, {
      deep: true,
      immediate: true,
    });

    const commitFilterDefault = () => {
      if (filterDefaultText.value !== filterDefaultDraft.value) {
        filterDefaultText.value = filterDefaultDraft.value;
      }
    };

    const save = async () => {
      commitFilterDefault();
      if (!name.value.trim()) {
        alertService.showError('请填写看板名称');
        return;
      }
      if (!spec.value.widgets.length) {
        alertService.showError('请至少添加一个组件');
        return;
      }
      saving.value = true;
      try {
        const incompleteLinkage = spec.value.widgets.find(
          widget =>
            widget.interaction?.clickAction === 'linkage' &&
            (widget.interaction.targets ?? []).some(target => {
              const targetWidget = spec.value.widgets.find(item => item.id === target.widgetId);
              if (!targetWidget || !widget.datasetId || !targetWidget.datasetId) {
                return false;
              }
              if (widget.datasetId === targetWidget.datasetId) {
                return false;
              }
              return !(target.fieldMap ?? []).some(mapping => !!mapping.sourceField && !!mapping.targetField);
            }),
        );
        if (incompleteLinkage) {
          alertService.showError(`组件「${incompleteLinkage.title || incompleteLinkage.id}」的跨数据集联动目标缺少字段关联`);
          saving.value = false;
          return;
        }
        spec.value.title = name.value.trim();
        spec.value.description = description.value;
        pruneIncompleteMapLayers(spec.value);
        // 历史 40px 行高看板在保存时统一迁移到新行高，并保持视觉高度。
        migrateLegacyRowHeight(spec.value);
        const normalized = await dashboardService.validateSpec(spec.value);
        spec.value = ensureSpecShape(normalized);
        const payload: AnalysisDashboard = {
          name: name.value.trim(),
          description: description.value,
          spec: JSON.stringify(spec.value),
          status: status.value,
        };
        if (editingId.value) {
          await dashboardService.update(editingId.value, { ...payload, id: editingId.value });
        } else {
          const created = await dashboardService.create(payload);
          if (created?.id) {
            editingId.value = created.id;
          }
        }
        ElMessage.success('看板已保存');
      } catch (err: any) {
        alertService.showError(errorMessage(err, '保存失败'));
      } finally {
        saving.value = false;
      }
    };

    const openPreview = () => {
      commitFilterDefault();
      const previewName = name.value.trim() || '未命名看板';
      const previewSpec = pruneIncompleteMapLayers({
        ...(JSON.parse(JSON.stringify(spec.value)) as DashboardSpec),
        title: previewName,
        description: description.value,
      });
      try {
        const draftId = createDashboardPreviewDraft(previewName, previewSpec);
        const previewWindow = window.open(`/dashboard.html?draft=${encodeURIComponent(draftId)}`, '_blank');
        if (!previewWindow) {
          alertService.showError('预览窗口被浏览器拦截，请允许弹出窗口后重试');
        }
      } catch (err: any) {
        alertService.showError(errorMessage(err, '打开预览失败'));
      }
    };

    const goBack = () => router.push({ name: 'AnalysisDashboard' });

    return {
      editingId,
      loading,
      saving,
      canUndo,
      canRedo,
      undo,
      redo,
      name,
      status,
      activePanel,
      propertyTab,
      customOptionText,
      customOptionError,
      paletteCategory,
      paletteDragItem,
      paletteCategories,
      activePaletteItems,
      selectedWidgetId,
      selectedFilterId,
      datasetToAdd,
      availableDatasets,
      aiPanelVisible,
      aiInput,
      aiGenerating,
      aiMessages,
      aiMessageListRef,
      aiToolsExpanded,
      aiSamples,
      aiPlaceholder,
      openAiAssistant,
      sendAiMessage,
      onAiKeydown,
      onAiSample,
      toggleAiTools,
      formatAiToolArgs,
      applyAiSpec,
      spec,
      filterValues,
      datasetData,
      widgetData,
      loadingDatasets,
      datasetErrors,
      widgetErrors,
      serverPaginationWidgets,
      dataVersion,
      loadWidgetPage,
      loadingData,
      selectedWidget,
      selectedFilter,
      selectedWidgetDatasetLabel,
      dashboardSettingsVisible,
      openDashboardSettings,
      propertiesRef,
      isScreenMode,
      isPixelCanvas,
      canvasUnit,
      canvasPadding,
      canvasGap,
      layoutMode,
      screenPresets,
      screenPresetValue,
      screenWidth,
      screenHeight,
      screenFitMode,
      screenTheme,
      screenThemeClass,
      screenBackgroundStyle,
      fitModeOptions,
      fitModeHint,
      backgroundType,
      backgroundColor,
      gradientFrom,
      gradientTo,
      gradientAngle,
      backgroundImageUrl,
      backgroundImagePath,
      backgroundUploading,
      backgroundUploadRef,
      autoRefreshSeconds,
      onBackgroundFileChange,
      removeBackgroundImage,
      applyScreenPreset,
      canvasZoom,
      canvasScale,
      zoomPercent,
      zoomIn,
      zoomOut,
      fitCanvas,
      onCanvasScaleChange,
      isCanvasOverflow,
      canvasBindings,
      themePresetOptions,
      titleIconOptions: TITLE_ICON_OPTIONS,
      themePreset,
      themePrimary,
      themeSecondary,
      themeAccent,
      themeSurface,
      themeBorder,
      themeText,
      resetThemeTokens,
      cardPreset,
      cardFillType,
      cardFillColor,
      cardGradientFrom,
      cardGradientTo,
      cardGradientAngle,
      cardBorderWidth,
      cardBorderStyle,
      cardBorderColor,
      cardBorderRadius,
      cardPadding,
      cardOpacity,
      cardBackdropBlur,
      cardShadowBlur,
      cardShadowColor,
      titleShow,
      titleIcon,
      titleFontSize,
      titleColor,
      titleAlign,
      titleHeight,
      titleLetterSpacing,
      titleDecoration,
      titleBackground,
      bodyPadding,
      resetWidgetStyle,
      kpiLabel,
      kpiIcon,
      kpiLayout,
      kpiValueFontSize,
      kpiValueColor,
      kpiValueFontFamily,
      kpiValueBold,
      kpiValueLetterSpacing,
      kpiUnitText,
      kpiUnitFontSize,
      kpiUnitColor,
      kpiAnimate,
      kpiDecimals,
      mapScatterEnabled,
      mapLinesEnabled,
      mapScatterDatasetId,
      mapLinesDatasetId,
      mapScatterLngField,
      mapScatterLatField,
      mapScatterNameField,
      mapScatterValueField,
      mapScatterColor,
      mapScatterSize,
      mapLinesFromLngField,
      mapLinesFromLatField,
      mapLinesToLngField,
      mapLinesToLatField,
      mapLinesValueField,
      mapLinesColor,
      mapLinesCurveness,
      titleWidgetText,
      titleWidgetSubText,
      titleWidgetAlign,
      titleWidgetFrame,
      clockFormat,
      containerOptions,
      widgetContainerId,
      widgetZIndex,
      chartStylePreset,
      chartStyleGradient,
      chartStyleGlow,
      chartLegendPosition,
      chartLegendFontSize,
      chartAxisTitleFontSize,
      chartAxisTitleColor,
      chartXAxisTitle,
      chartYAxisTitle,
      chartXAxisVisible,
      chartYAxisVisible,
      chartXAxisLabelFontSize,
      chartYAxisLabelFontSize,
      chartGridLineStyle,
      chartAxisTick,
      chartAxisLineColor,
      chartBarRadius,
      chartLabelFontSize,
      chartPercentOfTotal,
      customSections,
      interactionClickAction,
      interactionTargetIds,
      linkage,
      linkageWidgetIds,
      applyLinkage,
      clearLinkage,
      interactionTargets,
      linkageCandidateWidgets,
      linkageCandidateLabel,
      linkageTargetSameDataset,
      linkageSourceFields,
      linkageTargetFields,
      addLinkageMapping,
      removeLinkageMapping,
      removeLinkageTarget,
      widgetFields,
      widgetValueFieldOptions,
      widgetValueFields,
      widgetPrimaryValueField,
      widgetCategoryField,
      widgetSeriesField,
      widgetRowField,
      widgetColumnField,
      widgetSizeField,
      widgetTableColumns,
      widgetAggregation,
      addFormatRule,
      removeFormatRule,
      chartType,
      chartTypeLabel,
      chartSubtype,
      chartSubtypeOptions,
      chartOrientation,
      chartCombo,
      hasChartAxes,
      chartLegendVisible,
      chartDataLabelVisible,
      chartMapRoam,
      chartAxesVisible,
      chartGridLinesVisible,
      applyCustomOption,
      resetCustomOption,
      filterOptionsText,
      filterDefaultText,
      filterDefaultHint,
      filterDefaultDraft,
      commitFilterDefault,
      fieldCandidates,
      isSqlDataset,
      isMetricDateRangeTarget,
      SQL_PARAM_OPTION,
      METRIC_TIME_OPTION,
      filterTargetFieldValue,
      onFilterTargetFieldSelect,
      addFilterTarget,
      removeFilterTarget,
      onFilterTargetDatasetChange,
      onFilterTargetFieldChange,
      onFilterTypeChange,
      filterTargetLabel,
      pushdownLabel,
      pushdownDetail,
      selectWidget,
      selectFilter,
      openFiltersPanel,
      addDataset,
      removeDataset,
      addWidget,
      widgetLabel,
      addPaletteWidget,
      onPaletteDragStart,
      onPaletteDragEnd,
      onCanvasAddWidget,
      removeWidget,
      onLayoutChange,
      onResize,
      onChildLayoutChange,
      onChildResize,
      moveWidgetLayer,
      setChartType,
      addFilter,
      removeFilter,
      onFilterSearch,
      refreshData,
      commitHistory,
      save,
      openPreview,
      goBack,
    };
  },
});
