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
  DEFAULT_SCREEN_HEIGHT,
  DEFAULT_SCREEN_WIDTH,
  getSystemScreenSize,
  LIGHT_SCREEN_BACKGROUND,
  normalizeDashboardLayout,
  normalizeDashboardSpec,
  normalizeFitMode,
  normalizeTheme,
  resolveScreenBackgroundStyle,
  resolveScreenTheme,
  SCREEN_FIT_MODES,
} from '@/shared/dashboard/layout-defaults';
import DashboardBackgroundService from '@/shared/dashboard/dashboard-background.service';
import { useDashboardBackgroundImage } from '@/shared/dashboard/useDashboardBackgroundImage';
import {
  buildDashboardFields,
  createDefaultDataRef,
  ensureDefaultFields,
  hasCompleteDefaultFields,
  resolveChartType,
} from '@/shared/dashboard/field-defaults';
import type { DashboardField } from '@/shared/dashboard/field-defaults';
import { createDashboardPreviewDraft } from '@/shared/dashboard/preview-draft';
import type {
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
  ScreenFitMode,
  WidgetLayout,
  WidgetPalettePayload,
  WidgetType,
} from '@/shared/dashboard/types';
import { WIDGET_PALETTE_MIME } from '@/shared/dashboard/types';
import { useDashboardData } from '@/shared/dashboard/useDashboardData';
import DashboardFilters from '@/shared/dashboard/DashboardFilters.vue';
import DashboardStage from '@/shared/dashboard/DashboardStage.vue';
import DesignerCanvas from '@/shared/dashboard/designer/DesignerCanvas.vue';
import WidgetPaletteIcon from '@/shared/dashboard/designer/WidgetPaletteIcon.vue';
import {
  CANVAS_PADDING,
  compactLayouts,
  DEFAULT_GAP,
  DEFAULT_ROW_HEIGHT,
  GRID_COLUMNS,
  layoutContentHeight,
  moveLayout,
  nextLayout,
  resizeLayout,
} from '@/shared/dashboard/designer/grid-layout';

type PaletteCategory = 'charts' | 'kpi' | 'table' | 'text';

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

const AI_ASSISTANT_ID = 'dashboard-design';
const HISTORY_LIMIT = 60;
const HISTORY_COMMIT_DELAY = 400;

const CHART_LABELS: Record<ChartType, string> = {
  bar: '柱状图',
  line: '折线图',
  pie: '饼图',
  area: '面积图',
};

const PALETTE_CATEGORIES: Array<{ key: PaletteCategory; label: string; icon: string }> = [
  { key: 'charts', label: '图表', icon: 'bar' },
  { key: 'kpi', label: 'KPI', icon: 'kpi' },
  { key: 'table', label: '表格', icon: 'table' },
  { key: 'text', label: '文本', icon: 'text' },
];

const PALETTE_ITEMS: PaletteItem[] = [
  { key: 'bar', label: '柱状图', icon: 'bar', category: 'charts', type: 'echarts', chartType: 'bar', w: 12, h: 8 },
  { key: 'line', label: '折线图', icon: 'line', category: 'charts', type: 'echarts', chartType: 'line', w: 12, h: 8 },
  { key: 'pie', label: '饼图', icon: 'pie', category: 'charts', type: 'echarts', chartType: 'pie', w: 8, h: 8 },
  { key: 'area', label: '面积图', icon: 'area', category: 'charts', type: 'echarts', chartType: 'area', w: 12, h: 8 },
  { key: 'kpi', label: 'KPI 指标', icon: 'kpi', category: 'kpi', type: 'kpi', w: 6, h: 4 },
  { key: 'table', label: '明细表', icon: 'table', category: 'table', type: 'table', w: 12, h: 8 },
  { key: 'html', label: '说明文本', icon: 'text', category: 'text', type: 'html', w: 12, h: 4 },
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
  const seriesType = type === 'area' ? 'line' : type;
  const option: Record<string, any> = {
    tooltip: { trigger: seriesType === 'pie' ? 'item' : 'axis' },
    legend: { top: 0 },
    series: [
      {
        type: seriesType,
        smooth: seriesType === 'line',
        areaStyle: type === 'area' ? {} : undefined,
        emphasis: { focus: 'series' },
      },
    ],
  };
  if (seriesType !== 'pie') {
    option.grid = { left: 48, right: 24, top: 40, bottom: 36 };
    option.xAxis = { type: 'category', boundaryGap: seriesType === 'bar' };
    option.yAxis = { type: 'value' };
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

const errorMessage = (err: any, fallback: string): string =>
  err?.response?.data?.errorMessage ||
  err?.response?.data?.detail ||
  err?.response?.data?.title ||
  err?.response?.data?.message ||
  err?.message ||
  fallback;

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
    const propertyTab = ref<'basic' | 'custom'>('basic');
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
      title: '',
      description: '',
      layout: normalizeDashboardLayout(),
      datasets: [],
      widgets: [],
      filters: [],
    });
    const filterValues = ref<FilterValues>({});

    const { datasetData, rawDatasetData, loadingDatasets, datasetErrors, loading: loadingData, loadAll } = useDashboardData({ spec });

    const selectedWidget = computed(() => spec.value.widgets.find(widget => widget.id === selectedWidgetId.value));
    const selectedFilter = computed(() => (spec.value.filters ?? []).find(filter => filter.id === selectedFilterId.value));

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
          autoRefreshSeconds: 0,
        };
      }
      return spec.value.layout.screen;
    };

    const updateScreenResolution = (width: number, height: number) => {
      const screen = ensureScreenSettings();
      screen.width = Math.min(7680, Math.max(640, Math.round(width || DEFAULT_SCREEN_WIDTH)));
      screen.height = Math.min(4320, Math.max(480, Math.round(height || DEFAULT_SCREEN_HEIGHT)));
      canvasZoom.value = 'fit';
    };

    const layoutMode = computed<DashboardMode>({
      get: () => spec.value.layout?.mode ?? 'standard',
      set: mode => {
        if (!spec.value.layout) {
          spec.value.layout = normalizeDashboardLayout();
        }
        spec.value.layout.mode = mode;
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
      return (
        layoutContentHeight(spec.value.widgets, layout.rowHeight ?? DEFAULT_ROW_HEIGHT, layout.gap ?? DEFAULT_GAP, CANVAS_PADDING) >
        screen.height
      );
    });

    const canvasBindings = computed(() => ({
      spec: spec.value,
      datasetData: datasetData.value,
      loadingDatasets: loadingDatasets.value,
      datasetErrors: datasetErrors.value,
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
      return buildDashboardFields(columns, result?.rows ?? [], declared);
    };

    /** 初始化或补全组件的 dataRef，并在字段尚未就绪时等待数据集结果。 */
    const setupWidgetDataRef = (widget: DashboardWidget, fields = fieldCandidates(widget.datasetId)) => {
      if (!widget.dataRef) {
        widget.dataRef = createDefaultDataRef(widget.type, resolveChartType(widget.echartsOption), fields);
      }
      if (widget.dataRef) {
        ensureDefaultFields(widget, fields);
      }
      if ((widget.type === 'echarts' || widget.type === 'kpi') && !hasCompleteDefaultFields(widget)) {
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
      if (widget && !widget.dataRef) {
        setupWidgetDataRef(widget);
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

    /**
     * 纵轴支持多个数值字段：单字段复用 valueField，多字段写入 series。
     */
    const widgetValueFields = computed<string[]>({
      get: () => {
        const ref = selectedWidget.value?.dataRef;
        if (!ref) {
          return [];
        }
        if (ref.series?.length) {
          return ref.series.map(series => series.field).filter(Boolean);
        }
        return ref.valueField ? [ref.valueField] : [];
      },
      set: fields => {
        const ref = selectedWidget.value?.dataRef;
        if (!ref) {
          return;
        }
        const unique = Array.from(new Set(fields.filter(Boolean)));
        ref.valueField = unique[0] ?? '';
        if (unique.length <= 1) {
          delete ref.series;
          return;
        }
        ref.series = unique.map(field => ({ name: field, field }));
        ref.seriesField = null;
      },
    });

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
            dataRef: widget.dataRef,
          })),
        }),
      () => scheduleReload(),
    );

    // 新建图表时数据集可能尚未完成取数；结果到达后补全默认分类/数值字段。
    watch(
      datasetData,
      () => {
        if (!pendingFieldDefaults.size) {
          return;
        }
        for (const widget of spec.value.widgets) {
          if (!pendingFieldDefaults.has(widget.id) || !widget.dataRef) {
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
      await loadAll(filterValues.value);
    }

    const onFiltersChange = async (values: FilterValues) => {
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
      spec.value.filters = (spec.value.filters ?? []).map(filter => {
        const next: DashboardFilter = {
          ...filter,
          bindings: (filter.bindings ?? []).filter(binding => binding.datasetId !== dataset.id),
        };
        if (next.datasetId === dataset.id) {
          next.datasetId = undefined;
          next.field = '';
        }
        return next;
      });
      commitHistory();
    };

    const nextWidgetId = (type: WidgetType) => {
      let index = spec.value.widgets.length + 1;
      while (spec.value.widgets.some(widget => widget.id === `${type}${index}`)) {
        index += 1;
      }
      return `${type}${index}`;
    };

    const addWidget = (type: WidgetType, chartType: ChartType = 'bar', layout?: WidgetLayout) => {
      const id = nextWidgetId(type);
      const title = type === 'html' ? '' : type === 'kpi' ? 'KPI 指标' : type === 'table' ? '明细表' : CHART_LABELS[chartType] || '图表';
      const defaultWidth = type === 'kpi' ? 6 : type === 'echarts' && chartType === 'pie' ? 8 : 12;
      const defaultHeight = type === 'kpi' ? 4 : type === 'html' ? 4 : 8;
      const widget: DashboardWidget = {
        id,
        title,
        type,
        layout: layout ? { ...layout } : nextLayout(spec.value.widgets, defaultWidth, defaultHeight),
      };
      if (type !== 'html') {
        widget.datasetId = spec.value.datasets[0]?.id;
      }
      if (type === 'echarts') {
        widget.echartsOption = DEFAULT_ECHARTS_OPTION(chartType);
      } else if (type === 'html') {
        widget.html = '<div>在此编写 HTML 说明</div>';
      }
      const fields = type === 'html' ? [] : fieldCandidates(widget.datasetId);
      if (type !== 'html') {
        widget.dataRef = createDefaultDataRef(type, chartType, fields);
      }
      spec.value.widgets.push(widget);
      if (type !== 'html') {
        setupWidgetDataRef(widget, fields);
      }
      if (layout) {
        applyLayouts(
          compactLayouts(
            spec.value.widgets.map(item => ({ id: item.id, layout: item.layout })),
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
      addWidget(item.type, item.chartType, nextLayout(spec.value.widgets, item.w, item.h));
    };

    const onCanvasAddWidget = (type: WidgetType, chartType: ChartType | undefined, layout: WidgetLayout) => {
      addWidget(type, chartType, layout);
      paletteDragItem.value = null;
    };

    const removeWidget = (id: string) => {
      pendingFieldDefaults.delete(id);
      spec.value.widgets = spec.value.widgets.filter(widget => widget.id !== id);
      if (selectedWidgetId.value === id) {
        selectedWidgetId.value = '';
      }
      commitHistory();
    };

    const onLayoutChange = (id: string, layout: WidgetLayout) => {
      const items = spec.value.widgets.map(widget => ({ id: widget.id, layout: widget.layout }));
      const updated = moveLayout(items, id, layout.x, layout.y, spec.value.layout?.columns ?? GRID_COLUMNS);
      applyLayouts(updated);
      commitHistory();
    };

    const onResize = (id: string, layout: WidgetLayout) => {
      const items = spec.value.widgets.map(widget => ({ id: widget.id, layout: widget.layout }));
      const updated = resizeLayout(items, id, layout.w, layout.h, spec.value.layout?.columns ?? GRID_COLUMNS);
      applyLayouts(updated);
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

    const chartType = computed<ChartType>(() => resolveChartType(selectedWidget.value?.echartsOption));

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
      if (type === 'pie' && widget.dataRef?.series?.length) {
        widget.dataRef.valueField = widget.dataRef.valueField || widget.dataRef.series[0]?.field || '';
        delete widget.dataRef.series;
      }
      setupWidgetDataRef(widget);
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

    const selectedFilterFields = computed(() => fieldCandidates(selectedFilter.value?.datasetId));

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

    const onFilterDatasetChange = () => {
      const filter = selectedFilter.value;
      if (!filter) {
        return;
      }
      filter.field = defaultFilterField(filter.datasetId, filter.type);
      filter.bindings = [];
      commitHistory();
    };

    const onFilterTypeChange = () => {
      const filter = selectedFilter.value;
      if (!filter) {
        return;
      }
      filter.field = defaultFilterField(filter.datasetId, filter.type);
      filter.bindings = [];
      commitHistory();
    };

    const onFilterFieldChange = () => {
      const filter = selectedFilter.value;
      if (filter) {
        filter.bindings = [];
        commitHistory();
      }
    };

    const filterTargetLabel = (filter: DashboardFilter): string => {
      const dataset = spec.value.datasets.find(item => item.id === filter.datasetId);
      if (!dataset) {
        return '未配置数据集/字段';
      }
      return `${dataset.name || dataset.id}.${filter.field || '未选字段'}`;
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
        options: { mode: 'static', values: [] },
        datasetId: dataset?.id,
        field: dataset ? defaultFilterField(dataset.id, 'select') : '',
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
          mode: 'static',
          ...(selectedFilter.value.options ?? {}),
          values: value
            .split(',')
            .map(item => item.trim())
            .filter(Boolean),
        };
      },
    });

    const filterDefaultText = computed({
      get: () => {
        const value = selectedFilter.value?.defaultValue;
        if (Array.isArray(value)) {
          return value.join(',');
        }
        return value === undefined || value === null ? '' : String(value);
      },
      set: (value: string) => {
        const filter = selectedFilter.value;
        if (!filter) {
          return;
        }
        const parts = value
          .split(',')
          .map(item => item.trim())
          .filter(Boolean);
        if (filter.type === 'multiSelect') {
          filter.defaultValue = parts;
        } else {
          filter.defaultValue = parts[0];
        }
        filterValues.value[filter.id] = filter.defaultValue;
      },
    });

    const save = async () => {
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
        spec.value.title = name.value.trim();
        spec.value.description = description.value;
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
      const previewName = name.value.trim() || '未命名看板';
      const previewSpec: DashboardSpec = {
        ...(JSON.parse(JSON.stringify(spec.value)) as DashboardSpec),
        title: previewName,
        description: description.value,
      };
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
      rawDatasetData,
      loadingDatasets,
      datasetErrors,
      loadingData,
      selectedWidget,
      selectedFilter,
      dashboardSettingsVisible,
      openDashboardSettings,
      propertiesRef,
      isScreenMode,
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
      widgetFields,
      widgetValueFieldOptions,
      widgetValueFields,
      chartType,
      applyCustomOption,
      resetCustomOption,
      filterOptionsText,
      filterDefaultText,
      selectedFilterFields,
      onFilterDatasetChange,
      onFilterTypeChange,
      onFilterFieldChange,
      filterTargetLabel,
      selectWidget,
      selectFilter,
      openFiltersPanel,
      addDataset,
      removeDataset,
      addWidget,
      addPaletteWidget,
      onPaletteDragStart,
      onPaletteDragEnd,
      onCanvasAddWidget,
      removeWidget,
      onLayoutChange,
      onResize,
      setChartType,
      addFilter,
      removeFilter,
      onFiltersChange,
      refreshData,
      save,
      openPreview,
      goBack,
    };
  },
});
