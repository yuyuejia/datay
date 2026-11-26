import type { DashboardWidget, KpiLayout, ThemeFontFamily, WidgetKpiStyle, WidgetStyle } from './types';

const px = (value: number): string => `${value}px`;

export interface ResolvedWidgetStyle {
  /** 卡片预设 class（tech-corner / tech-frame / bracket）。 */
  cardClasses: string[];
  cardStyle: Record<string, string>;
  headerClasses: string[];
  headerStyle: Record<string, string>;
  titleStyle: Record<string, string>;
  bodyStyle: Record<string, string>;
  titleIcon?: string;
}

/**
 * 把白名单归一化后的 `widget.style` 转换成 DOM 可用的 class 与内联样式。
 *
 * <p>这里不拼接任意 CSS 字符串：颜色、尺寸都来自服务端校验过的字段，
 * 预设与装饰只映射到固定 class，避免样式注入。
 */
export function resolveWidgetStyle(style?: WidgetStyle): ResolvedWidgetStyle {
  const cardStyle: Record<string, string> = {};
  const cardClasses: string[] = [];
  const card = style?.card;
  if (card) {
    if (card.fill?.type === 'none') {
      cardStyle.background = 'transparent';
    } else if (card.fill?.type === 'color' && card.fill.color) {
      cardStyle.background = card.fill.color;
    } else if (card.fill?.type === 'gradient' && card.fill.gradient) {
      const { from, to, angle } = card.fill.gradient;
      cardStyle.background = `linear-gradient(${angle}deg, ${from}, ${to})`;
    }
    if (card.border) {
      const { width, color, style: borderStyle, radius } = card.border;
      const parts: string[] = [];
      if (width != null) {
        parts.push(px(width), borderStyle ?? 'solid');
      }
      if (color) {
        parts.push(color);
      }
      if (parts.length > 0) {
        cardStyle.border = parts.join(' ');
      }
      if (radius != null) {
        cardStyle.borderRadius = px(radius);
      }
    }
    if (card.shadow) {
      const { x = 0, y = 0, blur = 0, spread = 0, color = 'rgba(0, 0, 0, 0.25)' } = card.shadow;
      cardStyle.boxShadow = `${px(x)} ${px(y)} ${px(blur)} ${px(spread)} ${color}`;
    }
    if (card.opacity != null) {
      cardStyle.opacity = String(card.opacity);
    }
    if (card.padding != null) {
      cardStyle.padding = px(card.padding);
    }
    if (card.backdropBlur) {
      cardStyle.backdropFilter = `blur(${px(card.backdropBlur)})`;
    }
    if (card.preset && card.preset !== 'none') {
      cardClasses.push(`dash-card--${card.preset}`);
    }
  }

  const headerClasses: string[] = [];
  const headerStyle: Record<string, string> = {};
  const titleStyle: Record<string, string> = {};
  const title = style?.title;
  if (title) {
    if (title.background) {
      headerStyle.background = title.background;
    }
    if (title.height != null && title.height > 0) {
      headerStyle.height = px(title.height);
      headerStyle.paddingTop = '0';
      headerStyle.paddingBottom = '0';
    }
    if (title.color) {
      titleStyle.color = title.color;
    }
    if (title.fontSize != null) {
      titleStyle.fontSize = px(title.fontSize);
    }
    if (title.fontWeight != null) {
      titleStyle.fontWeight = String(title.fontWeight);
    }
    if (title.letterSpacing != null) {
      titleStyle.letterSpacing = px(title.letterSpacing);
    }
    if (title.align) {
      titleStyle.textAlign = title.align;
    }
    if (title.decoration && title.decoration !== 'none') {
      headerClasses.push(`is-deco-${title.decoration}`);
    }
  }

  const bodyStyle: Record<string, string> = {};
  if (style?.body?.padding != null) {
    bodyStyle.padding = px(style.body.padding);
  }

  return {
    cardClasses,
    cardStyle,
    headerClasses,
    headerStyle,
    titleStyle,
    bodyStyle,
    titleIcon: title?.icon,
  };
}

/** KPI 数字字体栈；`system` 表示沿用主题正文字体。 */
export const KPI_FONT_STACKS: Record<ThemeFontFamily, string | undefined> = {
  system: undefined,
  'number-digital': "'Datay Orbitron', 'DIN Alternate', 'Bahnschrift', 'Arial Narrow', sans-serif",
  'number-condensed': "'Datay Rajdhani', 'DIN Condensed', 'Roboto Condensed', 'Arial Narrow', sans-serif",
};

export interface ResolvedKpiStyle {
  layout: KpiLayout;
  label?: string;
  icon?: string;
  valueStyle: Record<string, string>;
  unitStyle: Record<string, string>;
  /** 独立单位文案；为空时回落到 format.unit 并拼在数值后面。 */
  unitText?: string;
  animate: boolean;
  decimals?: number;
}

/**
 * 解析 KPI 展示配置：标签、图标、数值/单位排版与动效开关。
 *
 * <p>未配置的项返回空样式，由组件默认 CSS（主题变量）兜底。
 */
export function resolveKpiStyle(widget: DashboardWidget, fallbackLabel?: string): ResolvedKpiStyle {
  const kpi: WidgetKpiStyle | undefined = widget.style?.kpi;
  const label = kpi?.label?.trim() || widget.title?.trim() || fallbackLabel || '';
  const valueStyle: Record<string, string> = {};
  const value = kpi?.value;
  if (value?.fontSize != null) {
    valueStyle.fontSize = px(value.fontSize);
  }
  if (value?.color) {
    valueStyle.color = value.color;
  }
  const stack = value?.fontFamily ? KPI_FONT_STACKS[value.fontFamily] : undefined;
  if (stack) {
    valueStyle.fontFamily = stack;
    valueStyle.fontVariantNumeric = 'tabular-nums';
  }
  if (value?.bold !== undefined) {
    valueStyle.fontWeight = value.bold ? '700' : '400';
  }
  if (value?.letterSpacing != null) {
    valueStyle.letterSpacing = px(value.letterSpacing);
  }

  const unitStyle: Record<string, string> = {};
  const unit = kpi?.unit;
  if (unit?.fontSize != null) {
    unitStyle.fontSize = px(unit.fontSize);
  }
  if (unit?.color) {
    unitStyle.color = unit.color;
  }

  return {
    layout: kpi?.layout ?? 'label-top',
    label,
    icon: kpi?.icon,
    valueStyle,
    unitStyle,
    unitText: unit?.text?.trim() || undefined,
    animate: kpi?.animate === true,
    decimals: kpi?.decimals,
  };
}
