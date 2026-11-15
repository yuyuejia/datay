import axios from 'axios';

const baseApiUrl = 'api/data-map';

export interface DataMapNode {
  id: string;
  type: string;
  name: string;
  code?: string;
  description?: string;
  qualifiedName?: string;
  dataSourceId?: string;
  dataSourceName?: string;
  schema?: string;
  table?: string;
  degree: number;
  status?: string;
  updatedAt?: string;
  layer: number;
  meta?: Record<string, any>;
}

export interface DataMapEdge {
  id: string;
  source: string;
  target: string;
  relation: string;
  label?: string;
  confidence?: string;
}

export interface DataMapGraph {
  scope: string;
  nodes: DataMapNode[];
  links: DataMapEdge[];
  categories: string[];
  totalNodes: number;
  totalLinks: number;
  truncated: boolean;
}

export interface DataMapFacet {
  key: string;
  label: string;
  count: number;
}

export interface DataMapSearchItem {
  node: DataMapNode;
  score: number;
  highlight?: string;
}

export interface DataMapSearchResult {
  items: DataMapSearchItem[];
  total: number;
  facets: Record<string, DataMapFacet[]>;
  tookMs: number;
  indexing: boolean;
  indexStatus?: string;
}

export interface DataMapIndexStatus {
  status: string;
  startedAt?: string;
  finishedAt?: string;
  nodeCount: number;
  edgeCount: number;
  message?: string;
  stale: boolean;
  coverage?: string;
}

export interface DataMapOverview {
  enabled: boolean;
  indexStatus: DataMapIndexStatus;
  totalNodes: number;
  totalEdges: number;
  typeCounts: Record<string, number>;
  coverage?: string;
}

export interface DataMapAssetDetail {
  node: DataMapNode;
  fields: DataMapNode[];
  graph: DataMapGraph;
  link?: string;
}

export default class DataMapService {
  overview(): Promise<DataMapOverview> {
    return axios.get(`${baseApiUrl}/overview`).then(res => res.data);
  }

  status(): Promise<DataMapIndexStatus> {
    return axios.get(`${baseApiUrl}/index/status`).then(res => res.data);
  }

  reindex(): Promise<DataMapIndexStatus> {
    return axios.post(`${baseApiUrl}/reindex`).then(res => res.data);
  }

  search(params: { q?: string; types?: string; dataSourceId?: string; limit?: number; offset?: number }): Promise<DataMapSearchResult> {
    return axios.get(`${baseApiUrl}/search`, { params }).then(res => res.data);
  }

  graph(params: {
    scope?: string;
    rootId?: string;
    direction?: string;
    depth?: number;
    types?: string;
    dataSourceId?: string;
    includeColumns?: boolean;
    limit?: number;
  }): Promise<DataMapGraph> {
    return axios.get(`${baseApiUrl}/graph`, { params }).then(res => res.data);
  }

  detail(nodeId: string): Promise<DataMapAssetDetail> {
    return axios.get(`${baseApiUrl}/assets/${encodeURIComponent(nodeId)}`).then(res => res.data);
  }

  children(parentId: string): Promise<DataMapNode[]> {
    return axios.get(`${baseApiUrl}/children`, { params: { parentId } }).then(res => res.data);
  }

  types(): Promise<Record<string, string>> {
    return axios.get(`${baseApiUrl}/types`).then(res => res.data);
  }
}
