import { mount } from '@vue/test-utils';
import { h, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import DashboardStage from './DashboardStage.vue';

let resizeCallback: (() => void) | null = null;

class ResizeObserverMock {
  constructor(callback: () => void) {
    resizeCallback = callback;
  }

  observe() {
    // no-op
  }

  disconnect() {
    // no-op
  }
}

function mountStage(props: Record<string, unknown>) {
  return mount(DashboardStage, {
    props: {
      width: 1920,
      height: 1080,
      zoom: 'fit',
      padding: 16,
      ...props,
    },
    slots: {
      default: (params: { scale: number }) => h('div', { class: 'stage-scale' }, String(params.scale)),
    },
  });
}

async function setViewportSize(wrapper: ReturnType<typeof mountStage>, width: number, height: number) {
  const viewport = wrapper.find('.dashboard-stage-viewport').element as HTMLElement;
  Object.defineProperty(viewport, 'clientWidth', { configurable: true, value: width });
  Object.defineProperty(viewport, 'clientHeight', { configurable: true, value: height });
  resizeCallback?.();
  await nextTick();
}

describe('DashboardStage', () => {
  beforeEach(() => {
    resizeCallback = null;
    vi.stubGlobal('ResizeObserver', ResizeObserverMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('scales the fixed stage to fit the viewport', async () => {
    const wrapper = mountStage({ fitMode: 'contain' });
    await setViewportSize(wrapper, 1000, 600);

    const emitted = wrapper.emitted('scale-change') ?? [];
    const actual = emitted.at(-1)?.[0] as number;
    expect(actual).toBeCloseTo(Math.min((1000 - 32) / 1920, (600 - 32) / 1080), 6);

    const frame = wrapper.find('.dashboard-stage-frame').attributes('style') ?? '';
    expect(frame).toContain('width: 968px');
    expect(frame).toContain('height: 544.5px');
  });

  it('honors an explicit zoom value', async () => {
    const wrapper = mountStage({ zoom: 1.5 });
    await setViewportSize(wrapper, 1000, 600);

    expect(wrapper.find('.stage-scale').text()).toBe('1.5');
    expect(wrapper.find('.dashboard-stage').attributes('style')).toContain('transform: scale(1.5, 1.5)');
    expect(wrapper.find('.dashboard-stage-frame').attributes('style')).toContain('width: 2880px');
  });

  it('recomputes fit scale when the viewport resizes', async () => {
    const wrapper = mountStage({ fitMode: 'contain' });
    await setViewportSize(wrapper, 1000, 600);
    const first = (wrapper.emitted('scale-change') ?? []).at(-1)?.[0] as number;

    await setViewportSize(wrapper, 2000, 1200);
    const second = (wrapper.emitted('scale-change') ?? []).at(-1)?.[0] as number;

    expect(second).toBeGreaterThan(first);
    expect(second).toBeCloseTo(Math.min((2000 - 32) / 1920, (1200 - 32) / 1080), 6);
  });

  it('covers the viewport when fitMode is cover', async () => {
    const wrapper = mountStage({ fitMode: 'cover' });
    await setViewportSize(wrapper, 1000, 600);

    expect(wrapper.find('.dashboard-stage-viewport').classes()).toContain('fit-cover');
    const scale = (wrapper.emitted('scale-change') ?? []).at(-1)?.[0] as number;
    expect(scale).toBeCloseTo(Math.max((1000 - 32) / 1920, (600 - 32) / 1080), 6);
  });

  it('scales both axes independently when fitMode is stretch', async () => {
    const wrapper = mountStage({ fitMode: 'stretch' });
    await setViewportSize(wrapper, 1000, 600);

    expect(wrapper.find('.dashboard-stage-viewport').classes()).toContain('fit-stretch');
    const frame = wrapper.find('.dashboard-stage-frame').attributes('style') ?? '';
    expect(frame).toContain('width: 968px');
    expect(frame).toContain('height: 568px');

    const style = wrapper.find('.dashboard-stage').attributes('style') ?? '';
    expect(style).toContain('transform: scale(');
    expect(style).toContain('0.504166');
    expect(style).toContain('0.525925');
  });
});
