import { KPI_FONT_STACKS, resolveKpiStyle, resolveWidgetStyle } from './widget-style';
import type { DashboardWidget, WidgetStyle } from './types';

describe('widget-style', () => {
  it('returns empty styles when no skin is configured', () => {
    expect(resolveWidgetStyle(undefined)).toEqual({
      cardClasses: [],
      cardStyle: {},
      headerClasses: [],
      headerStyle: {},
      titleStyle: {},
      bodyStyle: {},
      titleIcon: undefined,
    });
  });

  it('maps card fill, border, shadow and presets to inline styles', () => {
    const style: WidgetStyle = {
      card: {
        preset: 'tech-corner',
        fill: { type: 'gradient', gradient: { from: '#0a1b2e', to: '#0d2643', angle: 180 } },
        border: { width: 1, color: 'rgba(47, 168, 255, 0.45)', style: 'dashed', radius: 4 },
        shadow: { x: 0, y: 0, blur: 18, spread: 2, color: 'rgba(0, 180, 255, 0.25)' },
        opacity: 0.85,
        padding: 6,
        backdropBlur: 8,
      },
    };

    const resolved = resolveWidgetStyle(style);

    expect(resolved.cardClasses).toEqual(['dash-card--tech-corner']);
    expect(resolved.cardStyle.background).toBe('linear-gradient(180deg, #0a1b2e, #0d2643)');
    expect(resolved.cardStyle.border).toBe('1px dashed rgba(47, 168, 255, 0.45)');
    expect(resolved.cardStyle.borderRadius).toBe('4px');
    expect(resolved.cardStyle.boxShadow).toBe('0px 0px 18px 2px rgba(0, 180, 255, 0.25)');
    expect(resolved.cardStyle.opacity).toBe('0.85');
    expect(resolved.cardStyle.padding).toBe('6px');
    expect(resolved.cardStyle.backdropFilter).toBe('blur(8px)');
  });

  it('makes a card transparent when fill type is none', () => {
    expect(resolveWidgetStyle({ card: { fill: { type: 'none' } } }).cardStyle.background).toBe('transparent');
  });

  it('maps title typography, icon and decoration', () => {
    const resolved = resolveWidgetStyle({
      title: {
        icon: 'chart-line',
        fontSize: 18,
        fontWeight: 700,
        color: '#7fe3ff',
        align: 'center',
        height: 36,
        letterSpacing: 2,
        background: '#0c2238',
        decoration: 'gradient-underline',
      },
      body: { padding: 4 },
    });

    expect(resolved.titleIcon).toBe('chart-line');
    expect(resolved.headerClasses).toEqual(['is-deco-gradient-underline']);
    expect(resolved.headerStyle.background).toBe('#0c2238');
    expect(resolved.headerStyle.height).toBe('36px');
    expect(resolved.titleStyle).toEqual({
      color: '#7fe3ff',
      fontSize: '18px',
      fontWeight: '700',
      letterSpacing: '2px',
      textAlign: 'center',
    });
    expect(resolved.bodyStyle.padding).toBe('4px');
  });

  it('resolves KPI label, typography and unit', () => {
    const widget: DashboardWidget = {
      id: 'kpi1',
      type: 'kpi',
      title: '组件标题',
      layout: { x: 0, y: 0, w: 6, h: 4 },
      style: {
        kpi: {
          label: '学生总数',
          icon: 'users',
          layout: 'label-left',
          value: { fontSize: 56, color: '#eaf6ff', fontFamily: 'number-digital', bold: false, letterSpacing: 2 },
          unit: { text: '人', fontSize: 16, color: '#8fb6d6' },
          animate: true,
          decimals: 1,
        },
      },
    };

    const resolved = resolveKpiStyle(widget, '度量名称');

    expect(resolved).toMatchObject({
      layout: 'label-left',
      label: '学生总数',
      icon: 'users',
      unitText: '人',
      animate: true,
      decimals: 1,
    });
    expect(resolved.valueStyle).toMatchObject({
      fontSize: '56px',
      color: '#eaf6ff',
      fontFamily: KPI_FONT_STACKS['number-digital'],
      fontWeight: '400',
      letterSpacing: '2px',
    });
    expect(resolved.unitStyle).toEqual({ fontSize: '16px', color: '#8fb6d6' });
  });

  it('falls back to widget title or measure label and skips the system font stack', () => {
    expect(resolveKpiStyle({ id: 'k', type: 'kpi', title: '标题', layout: { x: 0, y: 0, w: 1, h: 1 } }).label).toBe('标题');
    expect(resolveKpiStyle({ id: 'k', type: 'kpi', layout: { x: 0, y: 0, w: 1, h: 1 } }, '度量').label).toBe('度量');

    const resolved = resolveKpiStyle({
      id: 'k',
      type: 'kpi',
      layout: { x: 0, y: 0, w: 1, h: 1 },
      style: { kpi: { value: { fontFamily: 'system', bold: true } } },
    });
    expect(resolved.valueStyle.fontFamily).toBeUndefined();
    expect(resolved.valueStyle.fontWeight).toBe('700');
    expect(resolved.animate).toBe(false);
    expect(resolved.layout).toBe('label-top');
  });
});
