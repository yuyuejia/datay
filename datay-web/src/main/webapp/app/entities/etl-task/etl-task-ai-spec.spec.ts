import { describe, expect, it } from 'vitest';

import { buildAiTaskContext, buildFlowEdges, buildFlowNodes, summarizeEtlSpec } from './etl-task-ai-spec';

describe('etl-task-ai-spec', () => {
  it('redacts sensitive fields when building the AI context', () => {
    const context = buildAiTaskContext(
      { taskName: '订单同步', taskDesc: '每天同步', cron: '0 0 2 * * ?' },
      [
        {
          id: 'n1',
          position: { x: 10, y: 20 },
          data: {
            type: 'StreamJdbcInput',
            label: '读取订单',
            config: { sourceId: 'ds1', password: 'secret', nested: { token: 'abc' } },
          },
        },
      ],
      [{ source: 'n1', target: 'n2', label: 'vip', data: { label: 'vip' } }],
    );

    expect(context.taskName).toBe('订单同步');
    expect(context.nodes).toHaveLength(1);
    expect(context.nodes[0]).toMatchObject({ key: 'n1', type: 'StreamJdbcInput', x: 10, y: 20 });
    expect(context.nodes[0].config).toMatchObject({ sourceId: 'ds1', password: '***', nested: { token: '***' } });
    expect(context.edges[0]).toMatchObject({ source: 'n1', target: 'n2', label: 'vip' });
  });

  it('summarizes nodes, edges and empty configs', () => {
    const summary = summarizeEtlSpec({
      taskName: '订单同步',
      nodes: [
        { key: 'n1', type: 'StreamJdbcInput', config: { table: 'orders' } },
        { key: 'n2', type: 'ModelWrite', config: {} },
      ],
      edges: [{ source: 'n1', target: 'n2' }],
    });

    expect(summary).toMatchObject({
      taskName: '订单同步',
      nodeCount: 2,
      edgeCount: 1,
      warningCount: 0,
    });
    expect(summary.emptyConfigNodes).toEqual(['ModelWrite']);
  });

  it('maps AI nodes to Vue Flow nodes and keeps keys unique', () => {
    const { nodes, keyToId } = buildFlowNodes(
      {
        nodes: [
          { key: 'n1', type: 'StreamJdbcInput', label: '读取', config: { table: 'orders' }, x: 1, y: 2 },
          { key: 'n1', type: 'StreamJdbcOutput', label: '写入', config: { table: 'orders' } },
        ],
        edges: [],
      },
      type => `group-${type}`,
      () => undefined,
    );

    expect(nodes).toHaveLength(2);
    expect(nodes[0]).toMatchObject({
      id: 'n1',
      type: 'etl',
      data: { label: '读取', type: 'StreamJdbcInput', config: { table: 'orders' }, group: 'group-StreamJdbcInput' },
      position: { x: 1, y: 2 },
    });
    expect(nodes[1].id).toBe('n1_2');
    expect(keyToId.n1).toBe('n1');
  });

  it('maps AI edges to Vue Flow edges with queue capacity', () => {
    const edges = buildFlowEdges(
      {
        nodes: [],
        edges: [
          { source: 'n1', target: 'n2', label: 'vip' },
          { source: 'n1', target: 'n1' },
        ],
      },
      { n1: 'n1', n2: 'n2' },
      10,
      () => undefined,
      () => undefined,
    );

    expect(edges).toHaveLength(1);
    expect(edges[0]).toMatchObject({
      id: 'ai-e1',
      source: 'n1',
      target: 'n2',
      type: 'etl',
      label: 'vip',
      data: { label: 'vip', queueCapacity: 10 },
    });
  });
});
