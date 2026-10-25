import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { getSystemScreenSize, normalizeDashboardLayout, normalizeDashboardSpec, resolveScreenBackgroundStyle } from './layout-defaults';
import type { DashboardSpec } from './types';

describe('layout-defaults', () => {
  beforeEach(() => {
    vi.spyOn(window.screen, 'width', 'get').mockReturnValue(2560);
    vi.spyOn(window.screen, 'height', 'get').mockReturnValue(1440);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('uses the current system screen for a new dashboard', () => {
    expect(getSystemScreenSize()).toEqual({ width: 2560, height: 1440 });
    expect(normalizeDashboardLayout({ columns: 12 })).toMatchObject({
      mode: 'standard',
      screen: { width: 2560, height: 1440 },
    });
  });

  it('forces the unified 24 column grid and keeps screen settings', () => {
    expect(
      normalizeDashboardLayout({ mode: 'screen', columns: 12, rowHeight: 90, gap: 16, screen: { width: 2560, height: 1440 } }),
    ).toEqual({
      mode: 'screen',
      columns: 24,
      rowHeight: 40,
      gap: 12,
      screen: {
        width: 2560,
        height: 1440,
        fitMode: 'stretch',
        theme: 'light',
        autoRefreshSeconds: 0,
      },
    });
  });

  it('falls back to standard mode and the current system resolution', () => {
    expect(normalizeDashboardLayout({ columns: 12 })).toEqual({
      mode: 'standard',
      columns: 24,
      rowHeight: 40,
      gap: 12,
      screen: {
        width: 2560,
        height: 1440,
        fitMode: 'stretch',
        theme: 'light',
        autoRefreshSeconds: 0,
      },
    });
  });

  it('clamps screen resolutions to a usable range', () => {
    expect(normalizeDashboardLayout({ mode: 'screen', screen: { width: 100, height: 9999 } }).screen).toMatchObject({
      width: 640,
      height: 4320,
    });
  });

  it('normalizes large-screen appearance settings', () => {
    const layout = normalizeDashboardLayout({
      mode: 'screen',
      screen: {
        width: 1920,
        height: 1080,
        fitMode: 'cover',
        theme: 'dark',
        autoRefreshSeconds: 30,
        background: { type: 'gradient', gradient: { from: '#000000', to: '#ffffff', angle: 45 } },
      },
    });

    expect(layout.screen).toMatchObject({
      fitMode: 'cover',
      theme: 'dark',
      autoRefreshSeconds: 30,
      background: {
        type: 'gradient',
        gradient: { from: '#000000', to: '#ffffff', angle: 45 },
      },
    });
  });

  it('falls back for invalid appearance settings', () => {
    const layout = normalizeDashboardLayout({
      mode: 'screen',
      screen: {
        width: 1920,
        height: 1080,
        fitMode: 'unknown' as any,
        theme: 'unknown' as any,
        autoRefreshSeconds: 999999,
        background: { type: 'image', imagePath: '' },
      },
    });

    expect(layout.screen).toMatchObject({
      fitMode: 'stretch',
      theme: 'light',
      autoRefreshSeconds: 86400,
    });
    expect(layout.screen?.background).toMatchObject({ type: 'color', color: '#f5f7fa' });
  });

  it('builds background styles and image view URLs', () => {
    expect(resolveScreenBackgroundStyle({ mode: 'screen', screen: { width: 1, height: 1, theme: 'dark' } })).toEqual({
      backgroundColor: '#0f172a',
    });
    expect(
      resolveScreenBackgroundStyle(
        {
          mode: 'screen',
          screen: {
            width: 1,
            height: 1,
            background: { type: 'image', imagePath: 'dashboard-backgrounds/a/b.png' },
          },
        },
        'blob:background-image',
      ).backgroundImage,
    ).toContain('blob:background-image');
    expect(
      resolveScreenBackgroundStyle({
        mode: 'screen',
        screen: {
          width: 1,
          height: 1,
          background: { type: 'image', imagePath: 'dashboard-backgrounds/a/b.png' },
        },
      }),
    ).toEqual({ backgroundColor: '#f5f7fa' });
    expect(
      resolveScreenBackgroundStyle({
        mode: 'screen',
        screen: {
          width: 1,
          height: 1,
          background: { type: 'gradient', gradient: { from: '#000000', to: '#ffffff', angle: 90 } },
        },
      }),
    ).toEqual({ backgroundImage: 'linear-gradient(90deg, #000000, #ffffff)' });
  });

  it('normalizes a stored dashboard spec', () => {
    const raw: DashboardSpec = {
      version: 1,
      title: '旧看板',
      layout: { columns: 12, rowHeight: 90, gap: 16 },
      datasets: [{ id: 'ds1', type: 'SQL' }],
      widgets: [{ id: 'w1', type: 'table', layout: { x: 2, y: 1, w: 6, h: 4 } }],
      filters: [{ id: 'f1', type: 'input', targets: [{ datasetId: 'ds1', field: 'keyword', mode: 'client' }] }],
    };

    const normalized = normalizeDashboardSpec(raw);

    expect(normalized).not.toBe(raw);
    expect(normalized.layout?.columns).toBe(24);
    expect(normalized.widgets[0].layout).toEqual({ x: 2, y: 1, w: 6, h: 4 });
    expect(normalized.widgets[0]).not.toBe(raw.widgets[0]);
    expect(normalized.datasets[0]).not.toBe(raw.datasets[0]);
    expect(normalized.filters?.[0]).not.toBe(raw.filters?.[0]);
  });
});
