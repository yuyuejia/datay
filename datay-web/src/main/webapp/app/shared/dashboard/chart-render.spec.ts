import { SVGRenderer } from 'echarts/renderers';

import { echarts } from './echarts';
import { buildEchartsOption } from './spec-to-option';
import type { ChartType, DashboardWidget, DatasetResult } from './types';

echarts.use([SVGRenderer]);

const result: DatasetResult = {
  columns: ['region', 'channel', 'amount', 'quantity'],
  rows: [
    { region: '华东', channel: '官网', amount: 120.5, quantity: 8 },
    { region: '华南', channel: 'APP', amount: 80, quantity: 5 },
    { region: '华北', channel: '门店', amount: 45.25, quantity: 3 },
    { region: '华东', channel: 'APP', amount: 60, quantity: 4 },
    { region: '西南', channel: '官网', amount: 70, quantity: 6 },
  ],
};

const widget = (type: ChartType, overrides: Partial<DashboardWidget> = {}): DashboardWidget => {
  const singleValue = type === 'pie' || type === 'donut' || type === 'funnel' || type === 'treemap';
  const nodeLink = type === 'sankey' || type === 'graph';
  const noDimension = type === 'gauge';
  return {
    id: `chart-${type}`,
    title: type,
    type: 'echarts',
    layout: { x: 0, y: 0, w: 12, h: 8 },
    chart: { type },
    encode: {
      dimensions: noDimension
        ? []
        : nodeLink
          ? [
              { field: 'region', role: 'x' },
              { field: 'channel', role: 'series' },
            ]
          : singleValue || type === 'heatmap'
            ? [{ field: 'region', role: 'x' }, ...(type === 'heatmap' ? [{ field: 'channel', role: 'series' as const }] : [])]
            : [{ field: 'region', role: 'x' }],
      measures: [
        {
          field: 'amount',
          agg: 'sum',
          role: singleValue || nodeLink || type === 'heatmap' || type === 'gauge' ? 'value' : 'y',
        },
        ...(type === 'radar' ? [{ field: 'quantity', agg: 'sum' as const, role: 'y' as const }] : []),
      ],
      groupBy: !noDimension,
      limit: 100,
    },
    echartsOption: {
      tooltip: { trigger: 'item' },
      series: [
        {
          type: type === 'area' ? 'line' : type === 'donut' ? 'pie' : type === 'waterfall' ? 'bar' : type,
        },
      ],
    },
    ...overrides,
  };
};

const renderSvg = (option: Record<string, any>): string => {
  const chart = echarts.init(null as any, undefined, {
    renderer: 'svg',
    ssr: true,
    width: 800,
    height: 600,
  });
  try {
    chart.setOption(option);
    return chart.renderToSVGString();
  } finally {
    chart.dispose();
  }
};

describe('chart ECharts SSR render', () => {
  const chartTypes: ChartType[] = [
    'bar',
    'line',
    'area',
    'pie',
    'donut',
    'gauge',
    'scatter',
    'radar',
    'funnel',
    'heatmap',
    'treemap',
    'waterfall',
    'sankey',
    'graph',
    'boxplot',
  ];

  it.each(chartTypes)('%s 生成有效 SVG', type => {
    const option = buildEchartsOption(widget(type), result);
    const svg = renderSvg(option);

    expect(svg).toContain('<svg');
    expect(svg.length).toBeGreaterThan(1000);
  });
});
