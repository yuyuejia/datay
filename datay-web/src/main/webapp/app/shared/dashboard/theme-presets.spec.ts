import { resolveThemeTokens, themeCssVars, THEME_PRESETS } from './theme-presets';

describe('theme-presets', () => {
  it('resolves a preset and merges token overrides', () => {
    const tokens = resolveThemeTokens('tech-blue', {
      primary: '#ff0000',
      map: { area: '#111111' },
    });

    expect(tokens.primary).toBe('#ff0000');
    expect(tokens.surface).toBe(THEME_PRESETS['tech-blue'].surface);
    expect(tokens.map).toMatchObject({ area: '#111111', border: '#2fa8ff' });
  });

  it('supports custom theme without a preset', () => {
    expect(resolveThemeTokens('custom', { accent: '#123456' })).toEqual({ accent: '#123456' });
    expect(resolveThemeTokens(undefined, undefined)).toEqual({});
  });

  it('maps tokens to dashboard CSS variables', () => {
    const vars = themeCssVars({
      primary: '#2fa8ff',
      text: '#d8ecff',
      textSecondary: '#7c9cb9',
      surface: 'rgba(9, 26, 48, 0.55)',
      border: 'rgba(47, 168, 255, 0.35)',
      secondary: '#1de9b6',
      accent: '#ffd54f',
    });

    expect(vars).toMatchObject({
      '--dashboard-primary': '#2fa8ff',
      '--dash-accent': '#2fa8ff',
      '--dashboard-text': '#d8ecff',
      '--dashboard-kpi-value': '#d8ecff',
      '--dashboard-surface': 'rgba(9, 26, 48, 0.55)',
      '--dashboard-border': 'rgba(47, 168, 255, 0.35)',
      '--dashboard-secondary': '#1de9b6',
      '--dashboard-accent': '#ffd54f',
    });
  });

  it('omits unset CSS variables', () => {
    expect(themeCssVars({})).toEqual({});
  });
});
