import { applyLinkageSelection } from './linkage-highlight';

describe('linkage-highlight', () => {
  it('高亮选中柱并置灰其余柱子', () => {
    const option = { series: [{ type: 'bar', data: [1, 2, 3] }] };

    const result = applyLinkageSelection(option, { seriesIndex: 0, dataIndex: 1 }) as any;

    expect(result.series[0].data[0]).toMatchObject({ value: 1, itemStyle: { opacity: 0.22 } });
    expect(result.series[0].data[1]).toMatchObject({ value: 2, itemStyle: { opacity: 1 } });
    expect(result.series[0].data[2]).toMatchObject({ value: 3, itemStyle: { opacity: 0.22 } });
  });

  it('不修改原始 option', () => {
    const option = { series: [{ type: 'bar', data: [1, 2, 3] }] };
    applyLinkageSelection(option, { seriesIndex: 0, dataIndex: 0 });

    expect(option.series[0].data).toEqual([1, 2, 3]);
  });

  it('多系列时置灰非选中系列', () => {
    const option = {
      series: [
        { type: 'bar', data: [1, 2] },
        { type: 'line', data: [3, 4], lineStyle: { width: 2 } },
      ],
    };

    const result = applyLinkageSelection(option, { seriesIndex: 0, dataIndex: 0 }) as any;

    expect(result.series[1].lineStyle).toMatchObject({ width: 2, opacity: 0.16 });
    expect(result.series[1].data[0].itemStyle.opacity).toBe(0.22);
    expect(result.series[0].data[0].itemStyle.opacity).toBe(1);
  });

  it('切换选中系列时清除旧的系列级透明度，避免新选中项一起置灰', () => {
    const option = {
      series: [
        { type: 'bar', data: [1, 2] },
        { type: 'line', data: [3, 4] },
      ],
    };

    // 先选中折线系列，柱系列被置灰。
    const first = applyLinkageSelection(option, { seriesIndex: 1, dataIndex: 0 }) as any;
    expect(first.series[0].itemStyle.opacity).toBe(1);
    expect(first.series[0].data[0].itemStyle.opacity).toBe(0.22);

    // 再切回柱系列：系列级透明度必须恢复 1，选中柱保持高亮。
    const second = applyLinkageSelection(option, { seriesIndex: 0, dataIndex: 0 }) as any;
    expect(second.series[0].itemStyle.opacity).toBe(1);
    expect(second.series[0].data[0].itemStyle.opacity).toBe(1);
    expect(second.series[0].data[1].itemStyle.opacity).toBe(0.22);
    expect(second.series[1].lineStyle.opacity).toBe(0.16);
  });

  it('饼图按数据项索引置灰其它扇区', () => {
    const option = {
      series: [
        {
          type: 'pie',
          data: [
            { name: 'A', value: 1 },
            { name: 'B', value: 2 },
            { name: 'C', value: 3 },
          ],
        },
      ],
    };

    const result = applyLinkageSelection(option, { seriesIndex: 0, dataIndex: 2 }) as any;

    expect(result.series[0].data[0]).toMatchObject({ name: 'A', itemStyle: { opacity: 0.22 } });
    expect(result.series[0].data[2]).toMatchObject({ name: 'C', itemStyle: { opacity: 1 } });
  });

  it('数组数据项保留 value 并放大选中散点', () => {
    const option = {
      series: [
        {
          type: 'scatter',
          data: [
            [1, 2],
            [3, 4],
          ],
        },
      ],
    };

    const result = applyLinkageSelection(option, { seriesIndex: 0, dataIndex: 1 }) as any;

    expect(result.series[0].data[1].value).toEqual([3, 4]);
    expect(result.series[0].data[1].symbolSize).toBeGreaterThanOrEqual(10);
    expect(result.series[0].data[0].itemStyle.opacity).toBe(0.22);
  });

  it('没有选中项或缺少数据索引时保持原样', () => {
    const option = { series: [{ type: 'bar', data: [1] }] };
    expect(applyLinkageSelection(option, null)).toBe(option);
    expect(applyLinkageSelection(option, { seriesIndex: 0 })).toBe(option);
  });
});
