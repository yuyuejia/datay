import { defineComponent, inject, onMounted, ref, onBeforeUnmount, computed, nextTick, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import DataSourceService from './data-source.service';
import DataQueryService from './data-query.service';
import { useAlertService } from '@/shared/alert/alert.service';

import { EditorState } from '@codemirror/state';
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from '@codemirror/view';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { sql, MySQL, PostgreSQL, StandardSQL } from '@codemirror/lang-sql';
import { autocompletion, completionKeymap, startCompletion } from '@codemirror/autocomplete';
import { oneDark } from '@codemirror/theme-one-dark';
import { format } from 'sql-formatter';
import DataQueryAi from './data-query-ai.vue';
import TableLineage from './TableLineage.vue';
import { getSqlDialect } from '@/shared/sql/sql-dialect';

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
    const hasSelection = ref(false);
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

    const activeTab = ref('sql');
    const previewTabs = ref<PreviewTab[]>([]);

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

      const startTime = performance.now();

      try {
        const res = await dataQueryService().executeQuery(dataSourceId.value, activeSql);
        const elapsed = Math.round(performance.now() - startTime);
        executionTime.value = elapsed;

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

        // DuckDB 的 ATTACH / DETACH 会改变 catalog 结构，执行后立即刷新左侧目录
        if (/\b(ATTACH|DETACH)\b/i.test(activeSql)) {
          await loadSchemas();
        }
      } catch (err: any) {
        const elapsed = Math.round(performance.now() - startTime);
        executionTime.value = elapsed;
        queryError.value = err.response?.data?.message || err.response?.data?.detail || err.message || '查询执行失败';
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
      if (type.includes('POSTGRESQL') || type.includes('DUCKDB') || type.includes('DUCKLAKE') || type.includes('GREENPLUM')) {
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
        if (v.selectionSet) {
          const sel = v.state.selection.main;
          hasSelection.value = !sel.empty && v.state.doc.sliceString(sel.from, sel.to).trim().length > 0;
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
          lineNumbers(),
          highlightActiveLine(),
          highlightActiveLineGutter(),
          history(),
          keymap.of([...defaultKeymap, ...historyKeymap, ...completionKeymap]),
          runQueryKeymap,
          sql({ dialect: getSqlDialectSpec() }),
          autocompletion({
            activateOnTyping: true,
            defaultKeymap: false,
          }),
          oneDark,
          updateListener,
          EditorState.tabSize.of(2),
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

    const describeLoadError = (err: any): string => {
      const status = err?.response?.status;
      const data = err?.response?.data;
      const raw = data?.detail || data?.message || data?.error || (status ? `请求失败（HTTP ${status}）` : err?.message) || '加载失败';
      const message = String(raw).replace(/\s+/g, ' ').trim();
      return message.length > 300 ? `${message.slice(0, 300)}…` : message;
    };

    const supportsCatalogTree = () => {
      const type = (dataSourceType.value || '').toUpperCase();
      return type.includes('DUCKDB') || type.includes('DUCKLAKE');
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

    const qualifiedName = (data: TableNode, quote: (id: string) => string) => {
      const parts: string[] = [];
      if (data.catalog) parts.push(quote(data.catalog));
      if (data.schema) parts.push(quote(data.schema));
      parts.push(quote(data.name));
      return parts.join('.');
    };

    const isDocumentDataSource = () => (dataSourceType.value || '').toUpperCase().includes('MONGO');

    const buildSelectSql = (catalog: string | undefined, schema: string | undefined, tableName: string, limit = 100) => {
      const dialect = getDialect();
      const catalogPart = catalog ? `${dialect.quote(catalog)}.` : '';
      const schemaPart = schema ? `${dialect.quote(schema)}.` : '';
      const baseSql = `SELECT * FROM ${catalogPart}${schemaPart}${dialect.quote(tableName)}`;
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

    const refreshPreview = (tab: PreviewTab) => {
      loadTableDetail(tab);
      loadPreview(tab);
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
        });
        tab = previewTabs.value[previewTabs.value.length - 1];
        loadTableDetail(tab);
        loadPreview(tab);
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
      if (type.includes('POSTGRESQL') || type.includes('DUCKDB') || type.includes('DUCKLAKE') || type.includes('GREENPLUM')) {
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
      hasSelection,
      resultColumns,
      resultData,
      queryError,
      executionTime,
      affectedRows,
      isExecuting,
      aiDrawerVisible,
      activeTab,
      previewTabs,
      contextMenu,
      applyAiSql,
      startResize,
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
      refreshPreview,
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
