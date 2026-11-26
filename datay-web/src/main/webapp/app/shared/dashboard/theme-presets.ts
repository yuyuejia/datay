import type { ThemePreset, ThemeTokens } from './types';

/**
 * 大屏主题预设。
 *
 * <p>预设只提供 token 默认值，`layout.screen.tokens` 中的显式配置优先；
 * 渲染时同时写入 CSS 变量并供后续 ECharts 主题复用。
 */
export const THEME_PRESETS: Record<Exclude<ThemePreset, 'custom'>, ThemeTokens> = {
  'tech-blue': {
    primary: '#2fa8ff',
    secondary: '#1de9b6',
    accent: '#ffd54f',
    text: '#d8ecff',
    textSecondary: '#7c9cb9',
    surface: 'rgba(9, 26, 48, 0.55)',
    border: 'rgba(47, 168, 255, 0.35)',
    palette: ['#2fa8ff', '#1de9b6', '#ffd54f', '#ff7a45', '#b37feb', '#36cfc9'],
    fontFamily: 'system',
    map: { area: '#0b3a5b', border: '#2fa8ff', emphasis: '#1a7fbf', label: '#cfeaff' },
  },
  'tech-cyan': {
    primary: '#22d3ee',
    secondary: '#34d399',
    accent: '#fbbf24',
    text: '#e0f7ff',
    textSecondary: '#7fa8b8',
    surface: 'rgba(6, 28, 38, 0.55)',
    border: 'rgba(34, 211, 238, 0.35)',
    palette: ['#22d3ee', '#34d399', '#fbbf24', '#f87171', '#a78bfa', '#60a5fa'],
    fontFamily: 'system',
    map: { area: '#083344', border: '#22d3ee', emphasis: '#0e7490', label: '#cffafe' },
  },
  'gov-red': {
    primary: '#ff5a5f',
    secondary: '#ffb020',
    accent: '#ffd666',
    text: '#ffe9e9',
    textSecondary: '#c99b9b',
    surface: 'rgba(38, 12, 14, 0.6)',
    border: 'rgba(255, 90, 95, 0.35)',
    palette: ['#ff5a5f', '#ffb020', '#ffd666', '#ff7875', '#ffa39e', '#ffd591'],
    fontFamily: 'system',
    map: { area: '#3b0d0f', border: '#ff5a5f', emphasis: '#7a1c1f', label: '#ffe9e9' },
  },
  'ink-dark': {
    primary: '#e2e8f0',
    secondary: '#94a3b8',
    accent: '#38bdf8',
    text: '#f1f5f9',
    textSecondary: '#94a3b8',
    surface: 'rgba(15, 23, 42, 0.7)',
    border: 'rgba(148, 163, 184, 0.3)',
    palette: ['#e2e8f0', '#94a3b8', '#38bdf8', '#f472b6', '#facc15', '#4ade80'],
    fontFamily: 'system',
    map: { area: '#1e293b', border: '#64748b', emphasis: '#334155', label: '#e2e8f0' },
  },
};

export const THEME_PRESET_OPTIONS: Array<{ value: ThemePreset; label: string }> = [
  { value: 'tech-blue', label: '科技蓝' },
  { value: 'tech-cyan', label: '科技青' },
  { value: 'gov-red', label: '政务红' },
  { value: 'ink-dark', label: '水墨深色' },
  { value: 'custom', label: '自定义' },
];

/** 合并预设与覆盖项；map 子对象按字段合并，避免覆盖掉未修改的地图配色。 */
export function resolveThemeTokens(preset?: string, overrides?: ThemeTokens): ThemeTokens {
  const base = (preset && THEME_PRESETS[preset as keyof typeof THEME_PRESETS]) || {};
  const result: ThemeTokens = { ...base, ...(overrides ?? {}) };
  if (base.map || overrides?.map) {
    result.map = { ...(base.map ?? {}), ...(overrides?.map ?? {}) };
  }
  if (!result.palette || result.palette.length === 0) {
    delete result.palette;
  }
  return result;
}

/**
 * token → CSS 变量。
 *
 * <p>看板容器上挂这些变量后，所有组件默认皮肤（卡片、标题、KPI 数值）都会跟着主题走；
 * 组件级 `widget.style` 仍然可以覆盖单项。
 */
export function themeCssVars(tokens: ThemeTokens): Record<string, string> {
  const vars: Record<string, string> = {};
  if (tokens.surface) {
    vars['--dashboard-surface'] = tokens.surface;
  }
  if (tokens.border) {
    vars['--dashboard-border'] = tokens.border;
    vars['--dash-border'] = tokens.border;
  }
  if (tokens.text) {
    vars['--dashboard-text'] = tokens.text;
    vars['--dashboard-kpi-value'] = tokens.text;
    vars['--dash-text'] = tokens.text;
  }
  if (tokens.textSecondary) {
    vars['--dashboard-text-secondary'] = tokens.textSecondary;
    vars['--dashboard-kpi-caption'] = tokens.textSecondary;
    vars['--dash-text-secondary'] = tokens.textSecondary;
  }
  if (tokens.primary) {
    vars['--dashboard-primary'] = tokens.primary;
    vars['--dash-accent'] = tokens.primary;
  }
  if (tokens.secondary) {
    vars['--dashboard-secondary'] = tokens.secondary;
  }
  if (tokens.accent) {
    vars['--dashboard-accent'] = tokens.accent;
  }
  return vars;
}

/** 图表序列色，未配置时回落到 ECharts 内置主题色。 */
export function resolveChartPalette(tokens: ThemeTokens): string[] | undefined {
  return tokens.palette?.length ? tokens.palette : undefined;
}
