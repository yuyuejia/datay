import { CHART_REPLACE_MERGE, chartSetOptionOptions } from './render-tuning';

describe('render-tuning', () => {
  it('增量合并参数包含结构性替换项', () => {
    const options = chartSetOptionOptions();
    expect(options).toMatchObject({ notMerge: false, lazyUpdate: true });
    expect(options.replaceMerge).toEqual(CHART_REPLACE_MERGE);
    expect(CHART_REPLACE_MERGE).toEqual(expect.arrayContaining(['series', 'xAxis', 'yAxis', 'geo', 'visualMap']));
  });
});
