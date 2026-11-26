import { childGridStyle, fitChildLayout, isChildGridLayout, normalizeChildLayout } from './container-layout';

describe('container-layout', () => {
  it('保留合法的 24 栅格子布局', () => {
    expect(normalizeChildLayout({ x: 2, y: 1, w: 10, h: 6 })).toEqual({ x: 2, y: 1, w: 10, h: 6 });
  });

  it('越界的栅格子布局被钳制', () => {
    expect(normalizeChildLayout({ x: 20, y: -3, w: 10, h: 0 })).toEqual({ x: 20, y: 0, w: 4, h: 1 });
  });

  it('旧版绝对坐标回落到默认子布局', () => {
    expect(isChildGridLayout({ x: 120, y: 60, w: 300, h: 200 })).toBe(false);
    expect(normalizeChildLayout({ x: 120, y: 60, w: 300, h: 200 })).toEqual({ x: 0, y: 0, w: 12, h: 6 });
  });

  it('生成 CSS Grid 位置', () => {
    expect(childGridStyle({ x: 0, y: 0, w: 12, h: 6 })).toEqual({
      gridColumn: '1 / span 12',
      gridRow: '1 / span 6',
    });
  });

  it('归入容器时把子组件收进可见行数', () => {
    expect(fitChildLayout({ x: 0, y: 14, w: 12, h: 6 }, { h: 8 }, false)).toEqual({ x: 0, y: 1, w: 12, h: 6 });
    expect(fitChildLayout({ x: 0, y: 0, w: 12, h: 20 }, { h: 8 }, false)).toEqual({ x: 0, y: 0, w: 12, h: 7 });
  });

  it('像素容器按 20px 行高换算可见行数', () => {
    expect(fitChildLayout({ x: 0, y: 20, w: 12, h: 6 }, { h: 480 }, true)).toEqual({ x: 0, y: 11, w: 12, h: 6 });
  });
});
