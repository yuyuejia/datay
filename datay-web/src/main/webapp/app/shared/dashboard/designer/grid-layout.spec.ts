import {
  canvasContentHeight,
  canvasContentWidth,
  clampPixelLayout,
  compactLayouts,
  isLayoutValid,
  layoutFromPixelPoint,
  layoutFromPoint,
  moveLayout,
  nextLayout,
  nextPixelLayout,
  normalizeLayout,
  normalizeWidgetLayout,
  overlaps,
  pixelSizeFromGrid,
  resizeLayout,
  resolveCanvasMetrics,
  GRID_COLUMNS,
} from './grid-layout';

describe('grid-layout', () => {
  it('normalizes out-of-range layout', () => {
    expect(normalizeLayout({ x: -3, y: -1, w: 30, h: 0 })).toEqual({
      x: 0,
      y: 0,
      w: GRID_COLUMNS,
      h: 8,
    });
    expect(normalizeLayout({ x: 10, y: 2, w: 6, h: 3 })).toEqual({
      x: 10,
      y: 2,
      w: 6,
      h: 3,
    });
  });

  it('detects overlapping widgets', () => {
    expect(overlaps({ x: 0, y: 0, w: 6, h: 4 }, { x: 3, y: 2, w: 6, h: 4 })).toBe(true);
    expect(overlaps({ x: 0, y: 0, w: 6, h: 4 }, { x: 6, y: 0, w: 6, h: 4 })).toBe(false);
  });

  it('pushes conflicting widgets down when one is moved', () => {
    const items = [
      { id: 'a', layout: { x: 0, y: 0, w: 6, h: 4 } },
      { id: 'b', layout: { x: 0, y: 4, w: 6, h: 4 } },
    ];
    const result = moveLayout(items, 'b', 0, 0);
    const a = result.find(item => item.id === 'a');
    const b = result.find(item => item.id === 'b');
    expect(b?.layout).toEqual({ x: 0, y: 0, w: 6, h: 4 });
    expect(a?.layout.y).toBe(4);
  });

  it('keeps the resized widget and moves others away', () => {
    const items = [
      { id: 'a', layout: { x: 0, y: 0, w: 6, h: 4 } },
      { id: 'b', layout: { x: 6, y: 0, w: 6, h: 4 } },
    ];
    const result = resizeLayout(items, 'a', 12, 4);
    expect(result.find(item => item.id === 'a')?.layout).toEqual({ x: 0, y: 0, w: 12, h: 4 });
    expect(result.find(item => item.id === 'b')?.layout.y).toBe(4);
  });

  it('compacts widgets from their requested positions', () => {
    const result = compactLayouts([
      { id: 'a', layout: { x: 0, y: 6, w: 6, h: 4 } },
      { id: 'b', layout: { x: 6, y: 2, w: 6, h: 4 } },
    ]);
    expect(result.find(item => item.id === 'b')?.layout.y).toBe(2);
    expect(result.find(item => item.id === 'a')?.layout.y).toBe(6);
  });

  it('places the next widget below existing ones', () => {
    const layout = nextLayout([
      {
        id: 'a',
        type: 'echarts',
        layout: { x: 0, y: 0, w: 12, h: 8 },
      },
    ]);
    expect(layout).toMatchObject({ x: 0, y: 8, w: 12, h: 8 });
  });

  it('validates layout bounds', () => {
    expect(isLayoutValid({ x: 0, y: 0, w: 24, h: 8 })).toBe(true);
    expect(isLayoutValid({ x: 12, y: 0, w: 24, h: 8 })).toBe(false);
    expect(isLayoutValid({ x: 0, y: 0, w: 0, h: 4 })).toBe(false);
  });

  it('maps a drop point to a grid layout', () => {
    const metrics = { cellWidth: 100, rowHeight: 40, gap: 12, columns: 24 };
    expect(layoutFromPoint({ x: 0, y: 0 }, { w: 6, h: 4 }, metrics)).toEqual({ x: 0, y: 0, w: 6, h: 4 });
    expect(layoutFromPoint({ x: 340, y: 210 }, { w: 6, h: 4 }, metrics)).toEqual({ x: 3, y: 4, w: 6, h: 4 });
    expect(layoutFromPoint({ x: 9999, y: -5 }, { w: 6, h: 4 }, metrics)).toEqual({ x: 18, y: 0, w: 6, h: 4 });
  });

  it('resolves canvas metrics and only enables px on screen mode', () => {
    expect(resolveCanvasMetrics()).toMatchObject({ unit: 'grid', padding: 16, gap: 12, columns: 24, rowHeight: 40 });
    expect(
      resolveCanvasMetrics({
        mode: 'screen',
        screen: { width: 1920, height: 1080, canvas: { unit: 'px', padding: 0, gap: 8 } },
      }),
    ).toMatchObject({ unit: 'px', width: 1920, height: 1080, padding: 0, gap: 8 });
    expect(
      resolveCanvasMetrics({
        mode: 'standard',
        screen: { width: 1920, height: 1080, canvas: { unit: 'px', padding: 0, gap: 8 } },
      }),
    ).toMatchObject({ unit: 'grid', padding: 16, gap: 12 });
  });

  it('clamps pixel layouts to the canvas content box', () => {
    const metrics = {
      unit: 'px' as const,
      width: 1920,
      height: 1080,
      padding: 16,
      gap: 8,
      columns: 24,
      rowHeight: 40,
    };
    expect(canvasContentWidth(metrics)).toBe(1888);
    expect(canvasContentHeight(metrics)).toBe(1048);
    expect(clampPixelLayout({ x: -20, y: 2000, w: 400, h: 300 }, metrics)).toEqual({
      x: 0,
      y: 748,
      w: 400,
      h: 300,
    });
    expect(clampPixelLayout({ x: 1800, y: 0, w: 400, h: 300 }, metrics)).toEqual({
      x: 1488,
      y: 0,
      w: 400,
      h: 300,
    });
  });

  it('maps a drop point to a pixel layout without grid snapping', () => {
    const metrics = { unit: 'px' as const, width: 1920, height: 1080, padding: 0, gap: 8, columns: 24, rowHeight: 40 };
    expect(layoutFromPixelPoint({ x: 137, y: 91 }, { w: 300, h: 200 }, metrics)).toEqual({ x: 137, y: 91, w: 300, h: 200 });
    expect(layoutFromPixelPoint({ x: 5000, y: 5000 }, { w: 300, h: 200 }, metrics)).toEqual({
      x: 1620,
      y: 880,
      w: 300,
      h: 200,
    });
  });

  it('converts grid sizes and appends pixel widgets below existing content', () => {
    const metrics = { unit: 'px' as const, width: 1920, height: 1080, padding: 0, gap: 8, columns: 24, rowHeight: 40 };
    expect(pixelSizeFromGrid(12, 8, metrics)).toEqual({ w: 960, h: 384 });
    const layout = nextPixelLayout([{ id: 'a', type: 'echarts', layout: { x: 0, y: 0, w: 400, h: 200 } }], metrics, 12, 8);
    expect(layout.y).toBe(208);
  });

  it('normalizes a widget layout by canvas unit', () => {
    const metrics = { unit: 'px' as const, width: 1920, height: 1080, padding: 0, gap: 8, columns: 24, rowHeight: 40 };
    expect(normalizeWidgetLayout({ x: 10, y: 20, w: 300, h: 200 }, metrics)).toEqual({ x: 10, y: 20, w: 300, h: 200 });
    expect(normalizeWidgetLayout({ x: 10, y: 20, w: 300, h: 200 }, { ...metrics, unit: 'grid' })).toEqual({
      x: 0,
      y: 20,
      w: 24,
      h: 60,
    });
  });
});
