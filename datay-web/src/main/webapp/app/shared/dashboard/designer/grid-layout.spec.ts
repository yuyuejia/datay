import {
  compactLayouts,
  isLayoutValid,
  layoutFromPoint,
  moveLayout,
  nextLayout,
  normalizeLayout,
  overlaps,
  resizeLayout,
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
});
