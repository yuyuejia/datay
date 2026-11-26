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
  const singleValue = type === 'pie' || type === 'donut' || type === 'funnel' || type === 'treemap' || type === 'map';
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
    'map',
  ];

  it.each(chartTypes)('%s 生成有效 SVG', type => {
    const option = buildEchartsOption(widget(type), result);
    const svg = renderSvg(option);

    expect(svg).toContain('<svg');
    expect(svg.length).toBeGreaterThan(1000);
  });

  it('地图多图层生成有效 SVG', () => {
    const province: DatasetResult = {
      columns: ['province', 'students'],
      rows: [
        { province: '广东省', students: 10 },
        { province: '北京市', students: 30 },
      ],
    };
    const city: DatasetResult = {
      columns: ['city', 'lng', 'lat', 'students'],
      rows: [{ city: '重庆', lng: 106.55, lat: 29.56, students: 15 }],
    };
    const flow: DatasetResult = {
      columns: ['from_lng', 'from_lat', 'to_lng', 'to_lat', 'flow'],
      rows: [{ from_lng: 114.05, from_lat: 22.55, to_lng: 106.55, to_lat: 29.56, flow: 5 }],
    };

    const option = buildEchartsOption(
      widget('map', {
        chart: {
          type: 'map',
          map: 'china',
          layers: [
            { kind: 'region', valueField: 'students' },
            { kind: 'effectScatter', datasetId: 'ds_city', lngField: 'lng', latField: 'lat', nameField: 'city', valueField: 'students' },
            {
              kind: 'lines',
              datasetId: 'ds_flow',
              fromLngField: 'from_lng',
              fromLatField: 'from_lat',
              toLngField: 'to_lng',
              toLatField: 'to_lat',
              valueField: 'flow',
            },
          ],
        },
        encode: {
          dimensions: [{ field: 'province', role: 'x' }],
          measures: [{ field: 'students', agg: 'sum', role: 'value' }],
        },
      }),
      province,
      { ds_city: city, ds_flow: flow },
    );

    const svg = renderSvg({ ...option, animation: false });

    expect(svg).toContain('<svg');
    expect(svg).toContain('path');
    // visualMap 会把高值区域渲染成渐变末端色，验证区域填色确实生效。
    expect(svg).toContain('rgb(49,54,149)');
    expect(svg.length).toBeGreaterThan(1000);
  });
});
