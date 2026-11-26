<template>
  <div
    ref="gridRef"
    class="designer-canvas"
    :class="{ 'is-drag-over': !!dropPreview, 'is-screen': isScreenMode, 'is-pixel': isPixel }"
    :style="rootStyle"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
  >
    <div
      v-for="widget in widgets"
      :key="widget.id"
      class="designer-canvas-item"
      :class="{ 'is-selected': widget.id === selectedId }"
      :style="itemStyle(widget)"
      @pointerdown="select(widget)"
    >
      <div class="designer-widget-toolbar" @pointerdown.stop="startDrag($event, widget)">
        <span class="designer-widget-title">{{ widgetLabel(widget) }}</span>
        <div class="designer-widget-actions">
          <template v-if="isPixel">
            <el-button text size="small" title="上移一层" @pointerdown.stop @click.stop="emit('layer-move', widget.id, 'up')">↑</el-button>
            <el-button text size="small" title="下移一层" @pointerdown.stop @click.stop="emit('layer-move', widget.id, 'down')"
              >↓</el-button
            >
          </template>
          <el-button text size="small" title="删除组件" @pointerdown.stop @click.stop="emit('delete', widget.id)">
            <font-awesome-icon icon="trash"></font-awesome-icon>
          </el-button>
        </div>
      </div>
      <div class="designer-widget-body">
        <DashboardWidget
          :widget="widget"
          :result="widgetData?.[widget.id] ?? datasetData?.[widget.datasetId || '']"
          :loading="loadingDatasets?.[widget.datasetId || '']"
          :error="widgetErrors?.[widget.id] ?? datasetErrors?.[widget.datasetId || '']"
          :theme="theme"
          :palette="chartPalette"
          :map-theme="mapTheme"
          :dataset-data="datasetData"
          :server-paged="serverPaginationWidgets?.[widget.id]"
          :page-loader="loadWidgetPage ? page => loadWidgetPage(widget.id, page) : undefined"
          :page-reset-key="dataVersion"
          :drill-loader="loadDrill"
        />
      </div>
      <div class="designer-resize-handle" title="拖动调整大小" @pointerdown.stop="startResize($event, widget)"></div>
    </div>
    <div v-if="!widgets.length" class="designer-empty">
      <el-empty description="从左侧拖拽图表、KPI 或表格到画布" />
    </div>
    <div v-if="dropPreview" class="designer-drop-preview" :style="dropPreviewStyle"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';

import DashboardWidget from '../DashboardWidget.vue';
import type {
  ChartType,
  DashboardSpec,
  DashboardWidget as Widget,
  DatasetResult,
  WidgetDrillLoader,
  WidgetLayout,
  WidgetPalettePayload,
  WidgetPageLoader,
  WidgetType,
} from '../types';
import { WIDGET_PALETTE_MIME } from '../types';
import { normalizeTheme } from '../layout-defaults';
import { resolveChartPalette, resolveThemeTokens, themeCssVars } from '../theme-presets';
import {
  clampPixelLayout,
  clamp,
  layoutFromPixelPoint,
  layoutFromPoint,
  normalizeWidgetLayout,
  pixelSizeFromGrid,
  resolveCanvasMetrics,
} from './grid-layout';

const props = defineProps<{
  spec: DashboardSpec;
  datasetData?: Record<string, DatasetResult>;
  widgetData?: Record<string, DatasetResult>;
  loadingDatasets?: Record<string, boolean>;
  datasetErrors?: Record<string, string>;
  widgetErrors?: Record<string, string>;
  serverPaginationWidgets?: Record<string, boolean>;
  loadWidgetPage?: WidgetPageLoader;
  loadDrill?: WidgetDrillLoader;
  dataVersion?: number;
  selectedId?: string;
  dragItem?: WidgetPalettePayload | null;
  /** 画布舞台缩放比例，拖动位移与落点需要按比例还原为逻辑像素。 */
  scale?: number;
}>();

const emit = defineEmits<{
  select: [string];
  'layout-change': [id: string, layout: WidgetLayout];
  resize: [id: string, layout: WidgetLayout];
  delete: [string];
  'layer-move': [id: string, direction: 'up' | 'down'];
  'add-widget': [type: WidgetType, chartType: ChartType | undefined, layout: WidgetLayout];
}>();

const gridRef = ref<HTMLElement | null>(null);
const widgets = computed(() => props.spec.widgets ?? []);

const metrics = computed(() => resolveCanvasMetrics(props.spec.layout));
const isPixel = computed(() => metrics.value.unit === 'px');
const isScreenMode = computed(() => props.spec.layout?.mode === 'screen');
const theme = computed(() => normalizeTheme(props.spec.layout?.screen?.theme));
const scale = computed(() => (props.scale && props.scale > 0 ? props.scale : 1));
const themeTokens = computed(() => resolveThemeTokens(props.spec.layout?.screen?.themePreset, props.spec.layout?.screen?.tokens));
const themeVars = computed(() => themeCssVars(themeTokens.value));
const chartPalette = computed(() => resolveChartPalette(themeTokens.value));
const mapTheme = computed(() => themeTokens.value.map);

const rootStyle = computed<Record<string, string>>(() => {
  if (isPixel.value) {
    // px 画布由组件自身携带 padding 偏移，根节点保持贴边以便坐标换算。
    return { position: 'relative', padding: '0', ...themeVars.value };
  }
  const lineWidth = `${Math.round(Math.min(4, Math.max(1, 1 / scale.value)) * 100) / 100}px`;
  const columnStep = `calc((100% + ${metrics.value.gap}px) / ${metrics.value.columns})`;
  const rowStep = `${metrics.value.rowHeight + metrics.value.gap}px`;
  return {
    padding: `${metrics.value.padding}px`,
    gridTemplateColumns: `repeat(${metrics.value.columns}, minmax(0, 1fr))`,
    gridAutoRows: `${metrics.value.rowHeight}px`,
    gap: `${metrics.value.gap}px`,
    // 竖向与横向网格线都按当前缩放反向加粗，缩小时仍保持至少 1 个物理像素。
    backgroundImage: `linear-gradient(90deg, var(--dashboard-grid-line, rgba(64, 158, 255, 0.24)) ${lineWidth}, transparent ${lineWidth}), linear-gradient(180deg, var(--dashboard-grid-line-secondary, rgba(64, 158, 255, 0.14)) ${lineWidth}, transparent ${lineWidth})`,
    backgroundSize: `${columnStep} 100%, 100% ${rowStep}`,
    ...themeVars.value,
  };
});

interface DragState {
  id: string;
  mode: 'move' | 'resize';
  startX: number;
  startY: number;
  origin: WidgetLayout;
  current: WidgetLayout;
  cellWidth: number;
  rowUnit: number;
  scale: number;
}

const drag = ref<DragState | null>(null);
const dropPreview = ref<WidgetLayout | null>(null);

watch(
  () => props.dragItem,
  item => {
    if (!item) {
      dropPreview.value = null;
    }
  },
);

function widgetLabel(widget: Widget): string {
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
}

function itemStyle(widget: Widget) {
  const layout = drag.value?.id === widget.id ? drag.value.current : widget.layout;
  if (isPixel.value) {
    return {
      position: 'absolute',
      left: `${metrics.value.padding + (layout?.x ?? 0)}px`,
      top: `${metrics.value.padding + (layout?.y ?? 0)}px`,
      width: `${layout?.w ?? 240}px`,
      height: `${layout?.h ?? 160}px`,
      zIndex: widget.zIndex || undefined,
    };
  }
  return {
    gridColumn: `${(layout?.x ?? 0) + 1} / span ${layout?.w ?? 12}`,
    gridRow: `${(layout?.y ?? 0) + 1} / span ${layout?.h ?? 8}`,
    zIndex: widget.zIndex || undefined,
  };
}

function measure() {
  const el = gridRef.value;
  const contentWidth = Math.max(1, (el?.clientWidth ?? 1200) - metrics.value.padding * 2);
  const cellWidth = Math.max(1, (contentWidth - metrics.value.gap * (metrics.value.columns - 1)) / metrics.value.columns);
  return { cellWidth, rowUnit: metrics.value.rowHeight + metrics.value.gap };
}

function readDragPayload(event: DragEvent): WidgetPalettePayload | null {
  if (props.dragItem) {
    return props.dragItem;
  }
  const raw = event.dataTransfer?.getData(WIDGET_PALETTE_MIME);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as WidgetPalettePayload;
  } catch {
    return null;
  }
}

function layoutForDrop(event: DragEvent, payload: WidgetPalettePayload): WidgetLayout | null {
  const el = gridRef.value;
  if (!el) {
    return null;
  }
  const rect = el.getBoundingClientRect();
  const point = {
    x: (event.clientX - rect.left) / scale.value - metrics.value.padding,
    y: (event.clientY - rect.top) / scale.value - metrics.value.padding,
  };
  if (isPixel.value) {
    return layoutFromPixelPoint(point, pixelSizeFromGrid(payload.w, payload.h, metrics.value), metrics.value);
  }
  const { cellWidth } = measure();
  return layoutFromPoint(
    point,
    { w: payload.w, h: payload.h },
    {
      cellWidth,
      rowHeight: metrics.value.rowHeight,
      gap: metrics.value.gap,
      columns: metrics.value.columns,
    },
  );
}

const dropPreviewStyle = computed(() => {
  const layout = dropPreview.value;
  const el = gridRef.value;
  if (!layout || !el) {
    return {};
  }
  if (isPixel.value) {
    return {
      left: `${metrics.value.padding + layout.x}px`,
      top: `${metrics.value.padding + layout.y}px`,
      width: `${layout.w}px`,
      height: `${layout.h}px`,
    };
  }
  const { cellWidth, rowUnit } = measure();
  return {
    left: `${metrics.value.padding + layout.x * (cellWidth + metrics.value.gap)}px`,
    top: `${metrics.value.padding + layout.y * rowUnit}px`,
    width: `${layout.w * cellWidth + Math.max(0, layout.w - 1) * metrics.value.gap}px`,
    height: `${layout.h * metrics.value.rowHeight + Math.max(0, layout.h - 1) * metrics.value.gap}px`,
  };
});

function hasPaletteData(event: DragEvent): boolean {
  return !!props.dragItem || Array.from(event.dataTransfer?.types ?? []).includes(WIDGET_PALETTE_MIME);
}

function onDragOver(event: DragEvent) {
  if (!hasPaletteData(event)) {
    return;
  }
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy';
  }
  dropPreview.value = props.dragItem ? layoutForDrop(event, props.dragItem) : null;
}

function onDragLeave(event: DragEvent) {
  const current = event.currentTarget as Node | null;
  const related = event.relatedTarget as Node | null;
  if (current && related && current.contains(related)) {
    return;
  }
  dropPreview.value = null;
}

function onDrop(event: DragEvent) {
  dropPreview.value = null;
  const payload = readDragPayload(event);
  if (!payload) {
    return;
  }
  event.preventDefault();
  const layout = layoutForDrop(event, payload);
  if (layout) {
    emit('add-widget', payload.type, payload.chartType, layout);
  }
}

function startDrag(event: PointerEvent, widget: Widget) {
  if (event.button !== 0) {
    return;
  }
  event.preventDefault();
  const { cellWidth, rowUnit } = measure();
  drag.value = {
    id: widget.id,
    mode: 'move',
    startX: event.clientX,
    startY: event.clientY,
    origin: normalizeWidgetLayout(widget.layout, metrics.value),
    current: normalizeWidgetLayout(widget.layout, metrics.value),
    cellWidth,
    rowUnit,
    scale: scale.value,
  };
  emit('select', widget.id);
  bindWindow();
}

function startResize(event: PointerEvent, widget: Widget) {
  if (event.button !== 0) {
    return;
  }
  event.preventDefault();
  const { cellWidth, rowUnit } = measure();
  drag.value = {
    id: widget.id,
    mode: 'resize',
    startX: event.clientX,
    startY: event.clientY,
    origin: normalizeWidgetLayout(widget.layout, metrics.value),
    current: normalizeWidgetLayout(widget.layout, metrics.value),
    cellWidth,
    rowUnit,
    scale: scale.value,
  };
  emit('select', widget.id);
  bindWindow();
}

function onPointerMove(event: PointerEvent) {
  const state = drag.value;
  if (!state || state.cellWidth <= 0 || state.scale <= 0) {
    return;
  }
  const dxRaw = (event.clientX - state.startX) / state.scale;
  const dyRaw = (event.clientY - state.startY) / state.scale;
  if (isPixel.value) {
    if (state.mode === 'move') {
      state.current = clampPixelLayout({ ...state.origin, x: state.origin.x + dxRaw, y: state.origin.y + dyRaw }, metrics.value);
    } else {
      state.current = clampPixelLayout({ ...state.origin, w: state.origin.w + dxRaw, h: state.origin.h + dyRaw }, metrics.value);
    }
    return;
  }
  const dx = Math.round(dxRaw / state.cellWidth);
  const dy = Math.round(dyRaw / state.rowUnit);
  if (state.mode === 'move') {
    state.current = {
      ...state.origin,
      x: clamp(state.origin.x + dx, 0, metrics.value.columns - state.origin.w),
      y: Math.max(0, state.origin.y + dy),
    };
  } else {
    state.current = {
      ...state.origin,
      w: clamp(state.origin.w + dx, 1, metrics.value.columns - state.origin.x),
      h: Math.max(1, state.origin.h + dy),
    };
  }
}

function onPointerUp() {
  const state = drag.value;
  if (state) {
    emit(state.mode === 'resize' ? 'resize' : 'layout-change', state.id, state.current);
  }
  drag.value = null;
  unbindWindow();
}

function bindWindow() {
  window.addEventListener('pointermove', onPointerMove);
  window.addEventListener('pointerup', onPointerUp);
  window.addEventListener('pointercancel', onPointerUp);
}

function unbindWindow() {
  window.removeEventListener('pointermove', onPointerMove);
  window.removeEventListener('pointerup', onPointerUp);
  window.removeEventListener('pointercancel', onPointerUp);
}

onUnmounted(unbindWindow);

function select(widget: Widget) {
  emit('select', widget.id);
}
</script>

<style scoped>
.designer-canvas {
  position: relative;
  display: grid;
  box-sizing: border-box;
  min-height: 480px;
  padding: 16px;
  background-color: #fbfcff;
  background-origin: content-box;
  border-radius: 8px;
}

.designer-canvas.is-screen {
  height: 100%;
  min-height: 0;
  background-color: transparent;
  border-radius: 6px;
}

.designer-canvas.is-pixel {
  display: block;
  background-color: transparent;
  background-image: radial-gradient(circle, var(--dashboard-grid-line, rgb(64 158 255 / 18%)) 1px, transparent 1px);
  background-size: 20px 20px;
}

.designer-canvas.is-drag-over {
  outline: 2px dashed #409eff;
  outline-offset: -2px;
}

.designer-drop-preview {
  position: absolute;
  z-index: 5;
  pointer-events: none;
  background: rgb(64 158 255 / 8%);
  border: 2px dashed #409eff;
  border-radius: 6px;
}

.designer-canvas-item {
  position: relative;
  min-width: 0;
  min-height: 0;
  outline: 1px solid transparent;
  transition: outline-color 0.15s;
}

.designer-canvas.is-pixel .designer-canvas-item {
  position: absolute;
}

.designer-canvas-item.is-selected {
  z-index: 10000 !important;
  outline: 2px solid #409eff;
  outline-offset: 1px;
}

.designer-widget-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 26px;
  padding: 0 6px;
  background: #ecf5ff;
  border-radius: 4px 4px 0 0;
  color: #409eff;
  font-size: 12px;
  cursor: move;
  touch-action: none;
  user-select: none;
}

.designer-widget-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.designer-widget-actions {
  display: flex;
  align-items: center;
}

.designer-widget-body {
  height: calc(100% - 26px);
}

.designer-resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 16px;
  height: 16px;
  background: linear-gradient(135deg, transparent 45%, #409eff 50%);
  border-radius: 0 0 4px 0;
  cursor: nwse-resize;
  touch-action: none;
}

.designer-empty {
  display: flex;
  grid-column: 1 / -1;
  align-items: center;
  justify-content: center;
  min-height: 420px;
}

.designer-canvas.is-pixel .designer-empty {
  position: absolute;
  inset: 0;
}
</style>
