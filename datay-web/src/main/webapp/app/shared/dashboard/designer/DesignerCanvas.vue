<template>
  <div
    ref="gridRef"
    class="designer-canvas"
    :class="{ 'is-drag-over': !!dropPreview, 'is-screen': isScreenMode, 'is-pixel': isPixel, 'is-nested': nested }"
    :style="rootStyle"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
  >
    <DashboardLinkageBar v-if="!nested" :linkage="linkage" :linked-count="linkedCount" @clear="clearLinkage" />
    <div
      v-for="widget in widgets"
      :key="widget.id"
      class="designer-canvas-item"
      :data-widget-id="widget.id"
      :class="{
        'is-selected': widget.id === selectedId,
        'is-container': widget.type === 'container',
        'is-linked': linkageWidgetIds?.[widget.id],
      }"
      :style="itemStyle(widget)"
      @pointerdown="select(widget)"
    >
      <div class="designer-widget-toolbar" @pointerdown.stop="startDrag($event, widget)">
        <span class="designer-widget-title">{{ widgetLabel(widget) }}</span>
        <span v-if="isPixel && widget.type !== 'container'" class="designer-widget-layer" title="当前层级">
          L{{ widget.zIndex ?? 0 }}
        </span>
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
          :loading="widgetLoading(widget)"
          :error="widgetErrors?.[widget.id] ?? datasetErrors?.[widget.datasetId || '']"
          :theme="theme"
          :palette="chartPalette"
          :map-theme="mapTheme"
          :dataset-data="datasetData"
          :server-paged="serverPaginationWidgets?.[widget.id]"
          :page-loader="loadWidgetPage ? page => loadWidgetPage(widget.id, page) : undefined"
          :page-reset-key="pageResetKeyOf(widget)"
          :drill-loader="loadDrill"
          :apply-linkage="applyLinkage"
          :linkage-selection="linkageSelectionOf(widget)"
        >
          <template v-if="widget.type === 'container'" #container>
            <DesignerCanvas
              nested
              :parent-id="widget.id"
              :spec="containerSpec(widget)"
              :scale="scale"
              :selected-id="selectedId"
              :dataset-data="datasetData"
              :widget-data="widgetData"
              :loading-datasets="loadingDatasets"
              :loading-widgets="loadingWidgets"
              :widget-reset-keys="widgetResetKeys"
              :dataset-errors="datasetErrors"
              :widget-errors="widgetErrors"
              :server-pagination-widgets="serverPaginationWidgets"
              :load-widget-page="loadWidgetPage"
              :load-drill="loadDrill"
              :apply-linkage="applyLinkage"
              :linkage="linkage"
              :linkage-widget-ids="linkageWidgetIds"
              :data-version="dataVersion"
              :drag-item="dragItem"
              @select="emit('select', $event)"
              @layout-change="(id, layout) => emit('child-layout-change', widget.id, id, layout)"
              @resize="(id, layout) => emit('child-resize', widget.id, id, layout)"
              @delete="emit('delete', $event)"
              @layer-move="(id, direction) => emit('layer-move', id, direction)"
              @add-widget="(type, chartType, layout) => emit('add-widget', type, chartType, layout, widget.id)"
            />
          </template>
        </DashboardWidget>
      </div>
      <div class="designer-resize-handle" title="拖动调整大小" @pointerdown.stop="startResize($event, widget)"></div>
    </div>
    <div v-if="!widgets.length && !nested" class="designer-empty">
      <el-empty description="从左侧拖拽图表、KPI 或表格到画布" />
    </div>
    <div v-if="dropPreview" class="designer-drop-preview" :style="dropPreviewStyle"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';

import DashboardLinkageBar from '../DashboardLinkageBar.vue';
import DashboardWidget from '../DashboardWidget.vue';
import { CONTAINER_COLUMNS, CONTAINER_GAP, CONTAINER_PADDING, CONTAINER_ROW_HEIGHT } from '../container-layout';
import type {
  ActiveWidgetLinkage,
  ChartType,
  DashboardSpec,
  DashboardWidget as Widget,
  DatasetResult,
  WidgetDrillLoader,
  WidgetLayout,
  WidgetLinkageHandler,
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
  loadingWidgets?: Record<string, boolean>;
  widgetResetKeys?: Record<string, number>;
  datasetErrors?: Record<string, string>;
  widgetErrors?: Record<string, string>;
  serverPaginationWidgets?: Record<string, boolean>;
  loadWidgetPage?: WidgetPageLoader;
  loadDrill?: WidgetDrillLoader;
  /** 当前生效的图表联动；设计画布内点击即可预览联动效果。 */
  linkage?: ActiveWidgetLinkage | null;
  linkageWidgetIds?: Record<string, boolean>;
  applyLinkage?: WidgetLinkageHandler;
  clearLinkage?: () => void;
  dataVersion?: number;
  selectedId?: string;
  dragItem?: WidgetPalettePayload | null;
  /** 画布舞台缩放比例，拖动位移与落点需要按比例还原为逻辑像素。 */
  scale?: number;
  /** 作为容器内部画布使用：24 栅格、只渲染子组件、隐藏空状态。 */
  nested?: boolean;
  /** 嵌套画布所属容器，拖入组件时随事件回传。 */
  parentId?: string;
}>();

const emit = defineEmits<{
  select: [string];
  'layout-change': [id: string, layout: WidgetLayout];
  resize: [id: string, layout: WidgetLayout];
  delete: [string];
  'layer-move': [id: string, direction: 'up' | 'down'];
  'add-widget': [type: WidgetType, chartType: ChartType | undefined, layout: WidgetLayout, parentId?: string];
  'child-layout-change': [containerId: string, id: string, layout: WidgetLayout];
  'child-resize': [containerId: string, id: string, layout: WidgetLayout];
}>();

const linkedCount = computed(() => Object.keys(props.linkageWidgetIds ?? {}).length);
const linkageSelectionOf = (widget: Widget) => (props.linkage?.sourceWidgetId === widget.id ? (props.linkage.selection ?? null) : null);
const widgetLoading = (widget: Widget) => props.loadingWidgets?.[widget.id] || !!props.loadingDatasets?.[widget.datasetId || ''];
const pageResetKeyOf = (widget: Widget) => (props.dataVersion ?? 0) + (props.widgetResetKeys?.[widget.id] ?? 0);
const gridRef = ref<HTMLElement | null>(null);
const widgets = computed(() => {
  const list = props.spec.widgets ?? [];
  if (props.nested) {
    return list;
  }
  // 子组件在容器内部渲染；父容器缺失时回退为顶层，避免组件直接消失。
  const containerIds = new Set(list.filter(widget => widget.type === 'container').map(widget => widget.id));
  return [...list.filter(widget => !widget.parentId || !containerIds.has(widget.parentId))].sort(
    (left, right) => Number(right.type === 'container') - Number(left.type === 'container'),
  );
});

const containerSpec = (container: Widget): DashboardSpec => ({
  ...props.spec,
  layout: {
    ...props.spec.layout,
    mode: 'standard',
    columns: 24,
    rowHeight: CONTAINER_ROW_HEIGHT,
    gap: 4,
    screen: {
      ...props.spec.layout?.screen,
      width: 1200,
      height: 800,
      canvas: { unit: 'grid', padding: 8, gap: 4 },
    },
  },
  widgets: (props.spec.widgets ?? []).filter(widget => widget.parentId === container.id && widget.type !== 'container'),
});

const metrics = computed(() => {
  const resolved = resolveCanvasMetrics(props.spec.layout);
  if (!props.nested) {
    return resolved;
  }
  return {
    ...resolved,
    unit: 'grid' as const,
    columns: CONTAINER_COLUMNS,
    rowHeight: CONTAINER_ROW_HEIGHT,
    gap: CONTAINER_GAP,
    padding: CONTAINER_PADDING,
  };
});
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

function stackOf(widget: Widget) {
  return widget.type === 'container' ? 0 : (widget.zIndex ?? 0);
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
      zIndex: stackOf(widget),
    };
  }
  return {
    gridColumn: `${(layout?.x ?? 0) + 1} / span ${layout?.w ?? 12}`,
    gridRow: `${(layout?.y ?? 0) + 1} / span ${layout?.h ?? 8}`,
    zIndex: stackOf(widget),
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
  if (props.nested) {
    // 容器内部画布自行处理拖入，避免外层画布再显示一份落点预览。
    event.stopPropagation();
  }
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy';
  }
  dropPreview.value = props.dragItem ? layoutForDrop(event, props.dragItem) : null;
}

function onDragLeave(event: DragEvent) {
  if (props.nested) {
    event.stopPropagation();
  }
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
    if (props.nested) {
      emit('add-widget', payload.type, payload.chartType, layout, props.parentId);
    } else {
      emit('add-widget', payload.type, payload.chartType, layout);
    }
  }
  event.stopPropagation();
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

.designer-canvas.is-nested {
  height: 100%;
  min-height: 0;
  background: transparent;
  border-radius: 0;
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

.designer-canvas.is-pixel > .designer-canvas-item {
  position: absolute;
}

.designer-canvas-item.is-linked {
  outline: 2px dashed #409eff;
  outline-offset: 1px;
}

.designer-canvas-item.is-selected:not(.is-container) {
  /* 高于画布内组件（zIndex ≤ 999），但低于 Element Plus 弹窗（2000+），避免遮挡下钻明细。 */
  z-index: 1000 !important;
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

.designer-widget-layer {
  flex: none;
  margin-left: 6px;
  padding: 0 4px;
  border-radius: 3px;
  background: rgb(64 158 255 / 12%);
  color: #2f7fd1;
  font-size: 11px;
  line-height: 16px;
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

.designer-canvas.is-pixel > .designer-empty {
  position: absolute;
  inset: 0;
}
</style>
