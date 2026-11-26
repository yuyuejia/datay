import type { CanvasUnit, DashboardLayout, DashboardWidget, WidgetLayout } from '../types';

/**
 * 轻量 24 列网格布局算法（无第三方依赖）。
 *
 * 坐标约定与看板 spec 一致：x / y 从 0 开始，w / h 为跨列 / 跨行数。
 * 设计器只在指针拖动、缩放结束时调用这里，避免拖动过程中反复计算。
 */

export const GRID_COLUMNS = 24;
export const DEFAULT_WIDGET_W = 12;
export const DEFAULT_WIDGET_H = 8;
export const DEFAULT_ROW_HEIGHT = 40;
export const DEFAULT_GAP = 12;
/** 设计态与浏览态统一的画布内边距，保证大屏模式所见即所得。 */
export const CANVAS_PADDING = 16;
/** px 模式下的最小组件尺寸，避免拖出不可用的小块。 */
export const MIN_PIXEL_WIDGET_W = 40;
export const MIN_PIXEL_WIDGET_H = 20;

/**
 * 画布度量：grid 模式按列/行定位，px 模式按画布内容区像素定位。
 *
 * <p>大屏画布的 padding 与 gap 可由 spec 覆盖，标准模式始终回落到默认栅格。
 */
export interface CanvasMetrics {
  unit: CanvasUnit;
  width: number;
  height: number;
  padding: number;
  gap: number;
  columns: number;
  rowHeight: number;
}

export function resolveCanvasMetrics(layout?: DashboardLayout): CanvasMetrics {
  const isScreen = layout?.mode === 'screen';
  // 标准模式只使用固定栅格，画布留白与间距不参与定位。
  const canvas = isScreen ? layout?.screen?.canvas : undefined;
  return {
    unit: canvas?.unit === 'px' ? 'px' : 'grid',
    width: clamp(Math.round(layout?.screen?.width || 1920), 640, 7680),
    height: clamp(Math.round(layout?.screen?.height || 1080), 480, 4320),
    padding: clamp(Math.round(canvas?.padding ?? CANVAS_PADDING), 0, 64),
    gap: clamp(Math.round(canvas?.gap ?? DEFAULT_GAP), 0, 48),
    columns: layout?.columns ?? GRID_COLUMNS,
    rowHeight: layout?.rowHeight ?? DEFAULT_ROW_HEIGHT,
  };
}

/** 画布内容区宽度（已扣除左右 padding）。 */
export function canvasContentWidth(metrics: CanvasMetrics): number {
  return Math.max(1, metrics.width - metrics.padding * 2);
}

/** 画布内容区高度（已扣除上下 padding）。 */
export function canvasContentHeight(metrics: CanvasMetrics): number {
  return Math.max(1, metrics.height - metrics.padding * 2);
}

export function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

export function normalizeLayout(layout: WidgetLayout, columns = GRID_COLUMNS): WidgetLayout {
  const w = clamp(Math.round(layout.w || DEFAULT_WIDGET_W), 1, columns);
  const h = clamp(Math.round(layout.h || DEFAULT_WIDGET_H), 1, 60);
  const x = clamp(Math.round(layout.x || 0), 0, columns - w);
  const y = Math.max(0, Math.round(layout.y || 0));
  return { x, y, w, h };
}

export interface GridMetrics {
  cellWidth: number;
  rowHeight: number;
  gap: number;
  columns: number;
}

/**
 * 把画布内的落点坐标换算成网格布局。
 *
 * <p>{@code point} 使用画布内容区坐标（已去掉 padding），用于从左侧组件库
 * 拖拽到画布时定位新组件。超出右边界时按组件宽度回退，保证不会越界。
 */
export function layoutFromPoint(point: { x: number; y: number }, size: { w: number; h: number }, metrics: GridMetrics): WidgetLayout {
  const stepX = Math.max(1, metrics.cellWidth + metrics.gap);
  const stepY = Math.max(1, metrics.rowHeight + metrics.gap);
  const x = clamp(Math.floor(point.x / stepX), 0, Math.max(0, metrics.columns - size.w));
  const y = Math.max(0, Math.floor(point.y / stepY));
  return normalizeLayout({ x, y, w: size.w, h: size.h }, metrics.columns);
}

/**
 * px 模式下把布局钳制到画布内容区。
 *
 * <p>与 grid 模式不同，这里不做取整吸附，保留用户拖动后的像素位置。
 */
export function clampPixelLayout(layout: WidgetLayout, metrics: CanvasMetrics): WidgetLayout {
  const contentWidth = canvasContentWidth(metrics);
  const contentHeight = canvasContentHeight(metrics);
  const w = clamp(Math.round(layout.w || contentWidth / 2), MIN_PIXEL_WIDGET_W, contentWidth);
  const h = clamp(Math.round(layout.h || contentHeight / 3), MIN_PIXEL_WIDGET_H, contentHeight);
  const x = clamp(Math.round(layout.x || 0), 0, Math.max(0, contentWidth - w));
  const y = clamp(Math.round(layout.y || 0), 0, Math.max(0, contentHeight - h));
  return { x, y, w, h };
}

/** px 模式下的落点定位：point 为画布内容区坐标（已扣除 padding）。 */
export function layoutFromPixelPoint(
  point: { x: number; y: number },
  size: { w: number; h: number },
  metrics: CanvasMetrics,
): WidgetLayout {
  const contentWidth = canvasContentWidth(metrics);
  const contentHeight = canvasContentHeight(metrics);
  const w = clamp(Math.round(size.w || contentWidth / 2), MIN_PIXEL_WIDGET_W, contentWidth);
  const h = clamp(Math.round(size.h || contentHeight / 3), MIN_PIXEL_WIDGET_H, contentHeight);
  const x = clamp(Math.floor(point.x), 0, Math.max(0, contentWidth - w));
  const y = clamp(Math.floor(point.y), 0, Math.max(0, contentHeight - h));
  return { x, y, w, h };
}

/** 把栅格尺寸换算成相近的像素尺寸，让组件库拖入 px 画布时大小合理。 */
export function pixelSizeFromGrid(gridW: number, gridH: number, metrics: CanvasMetrics): { w: number; h: number } {
  const contentWidth = canvasContentWidth(metrics);
  const contentHeight = canvasContentHeight(metrics);
  return {
    w: clamp(Math.round((gridW / metrics.columns) * contentWidth), MIN_PIXEL_WIDGET_W, contentWidth),
    h: clamp(Math.round(gridH * (metrics.rowHeight + metrics.gap)), MIN_PIXEL_WIDGET_H, contentHeight),
  };
}

/** 点击组件库添加到 px 画布时，放到现有内容下方。 */
export function nextPixelLayout(widgets: DashboardWidget[], metrics: CanvasMetrics, gridW: number, gridH: number): WidgetLayout {
  const size = pixelSizeFromGrid(gridW, gridH, metrics);
  const bottom = widgets.reduce((max, widget) => Math.max(max, (widget.layout?.y ?? 0) + (widget.layout?.h ?? 0)), 0);
  return clampPixelLayout({ x: 0, y: bottom + 8, w: size.w, h: size.h }, metrics);
}

/** 按画布单位归一化单个组件布局。 */
export function normalizeWidgetLayout(layout: WidgetLayout, metrics: CanvasMetrics): WidgetLayout {
  return metrics.unit === 'px' ? clampPixelLayout(layout, metrics) : normalizeLayout(layout, metrics.columns);
}

export function overlaps(left: WidgetLayout, right: WidgetLayout): boolean {
  return left.x < right.x + right.w && left.x + left.w > right.x && left.y < right.y + right.h && left.y + left.h > right.y;
}

/**
 * 从上到下压实布局：每个组件放到当前 y 下方第一个不与已放置组件冲突的位置。
 * 这样拖动/删除组件后不会留下过大的空洞。
 */
export function compactLayouts<T extends { id?: string; layout: WidgetLayout }>(
  items: T[],
  columns = GRID_COLUMNS,
  priorityId?: string,
): T[] {
  const sorted = [...items].sort((left, right) => {
    if (priorityId) {
      if (left.id === priorityId) {
        return -1;
      }
      if (right.id === priorityId) {
        return 1;
      }
    }
    if (left.layout.y !== right.layout.y) {
      return left.layout.y - right.layout.y;
    }
    return left.layout.x - right.layout.x;
  });
  const placed: T[] = [];
  for (const item of sorted) {
    const layout = normalizeLayout({ ...item.layout, y: 0 }, columns);
    let y = Math.max(0, Math.round(item.layout.y || 0));
    // 先尝试从原始 y 开始放置；若冲突则持续下移。
    while (placed.some(other => overlaps({ ...layout, y }, other.layout))) {
      y += 1;
    }
    placed.push({ ...item, layout: { ...layout, y } });
  }
  return placed;
}

export function moveLayout(
  items: Array<{ id: string; layout: WidgetLayout }>,
  id: string,
  x: number,
  y: number,
  columns = GRID_COLUMNS,
): Array<{ id: string; layout: WidgetLayout }> {
  const next = items.map(item =>
    item.id === id ? { ...item, layout: normalizeLayout({ ...item.layout, x, y }, columns) } : { ...item, layout: { ...item.layout } },
  );
  return compactLayouts(next, columns, id);
}

export function resizeLayout(
  items: Array<{ id: string; layout: WidgetLayout }>,
  id: string,
  w: number,
  h: number,
  columns = GRID_COLUMNS,
): Array<{ id: string; layout: WidgetLayout }> {
  const next = items.map(item =>
    item.id === id ? { ...item, layout: normalizeLayout({ ...item.layout, w, h }, columns) } : { ...item, layout: { ...item.layout } },
  );
  return compactLayouts(next, columns, id);
}

export function nextLayout(widgets: DashboardWidget[], w = DEFAULT_WIDGET_W, h = DEFAULT_WIDGET_H): WidgetLayout {
  const bottom = widgets.reduce((max, widget) => Math.max(max, (widget.layout?.y ?? 0) + (widget.layout?.h ?? 0)), 0);
  return normalizeLayout({ x: 0, y: bottom, w, h });
}

/** 当前组件布局占用的总高度（含画布内边距），用于大屏模式判断内容是否超出设计分辨率。 */
export function layoutContentHeight(
  widgets: DashboardWidget[],
  rowHeight = DEFAULT_ROW_HEIGHT,
  gap = DEFAULT_GAP,
  padding = CANVAS_PADDING,
): number {
  const bottom = widgets.reduce((max, widget) => Math.max(max, (widget.layout?.y ?? 0) + (widget.layout?.h ?? 0)), 0);
  if (bottom <= 0) {
    return padding * 2;
  }
  return bottom * rowHeight + (bottom - 1) * gap + padding * 2;
}

/** px 模式下组件占用高度（含画布内边距）。 */
export function pixelContentHeight(widgets: DashboardWidget[], padding = CANVAS_PADDING): number {
  const bottom = widgets.reduce((max, widget) => Math.max(max, (widget.layout?.y ?? 0) + (widget.layout?.h ?? 0)), 0);
  return bottom + padding * 2;
}

/** 组件是否落在网格内（用于校验导入的 spec）。 */
export function isLayoutValid(layout: WidgetLayout, columns = GRID_COLUMNS): boolean {
  return (
    Number.isFinite(layout.x) &&
    Number.isFinite(layout.y) &&
    Number.isFinite(layout.w) &&
    Number.isFinite(layout.h) &&
    layout.x >= 0 &&
    layout.y >= 0 &&
    layout.w > 0 &&
    layout.h > 0 &&
    layout.x + layout.w <= columns
  );
}
