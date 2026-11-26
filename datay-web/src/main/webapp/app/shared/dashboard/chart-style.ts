import type { ChartType, WidgetChartStyle, WidgetStyle } from './types';

/**
 * 图表样式预设。
 *
 * <p>预设只填充 `echartsOption` 中没有显式设置的键，避免覆盖用户在「自定义设置」里的调整；
 * `style.chart` 中的显式开关（渐变/发光/轴/图例/标签）优先级最高。
 */
interface PresetSkin {
  axis: string;
  label: string;
  split: string;
  legendText: string;
  tooltipBg: string;
  tooltipBorder: string;
  tooltipText: string;
  barRadius: number;
  lineWidth: number;
  symbolSize: number;
  gradient: boolean;
  glow: boolean;
  smooth: boolean;
}

const FALLBACK_PALETTE = ['#2fa8ff', '#1de9b6', '#ffd54f', '#ff7a45', '#b37feb', '#36cfc9'];

const PRESET_SKINS: Record<'tech' | 'minimal' | 'dark', PresetSkin> = {
  tech: {
    axis: 'rgba(120, 190, 255, 0.35)',
    label: '#9fc6e8',
    split: 'rgba(120, 190, 255, 0.12)',
    legendText: '#cfeaff',
    tooltipBg: 'rgba(6, 22, 40, 0.92)',
    tooltipBorder: 'rgba(47, 168, 255, 0.35)',
    tooltipText: '#e5f3ff',
    barRadius: 3,
    lineWidth: 2,
    symbolSize: 4,
    gradient: true,
    glow: true,
    smooth: true,
  },
  minimal: {
    axis: '#dcdfe6',
    label: '#909399',
    split: '#f0f0f0',
    legendText: '#606266',
    tooltipBg: '#ffffff',
    tooltipBorder: '#e4e7ed',
    tooltipText: '#303133',
    barRadius: 2,
    lineWidth: 2,
    symbolSize: 3,
    gradient: false,
    glow: false,
    smooth: false,
  },
  dark: {
    axis: 'rgba(148, 163, 184, 0.35)',
    label: '#94a3b8',
    split: 'rgba(148, 163, 184, 0.15)',
    legendText: '#e2e8f0',
    tooltipBg: 'rgba(15, 23, 42, 0.94)',
    tooltipBorder: 'rgba(148, 163, 184, 0.35)',
    tooltipText: '#f1f5f9',
    barRadius: 3,
    lineWidth: 2,
    symbolSize: 4,
    gradient: true,
    glow: true,
    smooth: true,
  },
};

export interface ChartStyleContext {
  chartType: ChartType;
  style?: WidgetStyle;
  palette: string[];
  dark: boolean;
}

function resolveSkin(style: WidgetChartStyle, dark: boolean): PresetSkin {
  const preset = style.preset ?? (dark ? 'tech' : 'minimal');
  return PRESET_SKINS[preset];
}

/** 仅填充缺失键；数组与函数按值覆盖，不做深合并。 */
function fillDefaults<T extends Record<string, any>>(target: T | undefined, defaults: Record<string, any>): T {
  const result: Record<string, any> = { ...(target ?? {}) };
  for (const [key, value] of Object.entries(defaults)) {
    if (result[key] === undefined) {
      result[key] = value;
      continue;
    }
    if (value && typeof value === 'object' && !Array.isArray(value) && typeof result[key] === 'object') {
      result[key] = fillDefaults(result[key], value);
    }
  }
  return result as T;
}

function withAlpha(color: string, alpha: number): string {
  const value = color.trim();
  if (value.startsWith('#')) {
    const hex = value.slice(1);
    const expand =
      hex.length === 3
        ? hex
            .split('')
            .map(char => char + char)
            .join('')
        : hex;
    if (expand.length === 6 || expand.length === 8) {
      const r = Number.parseInt(expand.slice(0, 2), 16);
      const g = Number.parseInt(expand.slice(2, 4), 16);
      const b = Number.parseInt(expand.slice(4, 6), 16);
      return `rgba(${r}, ${g}, ${b}, ${alpha})`;
    }
  }
  if (value.startsWith('rgb(')) {
    return value.replace('rgb(', 'rgba(').replace(')', `, ${alpha})`);
  }
  return value;
}

function gradientColor(color: string, kind: 'bar' | 'area'): Record<string, any> {
  const from = kind === 'bar' ? color : withAlpha(color, 0.45);
  const to = kind === 'bar' ? withAlpha(color, 0.12) : withAlpha(color, 0);
  return {
    type: 'linear',
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: [
      { offset: 0, color: from },
      { offset: 1, color: to },
    ],
  };
}

function styleAxis(axis: any, skin: PresetSkin, show?: boolean): any {
  if (!axis || typeof axis !== 'object') {
    return axis;
  }
  const isCategory = axis.type === 'category';
  const defaults = {
    axisLine: { show: true, lineStyle: { color: skin.axis } },
    axisTick: { show: false },
    axisLabel: { color: skin.label, fontSize: 11 },
    splitLine: isCategory ? { show: false } : { show: true, lineStyle: { color: skin.split, type: 'dashed' } },
  };
  const styled = fillDefaults(axis, defaults);
  return show === false ? { ...styled, show: false } : styled;
}

function styleLegend(legend: any, skin: PresetSkin, show?: boolean): any {
  const apply = (item: any) => {
    const styled = fillDefaults(item, {
      textStyle: { color: skin.legendText },
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 12,
    });
    return show === undefined ? styled : { ...styled, show };
  };
  return Array.isArray(legend) ? legend.map(apply) : apply(legend);
}

function styleTooltip(tooltip: any, skin: PresetSkin): any {
  const apply = (item: any) =>
    fillDefaults(item, {
      backgroundColor: skin.tooltipBg,
      borderColor: skin.tooltipBorder,
      textStyle: { color: skin.tooltipText },
    });
  return Array.isArray(tooltip) ? tooltip.map(apply) : apply(tooltip);
}

function seriesColor(series: Record<string, any>, index: number, palette: string[]): string | undefined {
  if (typeof series.color === 'string') {
    return series.color;
  }
  if (typeof series.itemStyle?.color === 'string') {
    return series.itemStyle.color;
  }
  return palette.length > 0 ? palette[index % palette.length] : undefined;
}

function styleSeries(series: Record<string, any>, index: number, context: ChartStyleContext, skin: PresetSkin): void {
  const chart = context.style?.chart ?? {};
  const type = series.type;
  const color = seriesColor(series, index, context.palette);

  if (type === 'bar') {
    series.itemStyle = fillDefaults(series.itemStyle, {
      borderRadius: [skin.barRadius, skin.barRadius, 0, 0],
    });
    const useGradient = chart.gradient ?? skin.gradient;
    if (color && series.itemStyle.color === undefined) {
      series.itemStyle.color = useGradient ? gradientColor(color, 'bar') : color;
    }
    series.emphasis = fillDefaults(series.emphasis, { focus: 'series' });
  } else if (type === 'line') {
    series.lineStyle = fillDefaults(series.lineStyle, { width: skin.lineWidth, color });
    if (chart.glow ?? skin.glow) {
      series.lineStyle.shadowBlur = series.lineStyle.shadowBlur ?? 10;
      series.lineStyle.shadowColor = series.lineStyle.shadowColor ?? color;
    }
    if (series.smooth === undefined && skin.smooth) {
      series.smooth = true;
    }
    if (series.symbol === undefined) {
      series.symbol = 'circle';
    }
    if (series.symbolSize === undefined) {
      series.symbolSize = skin.symbolSize;
    }
    if (series.areaStyle !== undefined) {
      series.areaStyle = fillDefaults(series.areaStyle, { opacity: 0.25 });
      if (color && series.areaStyle.color === undefined) {
        series.areaStyle.color = gradientColor(color, 'area');
      }
    }
  } else if (type === 'pie' || type === 'funnel' || type === 'treemap') {
    series.itemStyle = fillDefaults(series.itemStyle, { borderColor: 'transparent', borderWidth: 1 });
  }

  if (chart.showLabel === true) {
    series.label = fillDefaults(series.label, { show: true, color: skin.label });
  }
}

/**
 * 把 `style.chart` 应用到最终 option 上，返回同一个对象。
 *
 * <p>只处理配置了 `style.chart` 的组件，未配置时完全保持原有渲染，避免老看板视觉回归。
 */
export function applyChartStyle(option: Record<string, any>, context: ChartStyleContext): Record<string, any> {
  const chart = context.style?.chart;
  if (!chart || !option) {
    return option;
  }
  const skin = resolveSkin(chart, context.dark);
  const palette = context.palette.length > 0 ? context.palette : FALLBACK_PALETTE;

  if (option.color === undefined) {
    option.color = palette;
  }

  const cartesian = ['bar', 'line', 'area', 'scatter', 'heatmap', 'boxplot', 'waterfall'].includes(context.chartType);
  if (cartesian) {
    option.xAxis = Array.isArray(option.xAxis)
      ? option.xAxis.map((axis: any) => styleAxis(axis, skin, chart.showAxis))
      : styleAxis(option.xAxis, skin, chart.showAxis);
    option.yAxis = Array.isArray(option.yAxis)
      ? option.yAxis.map((axis: any) => styleAxis(axis, skin, chart.showAxis))
      : styleAxis(option.yAxis, skin, chart.showAxis);
  }
  if (option.legend !== undefined || chart.showLegend === true) {
    option.legend = styleLegend(option.legend, skin, chart.showLegend);
  }
  if (option.tooltip !== undefined) {
    option.tooltip = styleTooltip(option.tooltip, skin);
  }
  if (Array.isArray(option.series)) {
    option.series.forEach((series, index) => styleSeries(series, index, { ...context, palette }, skin));
  }
  return option;
}
