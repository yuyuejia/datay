import { defineComponent, inject, onMounted, ref, onBeforeUnmount, computed, nextTick, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import DataSourceService from './data-source.service';
import DataQueryService from './data-query.service';
import { useAlertService } from '@/shared/alert/alert.service';

import { EditorState } from '@codemirror/state';
import { EditorView, keymap } from '@codemirror/view';
import { defaultKeymap, historyKeymap, indentWithTab } from '@codemirror/commands';
import { sql, MySQL, PostgreSQL, StandardSQL } from '@codemirror/lang-sql';
import { autocompletion, completionKeymap, startCompletion } from '@codemirror/autocomplete';
import { foldKeymap } from '@codemirror/language';
import { format } from 'sql-formatter';
import DataQueryAi from './data-query-ai.vue';
import TableLineage from './TableLineage.vue';
import { getSqlDialect, qualifiedTableName } from '@/shared/sql/sql-dialect';
import { sqlEditorBaseExtensions } from '@/shared/sql/sql-editor-theme';

interface TableNode {
  id: string;
  label: string;
  type: 'catalog' | 'schema' | 'table' | 'more' | 'hint';
  name: string;
  schema?: string;
  catalog?: string;
  isLeaf?: boolean;
  /** 分页/提示伪节点关联的 schema 节点 id */
  schemaNodeId?: string;
}

interface ColumnInfo {
  name: string;
  type?: string;
  length?: number;
  precision?: number;
  scale?: number;
  nullable?: boolean;
  defaultValue?: string;
  comment?: string;
  primaryKey?: boolean;
}

interface IndexInfo {
  name: string;
  columns?: string[];
  unique?: boolean;
  type?: string;
}

interface TableDetail {
  table?: string;
  schema?: string;
  catalog?: string;
  dbType?: string;
  comment?: string;
  columns?: ColumnInfo[];
  indexes?: IndexInfo[];
}

interface TableHealthSummary {
  dataFiles?: number;
  deleteFiles?: number;
  totalFiles?: number;
  smallFiles?: number;
  smallFileThresholdBytes?: number;
  dataSizeBytes?: number;
  deleteSizeBytes?: number;
  totalSizeBytes?: number;
  avgFileSizeBytes?: number;
  totalRecords?: number;
  partitionCount?: number;
  snapshotCount?: number;
  dataManifests?: number;
  deleteManifests?: number;
  manifestFileCount?: number;
  manifestSizeBytes?: number;
  manifestListSizeBytes?: number;
  metadataFileCount?: number;
  metadataSizeBytes?: number;
  currentMetadataSizeBytes?: number;
  inlinedRows?: number;
  inlinedDeleteRows?: number;
}

interface TableHealthPartitionField {
  specId?: number;
  name?: string;
  transform?: string;
  sourceId?: number;
}

interface TableHealthFile {
  path?: string;
  contentType?: string;
  format?: string;
  sizeBytes?: number;
  recordCount?: number;
  partition?: string;
}

interface TableHealthManifest {
  path?: string;
  contentType?: string;
  sizeBytes?: number;
  addedFiles?: number;
  existingFiles?: number;
  deletedFiles?: number;
  addedRows?: number;
  existingRows?: number;
  deletedRows?: number;
}

interface TableHealthSnapshot {
  snapshotId?: number;
  sequenceNumber?: number;
  timestampMs?: number;
  operation?: string;
  totalRecords?: number;
  totalDataFiles?: number;
  totalDeleteFiles?: number;
  manifestList?: string;
}

interface TableHealthDetail {
  supported?: boolean;
  message?: string;
  format?: string;
  status?: string;
  healthScore?: number;
  location?: string;
  generatedAt?: number;
  lastUpdatedMs?: number;
  lastOperation?: string;
  smallFileThresholdBytes?: number;
  catalogType?: string;
  dataPath?: string;
  metadataVersion?: string;
  summary?: TableHealthSummary;
  partitioning?: {
    partitioned?: boolean;
    defaultSpecId?: number;
    partitionCount?: number;
    fields?: TableHealthPartitionField[];
  };
  warnings?: string[];
  files?: TableHealthFile[];
  filesTruncated?: boolean;
  manifests?: TableHealthManifest[];
  manifestsTruncated?: boolean;
  snapshots?: TableHealthSnapshot[];
}

interface TableMaintenanceParam {
  name: string;
  label?: string;
  type?: string;
  default?: any;
  min?: number | null;
  max?: number | null;
  step?: number | null;
  description?: string;
}

interface TableMaintenanceAction {
  id: string;
  label?: string;
  description?: string;
  scope?: string;
  danger?: boolean;
  available?: boolean;
  unavailableReason?: string;
  confirm?: string;
  params?: TableMaintenanceParam[];
}

interface TableMaintenanceResult {
  success?: boolean;
  message?: string;
  rowCount?: number;
  rowsTruncated?: boolean;
  dryRun?: boolean;
  rows?: any[];
}

interface MaintenanceDialogState {
  visible: boolean;
  tab: PreviewTab | null;
  action: TableMaintenanceAction | null;
  params: Record<string, any>;
  running: boolean;
  result: TableMaintenanceResult | null;
}

interface PreviewTab {
  key: string;
  title: string;
  table: string;
  schema: string;
  catalog?: string;
  limit: number;
  loading: boolean;
  columns: string[];
  rows: any[];
  error: string;
  metaLoading: boolean;
  metaError: string;
  detail: TableDetail | null;
  subTab: string;
  health: TableHealthDetail | null;
  healthLoading: boolean;
  healthError: string;
  healthLoaded: boolean;
  maintenanceActions: TableMaintenanceAction[];
}

export default defineComponent({
  name: 'DataQuery',
  components: { DataQueryAi, TableLineage },
  setup() {
    const route = useRoute();
    const router = useRouter();
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const dataQueryService = inject('dataQueryService', () => new DataQueryService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const dataSourceId = computed(() => (route.params.dataSourceId ? String(route.params.dataSourceId) : ''));
    const dataSourceName = ref('');
    const dataSourceType = ref('');
    const isExecuting = ref(false);
    const aiDrawerVisible = ref(false);

    const treeRef = ref();
    const treeData = ref<any[]>([]);
    const treeLoading = ref(false);
    const treeError = ref('');

    // 左侧树表清单分页/搜索：默认加载 1000 张，最多展示 5000 张，超出后依靠服务端搜索
    const TABLE_PAGE_SIZE = 1000;
    const TABLE_MAX_LOAD = 5000;
    const TABLE_SEARCH_LIMIT = 1000;
    const TABLE_SEARCH_DEBOUNCE_MS = 300;

    const tableSearch = ref('');
    const tableSearching = ref(false);
    const tableSearchHint = ref('');
    const hoveredNodeId = ref('');
    /** 当前已展开的 schema 节点，搜索时只刷新这些节点 */
    const expandedSchemas = new Map<string, TableNode>();
    /** 记录每个 schema 当前分页加载上限 */
    const schemaLoadLimits = new Map<string, number>();
    /** 每个 schema 的请求序号，用于丢弃过期响应 */
    const searchTokens = new Map<string, number>();
    let tableSearchTimer: ReturnType<typeof setTimeout> | null = null;

    const sqlCode = ref('');
    const editorContainer = ref<HTMLElement | null>(null);
    let editorView: EditorView | null = null;

    const editorHeight = ref(260);
    const isDragging = ref(false);
    let resizeStartY = 0;
    let resizeStartHeight = 0;
    const MIN_EDITOR_HEIGHT = 80;
    const MIN_RESULT_HEIGHT = 80;

    const resultColumns = ref<string[]>([]);
    const resultData = ref<any[]>([]);
    const queryError = ref('');
    const executionTime = ref<number | null>(null);
    const affectedRows = ref<number | null>(null);
    const hasExecuted = ref(false);
    const executedSuccessfully = ref(false);
    const executedSql = ref('');
    const activeResultTab = ref('result');
    // 单次查询最大返回行数：默认 1000，避免用户不写 LIMIT 时触发全量查询
    const queryLimitOptions = [100, 500, 1000, 5000, 10000];
    const queryLimit = ref(1000);
    const resultTruncated = ref(false);
    const resultRowLimit = ref<number | null>(null);

    const executionStatusLabel = computed(() => {
      if (isExecuting.value) {
        return '执行中';
      }
      return executedSuccessfully.value ? '执行成功' : '执行失败';
    });

    const executionStatusType = computed(() => {
      if (isExecuting.value) {
        return 'info';
      }
      return executedSuccessfully.value ? 'success' : 'danger';
    });

    const activeTab = ref('sql');
    const previewTabs = ref<PreviewTab[]>([]);
    const maintenanceDialog = ref<MaintenanceDialogState>({
      visible: false,
      tab: null,
      action: null,
      params: {},
      running: false,
      result: null,
    });

    const catalogMaintenanceActions = ref<TableMaintenanceAction[]>([]);

    const contextMenu = ref<{ visible: boolean; x: number; y: number; data: TableNode | null }>({
      visible: false,
      x: 0,
      y: 0,
      data: null,
    });

    const getActiveSql = (): string => {
      if (!editorView) return sqlCode.value.trim();
      const sel = editorView.state.selection.main;
      if (!sel.empty) {
        const selected = editorView.state.doc.sliceString(sel.from, sel.to).trim();
        if (selected) return selected;
      }
      return sqlCode.value.trim();
    };

    const executeQuery = async () => {
      const activeSql = getActiveSql();
      if (!activeSql) {
        alertService.showWarning('请输入 SQL 语句');
        return;
      }

      isExecuting.value = true;
      queryError.value = '';
      resultColumns.value = [];
      resultData.value = [];
      executionTime.value = null;
      affectedRows.value = null;
      executedSql.value = activeSql;
      hasExecuted.value = true;
      executedSuccessfully.value = false;
      resultTruncated.value = false;
      resultRowLimit.value = null;

      const startTime = performance.now();

      try {
        const res = await dataQueryService().executeQuery(dataSourceId.value, activeSql, queryLimit.value);
        const elapsed = Math.round(performance.now() - startTime);
        executionTime.value = elapsed;
        executedSuccessfully.value = true;

        const columns = res.columns || res.columnNames || res.fields || [];
        const rows = res.rows || res.data || res.result || [];

        if (Array.isArray(columns) && columns.length > 0) {
          resultColumns.value = columns;
        } else if (Array.isArray(rows) && rows.length > 0) {
          resultColumns.value = Object.keys(rows[0]);
        }

        if (Array.isArray(rows)) {
          resultData.value = rows;
        }
        affectedRows.value = res.affectedRows || (Array.isArray(rows) ? rows.length : null);
        resultTruncated.value = res.truncated === true;
        resultRowLimit.value = typeof res.rowLimit === 'number' ? res.rowLimit : queryLimit.value;

        // DuckDB 的 ATTACH / DETACH 会改变 catalog 结构，执行后立即刷新左侧目录
        if (/\b(ATTACH|DETACH)\b/i.test(activeSql)) {
          await loadSchemas();
        }
      } catch (err: any) {
        const elapsed = Math.round(performance.now() - startTime);
        executionTime.value = elapsed;
        queryError.value = err.response?.data?.message || err.response?.data?.detail || err.message || '查询执行失败';
        activeResultTab.value = 'log';
      } finally {
        isExecuting.value = false;
      }
    };

    const generateDataset = () => {
      const activeSql = getActiveSql();
      if (!activeSql) {
        alertService.showWarning('请先编写 SQL 语句');
        return;
      }
      if (!dataSourceId.value) {
        alertService.showWarning('缺少数据源信息');
        return;
      }
      router.push({
        name: 'AnalysisDatasetEdit',
        query: {
          dataSourceId: dataSourceId.value,
          sql: activeSql,
          name: `${dataSourceName.value || 'SQL'} 分析数据集`,
          from: 'data-query',
        },
      });
    };

    const getSqlDialectSpec = () => {
      const type = (dataSourceType.value || '').toUpperCase();
      if (
        type.includes('MYSQL') ||
        type.includes('TIDB') ||
        type.includes('DORIS') ||
        type.includes('CLICKHOUSE') ||
        type.includes('TDENGINE')
      ) {
        return MySQL;
      }
      if (type.includes('ORACLE') || type.includes('SQLSERVER') || type.includes('MSSQL')) {
        return StandardSQL;
      }
      if (
        type.includes('POSTGRESQL') ||
        type.includes('DUCKDB') ||
        type.includes('DUCKLAKE') ||
        type.includes('POLARIS') ||
        type.includes('GREENPLUM') ||
        type.includes('YMATRIX') ||
        type.includes('KINGBASE')
      ) {
        return PostgreSQL;
      }
      return StandardSQL;
    };

    const initEditor = () => {
      if (!editorContainer.value) return;

      const updateListener = EditorView.updateListener.of(v => {
        if (v.docChanged) {
          sqlCode.value = v.state.doc.toString();
        }
      });

      const runQueryKeymap = keymap.of([
        {
          key: 'Mod-Enter',
          preventDefault: true,
          run: () => {
            executeQuery();
            return true;
          },
        },
        {
          key: 'Mod-Space',
          preventDefault: true,
          run: () => {
            if (editorView) {
              startCompletion(editorView);
            }
            return true;
          },
        },
        {
          key: 'Mod-Shift-f',
          preventDefault: true,
          run: () => {
            formatSql();
            return true;
          },
        },
      ]);

      const state = EditorState.create({
        doc: sqlCode.value,
        extensions: [
          ...sqlEditorBaseExtensions(),
          keymap.of([indentWithTab, ...defaultKeymap, ...historyKeymap, ...completionKeymap, ...foldKeymap]),
          runQueryKeymap,
          sql({ dialect: getSqlDialectSpec() }),
          autocompletion({
            activateOnTyping: true,
            defaultKeymap: false,
          }),
          updateListener,
        ],
      });

      editorView = new EditorView({
        state,
        parent: editorContainer.value,
      });
    };

    const destroyEditor = () => {
      if (editorView) {
        editorView.destroy();
        editorView = null;
      }
    };

    const getQueryMainHeight = () => {
      const el = document.querySelector('.sql-pane') as HTMLElement | null;
      return el ? el.clientHeight : 600;
    };

    const onMouseMove = (e: MouseEvent) => {
      if (!isDragging.value) return;
      const delta = e.clientY - resizeStartY;
      const newHeight = resizeStartHeight + delta;
      const mainHeight = getQueryMainHeight();
      const maxEditorHeight = mainHeight - MIN_RESULT_HEIGHT - 6;
      editorHeight.value = Math.max(MIN_EDITOR_HEIGHT, Math.min(newHeight, maxEditorHeight));
    };

    const onMouseUp = () => {
      isDragging.value = false;
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      if (editorView) {
        editorView.requestMeasure();
      }
    };

    const startResize = (e: MouseEvent) => {
      isDragging.value = true;
      resizeStartY = e.clientY;
      resizeStartHeight = editorHeight.value;
      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
      e.preventDefault();
    };

    /** 复制当前 SQL，便于在其它工具中复用。 */
    const copySql = async () => {
      const text = sqlCode.value || '';
      if (!text.trim()) {
        alertService.showWarning('请输入 SQL 语句');
        return;
      }
      try {
        if (navigator.clipboard?.writeText) {
          await navigator.clipboard.writeText(text);
        } else {
          const textarea = document.createElement('textarea');
          textarea.value = text;
          textarea.style.position = 'fixed';
          textarea.style.opacity = '0';
          document.body.appendChild(textarea);
          textarea.select();
          document.execCommand('copy');
          document.body.removeChild(textarea);
        }
        alertService.showSuccess('SQL 已复制');
      } catch {
        alertService.showError('复制失败，请手动选择复制');
      }
    };

    const describeLoadError = (err: any): string => {
      const status = err?.response?.status;
      const data = err?.response?.data;
      const raw = data?.detail || data?.message || data?.error || (status ? `请求失败（HTTP ${status}）` : err?.message) || '加载失败';
      const message = String(raw).replace(/\s+/g, ' ').trim();
      return message.length > 300 ? `${message.slice(0, 300)}…` : message;
    };

    const supportsCatalogTree = () => {
      const type = (dataSourceType.value || '').toUpperCase();
      return type.includes('DUCKDB') || type.includes('DUCKLAKE') || type.includes('POLARIS');
    };

    const buildSchemaNodes = (schemas: any[], catalog?: string) =>
      (Array.isArray(schemas) ? schemas : []).map((item: any) => {
        const schema = typeof item === 'string' ? item : (item.schema ?? item.schemaName ?? item.name ?? String(item));
        return {
          id: `schema:${catalog ? catalog + '.' : ''}${schema}`,
          label: schema,
          type: 'schema',
          name: schema,
          catalog,
          isLeaf: false,
        };
      });

    /**
     * 重置表搜索相关的临时状态（刷新目录时调用）。
     */
    const resetTableSearchState = () => {
      if (tableSearchTimer) {
        clearTimeout(tableSearchTimer);
        tableSearchTimer = null;
      }
      tableSearch.value = '';
      tableSearchHint.value = '';
      tableSearching.value = false;
      hoveredNodeId.value = '';
      expandedSchemas.clear();
      schemaLoadLimits.clear();
      searchTokens.clear();
    };

    const loadSchemas = async () => {
      resetTableSearchState();
      treeLoading.value = true;
      treeError.value = '';
      try {
        if (supportsCatalogTree()) {
          const catalogRes = await dataSourceService().getCatalogs(dataSourceId.value);
          const catalogs = (catalogRes.data || catalogRes || []) as any[];
          const catalogNames = (Array.isArray(catalogs) ? catalogs : [])
            .map((item: any) => (typeof item === 'string' ? item : (item.catalog ?? item.name)))
            .filter((name: any): name is string => typeof name === 'string' && name.length > 0);
          if (catalogNames.length > 0) {
            treeData.value = catalogNames.map((catalog: string) => ({
              id: `catalog:${catalog}`,
              label: catalog,
              type: 'catalog',
              name: catalog,
              isLeaf: false,
            }));
            return;
          }
        }

        const res = await dataSourceService().getSchemas(dataSourceId.value);
        treeData.value = buildSchemaNodes(res.data || res || []);
      } catch (err) {
        treeData.value = [];
        treeError.value = describeLoadError(err);
      } finally {
        treeLoading.value = false;
      }
    };

    const isSearchMode = () => tableSearch.value.trim().length > 0;

    /**
     * 构建表节点。
     */
    const buildTableNodes = (tables: any[], schemaNode: TableNode): TableNode[] =>
      (Array.isArray(tables) ? tables : []).map((table: any) => {
        const tableName = typeof table === 'string' ? table : (table.table ?? table.tableName ?? table.name ?? table);
        const catalog = table?.catalog ?? schemaNode.catalog;
        const schema = table?.schema ?? schemaNode.name;
        return {
          id: `table:${catalog ? catalog + '.' : ''}${schema}.${tableName}`,
          label: tableName,
          type: 'table',
          name: tableName,
          schema,
          catalog,
          isLeaf: true,
        };
      });

    /**
     * 追加分页/搜索提示伪节点：更多可点击加载，超出上限则只能搜索。
     */
    const buildTrailingNode = (
      schemaNode: TableNode,
      loadedCount: number,
      currentLimit: number,
      searchMode: boolean,
      hasMore: boolean,
    ): TableNode | null => {
      if (!hasMore) return null;
      if (searchMode) {
        return {
          id: `hint:${schemaNode.id}:search`,
          label: `仅显示前 ${TABLE_SEARCH_LIMIT} 条匹配结果，请细化关键字`,
          type: 'hint',
          name: '',
          schema: schemaNode.name,
          catalog: schemaNode.catalog,
          schemaNodeId: schemaNode.id,
          isLeaf: true,
        };
      }
      if (currentLimit >= TABLE_MAX_LOAD) {
        return {
          id: `hint:${schemaNode.id}:max`,
          label: `仅显示前 ${TABLE_MAX_LOAD} 张表，请输入表名搜索`,
          type: 'hint',
          name: '',
          schema: schemaNode.name,
          catalog: schemaNode.catalog,
          schemaNodeId: schemaNode.id,
          isLeaf: true,
        };
      }
      return {
        id: `more:${schemaNode.id}`,
        label: `加载更多（已显示 ${loadedCount} 张，最多 ${TABLE_MAX_LOAD} 张）`,
        type: 'more',
        name: '',
        schema: schemaNode.name,
        catalog: schemaNode.catalog,
        schemaNodeId: schemaNode.id,
        isLeaf: true,
      };
    };

    /**
     * 请求某个 schema 下的表：多请求 1 条用于判断是否还有更多数据。
     */
    const fetchSchemaTables = async (schemaNode: TableNode, limit: number, search?: string) => {
      const res = await dataSourceService().getTables(dataSourceId.value, schemaNode.name, limit + 1, search, schemaNode.catalog);
      const raw = (res.data || res || []) as any[];
      const tables = Array.isArray(raw) ? raw : [];
      const hasMore = tables.length > limit;
      return { tables: hasMore ? tables.slice(0, limit) : tables, hasMore };
    };

    const resolveSchemaLimit = (schemaNode: TableNode) => {
      const searchMode = isSearchMode();
      if (searchMode) return TABLE_SEARCH_LIMIT;
      return Math.min(schemaLoadLimits.get(schemaNode.id) ?? TABLE_PAGE_SIZE, TABLE_MAX_LOAD);
    };

    /**
     * 根据当前搜索/分页状态加载 schema 子节点。
     */
    const loadSchemaChildren = async (schemaNode: TableNode): Promise<TableNode[]> => {
      const searchMode = isSearchMode();
      const keyword = tableSearch.value.trim();
      const limit = resolveSchemaLimit(schemaNode);
      const { tables, hasMore } = await fetchSchemaTables(schemaNode, limit, searchMode ? keyword : undefined);
      if (!searchMode) {
        schemaLoadLimits.set(schemaNode.id, limit);
      }
      const nodes = buildTableNodes(tables, schemaNode);
      const trailing = buildTrailingNode(schemaNode, tables.length, limit, searchMode, hasMore);
      if (trailing) {
        nodes.push(trailing);
      }
      return nodes;
    };

    const updateSchemaChildren = (schemaNode: TableNode, nodes: TableNode[]) => {
      const tree = treeRef.value;
      if (!tree) return;
      tree.updateKeyChildren(schemaNode.id, nodes);
    };

    const loadTables = async (node: any, resolve: (data: any[]) => void) => {
      if (node.data?.type === 'catalog' && node.data?.name) {
        try {
          const res = await dataSourceService().getSchemas(dataSourceId.value, node.data.name);
          resolve(buildSchemaNodes(res.data || res || [], node.data.name));
        } catch (err: any) {
          if (err?.response) {
            alertService.showHttpError(err.response);
          } else {
            alertService.showError(describeLoadError(err));
          }
          resolve([]);
        }
        return;
      }

      if (node.data?.type === 'schema' && node.data?.name) {
        const schemaNode = node.data as TableNode;
        // 使仍在进行中的旧搜索请求失效，避免覆盖本次懒加载结果
        searchTokens.set(schemaNode.id, (searchTokens.get(schemaNode.id) ?? 0) + 1);
        try {
          resolve(await loadSchemaChildren(schemaNode));
        } catch (err: any) {
          if (err?.response) {
            alertService.showHttpError(err.response);
          } else {
            alertService.showError(describeLoadError(err));
          }
          resolve([]);
        }
        return;
      }

      resolve([]);
    };

    const handleNodeExpand = (data: TableNode) => {
      if (data?.type === 'schema') {
        expandedSchemas.set(data.id, data);
        if (isSearchMode()) {
          tableSearchHint.value = '';
        }
      }
    };

    const handleNodeCollapse = (data: TableNode) => {
      if (data?.type === 'schema') {
        expandedSchemas.delete(data.id);
        // 搜索结果只对当前关键字有效，收起后下次展开需按最新关键字重新加载
        if (isSearchMode()) {
          const elNode = treeRef.value?.getNode(data.id);
          if (elNode) {
            elNode.loaded = false;
          }
          if (expandedSchemas.size === 0) {
            tableSearchHint.value = '请先展开 schema 后搜索表';
          }
        }
      }
    };

    /**
     * 重新拉取单个已展开 schema 的子节点（搜索/加载更多共用）。
     */
    const refreshSchemaChildren = async (schemaNode: TableNode) => {
      const token = (searchTokens.get(schemaNode.id) ?? 0) + 1;
      searchTokens.set(schemaNode.id, token);
      try {
        const nodes = await loadSchemaChildren(schemaNode);
        if (searchTokens.get(schemaNode.id) !== token) return;
        updateSchemaChildren(schemaNode, nodes);
      } catch (err: any) {
        if (searchTokens.get(schemaNode.id) !== token) return;
        if (err?.response) {
          alertService.showHttpError(err.response);
        } else {
          alertService.showError(describeLoadError(err));
        }
      }
    };

    /**
     * 对所有已展开的 schema 应用当前搜索关键字（或恢复分页数据）。
     */
    const applyTableSearch = async () => {
      const schemas = Array.from(expandedSchemas.values());
      if (isSearchMode() && schemas.length === 0) {
        tableSearchHint.value = '请先展开 schema 后搜索表';
        return;
      }
      tableSearchHint.value = '';
      if (schemas.length === 0) return;
      tableSearching.value = true;
      try {
        await Promise.all(schemas.map(schema => refreshSchemaChildren(schema)));
      } finally {
        tableSearching.value = false;
      }
    };

    const onTableSearchInput = () => {
      if (tableSearchTimer) {
        clearTimeout(tableSearchTimer);
      }
      tableSearchTimer = setTimeout(() => {
        tableSearchTimer = null;
        applyTableSearch();
      }, TABLE_SEARCH_DEBOUNCE_MS);
    };

    const onTableSearchClear = () => {
      if (tableSearchTimer) {
        clearTimeout(tableSearchTimer);
        tableSearchTimer = null;
      }
      // el-input 的 clear 事件可能先于 model 更新，等一个 tick 再按空关键字恢复
      nextTick(() => {
        applyTableSearch();
      });
    };

    /**
     * 点击“加载更多”：提高该 schema 的分页上限后重新拉取。
     */
    const handleLoadMore = async (data: TableNode) => {
      if (data?.type !== 'more' || !data.schemaNodeId) return;
      const schemaNode = expandedSchemas.get(data.schemaNodeId);
      if (!schemaNode) return;
      const current = schemaLoadLimits.get(schemaNode.id) ?? TABLE_PAGE_SIZE;
      const next = Math.min(current + TABLE_PAGE_SIZE, TABLE_MAX_LOAD);
      schemaLoadLimits.set(schemaNode.id, next);
      const token = (searchTokens.get(schemaNode.id) ?? 0) + 1;
      searchTokens.set(schemaNode.id, token);
      try {
        const nodes = await loadSchemaChildren(schemaNode);
        if (searchTokens.get(schemaNode.id) !== token) return;
        updateSchemaChildren(schemaNode, nodes);
      } catch (err: any) {
        if (searchTokens.get(schemaNode.id) !== token) return;
        if (err?.response) {
          alertService.showHttpError(err.response);
        } else {
          alertService.showError(describeLoadError(err));
        }
      }
    };

    const getDialect = () => getSqlDialect(dataSourceType.value);

    const qualifiedName = (data: TableNode, quote: (id: string) => string) =>
      qualifiedTableName(quote, data.catalog, data.schema, data.name);

    const isDocumentDataSource = () => (dataSourceType.value || '').toUpperCase().includes('MONGO');

    const buildSelectSql = (catalog: string | undefined, schema: string | undefined, tableName: string, limit = 100) => {
      const dialect = getDialect();
      const baseSql = `SELECT * FROM ${qualifiedTableName(dialect.quote, catalog, schema, tableName)}`;
      return dialect.limit(baseSql, limit);
    };

    const buildMongoQuery = (schema: string | undefined, tableName: string, limit = 100) => {
      const envelope: Record<string, unknown> = { collection: tableName, filter: {}, limit };
      if (schema) {
        envelope.database = schema;
      }
      return JSON.stringify(envelope);
    };

    const buildQueryStatement = (catalog: string | undefined, schema: string | undefined, tableName: string, limit = 100) => {
      if (isDocumentDataSource()) {
        return buildMongoQuery(schema, tableName, limit);
      }
      return buildSelectSql(catalog, schema, tableName, limit);
    };

    const buildInsertSql = (data: TableNode, columns: ColumnInfo[]) => {
      const dialect = getDialect();
      const name = qualifiedName(data, dialect.quote);
      const columnList = columns.map(c => dialect.quote(c.name)).join(', ');
      const placeholders = columns.map(() => '?').join(', ');
      return `INSERT INTO ${name} (${columnList})\nVALUES (${placeholders});`;
    };

    const formatColumnType = (col: ColumnInfo) => {
      const type = (col.type || '').toUpperCase();
      const length = col.length || 0;
      const precision = col.precision || 0;
      const scale = col.scale || 0;
      if (length > 0 && (type.includes('CHAR') || type.includes('BINARY'))) {
        return `${col.type}(${length})`;
      }
      if (precision > 0 && (type.includes('DECIMAL') || type.includes('NUMERIC'))) {
        return scale > 0 ? `${col.type}(${precision}, ${scale})` : `${col.type}(${precision})`;
      }
      return col.type || 'VARCHAR';
    };

    const formatBytes = (bytes?: number | null) => {
      if (bytes === null || bytes === undefined || Number.isNaN(bytes)) return '-';
      if (bytes < 1024) return `${bytes} B`;
      const units = ['KB', 'MB', 'GB', 'TB', 'PB'];
      let value = bytes;
      let index = -1;
      do {
        value /= 1024;
        index += 1;
      } while (value >= 1024 && index < units.length - 1);
      const digits = value >= 100 ? 0 : value >= 10 ? 1 : 2;
      return `${value.toFixed(digits)} ${units[index]}`;
    };

    const formatCount = (value?: number | null) => {
      if (value === null || value === undefined || Number.isNaN(value)) return '-';
      return Number(value).toLocaleString('zh-CN');
    };

    const formatTimestamp = (ms?: number | null) => {
      if (!ms || ms <= 0) return '-';
      return new Date(ms).toLocaleString('zh-CN', { hour12: false });
    };

    const healthStatusText = (status?: string) => {
      if (status === 'HEALTHY') return '健康';
      if (status === 'WARNING') return '需关注';
      if (status === 'CRITICAL') return '有风险';
      return '未知';
    };

    const healthStatusTagType = (status?: string) => {
      if (status === 'HEALTHY') return 'success';
      if (status === 'WARNING') return 'warning';
      return 'danger';
    };

    const healthScoreColor = (score?: number) => {
      const value = score ?? 0;
      if (value >= 85) return '#67c23a';
      if (value >= 60) return '#e6a23c';
      return '#f56c6c';
    };

    const buildDdlSql = (data: TableNode, columns: ColumnInfo[]) => {
      const dialect = getDialect();
      const name = qualifiedName(data, dialect.quote);
      const lines = columns.map(col => {
        let line = `  ${dialect.quote(col.name)} ${formatColumnType(col)}`;
        if (col.nullable === false) {
          line += ' NOT NULL';
        }
        if (col.defaultValue !== undefined && col.defaultValue !== null && col.defaultValue !== '') {
          line += ` DEFAULT ${col.defaultValue}`;
        }
        if (col.comment) {
          line += ` COMMENT '${String(col.comment).replace(/'/g, "''")}'`;
        }
        return line;
      });
      return `CREATE TABLE ${name} (\n${lines.join(',\n')}\n);`;
    };

    const handleNodeDblClick = (data: any) => {
      if (data?.type === 'table') {
        openPreviewTab(data);
      }
    };

    const applyToEditor = (text: string) => {
      if (editorView) {
        editorView.dispatch({
          changes: { from: 0, to: editorView.state.doc.length, insert: text },
        });
        editorView.focus();
      } else {
        sqlCode.value = text;
      }
    };

    const copyToClipboard = async (text: string, successMessage: string) => {
      try {
        await navigator.clipboard.writeText(text);
        alertService.showSuccess(successMessage);
      } catch {
        alertService.showError('复制失败，请手动复制');
      }
    };

    const fetchColumns = async (data: TableNode): Promise<ColumnInfo[]> => {
      const res = await dataSourceService().getFields(dataSourceId.value, data.schema ?? '', data.name, data.catalog);
      const list = res.data || res || [];
      return Array.isArray(list) ? (list as ColumnInfo[]) : [];
    };

    const copyTableSql = async (data: TableNode, kind: 'select' | 'insert' | 'ddl') => {
      if (kind === 'select') {
        await copyToClipboard(buildQueryStatement(data.catalog, data.schema, data.name), 'SELECT 语句已复制');
        return;
      }
      try {
        const columns = await fetchColumns(data);
        if (!columns.length) {
          alertService.showWarning('未获取到表字段信息');
          return;
        }
        if (kind === 'insert') {
          await copyToClipboard(buildInsertSql(data, columns), 'INSERT 语句已复制');
        } else {
          await copyToClipboard(buildDdlSql(data, columns), 'CREATE TABLE 语句已复制');
        }
      } catch {
        alertService.showError('生成 SQL 失败');
      }
    };

    const loadPreview = async (tab: PreviewTab) => {
      tab.loading = true;
      tab.error = '';
      try {
        const sqlText = buildQueryStatement(tab.catalog, tab.schema, tab.table, tab.limit);
        const res = await dataQueryService().executeQuery(dataSourceId.value, sqlText);
        const columns = res.columns || res.columnNames || res.fields || [];
        const rows = res.rows || res.data || res.result || [];
        if (Array.isArray(columns) && columns.length > 0) {
          tab.columns = columns;
        } else if (Array.isArray(rows) && rows.length > 0) {
          tab.columns = Object.keys(rows[0]);
        } else {
          tab.columns = [];
        }
        tab.rows = Array.isArray(rows) ? rows : [];
      } catch (err: any) {
        tab.error = err.response?.data?.message || err.response?.data?.detail || err.message || '数据加载失败';
        tab.columns = [];
        tab.rows = [];
      } finally {
        tab.loading = false;
      }
    };

    const loadTableDetail = async (tab: PreviewTab) => {
      tab.metaLoading = true;
      tab.metaError = '';
      try {
        const res = await dataSourceService().getTableDetail(dataSourceId.value, tab.schema, tab.table, tab.catalog);
        tab.detail = res.data || res || null;
      } catch (err: any) {
        tab.metaError = err.response?.data?.message || err.response?.data?.detail || err.message || '获取表信息失败';
        tab.detail = null;
      } finally {
        tab.metaLoading = false;
      }
    };

    const loadTableHealth = async (tab: PreviewTab) => {
      if (tab.healthLoading) return;
      tab.healthLoading = true;
      tab.healthError = '';
      try {
        const res = await dataSourceService().getTableHealth(dataSourceId.value, tab.schema, tab.table, tab.catalog);
        tab.health = res.data || res || null;
        tab.healthLoaded = true;
      } catch (err: any) {
        tab.healthError = err.response?.data?.message || err.response?.data?.detail || err.message || '获取表健康信息失败';
        tab.health = null;
        tab.healthLoaded = false;
      } finally {
        tab.healthLoading = false;
      }
    };

    const loadMaintenanceActions = async (tab: PreviewTab) => {
      try {
        const res = await dataSourceService().getTableMaintenanceActions(dataSourceId.value, tab.schema, tab.table, tab.catalog);
        tab.maintenanceActions = res.data?.actions || [];
      } catch {
        tab.maintenanceActions = [];
      }
    };

    const loadCatalogMaintenanceActions = async () => {
      try {
        const res = await dataSourceService().getCatalogMaintenanceActions(dataSourceId.value);
        catalogMaintenanceActions.value = res.data?.actions || [];
      } catch {
        catalogMaintenanceActions.value = [];
      }
    };

    const maintenanceActionsForScope = (tab: PreviewTab, scope: string) =>
      (tab.maintenanceActions || []).filter(action => (action.scope || 'TABLE') === scope);

    const tableMaintenanceActions = (tab: PreviewTab) => maintenanceActionsForScope(tab, 'TABLE');

    const hasAvailableMaintenanceAction = (actions: TableMaintenanceAction[]) => actions.some(action => action.available !== false);

    const handlePreviewSubTabChange = (tab: PreviewTab, name: string | number) => {
      if (String(name) === 'health' && !tab.healthLoaded && !tab.healthLoading) {
        loadTableHealth(tab);
      }
    };

    const openMaintenance = (tab: PreviewTab, action: TableMaintenanceAction) => {
      if (!action || action.available === false) return;
      const params: Record<string, any> = {};
      (action.params || []).forEach(param => {
        params[param.name] = param.default;
      });
      maintenanceDialog.value = {
        visible: true,
        tab,
        action,
        params,
        running: false,
        result: null,
      };
    };

    const openCatalogMaintenance = (action: TableMaintenanceAction) => {
      if (!action || action.available === false) return;
      const params: Record<string, any> = {};
      (action.params || []).forEach(param => {
        params[param.name] = param.default;
      });
      maintenanceDialog.value = {
        visible: true,
        tab: null,
        action,
        params,
        running: false,
        result: null,
      };
    };

    const closeMaintenance = () => {
      maintenanceDialog.value.visible = false;
    };

    const runMaintenance = async () => {
      const state = maintenanceDialog.value;
      if (!state.action || state.running) return;
      const action = state.action;
      const isCatalogAction = action.scope === 'CATALOG';
      if (!isCatalogAction && !state.tab) {
        alertService.showError('请先打开表详情后再执行表级运维操作');
        return;
      }
      state.running = true;
      state.result = null;
      try {
        const res = isCatalogAction
          ? await dataSourceService().executeCatalogMaintenance(dataSourceId.value, action.id, state.params)
          : await dataSourceService().executeTableMaintenance(
              dataSourceId.value,
              state.tab!.schema,
              state.tab!.table,
              action.id,
              state.params,
              state.tab!.catalog,
            );
        const result = (res.data || res) as TableMaintenanceResult;
        state.result = result;
        if (result?.success) {
          alertService.showSuccess(result.message || '运维操作已完成');
          if (isCatalogAction) {
            await loadCatalogMaintenanceActions();
          } else if (state.tab) {
            await loadMaintenanceActions(state.tab);
            if (state.tab.healthLoaded || state.tab.subTab === 'health') {
              await loadTableHealth(state.tab);
            }
          }
        } else {
          alertService.showError(result?.message || '运维操作失败');
        }
      } catch (err: any) {
        const message = err.response?.data?.message || err.response?.data?.detail || err.message || '运维操作失败';
        state.result = { success: false, message, rows: [] };
        alertService.showError(message);
      } finally {
        state.running = false;
      }
    };

    const formatMaintenanceRows = (rows?: any[]) => {
      if (!rows || rows.length === 0) return '';
      return JSON.stringify(rows.slice(0, 100), null, 2);
    };

    const refreshPreview = (tab: PreviewTab) => {
      loadTableDetail(tab);
      loadPreview(tab);
      loadMaintenanceActions(tab);
      if (tab.subTab === 'health') {
        loadTableHealth(tab);
      }
    };

    const openPreviewTab = (data: TableNode) => {
      const key = `preview:${data.catalog ? data.catalog + '.' : ''}${data.schema}.${data.name}`;
      let tab = previewTabs.value.find(t => t.key === key);
      if (!tab) {
        previewTabs.value.push({
          key,
          title: data.name,
          table: data.name,
          schema: data.schema ?? '',
          catalog: data.catalog,
          limit: 100,
          loading: false,
          columns: [],
          rows: [],
          error: '',
          metaLoading: false,
          metaError: '',
          detail: null,
          subTab: 'columns',
          health: null,
          healthLoading: false,
          healthError: '',
          healthLoaded: false,
          maintenanceActions: [],
        });
        tab = previewTabs.value[previewTabs.value.length - 1];
        loadTableDetail(tab);
        loadPreview(tab);
        loadMaintenanceActions(tab);
      }
      activeTab.value = key;
    };

    const previewTable = (data: TableNode) => {
      if (data?.type !== 'table') return;
      closeContextMenu();
      openPreviewTab(data);
    };

    const openSelectInEditor = (tab: PreviewTab) => {
      applyToEditor(buildQueryStatement(tab.catalog, tab.schema, tab.table, 100));
      activeTab.value = 'sql';
    };

    const copyPreviewSelect = (tab: PreviewTab) => {
      copyToClipboard(buildQueryStatement(tab.catalog, tab.schema, tab.table, tab.limit), 'SELECT 语句已复制');
    };

    const handleTabRemove = (name: string | number) => {
      const key = String(name);
      const idx = previewTabs.value.findIndex(t => t.key === key);
      if (idx >= 0) {
        previewTabs.value.splice(idx, 1);
      }
      if (activeTab.value === key) {
        activeTab.value = 'sql';
      }
    };

    const openContextMenu = (e: MouseEvent, data: any) => {
      if (data?.type !== 'table') return;
      const menuWidth = 220;
      const menuHeight = 260;
      const x = Math.min(e.clientX, window.innerWidth - menuWidth - 8);
      const y = Math.min(e.clientY, window.innerHeight - menuHeight - 8);
      contextMenu.value = {
        visible: true,
        x: Math.max(8, x),
        y: Math.max(8, y),
        data: data as TableNode,
      };
    };

    const closeContextMenu = () => {
      contextMenu.value.visible = false;
    };

    const handleTableCommand = async (command: string, data: TableNode | null) => {
      closeContextMenu();
      if (!data) return;
      if (command === 'preview') {
        openPreviewTab(data);
        return;
      }
      if (command === 'toEditor') {
        applyToEditor(buildQueryStatement(data.catalog, data.schema, data.name, 100));
        activeTab.value = 'sql';
        return;
      }
      if (command === 'select' || command === 'insert' || command === 'ddl') {
        await copyTableSql(data, command);
      }
    };

    const getFormatterDialect = (): string => {
      const type = (dataSourceType.value || '').toUpperCase();
      if (type.includes('MYSQL') || type.includes('TIDB') || type.includes('DORIS') || type.includes('TDENGINE')) {
        return 'mysql';
      }
      if (type.includes('CLICKHOUSE')) {
        return 'clickhouse';
      }
      if (
        type.includes('POSTGRESQL') ||
        type.includes('DUCKDB') ||
        type.includes('DUCKLAKE') ||
        type.includes('POLARIS') ||
        type.includes('GREENPLUM') ||
        type.includes('YMATRIX') ||
        type.includes('KINGBASE')
      ) {
        return 'postgresql';
      }
      if (type.includes('SQLSERVER') || type.includes('MSSQL')) {
        return 'transactsql';
      }
      if (type.includes('ORACLE')) {
        return 'plsql';
      }
      return 'postgresql';
    };

    const formatSql = () => {
      const rawSql = getActiveSql();
      if (!rawSql) {
        alertService.showWarning('请输入 SQL 语句');
        return;
      }
      try {
        const formatted = format(rawSql, {
          language: getFormatterDialect(),
          keywordCase: 'upper',
          linesBetweenQueries: 2,
        });
        if (editorView) {
          const sel = editorView.state.selection.main;
          if (!sel.empty) {
            editorView.dispatch({
              changes: { from: sel.from, to: sel.to, insert: formatted },
            });
          } else {
            editorView.dispatch({
              changes: { from: 0, to: editorView.state.doc.length, insert: formatted },
            });
          }
        } else {
          sqlCode.value = formatted;
        }
      } catch {
        alertService.showError('SQL 格式化失败，请检查语法');
      }
    };

    const formatTime = (ms: number | null) => {
      if (ms === null) return '-';
      if (ms < 1000) return `${ms}ms`;
      return `${(ms / 1000).toFixed(2)}s`;
    };

    /**
     * 接收 AI 助手生成的 SQL，写入编辑器并聚焦。
     */
    const applyAiSql = (sql: string) => {
      if (!sql) return;
      if (editorView) {
        editorView.dispatch({
          changes: { from: 0, to: editorView.state.doc.length, insert: sql },
        });
        editorView.focus();
      } else {
        sqlCode.value = sql;
      }
      activeTab.value = 'sql';
    };

    const onDocumentClick = () => {
      if (contextMenu.value.visible) {
        closeContextMenu();
      }
    };

    const onEscKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        closeContextMenu();
      }
    };

    watch(activeTab, async val => {
      if (val === 'sql') {
        await nextTick();
        editorView?.requestMeasure();
      }
    });

    onMounted(async () => {
      try {
        const ds = await dataSourceService().find(dataSourceId.value);
        dataSourceName.value = ds.name || '';
        dataSourceType.value = ds.type || '';
      } catch {
        // ignore
      }
      loadCatalogMaintenanceActions();
      initEditor();
      await loadSchemas();
      document.addEventListener('click', onDocumentClick);
      document.addEventListener('contextmenu', onDocumentClick);
      document.addEventListener('keydown', onEscKeydown);
      window.addEventListener('resize', closeContextMenu);
    });

    onBeforeUnmount(() => {
      destroyEditor();
      if (tableSearchTimer) {
        clearTimeout(tableSearchTimer);
        tableSearchTimer = null;
      }
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      document.removeEventListener('click', onDocumentClick);
      document.removeEventListener('contextmenu', onDocumentClick);
      document.removeEventListener('keydown', onEscKeydown);
      window.removeEventListener('resize', closeContextMenu);
    });

    return {
      dataSourceId,
      dataSourceName,
      dataSourceType,
      editorContainer,
      editorHeight,
      isDragging,
      treeRef,
      treeData,
      treeLoading,
      treeError,
      tableSearch,
      tableSearching,
      tableSearchHint,
      hoveredNodeId,
      sqlCode,
      resultColumns,
      resultData,
      queryError,
      executionTime,
      affectedRows,
      hasExecuted,
      executedSuccessfully,
      executedSql,
      activeResultTab,
      executionStatusLabel,
      executionStatusType,
      queryLimitOptions,
      queryLimit,
      resultTruncated,
      resultRowLimit,
      isExecuting,
      aiDrawerVisible,
      activeTab,
      previewTabs,
      contextMenu,
      applyAiSql,
      startResize,
      copySql,
      handleNodeDblClick,
      loadSchemas,
      loadTables,
      handleNodeExpand,
      handleNodeCollapse,
      onTableSearchInput,
      onTableSearchClear,
      handleLoadMore,
      executeQuery,
      generateDataset,
      formatSql,
      formatTime,
      previewTable,
      loadPreview,
      loadTableDetail,
      loadTableHealth,
      handlePreviewSubTabChange,
      refreshPreview,
      formatBytes,
      formatCount,
      formatTimestamp,
      healthStatusText,
      healthStatusTagType,
      healthScoreColor,
      maintenanceDialog,
      openMaintenance,
      openCatalogMaintenance,
      closeMaintenance,
      runMaintenance,
      formatMaintenanceRows,
      tableMaintenanceActions,
      catalogMaintenanceActions,
      hasAvailableMaintenanceAction,
      handleTabRemove,
      openContextMenu,
      handleTableCommand,
      copyTableSql,
      copyPreviewSelect,
      openSelectInEditor,
      formatColumnType,
    };
  },
});
