import { type Ref, computed, defineComponent, inject, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import { useAlertService } from '@/shared/alert/alert.service';
import DataSourceService from '@/entities/data-source/data-source.service';
import MetricService from '@/entities/metric/metric.service';
import DatasetService from '@/shared/dataset/dataset.service';
import type { DatasetPreviewResult } from '@/shared/dataset/dataset.service';
import type { AnalysisDataset, AnalysisDatasetType, MetricDimensionMeta, MetricQueryMeta } from '@/shared/dataset/types';

interface MetricOption {
  code: string;
  name: string;
  metricType?: string;
}

interface DimensionOption {
  dimensionModelCode: string;
  dimensionModelName?: string;
  selected: boolean;
  fieldName: string;
  fields: Array<{ fieldName: string; fieldType?: string; description?: string }>;
  isTimeDimension?: boolean;
}

const PLACEHOLDER = /\$\{([A-Za-z_][A-Za-z0-9_]*)\}/g;

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
): string {
  if (type === 'SQL') {
    return JSON.stringify({
      sql,
      params: extractSqlParams(sql),
      dataSourceId: dataSourceId || undefined,
    });
  }
  return JSON.stringify({
    metricQuery: {
      metricCodes,
      dimensions: dimensions
        .filter(dimension => dimension.selected)
        .map(dimension => ({
          dimensionModelCode: dimension.dimensionModelCode,
          dimensionFieldName: dimension.fieldName || undefined,
        })),
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
    const loadingMeta = ref(false);

    const dataSources = ref<any[]>([]);
    const schemas = ref<string[]>([]);
    const tables = ref<string[]>([]);
    const selectedSchema = ref('');
    const selectedTable = ref('');
    const loadingTables = ref(false);

    const previewLoading = ref(false);
    const previewError = ref('');
    const previewColumns = ref<string[]>([]);
    const previewRows = ref<Record<string, any>[]>([]);
    const previewSql = ref('');

    const definition = computed(() =>
      buildDatasetDefinition(form.value.type, sqlText.value, metricCodes.value, dimensions.value, form.value.dataSourceId),
    );

    const placeholders = computed(() => extractSqlParams(sqlText.value));

    const errorMessage = (err: any, fallback: string): string =>
      err?.response?.data?.errorMessage ||
      err?.response?.data?.detail ||
      err?.response?.data?.title ||
      err?.response?.data?.message ||
      err?.message ||
      fallback;

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
      if (!metricCodes.value.length) {
        return;
      }
      loadingMeta.value = true;
      try {
        const meta: MetricQueryMeta = await datasetService.loadMetricMeta(metricCodes.value);
        dimensions.value = (meta.dimensions ?? []).map((dimension: MetricDimensionMeta) => ({
          dimensionModelCode: dimension.dimensionModelCode,
          dimensionModelName: dimension.dimensionModelName || dimension.dimensionModelCode,
          selected: false,
          fieldName: dimension.displayFieldName || dimension.recommendedDisplayField || '',
          fields: dimension.fields ?? [],
          isTimeDimension: dimension.isTimeDimension,
        }));
      } catch (err: any) {
        alertService.showError(errorMessage(err, '维度信息加载失败'));
      } finally {
        loadingMeta.value = false;
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
        const def = DatasetService.parseDefinition(dataset);
        if (dataset.type === 'SQL') {
          sqlText.value = def.sql || '';
        } else {
          const query = def.metricQuery || def;
          metricCodes.value = query.metricCodes || [];
          await loadDimensions();
          const selected: Array<{ dimensionModelCode: string; dimensionFieldName?: string }> = query.dimensions || [];
          for (const dimension of dimensions.value) {
            const hit = selected.find(item => item.dimensionModelCode === dimension.dimensionModelCode);
            if (hit) {
              dimension.selected = true;
              dimension.fieldName = hit.dimensionFieldName || dimension.fieldName;
            }
          }
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

    onMounted(async () => {
      await Promise.all([loadMetrics(), loadDataSources()]);
      const id = route.query.id ? String(route.query.id) : undefined;
      if (id) {
        await loadExisting(id);
      }
    });

    const onTypeChange = () => {
      previewColumns.value = [];
      previewRows.value = [];
      previewError.value = '';
    };

    const onDataSourceChange = async () => {
      await loadSchemas();
    };

    const onMetricChange = async () => {
      await loadDimensions();
    };

    const buildPayload = (normalizedDefinition?: string): AnalysisDataset => ({
      ...form.value,
      name: form.value.name?.trim(),
      definition: normalizedDefinition || definition.value,
    });

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
      loadingMeta,
      dataSources,
      schemas,
      tables,
      selectedSchema,
      selectedTable,
      loadingTables,
      placeholders,
      previewLoading,
      previewError,
      previewColumns,
      previewRows,
      previewSql,
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
