import { type PropType, computed, defineComponent, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import DataSourceService from '@/entities/data-source/data-source.service';
import ETLTaskDirectoryService from '@/entities/etl-task/etl-task-directory.service';
import SqlJobService from './sql-job.service';
import { type IJob } from '@/shared/model/job.model';
import { type IDataSource } from '@/shared/model/data-source.model';
import { type IETLTaskDirectory } from '@/shared/model/etl-task-directory.model';
import { useAlertService } from '@/shared/alert/alert.service';
import DataQueryAi from '@/entities/data-source/data-query-ai.vue';
import { getSqlDialect, qualifiedTableName } from '@/shared/sql/sql-dialect';

import { EditorState } from '@codemirror/state';
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from '@codemirror/view';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { sql, MySQL, PostgreSQL, StandardSQL } from '@codemirror/lang-sql';
import { autocompletion, completionKeymap, startCompletion } from '@codemirror/autocomplete';
import { oneDark } from '@codemirror/theme-one-dark';
import { format } from 'sql-formatter';

const DIRECTORY_TASK_TYPE = 'SQL';

interface IDirectoryOption {
  id: string;
  label: string;
  children?: IDirectoryOption[];
}

export default defineComponent({
  name: 'SqlJobEditor',
  components: {
    DataQueryAi,
  },
  props: {
    jobId: {
      type: String,
      default: null,
    },
    directoryId: {
      type: String,
      default: null,
    },
    directories: {
      type: Array as PropType<IETLTaskDirectory[]>,
      default: () => [],
    },
    standalone: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['saved'],
  setup(props, { emit }) {
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const sqlJobService = inject('sqlJobService', () => new SqlJobService());
    const etlTaskDirectoryService = inject('etlTaskDirectoryService', () => new ETLTaskDirectoryService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const isEditMode = computed(() => !!props.jobId);
    const isSaving = ref(false);

    const sqlJob = ref<IJob>({
      jobName: '',
      cron: '',
      status: 'OFFLINE',
      directoryId: props.directoryId || null,
    });

    const scheduleStatusLabel = computed(() => (sqlJob.value.status === 'ONLINE' ? '已上线' : '未上线'));
    const scheduleStatusType = computed(() => (sqlJob.value.status === 'ONLINE' ? 'success' : 'info'));

    const dataSources = ref<IDataSource[]>([]);
    const dataSourceId = ref<string | null>(null);
    const dataSourceType = ref('');
    const selectedSchema = ref<string | null>(null);

    const localDirectories = ref<IETLTaskDirectory[]>([]);
    const directories = computed<IETLTaskDirectory[]>(() =>
      props.directories && props.directories.length > 0 ? props.directories : localDirectories.value,
    );

    const directoryTreeOptions = computed<IDirectoryOption[]>(() => {
      const byParent = new Map<string, IETLTaskDirectory[]>();
      directories.value.forEach(directory => {
        const parentId = directory.parentId || '';
        if (!byParent.has(parentId)) {
          byParent.set(parentId, []);
        }
        byParent.get(parentId)!.push(directory);
      });
      const build = (parentId: string): IDirectoryOption[] =>
        (byParent.get(parentId) || [])
          .slice()
          .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0) || String(a.name || '').localeCompare(String(b.name || '')))
          .map(directory => {
            const children = build(directory.id!);
            return {
              id: directory.id!,
              label: directory.name || '',
              children: children.length ? children : undefined,
            };
          });
      return build('');
    });

    const treeRef = ref();
    const treeData = ref<any[]>([]);
    const treeLoading = ref(false);

    const sqlCode = ref('');
    const editorContainer = ref<HTMLElement | null>(null);
    const queryMainRef = ref<HTMLElement | null>(null);
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
    const isExecuting = ref(false);
    const hasExecuted = ref(false);
    const executedSuccessfully = ref(false);
    const executedSql = ref('');
    const activeResultTab = ref('result');
    const aiDrawerVisible = ref(false);

    /**
     * 结果区在没有结果集时的占位文案：DDL/DML 执行成功后也应给出明确反馈，
     * 而不是继续显示「点击调试运行」的初始提示。
     */
    const resultPlaceholder = computed(() => {
      if (!hasExecuted.value) {
        return '点击「调试运行」执行 SQL';
      }
      if (!executedSuccessfully.value) {
        return 'SQL 执行失败';
      }
      if (affectedRows.value !== null && affectedRows.value > 0) {
        return `执行成功，影响 ${affectedRows.value} 行`;
      }
      return '执行成功';
    });

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

    const getActiveSql = (): string => {
      if (!editorView) return sqlCode.value.trim();
      const sel = editorView.state.selection.main;
      if (!sel.empty) {
        const selected = editorView.state.doc.sliceString(sel.from, sel.to).trim();
        if (selected) return selected;
      }
      return sqlCode.value.trim();
    };

    const executeDebug = async () => {
      if (!dataSourceId.value) {
        alertService.showWarning('请先选择数据源');
        return;
      }
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

      const startTime = performance.now();
      try {
        const res = await sqlJobService().debugQuery(dataSourceId.value, activeSql);
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
        affectedRows.value = res.affectedRows ?? (Array.isArray(rows) ? rows.length : null);
      } catch (err: any) {
        const elapsed = Math.round(performance.now() - startTime);
        executionTime.value = elapsed;
        queryError.value = err.response?.data?.message || err.response?.data?.detail || err.message || 'SQL 执行失败';
        activeResultTab.value = 'log';
      } finally {
        isExecuting.value = false;
      }
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
            executeDebug();
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
      return queryMainRef.value ? queryMainRef.value.clientHeight : 600;
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

    const loadDataSources = async () => {
      try {
        const res = await dataSourceService().retrieve({
          page: 0,
          size: 1000,
          sort: ['name,asc'],
        });
        dataSources.value = res.data || [];
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const loadDirectories = async () => {
      if (props.directories && props.directories.length > 0) {
        return;
      }
      try {
        const res = await etlTaskDirectoryService().retrieve(DIRECTORY_TASK_TYPE);
        localDirectories.value = res.data || [];
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    watch(
      () => props.directories,
      value => {
        localDirectories.value = (value || []) as IETLTaskDirectory[];
      },
    );

    // 新建任务时，左侧目录切换后同步更新任务归属目录。
    watch(
      () => props.directoryId,
      value => {
        if (!props.jobId) {
          sqlJob.value.directoryId = value || null;
        }
      },
    );

    const loadDataSourceMeta = async () => {
      if (!dataSourceId.value) {
        dataSourceType.value = '';
        return;
      }
      try {
        const ds = await dataSourceService().find(dataSourceId.value);
        dataSourceType.value = ds.type || '';
      } catch {
        // ignore
      }
    };

    const loadSchemas = async () => {
      treeData.value = [];
      selectedSchema.value = null;
      if (!dataSourceId.value) {
        return;
      }
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

    const loadTables = async (node: any, resolve: (nodes: any[]) => void) => {
      if (node.data?.type === 'schema' && node.data?.name && dataSourceId.value) {
        try {
          const res = await dataSourceService().getTables(dataSourceId.value, node.data.name);
          const tables = res.data || res || [];
          const tableNodes = tables.map((table: any) => {
            const tableName = typeof table === 'string' ? table : (table.table ?? table.tableName ?? table.name ?? table);
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

    const handleNodeClick = (data: any) => {
      if (data?.type === 'schema') {
        selectedSchema.value = data.name;
      } else if (data?.type === 'table') {
        selectedSchema.value = data.schema || null;
      }
    };

    const onDataSourceChange = async (value: string | null) => {
      dataSourceId.value = value;
      resultColumns.value = [];
      resultData.value = [];
      queryError.value = '';
      await loadDataSourceMeta();
      await loadSchemas();
    };

    const getDialect = () => getSqlDialect(dataSourceType.value);

    const buildSelectSql = (catalog: string | undefined, schema: string | undefined, tableName: string, limit = 10) => {
      const dialect = getDialect();
      const rawSql = `SELECT * FROM ${qualifiedTableName(dialect.quote, catalog, schema, tableName)}`;
      return dialect.limit(rawSql, limit);
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
              changes: {
                from: 0,
                to: editorView.state.doc.length,
                insert: formatted,
              },
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

    const openAiDrawer = () => {
      if (!dataSourceId.value) {
        alertService.showWarning('请先选择数据源');
        return;
      }
      aiDrawerVisible.value = true;
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
    };

    const parseJobContext = (jobContext?: string | null): void => {
      if (!jobContext) return;
      try {
        const config = typeof jobContext === 'string' ? JSON.parse(jobContext) : jobContext;
        if (config.dataSourceId != null) {
          dataSourceId.value = String(config.dataSourceId);
        }
        if (config.schema) {
          selectedSchema.value = config.schema;
        }
        if (config.sql) {
          sqlCode.value = config.sql;
        }
      } catch (error) {
        console.error('解析 SQL 任务配置失败', error);
      }
    };

    const loadSqlJob = async () => {
      if (!props.jobId) {
        sqlJob.value = {
          jobName: '',
          cron: '',
          status: 'OFFLINE',
          directoryId: props.directoryId || null,
        };
        sqlCode.value = 'SELECT 1;';
        return;
      }
      try {
        const job = await sqlJobService().find(String(props.jobId));
        sqlJob.value = {
          ...job,
          directoryId: job.directoryId ?? null,
        };
        parseJobContext(job.jobContext);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const buildJobContext = (): string =>
      JSON.stringify({
        dataSourceId: dataSourceId.value,
        schema: selectedSchema.value || null,
        sql: sqlCode.value.trim(),
      });

    const save = async () => {
      if (!sqlJob.value.jobName || !sqlJob.value.jobName.trim()) {
        alertService.showError('请输入任务名称');
        return;
      }
      if (!dataSourceId.value) {
        alertService.showError('请选择数据源');
        return;
      }
      if (!sqlCode.value.trim()) {
        alertService.showError('请输入 SQL 语句');
        return;
      }

      isSaving.value = true;
      const jobContext = buildJobContext();
      try {
        let saved: IJob;
        if (isEditMode.value) {
          const entity: IJob = {
            ...sqlJob.value,
            jobName: sqlJob.value.jobName.trim(),
            type: 'SQL',
            cron: sqlJob.value.cron || '',
            directoryId: sqlJob.value.directoryId || null,
            jobContext,
            updateTime: new Date(),
          };
          saved = await sqlJobService().update(entity);
          alertService.showSuccess('SQL 任务保存成功');
        } else {
          const entity: IJob = {
            jobName: sqlJob.value.jobName.trim(),
            jobGroup: 'datafusion',
            type: 'SQL',
            cron: sqlJob.value.cron || '',
            status: 'OFFLINE',
            directoryId: sqlJob.value.directoryId || null,
            jobContext,
            createTime: new Date(),
            updateTime: new Date(),
          };
          saved = await sqlJobService().create(entity);
          alertService.showSuccess('SQL 任务创建成功');
        }
        emit('saved', saved);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      } finally {
        isSaving.value = false;
      }
    };

    onMounted(async () => {
      await Promise.all([loadDataSources(), loadDirectories()]);
      await loadSqlJob();
      if (dataSourceId.value) {
        await loadDataSourceMeta();
        await loadSchemas();
      }
      initEditor();
    });

    onBeforeUnmount(() => {
      destroyEditor();
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    });

    return {
      isEditMode,
      isSaving,
      sqlJob,
      scheduleStatusLabel,
      scheduleStatusType,
      dataSources,
      dataSourceId,
      selectedSchema,
      directoryTreeOptions,
      treeRef,
      treeData,
      treeLoading,
      sqlCode,
      editorContainer,
      queryMainRef,
      editorHeight,
      isDragging,
      hasSelection,
      resultColumns,
      resultData,
      resultPlaceholder,
      queryError,
      executionTime,
      affectedRows,
      hasExecuted,
      executedSuccessfully,
      executedSql,
      executionStatusLabel,
      executionStatusType,
      activeResultTab,
      isExecuting,
      aiDrawerVisible,
      startResize,
      handleNodeClick,
      handleNodeDblClick,
      loadTables,
      onDataSourceChange,
      executeDebug,
      formatSql,
      formatTime,
      openAiDrawer,
      applyAiSql,
      save,
    };
  },
});
