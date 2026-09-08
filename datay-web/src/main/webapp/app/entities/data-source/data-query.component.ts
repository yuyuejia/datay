import { defineComponent, inject, onMounted, ref, onBeforeUnmount, computed } from 'vue';
import { useRoute } from 'vue-router';
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

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'DataQuery',
  setup() {
    const route = useRoute();
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const dataQueryService = inject('dataQueryService', () => new DataQueryService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const dataSourceId = computed(() => Number(route.params.dataSourceId));
    const dataSourceName = ref('');
    const dataSourceType = ref('');
    const isExecuting = ref(false);

    const treeRef = ref();
    const treeData = ref<any[]>([]);
    const treeLoading = ref(false);

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
      } catch (err: any) {
        const elapsed = Math.round(performance.now() - startTime);
        executionTime.value = elapsed;
        queryError.value = err.response?.data?.message || err.response?.data?.detail || err.message || '查询执行失败';
      } finally {
        isExecuting.value = false;
      }
    };

    const getSqlDialectSpec = () => {
      const type = (dataSourceType.value || '').toUpperCase();
      if (type.includes('MYSQL') || type.includes('DORIS') || type.includes('CLICKHOUSE')) {
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
      const el = document.querySelector('.query-main') as HTMLElement | null;
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

    const loadSchemas = async () => {
      treeLoading.value = true;
      try {
        const res = await dataSourceService().getSchemas(dataSourceId.value);
        const schemas = res.data || res || [];
        treeData.value = schemas.map((schema: string) => ({
          id: `schema:${schema}`,
          label: schema,
          type: 'schema',
          name: schema,
          isLeaf: false,
        }));
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        treeLoading.value = false;
      }
    };

    const loadTables = async (node: any, resolve: Function) => {
      if (node.data?.type === 'schema' && node.data?.name) {
        try {
          const res = await dataSourceService().getTables(dataSourceId.value, node.data.name);
          const tables = res.data || res || [];
          const tableNodes = tables.map((table: any) => {
            const tableName = typeof table === 'string' ? table : table.table ?? table.tableName ?? table.name ?? table;
            const catalog = table?.catalog;
            const schema = table?.schema ?? node.data.name;
            return {
              id: `table:${schema}.${tableName}`,
              label: tableName,
              type: 'table',
              name: tableName,
              schema: schema,
              catalog: catalog,
              isLeaf: true,
            };
          });
          resolve(tableNodes);
        } catch (err) {
          alertService.showHttpError(err.response);
          resolve([]);
        }
      } else {
        resolve([]);
      }
    };

    const getSqlDialect = () => {
      const type = (dataSourceType.value || '').toUpperCase();
      const backtickTypes = ['MYSQL', 'DORIS', 'CLICKHOUSE'];
      const bracketTypes = ['SQLSERVER', 'SQL_SERVER', 'MSSQL'];
      const isBacktick = backtickTypes.some(t => type.includes(t));
      const isBracket = bracketTypes.some(t => type.includes(t));
      const isOracle = type.includes('ORACLE');

      return {
        quote: (identifier: string) => {
          if (isBacktick) return `\`${identifier}\``;
          if (isBracket) return `[${identifier}]`;
          return `"${identifier}"`;
        },
        limit: (sql: string, limit: number) => {
          if (isOracle) {
            return `${sql} FETCH FIRST ${limit} ROWS ONLY;`;
          }
          return `${sql} LIMIT ${limit};`;
        },
      };
    };

    const buildSelectSql = (catalog: string | undefined, schema: string | undefined, tableName: string, limit = 10) => {
      const dialect = getSqlDialect();
      const catalogPart = catalog ? `${dialect.quote(catalog)}.` : '';
      const schemaPart = schema ? `${dialect.quote(schema)}.` : '';
      const sql = `SELECT * FROM ${catalogPart}${schemaPart}${dialect.quote(tableName)}`;
      return dialect.limit(sql, limit);
    };

    const handleNodeDblClick = (data: any) => {
      if (data?.type === 'table') {
        const insertSql = buildSelectSql(data.catalog, data.schema, data.name);
        if (editorView) {
          editorView.dispatch({
            changes: {
              from: editorView.state.selection.main.head,
              insert: insertSql,
            },
          });
        } else {
          sqlCode.value += insertSql;
        }
      }
    };

    const getFormatterDialect = (): string => {
      const type = (dataSourceType.value || '').toUpperCase();
      if (type.includes('MYSQL') || type.includes('DORIS')) {
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
    });

    onBeforeUnmount(() => {
      destroyEditor();
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    });

    return {
      dataSourceId,
      dataSourceName,
      editorContainer,
      editorHeight,
      isDragging,
      treeRef,
      treeData,
      treeLoading,
      sqlCode,
      hasSelection,
      resultColumns,
      resultData,
      queryError,
      executionTime,
      affectedRows,
      isExecuting,
      startResize,
      handleNodeDblClick,
      loadTables,
      executeQuery,
      formatSql,
      formatTime,
    };
  },
});