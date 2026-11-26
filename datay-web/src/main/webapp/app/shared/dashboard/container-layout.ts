import type { WidgetLayout } from './types';

/** 容器内部固定使用 24 栅格，与看板本身是栅格还是像素无关。 */
export const CONTAINER_COLUMNS = 24;
export const CONTAINER_ROW_HEIGHT = 20;
export const CONTAINER_GAP = 4;
export const CONTAINER_PADDING = 8;

export const DEFAULT_CHILD_LAYOUT: WidgetLayout = { x: 0, y: 0, w: 12, h: 6 };

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

const asInt = (value: unknown, fallback: number) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.round(parsed) : fallback;
};

/** 判断布局是否已经是容器内的 24 栅格坐标。 */
export function isChildGridLayout(layout?: Partial<WidgetLayout> | null): boolean {
  if (!layout) {
    return false;
  }
  const x = asInt(layout.x, 0);
  const w = asInt(layout.w, 0);
  return x >= 0 && x < CONTAINER_COLUMNS && w >= 1 && w <= CONTAINER_COLUMNS && x + w <= CONTAINER_COLUMNS;
}

/** 归一化容器子组件布局；旧版绝对坐标回落到默认位置。 */
export function normalizeChildLayout(layout?: Partial<WidgetLayout> | null): WidgetLayout {
  const rawX = asInt(layout?.x, 0);
  const rawW = asInt(layout?.w, 12);
  // 超出 24 栅格范围视为旧版绝对坐标，整体回落到默认位置。
  if (rawX < 0 || rawX > CONTAINER_COLUMNS - 1 || rawW < 1 || rawW > CONTAINER_COLUMNS) {
    return { ...DEFAULT_CHILD_LAYOUT };
  }
  const x = rawX;
  const w = clamp(rawW, 1, CONTAINER_COLUMNS - x);
  return {
    x,
    y: clamp(asInt(layout?.y, 0), 0, 1000),
    w,
    h: clamp(asInt(layout?.h, 6), 1, 100),
  };
}

/**
 * 把子组件收进容器可见行数内，避免从画布底部归入容器后落到容器外面。
 * pixel 为看板是否像素画布；容器行高固定 40px。
 */
export function fitChildLayout(
  layout: Partial<WidgetLayout> | null | undefined,
  containerLayout: Partial<WidgetLayout> | null | undefined,
  pixel: boolean,
): WidgetLayout {
  const normalized = normalizeChildLayout(layout);
  const containerHeight = Math.max(1, Number(containerLayout?.h) || 1);
  const usableRows = Math.max(
    2,
    pixel ? Math.floor((containerHeight - 64) / (CONTAINER_ROW_HEIGHT + CONTAINER_GAP)) : Math.max(2, containerHeight - 1),
  );
  const h = Math.min(normalized.h, usableRows);
  return {
    ...normalized,
    h,
    y: Math.min(normalized.y, Math.max(0, usableRows - h)),
  };
}

/** 子组件在容器 24 栅格中的 CSS Grid 位置。 */
export function childGridStyle(layout?: Partial<WidgetLayout> | null): Record<string, string> {
  const normalized = normalizeChildLayout(layout);
  return {
    gridColumn: `${normalized.x + 1} / span ${normalized.w}`,
    gridRow: `${normalized.y + 1} / span ${normalized.h}`,
  };
}
