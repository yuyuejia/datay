import {
  defineComponent,
  inject,
  onMounted,
  ref,
  onBeforeUnmount,
  computed,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import DataSourceService from "@/entities/data-source/data-source.service";
import SqlJobService from "./sql-job.service";
import { type IJob } from "@/shared/model/job.model";
import { type IDataSource } from "@/shared/model/data-source.model";
import { useAlertService } from "@/shared/alert/alert.service";
import CronExpressionSelector from "@/components/CronExpressionSelector.vue";
import DataQueryAi from "@/entities/data-source/data-query-ai.vue";

import { EditorState } from "@codemirror/state";
import {
  EditorView,
  keymap,
  lineNumbers,
  highlightActiveLine,
  highlightActiveLineGutter,
} from "@codemirror/view";
import { defaultKeymap, history, historyKeymap } from "@codemirror/commands";
import { sql, MySQL, PostgreSQL, StandardSQL } from "@codemirror/lang-sql";
import {
  autocompletion,
  completionKeymap,
  startCompletion,
} from "@codemirror/autocomplete";
import { oneDark } from "@codemirror/theme-one-dark";
import { format } from "sql-formatter";

export default defineComponent({
  name: "SqlJobUpdate",
  components: {
    CronExpressionSelector,
    DataQueryAi,
  },
  setup() {
    const route = useRoute();
    const router = useRouter();
    const dataSourceService = inject(
      "dataSourceService",
      () => new DataSourceService(),
    );
    const sqlJobService = inject("sqlJobService", () => new SqlJobService());
    const alertService = inject("alertService", () => useAlertService(), true);

    const isEditMode = computed(() => !!route.params.jobId);
    const isSaving = ref(false);

    const sqlJob = ref<IJob>({ jobName: "", cron: "", status: "OFFLINE" });
    const dataSources = ref<IDataSource[]>([]);
    const dataSourceId = ref<string | null>(null);
    const dataSourceName = ref("");
    const dataSourceType = ref("");
    const selectedSchema = ref<string | null>(null);

    const treeRef = ref();
    const treeData = ref<any[]>([]);
    const treeLoading = ref(false);

    const sqlCode = ref("");
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
    const queryError = ref("");
    const executionTime = ref<number | null>(null);
    const affectedRows = ref<number | null>(null);
    const isExecuting = ref(false);
    const aiDrawerVisible = ref(false);

    const getActiveSql = (): string => {
      if (!editorView) return sqlCode.value.trim();
      const sel = editorView.state.selection.main;
      if (!sel.empty) {
        const selected = editorView.state.doc
          .sliceString(sel.from, sel.to)
          .trim();
        if (selected) return selected;
      }
      return sqlCode.value.trim();
    };

    const executeDebug = async () => {
      if (!dataSourceId.value) {
        alertService.showWarning("请先选择数据源");
        return;
      }
      const activeSql = getActiveSql();
      if (!activeSql) {
        alertService.showWarning("请输入 SQL 语句");
        return;
      }

      isExecuting.value = true;
      queryError.value = "";
      resultColumns.value = [];
      resultData.value = [];
      executionTime.value = null;
      affectedRows.value = null;

      const startTime = performance.now();
      try {
        const res = await sqlJobService().debugQuery(
          dataSourceId.value,
          activeSql,
        );
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
        affectedRows.value =
          res.affectedRows ?? (Array.isArray(rows) ? rows.length : null);
      } catch (err: any) {
        const elapsed = Math.round(performance.now() - startTime);
        executionTime.value = elapsed;
        queryError.value =
          err.response?.data?.message ||
          err.response?.data?.detail ||
          err.message ||
          "SQL 执行失败";
      } finally {
        isExecuting.value = false;
      }
    };

    const getSqlDialectSpec = () => {
      const type = (dataSourceType.value || "").toUpperCase();
      if (
        type.includes("MYSQL") ||
        type.includes("DORIS") ||
        type.includes("CLICKHOUSE")
      ) {
        return MySQL;
      }
      if (
        type.includes("ORACLE") ||
        type.includes("SQLSERVER") ||
        type.includes("MSSQL")
      ) {
        return StandardSQL;
      }
      if (
        type.includes("POSTGRESQL") ||
        type.includes("DUCKDB") ||
        type.includes("DUCKLAKE") ||
        type.includes("GREENPLUM")
      ) {
        return PostgreSQL;
      }
      return StandardSQL;
    };

    const initEditor = () => {
      if (!editorContainer.value) return;

      const updateListener = EditorView.updateListener.of((v) => {
        if (v.docChanged) {
          sqlCode.value = v.state.doc.toString();
        }
        if (v.selectionSet) {
          const sel = v.state.selection.main;
          hasSelection.value =
            !sel.empty &&
            v.state.doc.sliceString(sel.from, sel.to).trim().length > 0;
        }
      });

      const runQueryKeymap = keymap.of([
        {
          key: "Mod-Enter",
          preventDefault: true,
          run: () => {
            executeDebug();
            return true;
          },
        },
        {
          key: "Mod-Space",
          preventDefault: true,
          run: () => {
            if (editorView) {
              startCompletion(editorView);
            }
            return true;
          },
        },
        {
          key: "Mod-Shift-f",
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
      const el = document.querySelector(".query-main") as HTMLElement | null;
      return el ? el.clientHeight : 600;
    };

    const onMouseMove = (e: MouseEvent) => {
      if (!isDragging.value) return;
      const delta = e.clientY - resizeStartY;
      const newHeight = resizeStartHeight + delta;
      const mainHeight = getQueryMainHeight();
      const maxEditorHeight = mainHeight - MIN_RESULT_HEIGHT - 6;
      editorHeight.value = Math.max(
        MIN_EDITOR_HEIGHT,
        Math.min(newHeight, maxEditorHeight),
      );
    };

    const onMouseUp = () => {
      isDragging.value = false;
      document.removeEventListener("mousemove", onMouseMove);
      document.removeEventListener("mouseup", onMouseUp);
      if (editorView) {
        editorView.requestMeasure();
      }
    };

    const startResize = (e: MouseEvent) => {
      isDragging.value = true;
      resizeStartY = e.clientY;
      resizeStartHeight = editorHeight.value;
      document.addEventListener("mousemove", onMouseMove);
      document.addEventListener("mouseup", onMouseUp);
      e.preventDefault();
    };

    const loadDataSources = async () => {
      try {
        const res = await dataSourceService().retrieve({
          page: 0,
          size: 1000,
          sort: ["name,asc"],
        });
        dataSources.value = res.data || [];
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const loadDataSourceMeta = async () => {
      if (!dataSourceId.value) {
        dataSourceName.value = "";
        dataSourceType.value = "";
        return;
      }
      try {
        const ds = await dataSourceService().find(dataSourceId.value);
        dataSourceName.value = ds.name || "";
        dataSourceType.value = ds.type || "";
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
          type: "schema",
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
      if (
        node.data?.type === "schema" &&
        node.data?.name &&
        dataSourceId.value
      ) {
        try {
          const res = await dataSourceService().getTables(
            dataSourceId.value,
            node.data.name,
          );
          const tables = res.data || res || [];
          const tableNodes = tables.map((table: any) => {
            const tableName =
              typeof table === "string"
                ? table
                : (table.table ?? table.tableName ?? table.name ?? table);
            const catalog = table?.catalog;
            const schema = table?.schema ?? node.data.name;
            return {
              id: `table:${schema}.${tableName}`,
              label: tableName,
              type: "table",
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
      if (data?.type === "schema") {
        selectedSchema.value = data.name;
      } else if (data?.type === "table") {
        selectedSchema.value = data.schema || null;
      }
    };

    const onDataSourceChange = async (value: string | null) => {
      dataSourceId.value = value;
      resultColumns.value = [];
      resultData.value = [];
      queryError.value = "";
      await loadDataSourceMeta();
      await loadSchemas();
    };

    const getSqlDialect = () => {
      const type = (dataSourceType.value || "").toUpperCase();
      const backtickTypes = ["MYSQL", "DORIS", "CLICKHOUSE"];
      const bracketTypes = ["SQLSERVER", "SQL_SERVER", "MSSQL"];
      const isBacktick = backtickTypes.some((t) => type.includes(t));
      const isBracket = bracketTypes.some((t) => type.includes(t));
      const isOracle = type.includes("ORACLE");

      return {
        quote: (identifier: string) => {
          if (isBacktick) return `\`${identifier}\``;
          if (isBracket) return `[${identifier}]`;
          return `"${identifier}"`;
        },
        limit: (rawSql: string, limit: number) => {
          if (isOracle) {
            return `${rawSql} FETCH FIRST ${limit} ROWS ONLY;`;
          }
          return `${rawSql} LIMIT ${limit};`;
        },
      };
    };

    const buildSelectSql = (
      catalog: string | undefined,
      schema: string | undefined,
      tableName: string,
      limit = 10,
    ) => {
      const dialect = getSqlDialect();
      const catalogPart = catalog ? `${dialect.quote(catalog)}.` : "";
      const schemaPart = schema ? `${dialect.quote(schema)}.` : "";
      const rawSql = `SELECT * FROM ${catalogPart}${schemaPart}${dialect.quote(tableName)}`;
      return dialect.limit(rawSql, limit);
    };

    const handleNodeDblClick = (data: any) => {
      if (data?.type === "table") {
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
      const type = (dataSourceType.value || "").toUpperCase();
      if (type.includes("MYSQL") || type.includes("DORIS")) {
        return "mysql";
      }
      if (type.includes("CLICKHOUSE")) {
        return "clickhouse";
      }
      if (
        type.includes("POSTGRESQL") ||
        type.includes("DUCKDB") ||
        type.includes("DUCKLAKE") ||
        type.includes("GREENPLUM")
      ) {
        return "postgresql";
      }
      if (type.includes("SQLSERVER") || type.includes("MSSQL")) {
        return "transactsql";
      }
      if (type.includes("ORACLE")) {
        return "plsql";
      }
      return "postgresql";
    };

    const formatSql = () => {
      const rawSql = getActiveSql();
      if (!rawSql) {
        alertService.showWarning("请输入 SQL 语句");
        return;
      }
      try {
        const formatted = format(rawSql, {
          language: getFormatterDialect(),
          keywordCase: "upper",
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
        alertService.showError("SQL 格式化失败，请检查语法");
      }
    };

    const formatTime = (ms: number | null) => {
      if (ms === null) return "-";
      if (ms < 1000) return `${ms}ms`;
      return `${(ms / 1000).toFixed(2)}s`;
    };

    const openAiDrawer = () => {
      if (!dataSourceId.value) {
        alertService.showWarning("请先选择数据源");
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
        const config =
          typeof jobContext === "string" ? JSON.parse(jobContext) : jobContext;
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
        console.error("解析 SQL 任务配置失败", error);
      }
    };

    const loadSqlJob = async () => {
      if (!isEditMode.value) {
        sqlJob.value = { jobName: "", cron: "", status: "OFFLINE" };
        sqlCode.value = "SELECT 1;";
        return;
      }
      try {
        const job = await sqlJobService().find(String(route.params.jobId));
        sqlJob.value = job;
        parseJobContext(job.jobContext);
      } catch (error) {
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
        alertService.showError("请输入任务名称");
        return;
      }
      if (!dataSourceId.value) {
        alertService.showError("请选择数据源");
        return;
      }
      if (!sqlCode.value.trim()) {
        alertService.showError("请输入 SQL 语句");
        return;
      }

      isSaving.value = true;
      const jobContext = buildJobContext();
      try {
        if (isEditMode.value) {
          const entity: IJob = {
            ...sqlJob.value,
            jobName: sqlJob.value.jobName.trim(),
            type: "SQL",
            cron: sqlJob.value.cron || "",
            jobContext,
            updateTime: new Date(),
          };
          await sqlJobService().update(entity);
          alertService.showSuccess("SQL 任务保存成功");
        } else {
          const entity: IJob = {
            jobName: sqlJob.value.jobName.trim(),
            jobGroup: "datafusion",
            type: "SQL",
            cron: sqlJob.value.cron || "",
            status: "OFFLINE",
            jobContext,
            createTime: new Date(),
            updateTime: new Date(),
          };
          await sqlJobService().create(entity);
          alertService.showSuccess("SQL 任务创建成功");
        }
        router.push({ name: "SqlJob" });
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isSaving.value = false;
      }
    };

    onMounted(async () => {
      await loadDataSources();
      await loadSqlJob();
      if (dataSourceId.value) {
        await loadDataSourceMeta();
        await loadSchemas();
      }
      initEditor();
    });

    onBeforeUnmount(() => {
      destroyEditor();
      document.removeEventListener("mousemove", onMouseMove);
      document.removeEventListener("mouseup", onMouseUp);
    });

    return {
      isEditMode,
      isSaving,
      sqlJob,
      dataSources,
      dataSourceId,
      dataSourceName,
      selectedSchema,
      treeRef,
      treeData,
      treeLoading,
      sqlCode,
      editorContainer,
      editorHeight,
      isDragging,
      hasSelection,
      resultColumns,
      resultData,
      queryError,
      executionTime,
      affectedRows,
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
