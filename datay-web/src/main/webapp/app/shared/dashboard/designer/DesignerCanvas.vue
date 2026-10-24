<template>
  <div
    ref="gridRef"
    class="designer-canvas"
    :class="{ 'is-drag-over': !!dropPreview, 'is-screen': isScreenMode }"
    :style="gridStyle"
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
        <span class="designer-widget-title">{{ widget.title || (widget.type === 'html' ? '说明文本' : widget.id) }}</span>
        <div class="designer-widget-actions">
          <el-button text size="small" title="删除组件" @pointerdown.stop @click.stop="emit('delete', widget.id)">
            <font-awesome-icon icon="trash"></font-awesome-icon>
          </el-button>
        </div>
      </div>
      <div class="designer-widget-body">
        <DashboardWidget
          :widget="widget"
          :result="datasetData?.[widget.datasetId || '']"
          :loading="loadingDatasets?.[widget.datasetId || '']"
          :error="datasetErrors?.[widget.datasetId || '']"
          :theme="theme"
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
  WidgetLayout,
  WidgetPalettePayload,
  WidgetType,
} from '../types';
import { WIDGET_PALETTE_MIME } from '../types';
import { normalizeTheme } from '../layout-defaults';
import { CANVAS_PADDING, clamp, DEFAULT_GAP, DEFAULT_ROW_HEIGHT, GRID_COLUMNS, layoutFromPoint, normalizeLayout } from './grid-layout';

const props = defineProps<{
  spec: DashboardSpec;
  datasetData?: Record<string, DatasetResult>;
  loadingDatasets?: Record<string, boolean>;
  datasetErrors?: Record<string, string>;
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
  'add-widget': [type: WidgetType, chartType: ChartType | undefined, layout: WidgetLayout];
}>();

const gridRef = ref<HTMLElement | null>(null);
const widgets = computed(() => props.spec.widgets ?? []);

const columns = computed(() => props.spec.layout?.columns ?? GRID_COLUMNS);
const rowHeight = computed(() => props.spec.layout?.rowHeight ?? DEFAULT_ROW_HEIGHT);
const gap = computed(() => props.spec.layout?.gap ?? DEFAULT_GAP);
const isScreenMode = computed(() => props.spec.layout?.mode === 'screen');
const theme = computed(() => normalizeTheme(props.spec.layout?.screen?.theme));
const scale = computed(() => (props.scale && props.scale > 0 ? props.scale : 1));

const gridStyle = computed(() => {
  const lineWidth = `${Math.round(Math.min(4, Math.max(1, 1 / scale.value)) * 100) / 100}px`;
  const columnStep = `calc((100% + ${gap.value}px) / ${columns.value})`;
  const rowStep = `${rowHeight.value + gap.value}px`;
  return {
    gridTemplateColumns: `repeat(${columns.value}, minmax(0, 1fr))`,
    gridAutoRows: `${rowHeight.value}px`,
    gap: `${gap.value}px`,
    // 竖向与横向网格线都按当前缩放反向加粗，缩小时仍保持至少 1 个物理像素。
    backgroundImage: `linear-gradient(90deg, var(--dashboard-grid-line, rgba(64, 158, 255, 0.24)) ${lineWidth}, transparent ${lineWidth}), linear-gradient(180deg, var(--dashboard-grid-line-secondary, rgba(64, 158, 255, 0.14)) ${lineWidth}, transparent ${lineWidth})`,
    backgroundSize: `${columnStep} 100%, 100% ${rowStep}`,
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

function itemStyle(widget: Widget) {
  const layout = drag.value?.id === widget.id ? drag.value.current : widget.layout;
  return {
    gridColumn: `${(layout?.x ?? 0) + 1} / span ${layout?.w ?? 12}`,
    gridRow: `${(layout?.y ?? 0) + 1} / span ${layout?.h ?? 8}`,
  };
}

function measure() {
  const el = gridRef.value;
  const width = Math.max(1, (el?.clientWidth ?? 1200) - CANVAS_PADDING * 2);
  const cellWidth = Math.max(1, (width - gap.value * (columns.value - 1)) / columns.value);
  return { cellWidth, rowUnit: rowHeight.value + gap.value };
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
  const { cellWidth } = measure();
  return layoutFromPoint(
    {
      x: (event.clientX - rect.left) / scale.value - CANVAS_PADDING,
      y: (event.clientY - rect.top) / scale.value - CANVAS_PADDING,
    },
    { w: payload.w, h: payload.h },
    { cellWidth, rowHeight: rowHeight.value, gap: gap.value, columns: columns.value },
  );
}

const dropPreviewStyle = computed(() => {
  const layout = dropPreview.value;
  const el = gridRef.value;
  if (!layout || !el) {
    return {};
  }
  const { cellWidth, rowUnit } = measure();
  return {
    left: `${CANVAS_PADDING + layout.x * (cellWidth + gap.value)}px`,
    top: `${CANVAS_PADDING + layout.y * rowUnit}px`,
    width: `${layout.w * cellWidth + Math.max(0, layout.w - 1) * gap.value}px`,
    height: `${layout.h * rowHeight.value + Math.max(0, layout.h - 1) * gap.value}px`,
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
    origin: normalizeLayout(widget.layout, columns.value),
    current: normalizeLayout(widget.layout, columns.value),
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
    origin: normalizeLayout(widget.layout, columns.value),
    current: normalizeLayout(widget.layout, columns.value),
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
  const dx = Math.round((event.clientX - state.startX) / state.scale / state.cellWidth);
  const dy = Math.round((event.clientY - state.startY) / state.scale / state.rowUnit);
  if (state.mode === 'move') {
    state.current = {
      ...state.origin,
      x: clamp(state.origin.x + dx, 0, columns.value - state.origin.w),
      y: Math.max(0, state.origin.y + dy),
    };
  } else {
    state.current = {
      ...state.origin,
      w: clamp(state.origin.w + dx, 1, columns.value - state.origin.x),
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

.designer-canvas-item.is-selected {
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
</style>
