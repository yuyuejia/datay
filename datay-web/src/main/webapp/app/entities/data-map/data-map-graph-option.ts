import type { DataMapEdge, DataMapNode } from './data-map.service';

export interface DataMapGraphOptionContext {
  nodes: DataMapNode[];
  links: DataMapEdge[];
  categories: string[];
  layout: 'force' | 'circular';
  showLabels: boolean;
  focusMode: boolean;
}

export const DATA_MAP_TYPE_LABELS: Record<string, string> = {
  DATASOURCE: '数据源',
  SCHEMA: 'Schema',
  TABLE: '物理表',
  COLUMN: '字段',
  ETL_TASK: 'ETL 任务',
  DATA_SYNC: '数据同步',
  DATA_MODEL: '数据模型',
  MODEL_FIELD: '模型字段',
  METRIC: '指标',
  DATASET: '数据集',
  DASHBOARD: '看板',
  DATA_API: 'API 服务',
  QUALITY_TASK: '质量任务',
};

export const DATA_MAP_TYPE_COLORS: Record<string, string> = {
  DATASOURCE: '#409eff',
  SCHEMA: '#909399',
  TABLE: '#67c23a',
  COLUMN: '#a8c6a0',
  ETL_TASK: '#b37feb',
  DATA_SYNC: '#e6a23c',
  DATA_MODEL: '#2f7de1',
  MODEL_FIELD: '#9db8e8',
  METRIC: '#f56c6c',
  DATASET: '#6366f1',
  DASHBOARD: '#14b8a6',
  DATA_API: '#0ea5e9',
  QUALITY_TASK: '#f59e0b',
};

const TYPE_SIZE: Record<string, number> = {
  DATASOURCE: 46,
  SCHEMA: 26,
  TABLE: 24,
  COLUMN: 9,
  ETL_TASK: 30,
  DATA_SYNC: 30,
  DATA_MODEL: 34,
  MODEL_FIELD: 12,
  METRIC: 30,
  DATASET: 28,
  DASHBOARD: 30,
  DATA_API: 24,
  QUALITY_TASK: 24,
};

export function buildDataMapGraphOption(context: DataMapGraphOptionContext): Record<string, any> {
  const categories = context.categories.map(type => ({
    name: DATA_MAP_TYPE_LABELS[type] || type,
    itemStyle: { color: DATA_MAP_TYPE_COLORS[type] || '#909399' },
  }));
  const categoryIndex = new Map(context.categories.map((type, index) => [type, index]));

  const data = context.nodes.map(node => {
    const size = TYPE_SIZE[node.type] || 18;
    const degreeBoost = Math.min(14, Math.sqrt(Math.max(0, node.degree)) * 2);
    return {
      id: node.id,
      name: node.name,
      value: node.degree,
      category: categoryIndex.get(node.type) ?? 0,
      symbol: 'circle',
      symbolSize: Math.round(size + degreeBoost),
      itemStyle: {
        color: DATA_MAP_TYPE_COLORS[node.type] || '#909399',
        borderColor: '#ffffff',
        borderWidth: 1,
        opacity: node.type === 'COLUMN' || node.type === 'MODEL_FIELD' ? 0.72 : 1,
      },
      label: {
        show: context.showLabels || context.focusMode,
        position: 'right',
        fontSize: 11,
        color: '#4b5563',
        formatter: (params: any) => shorten(params.data?.name, 18),
      },
      nodeId: node.id,
      nodeType: node.type,
      dataSourceName: node.dataSourceName,
      qualifiedName: node.qualifiedName,
      raw: node,
    };
  });

  const links = context.links.map(link => ({
    source: link.source,
    target: link.target,
    value: link.relation,
    relation: link.relation,
    lineStyle: {
      color: 'source',
      curveness: 0.08,
      opacity: 0.42,
      width: link.relation === 'CONTAINS' ? 0.8 : 1.2,
    },
    label: {
      show: false,
      formatter: link.label || link.relation,
      fontSize: 10,
      color: '#6b7280',
    },
  }));

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (params: any) => {
        if (params.dataType === 'edge') {
          const edge = params.data;
          return `${escapeHtml(edge?.relation || '')}<br/>${escapeHtml(String(edge?.source || ''))} → ${escapeHtml(String(edge?.target || ''))}`;
        }
        const data = params.data || {};
        const labels: string[] = [DATA_MAP_TYPE_LABELS[data.nodeType] || data.nodeType || '资产'];
        if (data.qualifiedName) {
          labels.push(escapeHtml(shorten(data.qualifiedName, 60)));
        }
        if (data.dataSourceName) {
          labels.push(`数据源：${escapeHtml(data.dataSourceName)}`);
        }
        labels.push(`关系数：${data.value ?? 0}`);
        return `<strong>${escapeHtml(data.name || '')}</strong><br/>${labels.join('<br/>')}`;
      },
    },
    legend: [
      {
        type: 'scroll',
        data: categories.map(category => category.name),
        left: 16,
        top: 12,
        icon: 'circle',
        itemWidth: 9,
        itemHeight: 9,
        textStyle: { fontSize: 11, color: '#606266' },
        selectedMode: true,
      },
    ],
    series: [
      {
        type: 'graph',
        layout: context.layout,
        circular: { rotateLabel: true },
        force: {
          repulsion: context.nodes.length > 3000 ? 140 : 80,
          edgeLength: [55, 150],
          gravity: 0.08,
          friction: 0.6,
          layoutAnimation: false,
        },
        roam: true,
        draggable: true,
        animation: false,
        progressive: 400,
        progressiveThreshold: 3000,
        scaleLimit: { min: 0.15, max: 4 },
        categories,
        data,
        links,
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 5,
        emphasis: {
          focus: 'adjacency',
          label: { show: true, fontSize: 12, color: '#111827' },
          lineStyle: { opacity: 0.85, width: 2 },
        },
        blur: {
          itemStyle: { opacity: 0.12 },
          lineStyle: { opacity: 0.06 },
        },
        labelLayout: { hideOverlap: true },
      },
    ],
  };
}

function shorten(value: unknown, max: number): string {
  const text = value == null ? '' : String(value);
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
