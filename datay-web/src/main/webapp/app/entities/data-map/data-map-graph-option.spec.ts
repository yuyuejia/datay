import { describe, expect, it } from 'vitest';

import { buildDataMapGraphOption, DATA_MAP_TYPE_COLORS } from './data-map-graph-option';
import type { DataMapEdge, DataMapNode } from './data-map.service';

const node = (overrides: Partial<DataMapNode>): DataMapNode => ({
  id: 'table:1',
  type: 'TABLE',
  name: 'orders',
  degree: 2,
  layer: 0,
  ...overrides,
});

describe('data map graph option', () => {
  it('builds echarts graph categories, nodes and links', () => {
    const nodes: DataMapNode[] = [
      node({ id: 'source:1', type: 'DATASOURCE', name: '订单库' }),
      node({ id: 'table:1', type: 'TABLE', name: 'orders' }),
      node({ id: 'metric:1', type: 'METRIC', name: '订单金额' }),
    ];
    const links: DataMapEdge[] = [
      { id: 'e1', source: 'source:1', target: 'table:1', relation: 'CONTAINS', label: '包含' },
      { id: 'e2', source: 'table:1', target: 'metric:1', relation: 'COMPUTES', label: '计算' },
    ];

    const option = buildDataMapGraphOption({
      nodes,
      links,
      categories: ['DATASOURCE', 'TABLE', 'METRIC'],
      layout: 'force',
      showLabels: false,
      focusMode: false,
    });

    expect(option.series[0].type).toBe('graph');
    expect(option.series[0].data).toHaveLength(3);
    expect(option.series[0].links).toHaveLength(2);
    expect(option.series[0].categories[1].name).toBe('物理表');
    expect(option.series[0].data[1].itemStyle.color).toBe(DATA_MAP_TYPE_COLORS.TABLE);
    expect(option.series[0].data[1].label.show).toBe(false);
  });

  it('shows labels in focus mode', () => {
    const option = buildDataMapGraphOption({
      nodes: [node({})],
      links: [],
      categories: ['TABLE'],
      layout: 'circular',
      showLabels: false,
      focusMode: true,
    });

    expect(option.series[0].layout).toBe('circular');
    expect(option.series[0].data[0].label.show).toBe(true);
  });
});
