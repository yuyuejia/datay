import { DEFAULT_GAP, DEFAULT_ROW_HEIGHT, GRID_COLUMNS } from './designer/grid-layout';
import type { DashboardBackground, DashboardLayout, DashboardScreen, DashboardSpec, DashboardTheme, ScreenFitMode } from './types';

export const DEFAULT_SCREEN_WIDTH = 1920;
export const DEFAULT_SCREEN_HEIGHT = 1080;

export const LIGHT_SCREEN_BACKGROUND = '#f5f7fa';
export const DARK_SCREEN_BACKGROUND = '#0f172a';

export const SCREEN_FIT_MODES: Array<{ value: ScreenFitMode; label: string; hint: string }> = [
  { value: 'contain', label: '居中', hint: '完整显示画布，按比例居中，可能留边' },
  { value: 'stretch', label: '铺满', hint: '横向和纵向分别拉伸铺满全屏，画面可能变形' },
  { value: 'cover', label: '裁切', hint: '等比放大铺满全屏，超出部分会被裁切' },
];

const FIT_MODES: ScreenFitMode[] = ['contain', 'stretch', 'cover'];
const THEMES: DashboardTheme[] = ['light', 'dark'];
const COLOR_PATTERN = /^(#[0-9a-f]{3,8}|rgba?\([^)]+\)|hsla?\([^)]+\))$/i;
const GRADIENT_DEFAULT_FROM = '#0f172a';
const GRADIENT_DEFAULT_TO = '#1e293b';

const clampNumber = (value: number | undefined, min: number, max: number, fallback: number): number => {
  if (!Number.isFinite(value)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, Math.round(value as number)));
};

const normalizeColor = (value: unknown, fallback: string): string => {
  if (typeof value !== 'string') {
    return fallback;
  }
  const color = value.trim();
  return COLOR_PATTERN.test(color) ? color : fallback;
};

export function normalizeFitMode(value: unknown): ScreenFitMode {
  return FIT_MODES.includes(value as ScreenFitMode) ? (value as ScreenFitMode) : 'stretch';
}

export function normalizeTheme(value: unknown): DashboardTheme {
  return THEMES.includes(value as DashboardTheme) ? (value as DashboardTheme) : 'light';
}

export function normalizeBackground(raw: DashboardBackground | undefined, theme: DashboardTheme): DashboardBackground | undefined {
  if (!raw || typeof raw !== 'object') {
    return undefined;
  }
  const defaultColor = theme === 'dark' ? DARK_SCREEN_BACKGROUND : LIGHT_SCREEN_BACKGROUND;
  if (raw.type === 'gradient') {
    return {
      type: 'gradient',
      gradient: {
        from: normalizeColor(raw.gradient?.from, GRADIENT_DEFAULT_FROM),
        to: normalizeColor(raw.gradient?.to, GRADIENT_DEFAULT_TO),
        angle: clampNumber(raw.gradient?.angle, 0, 360, 135),
      },
    };
  }
  if (raw.type === 'image' && typeof raw.imagePath === 'string' && raw.imagePath.trim()) {
    return {
      type: 'image',
      imagePath: raw.imagePath.trim(),
      imageContentType: typeof raw.imageContentType === 'string' && raw.imageContentType.trim() ? raw.imageContentType.trim() : undefined,
    };
  }
  return {
    type: 'color',
    color: normalizeColor(raw.color, defaultColor),
  };
}

export function resolveScreenTheme(layout?: DashboardLayout): DashboardTheme {
  return normalizeTheme(layout?.screen?.theme);
}

export function isScreenLayout(layout?: DashboardLayout): boolean {
  return layout?.mode === 'screen';
}

export function resolveScreenFitMode(layout?: DashboardLayout): ScreenFitMode {
  return isScreenLayout(layout) ? normalizeFitMode(layout?.screen?.fitMode) : 'stretch';
}

/** 把大屏背景配置转换成固定分辨率画布上的内联样式。imageUrl 为带鉴权加载后的 blob URL。 */
export function resolveScreenBackgroundStyle(layout?: DashboardLayout, imageUrl?: string): Record<string, string> {
  const theme = resolveScreenTheme(layout);
  const defaultColor = theme === 'dark' ? DARK_SCREEN_BACKGROUND : LIGHT_SCREEN_BACKGROUND;
  const background = layout?.screen?.background;
  if (!background) {
    return { backgroundColor: defaultColor };
  }
  if (background.type === 'image') {
    if (background.imagePath && imageUrl) {
      return {
        backgroundColor: defaultColor,
        backgroundImage: `url("${imageUrl}")`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
        backgroundRepeat: 'no-repeat',
      };
    }
    return { backgroundColor: defaultColor };
  }
  if (background.type === 'gradient') {
    const from = normalizeColor(background.gradient?.from, GRADIENT_DEFAULT_FROM);
    const to = normalizeColor(background.gradient?.to, GRADIENT_DEFAULT_TO);
    const angle = clampNumber(background.gradient?.angle, 0, 360, 135);
    return { backgroundImage: `linear-gradient(${angle}deg, ${from}, ${to})` };
  }
  return { backgroundColor: normalizeColor(background.color, defaultColor) };
}

/** 当前系统屏幕分辨率（浏览器逻辑像素），用于新建看板时的大屏默认分辨率。 */
export function getSystemScreenSize(): DashboardScreen {
  const fallback = { width: DEFAULT_SCREEN_WIDTH, height: DEFAULT_SCREEN_HEIGHT };
  if (typeof window === 'undefined') {
    return fallback;
  }
  const screenWidth = Number(window.screen?.width);
  const screenHeight = Number(window.screen?.height);
  if (!Number.isFinite(screenWidth) || !Number.isFinite(screenHeight) || screenWidth <= 0 || screenHeight <= 0) {
    return {
      width: clampNumber(Number(window.innerWidth), 640, 7680, fallback.width),
      height: clampNumber(Number(window.innerHeight), 480, 4320, fallback.height),
    };
  }
  return {
    width: clampNumber(screenWidth, 640, 7680, fallback.width),
    height: clampNumber(screenHeight, 480, 4320, fallback.height),
  };
}

/**
 * 统一看板布局参数。
 *
 * <p>看板统一使用 24 栅格、40px 行高与 12px 间距，不兼容历史 12 栅格数据；
 * 旧的 columns / rowHeight / gap 会被忽略，只保留大屏模式与设计分辨率。
 */
export function normalizeDashboardLayout(raw?: DashboardLayout): DashboardLayout {
  const systemScreen = getSystemScreenSize();
  const theme = normalizeTheme(raw?.screen?.theme);
  const background = normalizeBackground(raw?.screen?.background, theme);
  return {
    mode: raw?.mode === 'screen' ? 'screen' : 'standard',
    columns: GRID_COLUMNS,
    rowHeight: DEFAULT_ROW_HEIGHT,
    gap: DEFAULT_GAP,
    screen: {
      width: clampNumber(raw?.screen?.width, 640, 7680, systemScreen.width),
      height: clampNumber(raw?.screen?.height, 480, 4320, systemScreen.height),
      fitMode: normalizeFitMode(raw?.screen?.fitMode),
      theme,
      autoRefreshSeconds: clampNumber(raw?.screen?.autoRefreshSeconds, 0, 86400, 0),
      ...(background ? { background } : {}),
    },
  };
}

/** 读取看板定义时补齐布局默认值，保证设计态与浏览态使用同一套栅格。 */
export function normalizeDashboardSpec(raw: DashboardSpec): DashboardSpec {
  return {
    ...raw,
    layout: normalizeDashboardLayout(raw.layout),
    datasets: (raw.datasets ?? []).map(dataset => ({ ...dataset })),
    widgets: (raw.widgets ?? []).map(widget => ({
      ...widget,
      layout: { x: 0, y: 0, w: 12, h: 8, ...(widget.layout ?? {}) },
    })),
    filters: (raw.filters ?? []).map(filter => ({ ...filter })),
  };
}
