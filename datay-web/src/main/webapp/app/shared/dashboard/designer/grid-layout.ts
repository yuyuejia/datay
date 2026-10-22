import type { DashboardWidget, WidgetLayout } from '../types';

/**
 * 轻量 12 列网格布局算法（无第三方依赖）。
 *
 * 坐标约定与看板 spec 一致：x / y 从 0 开始，w / h 为跨列 / 跨行数。
 * 设计器只在指针拖动、缩放结束时调用这里，避免拖动过程中反复计算。
 */

export const GRID_COLUMNS = 12;
export const DEFAULT_WIDGET_W = 6;
export const DEFAULT_WIDGET_H = 4;

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
