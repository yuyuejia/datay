import { type Ref, computed, defineComponent, inject, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import { useAlertService } from '@/shared/alert/alert.service';
import DataSourceService from '@/entities/data-source/data-source.service';
import MetricService from '@/entities/metric/metric.service';
import { buildDashboardFields } from '@/shared/dashboard/field-defaults';
import DatasetService from '@/shared/dataset/dataset.service';
import type { DatasetPreviewResult } from '@/shared/dataset/dataset.service';
import type {
  AnalysisDataset,
  AnalysisDatasetType,
  DatasetColumn,
  MetricDimensionMeta,
  MetricQueryMeta,
  MetricTimeFieldMeta,
} from '@/shared/dataset/types';
import {
  FILTER_OPERATORS,
  FILTER_TYPE_DIMENSION,
  isRangeOperator,
  isUnaryOperator,
  type IMetricFilterCondition,
} from '@/shared/model/metric.model';

interface MetricOption {
  code: string;
  name: string;
  metricType?: string;
}

export interface DimensionOption {
  dimensionModelCode: string;
  dimensionModelName?: string;
  selected: boolean;
  fieldNames: string[];
  fields: Array<{ fieldName: string; fieldType?: string; description?: string }>;
  isTimeDimension?: boolean;
  isHierarchy?: boolean;
  levels?: Array<{ levelIndex: number; label?: string }>;
  levelCount?: number;
  levelIndex?: number | null;
}

export interface MetricFilterConditionOption {
  dimensionModelCode: string;
  dimensionFieldName: string;
  operator: string;
  value: string;
  valueEnd: string;
  logic: 'AND' | 'OR';
}

export interface MetricFilterConfigPayload {
  conditions: IMetricFilterCondition[];
}

export interface MetricTimeRangeOption {
  start: string;
  end: string;
}

const PLACEHOLDER = /\$\{([A-Za-z_][A-Za-z0-9_]*)\}/g;

export function buildMetricFilterConfig(conditions: MetricFilterConditionOption[]): MetricFilterConfigPayload | undefined {
  const normalized = conditions
    .filter(condition => condition.dimensionModelCode && condition.dimensionFieldName)
    .map((condition, index) => {
      const operator = condition.operator || 'EQ';
      const unary = isUnaryOperator(operator);
      const range = isRangeOperator(operator);
      return {
        type: FILTER_TYPE_DIMENSION,
        dimensionModelCode: condition.dimensionModelCode,
        dimensionFieldName: condition.dimensionFieldName,
        operator,
        value: unary ? null : condition.value || null,
        valueEnd: range ? condition.valueEnd || null : null,
        logic: index === 0 ? 'AND' : condition.logic || 'AND',
      } satisfies IMetricFilterCondition;
    });
  return normalized.length ? { conditions: normalized } : undefined;
}

function normalizeTimeRange(timeRange?: MetricTimeRangeOption | null) {
  const start = timeRange?.start?.trim() || undefined;
  const end = timeRange?.end?.trim() || undefined;
  return start || end ? { start, end } : undefined;
}

/** 从 SQL 中提取命名占位符，作为数据集参数声明。 */
export function extractSqlParams(sql: string): Array<{ name: string; label: string }> {
  const names = new Set<string>();
  let match: RegExpExecArray | null;
  PLACEHOLDER.lastIndex = 0;
  while ((match = PLACEHOLDER.exec(sql || '')) !== null) {
    names.add(match[1]);
  }
  return Array.from(names).map(name => ({ name, label: name }));
}

/** 根据数据集表单构建后端使用的 definition JSON 字符串。 */
export function buildDatasetDefinition(
  type: AnalysisDatasetType,
  sql: string,
  metricCodes: string[],
  dimensions: DimensionOption[],
  dataSourceId?: string,
  filterConfig?: MetricFilterConfigPayload | null,
  timeRange?: MetricTimeRangeOption | null,
): string {
  if (type === 'SQL') {
    return JSON.stringify({
      sql,
      params: extractSqlParams(sql),
      dataSourceId: dataSourceId || undefined,
    });
  }
  const normalizedTimeRange = normalizeTimeRange(timeRange);
  return JSON.stringify({
    metricQuery: {
      metricCodes,
      dimensions: dimensions
        .filter(dimension => dimension.selected)
        .map(dimension => {
          const fieldNames = dimension.fieldNames.filter(field => !!field);
          return {
            dimensionModelCode: dimension.dimensionModelCode,
            dimensionFieldNames: fieldNames.length ? fieldNames : undefined,
            levelIndex: dimension.levelIndex ?? undefined,
          };
        }),
      filterConfig: filterConfig?.conditions?.length ? filterConfig : undefined,
      timeRange: normalizedTimeRange,
    },
  });
}

/**
 * 分析数据集编辑器：支持指标数据集与 SQL 数据集，保存前可实时预览。
 */
export default defineComponent({
  name: 'AnalysisDatasetEdit',
  setup() {
    const route = useRoute();
    const router = useRouter();
    const alertService = inject('alertService', () => useAlertService(), true);
    const datasetService = new DatasetService();
    const dataSourceService = new DataSourceService();
    const metricService = new MetricService();

    const editingId: Ref<string | undefined> = ref(undefined);
    const loading = ref(false);
    const saving = ref(false);
    const form = ref<AnalysisDataset>({
      name: '',
      code: '',
      description: '',
      type: 'METRIC',
      status: 'ENABLED',
    });
    const sqlText = ref('');
    const metricCodes = ref<string[]>([]);
    const metrics = ref<MetricOption[]>([]);
    const dimensions = ref<DimensionOption[]>([]);
    const factTables = ref<string[]>([]);
    const timeFields = ref<MetricTimeFieldMeta[]>([]);
    const filterConditions = ref<MetricFilterConditionOption[]>([]);
    const queryTimeRange = ref<MetricTimeRangeOption>({ start: '', end: '' });
    const filterOperators = FILTER_OPERATORS;
    const loadingMeta = ref(false);

    const dataSources = ref<any[]>([]);
    const schemas = ref<string[]>([]);
    const tables = ref<string[]>([]);
    const selectedSchema = ref('');
    const selectedTable = ref('');
    const loadingTables = ref(false);

    const previewVisible = ref(false);
    const previewLoading = ref(false);
    const previewError = ref('');
    const previewColumns = ref<string[]>([]);
    const previewRows = ref<Record<string, any>[]>([]);
    const previewSql = ref('');
    const previewColumnLabels = ref<Record<string, string>>({});
    const fieldMetadata = ref<DatasetColumn[]>([]);

    const definition = computed(() =>
      buildDatasetDefinition(
        form.value.type,
        sqlText.value,
        metricCodes.value,
        dimensions.value,
        form.value.dataSourceId,
        buildMetricFilterConfig(filterConditions.value),
        queryTimeRange.value,
      ),
    );

    const placeholders = computed(() => extractSqlParams(sqlText.value));

    const errorMessage = (err: any, fallback: string): string =>
      err?.response?.data?.errorMessage ||
      err?.response?.data?.detail ||
      err?.response?.data?.title ||
      err?.response?.data?.message ||
      err?.message ||
      fallback;

    const previewColumnLabel = (column: string): string => fieldMetadata.value.find(field => field.name === column)?.description || column;

    const applyPreviewFields = (labels: Record<string, string>) => {
      const existing = new Map(fieldMetadata.value.map(field => [field.name, field] as const));
      fieldMetadata.value = buildDashboardFields(previewColumns.value, previewRows.value, fieldMetadata.value).map(field => {
        const previous = existing.get(field.name);
        return {
          name: field.name,
          label: previous?.label || field.label,
          description: previous?.description || labels[field.name] || field.name,
          type: previous?.type && previous.type !== 'unknown' ? previous.type : field.type,
          primaryKey: previous?.primaryKey ?? false,
        };
      });
    };

    const loadMetrics = async () => {
      try {
        const result = await metricService.retrieveAll();
        const list: any[] = Array.isArray(result) ? result : (result?.data ?? result?.content ?? []);
        metrics.value = list.map((item: any) => ({
          code: item.code,
          name: item.name || item.code,
          metricType: item.metricType,
        }));
      } catch (err: any) {
        alertService.showError(errorMessage(err, '指标列表加载失败'));
      }
    };

    const loadDimensions = async () => {
      dimensions.value = [];
      factTables.value = [];
      timeFields.value = [];
      if (!metricCodes.value.length) {
        return;
      }
      loadingMeta.value = true;
      try {
        const meta: MetricQueryMeta = await datasetService.loadMetricMeta(metricCodes.value);
        factTables.value = meta.factTables ?? [];
        timeFields.value = meta.timeFields ?? [];
        dimensions.value = (meta.dimensions ?? []).map((dimension: MetricDimensionMeta) => {
          const levels = dimension.levels ?? [];
          const isHierarchy = !!dimension.isHierarchy;
          return {
            dimensionModelCode: dimension.dimensionModelCode,
            dimensionModelName: dimension.dimensionModelName || dimension.dimensionModelCode,
            selected: false,
            fieldNames: isHierarchy ? [] : [dimension.recommendedDisplayField || dimension.displayFieldName || ''].filter(Boolean),
            fields: dimension.fields ?? [],
            isTimeDimension: dimension.isTimeDimension,
            isHierarchy,
            levels,
            levelCount: dimension.levelCount ?? levels.length,
            levelIndex: isHierarchy ? (levels.length ? levels[levels.length - 1].levelIndex : (dimension.levelCount ?? 1)) : null,
          };
        });
      } catch (err: any) {
        alertService.showError(errorMessage(err, '维度信息加载失败'));
      } finally {
        loadingMeta.value = false;
      }
    };

    const dimensionLevels = (dimension: DimensionOption) => {
      if (!dimension.isHierarchy) {
        return [] as Array<{ levelIndex: number; label?: string }>;
      }
      if (dimension.levels?.length) {
        return dimension.levels;
      }
      const count = Number(dimension.levelCount) || 0;
      return Array.from({ length: count }, (_value, index) => ({ levelIndex: index + 1 }));
    };

    const dimensionLevelLabel = (dimension: DimensionOption, levelIndex: number) => {
      const levels = dimensionLevels(dimension);
      const current = levels.find(level => level.levelIndex === levelIndex);
      if (current?.label) {
        return current.label;
      }
      const names = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十'];
      const label = names[levelIndex - 1] ? `${names[levelIndex - 1]}级` : `第${levelIndex}级`;
      const last = levels.length > 0 && levels[levels.length - 1].levelIndex === levelIndex;
      return last ? `${label}(末级)` : label;
    };

    const conditionDimensionFields = (condition: MetricFilterConditionOption) => {
      return dimensions.value.find(dimension => dimension.dimensionModelCode === condition.dimensionModelCode)?.fields ?? [];
    };

    const createFilterCondition = (): MetricFilterConditionOption => ({
      dimensionModelCode: '',
      dimensionFieldName: '',
      operator: 'EQ',
      value: '',
      valueEnd: '',
      logic: 'AND',
    });

    const addFilterCondition = () => {
      filterConditions.value.push(createFilterCondition());
    };

    const removeFilterCondition = (index: number) => {
      filterConditions.value.splice(index, 1);
    };

    const onFilterDimensionChange = (condition: MetricFilterConditionOption) => {
      condition.dimensionFieldName = '';
    };

    const parseFilterConditions = (raw: unknown): MetricFilterConditionOption[] => {
      let config: any = raw;
      if (typeof raw === 'string') {
        if (!raw.trim()) {
          return [];
        }
        try {
          config = JSON.parse(raw);
        } catch {
          return [];
        }
      }
      const conditions = Array.isArray(config?.conditions) ? config.conditions : [];
      return conditions
        .filter((condition: any) => condition?.dimensionModelCode && condition?.dimensionFieldName)
        .map((condition: any, index: number) => ({
          dimensionModelCode: String(condition.dimensionModelCode),
          dimensionFieldName: String(condition.dimensionFieldName),
          operator: condition.operator || 'EQ',
          value: condition.value == null ? '' : String(condition.value),
          valueEnd: condition.valueEnd == null ? '' : String(condition.valueEnd),
          logic: index === 0 ? 'AND' : condition.logic === 'OR' ? 'OR' : 'AND',
        }));
    };

    const applySelectedDimensions = (selected: any[]) => {
      for (const dimension of dimensions.value) {
        const hit = selected.find(item => item.dimensionModelCode === dimension.dimensionModelCode);
        if (!hit) {
          continue;
        }
        dimension.selected = true;
        const fieldNames = Array.isArray(hit.dimensionFieldNames)
          ? hit.dimensionFieldNames
          : hit.dimensionFieldName
            ? [hit.dimensionFieldName]
            : [];
        dimension.fieldNames = fieldNames.filter((field: unknown): field is string => typeof field === 'string' && !!field);
        if (hit.levelIndex != null) {
          dimension.levelIndex = Number(hit.levelIndex);
        }
      }
    };

    const loadDataSources = async () => {
      try {
        const res = await dataSourceService.retrieve({ page: 0, size: 500, sort: ['id,asc'] });
        dataSources.value = res.data ?? [];
      } catch (err: any) {
        alertService.showError(errorMessage(err, '数据源加载失败'));
      }
    };

    const loadSchemas = async () => {
      schemas.value = [];
      tables.value = [];
      selectedSchema.value = '';
      selectedTable.value = '';
      if (!form.value.dataSourceId) {
        return;
      }
      try {
        const result = await dataSourceService.getSchemas(form.value.dataSourceId);
        const list = result?.data ?? result ?? [];
        schemas.value = Array.isArray(list)
          ? list.map((item: any) => (typeof item === 'string' ? item : (item.schemaName ?? item.name ?? item.schema)))
          : [];
        selectedSchema.value = schemas.value[0] ?? '';
        await loadTables();
      } catch (err: any) {
        alertService.showError(errorMessage(err, 'schema 加载失败'));
      }
    };

    const loadTables = async () => {
      tables.value = [];
      selectedTable.value = '';
      if (!form.value.dataSourceId || !selectedSchema.value) {
        return;
      }
      loadingTables.value = true;
      try {
        const result = await dataSourceService.getTables(form.value.dataSourceId, selectedSchema.value, 500);
        const list = result?.data ?? result ?? [];
        tables.value = Array.isArray(list)
          ? list.map((item: any) => (typeof item === 'string' ? item : (item.tableName ?? item.name ?? item.table)))
          : [];
      } catch (err: any) {
        alertService.showWarning(errorMessage(err, '表列表加载失败'));
      } finally {
        loadingTables.value = false;
      }
    };

    const insertTable = () => {
      if (!selectedTable.value) {
        return;
      }
      const prefix = selectedSchema.value ? `${selectedSchema.value}.` : '';
      const statement = `SELECT *\nFROM ${prefix}${selectedTable.value}\nLIMIT 100`;
      sqlText.value = sqlText.value?.trim() ? `${sqlText.value.trim()}\n\n${statement}` : statement;
    };

    const loadExisting = async (id: string) => {
      loading.value = true;
      try {
        const dataset = await datasetService.get(id);
        editingId.value = id;
        form.value = { ...dataset };
        fieldMetadata.value = DatasetService.columns(DatasetService.parseFields(dataset));
        const def = DatasetService.parseDefinition(dataset);
        if (dataset.type === 'SQL') {
          sqlText.value = def.sql || '';
        } else {
          const query = def.metricQuery || def;
          metricCodes.value = query.metricCodes || [];
          await loadDimensions();
          applySelectedDimensions(Array.isArray(query.dimensions) ? query.dimensions : []);
          filterConditions.value = parseFilterConditions(query.filterConfig);
          queryTimeRange.value = {
            start: query.timeRange?.start || '',
            end: query.timeRange?.end || '',
          };
        }
        if (dataset.type === 'SQL' && dataset.dataSourceId) {
          await loadSchemas();
        }
      } catch (err: any) {
        alertService.showError(errorMessage(err, '数据集加载失败'));
      } finally {
        loading.value = false;
      }
    };

    const applyMetricQueryPrefill = async (metricQuery: any, name?: string, description?: string) => {
      form.value.type = 'METRIC';
      if (name) {
        form.value.name = name;
      }
      if (description && !form.value.description) {
        form.value.description = description;
      }
      metricCodes.value = Array.isArray(metricQuery?.metricCodes) ? metricQuery.metricCodes : [];
      await loadDimensions();
      applySelectedDimensions(Array.isArray(metricQuery?.dimensions) ? metricQuery.dimensions : []);
      filterConditions.value = parseFilterConditions(metricQuery?.filterConfig);
      queryTimeRange.value = {
        start: metricQuery?.timeRange?.start || '',
        end: metricQuery?.timeRange?.end || '',
      };
    };

    onMounted(async () => {
      await Promise.all([loadMetrics(), loadDataSources()]);
      const id = route.query.id ? String(route.query.id) : undefined;
      if (id) {
        await loadExisting(id);
        return;
      }

      // 从数据源查询页携带 SQL 快速创建数据集。
      const prefillSql = route.query.sql ? String(route.query.sql) : '';
      const prefillDataSourceId = route.query.dataSourceId ? String(route.query.dataSourceId) : '';
      if (prefillSql || prefillDataSourceId) {
        form.value.type = 'SQL';
        form.value.dataSourceId = prefillDataSourceId || undefined;
        sqlText.value = prefillSql;
        const queryName = route.query.name ? String(route.query.name).trim() : '';
        const dataSource = dataSources.value.find(item => item.id === prefillDataSourceId);
        form.value.name = queryName || (dataSource ? `${dataSource.name} 分析数据集` : 'SQL 分析数据集');
        if (route.query.from === 'data-query' && !form.value.description) {
          form.value.description = '由数据查询 SQL 快速生成';
        }
        if (form.value.dataSourceId) {
          await loadSchemas();
        }
        return;
      }

      // 从指标查询页携带完整的指标查询配置快速创建指标数据集。
      const prefillMetricQuery = route.query.metricQuery ? String(route.query.metricQuery) : '';
      if (prefillMetricQuery) {
        try {
          const metricQuery = JSON.parse(prefillMetricQuery);
          const queryName = route.query.name ? String(route.query.name).trim() : '';
          await applyMetricQueryPrefill(metricQuery, queryName, '由指标查询快速生成');
        } catch {
          alertService.showError('指标查询配置解析失败，请返回指标查询后重试');
        }
      }
    });

    const onTypeChange = () => {
      previewColumns.value = [];
      previewRows.value = [];
      previewSql.value = '';
      previewColumnLabels.value = {};
      fieldMetadata.value = [];
      previewError.value = '';
    };

    const onDataSourceChange = async () => {
      await loadSchemas();
    };

    const clearTimeRange = () => {
      queryTimeRange.value = { start: '', end: '' };
    };

    const onMetricChange = async () => {
      filterConditions.value = [];
      clearTimeRange();
      await loadDimensions();
    };

    const buildPayload = (normalizedDefinition?: string): AnalysisDataset => {
      const columns = fieldMetadata.value.map(field => ({
        name: field.name,
        label: field.label,
        description: field.description || field.name,
        type: field.type,
        primaryKey: !!field.primaryKey,
      }));
      const columnLabels = Object.fromEntries(
        columns.filter(field => !!field.description).map(field => [field.name, field.description as string]),
      );
      return {
        ...form.value,
        name: form.value.name?.trim(),
        definition: normalizedDefinition || definition.value,
        fields: columns.length ? JSON.stringify({ columns, columnLabels }) : undefined,
      };
    };

    const validate = async (): Promise<AnalysisDataset | null> => {
      if (!form.value.name?.trim()) {
        alertService.showError('请填写数据集名称');
        return null;
      }
      if (form.value.type === 'SQL') {
        if (!form.value.dataSourceId) {
          alertService.showError('SQL 数据集必须选择数据源');
          return null;
        }
        if (!sqlText.value.trim()) {
          alertService.showError('请填写 SQL');
          return null;
        }
      } else if (!metricCodes.value.length) {
        alertService.showError('指标数据集至少选择一个指标');
        return null;
      }
      try {
        const normalized = await datasetService.validate(buildPayload());
        form.value.definition = normalized.definition;
        return normalized;
      } catch (err: any) {
        alertService.showError(errorMessage(err, '数据集定义校验失败'));
        return null;
      }
    };

    const runPreview = async () => {
      const normalized = await validate();
      if (!normalized) {
        return;
      }
      previewVisible.value = true;
      previewLoading.value = true;
      previewError.value = '';
      previewColumns.value = [];
      previewRows.value = [];
      previewSql.value = '';
      try {
        const result: DatasetPreviewResult = await datasetService.previewDefinition({
          name: normalized.name,
          type: normalized.type,
          dataSourceId: normalized.dataSourceId,
          definition: normalized.definition as string,
        });
        previewColumns.value = result.columns ?? [];
        previewRows.value = result.rows ?? [];
        previewSql.value = result.sql || '';
        previewColumnLabels.value = result.columnLabels ?? {};
        applyPreviewFields(previewColumnLabels.value);
      } catch (err: any) {
        previewError.value = errorMessage(err, '预览失败');
      } finally {
        previewLoading.value = false;
      }
    };

    const save = async () => {
      const normalized = await validate();
      if (!normalized) {
        return;
      }
      saving.value = true;
      try {
        if (editingId.value) {
          await datasetService.update(editingId.value, normalized);
        } else {
          await datasetService.create(normalized);
        }
        ElMessage.success('数据集已保存');
        router.push({ name: 'AnalysisDataset' });
      } catch (err: any) {
        alertService.showError(errorMessage(err, '保存失败'));
      } finally {
        saving.value = false;
      }
    };

    const cancel = () => router.push({ name: 'AnalysisDataset' });

    return {
      editingId,
      loading,
      saving,
      form,
      sqlText,
      metricCodes,
      metrics,
      dimensions,
      factTables,
      timeFields,
      filterConditions,
      queryTimeRange,
      filterOperators,
      loadingMeta,
      dataSources,
      schemas,
      tables,
      selectedSchema,
      selectedTable,
      loadingTables,
      placeholders,
      previewVisible,
      previewLoading,
      previewError,
      previewColumns,
      previewRows,
      previewSql,
      fieldMetadata,
      previewColumnLabel,
      dimensionLevels,
      dimensionLevelLabel,
      conditionDimensionFields,
      addFilterCondition,
      removeFilterCondition,
      onFilterDimensionChange,
      clearTimeRange,
      isUnaryOperator,
      isRangeOperator,
      onTypeChange,
      onDataSourceChange,
      onMetricChange,
      loadTables,
      insertTable,
      runPreview,
      save,
      cancel,
    };
  },
});
