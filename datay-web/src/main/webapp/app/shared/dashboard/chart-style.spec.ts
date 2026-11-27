import { applyChartStyle } from './chart-style';
import type { WidgetStyle } from './types';

const baseOption = (): Record<string, any> => ({
  legend: { top: 0 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: ['一月', '二月'] },
  yAxis: { type: 'value' },
  series: [{ type: 'bar', name: '销售额', data: [10, 20] }],
});

const style = (chart: NonNullable<WidgetStyle['chart']>): WidgetStyle => ({ chart });

describe('chart-style', () => {
  it('无 style.chart 时完全不动 option', () => {
    const option = baseOption();
    const result = applyChartStyle(option, { chartType: 'bar', palette: [], dark: false });

    expect(result).toBe(option);
    expect(result.series[0].itemStyle).toBeUndefined();
  });

  it('科技风：柱状图渐变、坐标轴、图例与 tooltip 皮肤', () => {
    const option = baseOption();
    applyChartStyle(option, {
      chartType: 'bar',
      style: style({ preset: 'tech' }),
      palette: ['#2fa8ff'],
      dark: true,
    });

    const itemStyle = option.series[0].itemStyle as any;
    expect(itemStyle.color).toMatchObject({ type: 'linear' });
    expect(itemStyle.color.colorStops[0].color).toBe('#2fa8ff');
    expect(itemStyle.borderRadius).toEqual([3, 3, 0, 0]);

    expect((option.xAxis as any).axisLabel).toMatchObject({ color: '#9fc6e8', fontSize: 11 });
    expect((option.xAxis as any).splitLine).toMatchObject({ show: false });
    expect((option.yAxis as any).splitLine.lineStyle).toMatchObject({ type: 'dashed' });
    expect((option.legend as any).textStyle.color).toBe('#cfeaff');
    expect(option.tooltip).toMatchObject({ backgroundColor: 'rgba(6, 22, 40, 0.92)' });
  });

  it('保留 echartsOption 中显式设置的样式', () => {
    const option = baseOption();
    (option.xAxis as any).axisLabel = { color: '#ff0000' };
    option.series[0].itemStyle = { color: '#00ff00' };

    applyChartStyle(option, {
      chartType: 'bar',
      style: style({ preset: 'tech' }),
      palette: ['#2fa8ff'],
      dark: true,
    });

    expect((option.xAxis as any).axisLabel).toMatchObject({ color: '#ff0000', fontSize: 11 });
    expect(option.series[0].itemStyle).toMatchObject({ color: '#00ff00' });
  });

  it('未配置主题序列色时回落到内置科技色板', () => {
    const option = baseOption();
    applyChartStyle(option, {
      chartType: 'bar',
      style: style({ preset: 'tech' }),
      palette: [],
      dark: true,
    });

    expect(option.color).toContain('#2fa8ff');
    expect((option.series[0].itemStyle as any).color.colorStops[0].color).toBe('#2fa8ff');
  });

  it('简约风默认不渐变、不发光', () => {
    const option = baseOption();
    applyChartStyle(option, {
      chartType: 'bar',
      style: style({ preset: 'minimal' }),
      palette: ['#2fa8ff'],
      dark: false,
    });

    expect(option.series[0].itemStyle).toMatchObject({ color: '#2fa8ff' });
  });

  it('折线发光可开关', () => {
    const lineOption = (): Record<string, any> => ({
      xAxis: { type: 'category', data: ['a'] },
      yAxis: { type: 'value' },
      series: [{ type: 'line', data: [1] }],
    });

    const glowing = lineOption();
    applyChartStyle(glowing, {
      chartType: 'line',
      style: style({ preset: 'tech' }),
      palette: ['#1de9b6'],
      dark: true,
    });
    expect((glowing.series[0].lineStyle as any).shadowBlur).toBe(10);
    expect((glowing.series[0].lineStyle as any).shadowColor).toBe('#1de9b6');
    expect(glowing.series[0].symbol).toBe('circle');

    const plain = lineOption();
    applyChartStyle(plain, {
      chartType: 'line',
      style: style({ preset: 'tech', glow: false, gradient: false }),
      palette: ['#1de9b6'],
      dark: true,
    });
    expect((plain.series[0].lineStyle as any).shadowBlur).toBeUndefined();
  });

  it('显式开关控制坐标轴、图例与数据标签', () => {
    const option = baseOption();
    applyChartStyle(option, {
      chartType: 'bar',
      style: style({ preset: 'tech', showAxis: false, showLegend: false, showLabel: true }),
      palette: ['#2fa8ff'],
      dark: true,
    });

    expect((option.xAxis as any).show).toBe(false);
    expect((option.yAxis as any).show).toBe(false);
    expect((option.legend as any).show).toBe(false);
    expect(option.series[0].label).toMatchObject({ show: true });
  });

  it('轴标题写入首个坐标轴并应用字号与颜色', () => {
    const option = baseOption();
    applyChartStyle(option, {
      chartType: 'bar',
      style: style({
        preset: 'tech',
        axisTitleFontSize: 13,
        axisTitleColor: '#ff8800',
        xAxis: { title: '月份' },
        yAxis: { title: '销售额' },
      }),
      palette: ['#2fa8ff'],
      dark: true,
    });

    const xAxis = option.xAxis as any;
    expect(xAxis.name).toBe('月份');
    expect(xAxis.nameLocation).toBe('middle');
    expect(xAxis.nameTextStyle).toMatchObject({ fontSize: 13, color: '#ff8800' });

    const yAxis = option.yAxis as any;
    expect(yAxis.name).toBe('销售额');
    expect(yAxis.nameRotate).toBe(90);
  });

  it('轴标题为坐标轴预留空间', () => {
    const option = { ...baseOption(), grid: { left: 48, right: 24, top: 40, bottom: 36 } };
    applyChartStyle(option, {
      chartType: 'bar',
      style: style({ preset: 'tech', xAxis: { title: '月份' }, yAxis: { title: '销售额' } }),
      palette: [],
      dark: true,
    });

    expect(option.grid).toMatchObject({ left: 58, bottom: 58 });
  });

  it('双轴图表只在首个坐标轴写入标题', () => {
    const option: Record<string, any> = {
      xAxis: { type: 'category', data: ['a'] },
      yAxis: [{ type: 'value' }, { type: 'value' }],
      series: [{ type: 'bar', data: [1] }],
    };
    applyChartStyle(option, {
      chartType: 'bar',
      style: style({ preset: 'tech', yAxis: { title: '金额' } }),
      palette: [],
      dark: true,
    });

    expect((option.yAxis as any[])[0].name).toBe('金额');
    expect((option.yAxis as any[])[1].name).toBeUndefined();
  });

  it('图例位置与字号可覆盖', () => {
    const option = baseOption();
    applyChartStyle(option, {
      chartType: 'bar',
      style: style({ preset: 'tech', legendPosition: 'left', legendFontSize: 14 }),
      palette: [],
      dark: true,
    });

    const legend = option.legend as any;
    expect(legend).toMatchObject({ left: 0, top: 'middle', orient: 'vertical' });
    expect(legend.top).toBe('middle');
    expect(legend.textStyle.fontSize).toBe(14);
  });

  it('网格线样式、刻度标记与轴标签字号可覆盖', () => {
    const option = baseOption();
    applyChartStyle(option, {
      chartType: 'bar',
      style: style({
        preset: 'tech',
        yAxis: { grid: true, gridStyle: 'dotted', tick: true, labelFontSize: 14, lineColor: '#123456' },
      }),
      palette: [],
      dark: true,
    });

    const yAxis = option.yAxis as any;
    expect(yAxis.splitLine).toMatchObject({ show: true, lineStyle: { type: 'dotted' } });
    expect(yAxis.axisTick.show).toBe(true);
    expect(yAxis.axisLabel.fontSize).toBe(14);
    expect(yAxis.axisLine.lineStyle.color).toBe('#123456');
  });

  it('柱条圆角与数据标签字号可覆盖', () => {
    const option = baseOption();
    applyChartStyle(option, {
      chartType: 'bar',
      style: style({ preset: 'tech', barRadius: 8, showLabel: true, labelFontSize: 15 }),
      palette: ['#2fa8ff'],
      dark: true,
    });

    expect((option.series[0].itemStyle as any).borderRadius).toEqual([8, 8, 0, 0]);
    expect(option.series[0].label).toMatchObject({ show: true, fontSize: 15 });
  });
});
