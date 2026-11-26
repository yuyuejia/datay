import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

const { chartMock, initMock } = vi.hoisted(() => {
  const chart = {
    on: vi.fn(),
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
  };
  return { chartMock: chart, initMock: vi.fn(() => chart) };
});

vi.mock('./echarts', () => ({
  echarts: {
    init: initMock,
    registerMap: vi.fn(),
    use: vi.fn(),
  },
}));

import EChart from './EChart.vue';

describe('EChart 渲染修正', () => {
  it('setOption 使用增量合并并替换 series/坐标轴', () => {
    initMock.mockReturnValue(chartMock);

    mount(EChart, { props: { option: { series: [{ type: 'bar' }] } } });

    expect(initMock).toHaveBeenCalledWith(expect.anything(), undefined);
    const options = chartMock.setOption.mock.calls[0][1];
    expect(options).toMatchObject({ notMerge: false, lazyUpdate: true });
    expect(options.replaceMerge).toEqual(expect.arrayContaining(['series', 'xAxis', 'yAxis']));
  });
});
