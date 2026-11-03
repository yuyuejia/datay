export interface EtlAiSpecNode {
  key?: string;
  id?: string;
  type: string;
  label?: string;
  x?: number;
  y?: number;
  config?: Record<string, any> | string;
}

export interface EtlAiSpecEdge {
  source: string;
  target: string;
  label?: string;
}

export interface EtlAiTaskSpec {
  taskName?: string;
  taskDesc?: string;
  cron?: string;
  nodes?: EtlAiSpecNode[];
  edges?: EtlAiSpecEdge[];
  warnings?: string[];
}

export interface EtlAiTaskContext {
  taskName?: string;
  taskDesc?: string;
  cron?: string;
  nodes: Array<Record<string, any>>;
  edges: Array<Record<string, any>>;
}

export interface EtlAiSummary {
  taskName: string;
  nodeCount: number;
  edgeCount: number;
  warningCount: number;
  emptyConfigNodes: string[];
}

const SENSITIVE_KEY = /(password|passwd|secret|token|api[-_]?key|access[-_]?key|private[-_]?key|authorization)/i;
const MAX_CONTEXT_NODES = 60;
const MAX_CONTEXT_VALUE_CHARS = 1200;

const truncateValue = (value: string): string =>
  value.length > MAX_CONTEXT_VALUE_CHARS ? `${value.slice(0, MAX_CONTEXT_VALUE_CHARS)}…` : value;

export const sanitizeConfig = (config: any): Record<string, any> => {
  if (!config || typeof config !== 'object' || Array.isArray(config)) {
    return {};
  }
  const result: Record<string, any> = {};
  Object.keys(config)
    .slice(0, 100)
    .forEach(key => {
      result[key] = SENSITIVE_KEY.test(key) ? '***' : sanitizeValue(config[key]);
    });
  return result;
};

const sanitizeValue = (value: any): any => {
  if (value === null || value === undefined) {
    return value;
  }
  if (typeof value === 'string') {
    return truncateValue(value);
  }
  if (Array.isArray(value)) {
    return value.slice(0, 50).map(item => sanitizeValue(item));
  }
  if (typeof value === 'object') {
    return sanitizeConfig(value);
  }
  return value;
};

/**
 * 把当前画布转换成发给 AI 的上下文。
 * 只保留节点类型、标题、坐标与配置，并对密码/密钥字段脱敏。
 */
export const buildAiTaskContext = (task: any, nodes: any[], edges: any[]): EtlAiTaskContext => {
  const contextNodes = (nodes || []).slice(0, MAX_CONTEXT_NODES).map(node => ({
    key: node.id,
    type: node.data?.type,
    label: node.data?.label,
    x: node.position?.x,
    y: node.position?.y,
    config: sanitizeConfig(node.data?.config),
  }));
  const contextEdges = (edges || []).slice(0, MAX_CONTEXT_NODES * 3).map(edge => ({
    source: edge.source,
    target: edge.target,
    label: edge.label || edge.data?.label || '',
  }));
  return {
    taskName: task?.taskName || '',
    taskDesc: task?.taskDesc || '',
    cron: task?.cron || '',
    nodes: contextNodes,
    edges: contextEdges,
  };
};

const safeParseConfig = (raw: string): Record<string, any> => {
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
};

/**
 * 生成用于对话展示的任务摘要。
 */
export const summarizeEtlSpec = (spec?: EtlAiTaskSpec | null): EtlAiSummary => {
  const nodes = Array.isArray(spec?.nodes) ? spec!.nodes : [];
  const edges = Array.isArray(spec?.edges) ? spec!.edges : [];
  const warnings = Array.isArray(spec?.warnings) ? spec!.warnings : [];
  const emptyConfigNodes = nodes
    .filter(node => {
      const config = typeof node.config === 'string' ? safeParseConfig(node.config) : node.config;
      return !config || Object.keys(config).length === 0;
    })
    .map(node => node.label || node.type);
  return {
    taskName: spec?.taskName || '',
    nodeCount: nodes.length,
    edgeCount: edges.length,
    warningCount: warnings.length,
    emptyConfigNodes,
  };
};

const toCoordinate = (value: any, fallback: number): number => {
  if (value === null || value === undefined || value === '') {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

const toConfig = (raw: EtlAiSpecNode['config']): Record<string, any> => {
  if (!raw) {
    return {};
  }
  if (typeof raw === 'string') {
    return safeParseConfig(raw);
  }
  return raw;
};

/**
 * 把 AI 返回的节点定义转换成 Vue Flow 节点。
 * 同时返回 key -> 实际节点 id 的映射，供连线使用。
 */
export const buildFlowNodes = (
  spec: EtlAiTaskSpec,
  getGroup: (type: string) => string,
  onDelete: (id: string) => void,
): { nodes: any[]; keyToId: Record<string, string> } => {
  const nodes: any[] = [];
  const keyToId: Record<string, string> = {};
  const usedIds = new Set<string>();
  (spec?.nodes || []).forEach((node, index) => {
    if (!node || !node.type) {
      return;
    }
    const baseKey = String(node.key || node.id || `n${index + 1}`);
    let id = baseKey;
    let suffix = 2;
    while (usedIds.has(id)) {
      id = `${baseKey}_${suffix++}`;
    }
    usedIds.add(id);
    if (!keyToId[baseKey]) {
      keyToId[baseKey] = id;
    }
    nodes.push({
      id,
      type: 'etl',
      data: {
        label: node.label || node.type,
        type: node.type,
        config: toConfig(node.config),
        group: getGroup(node.type),
        onDelete,
      },
      position: {
        x: toCoordinate(node.x, 100 + index * 280),
        y: toCoordinate(node.y, 120 + index * 160),
      },
    });
  });
  return { nodes, keyToId };
};

/**
 * 把 AI 返回的连线定义转换成 Vue Flow 连线。
 */
export const buildFlowEdges = (
  spec: EtlAiTaskSpec,
  keyToId: Record<string, string>,
  queueCapacity: number,
  onEditLabel: (id: string) => void,
  onDeleteEdge: (id: string) => void,
): any[] => {
  const edges: any[] = [];
  const used = new Set<string>();
  (spec?.edges || []).forEach((edge, index) => {
    if (!edge || !edge.source || !edge.target) {
      return;
    }
    const source = keyToId[edge.source] || edge.source;
    const target = keyToId[edge.target] || edge.target;
    if (!source || !target || source === target) {
      return;
    }
    let id = `ai-e${index + 1}`;
    let suffix = 2;
    while (used.has(id)) {
      id = `ai-e${index + 1}_${suffix++}`;
    }
    used.add(id);
    const label = edge.label || '';
    edges.push({
      id,
      source,
      target,
      type: 'etl',
      label,
      data: {
        label,
        queueCapacity,
        onEditLabel,
        onDeleteEdge,
      },
    });
  });
  return edges;
};
