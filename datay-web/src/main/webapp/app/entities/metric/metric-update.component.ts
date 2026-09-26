import { type Ref, computed, defineComponent, inject, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

import DataModelService from "@/entities/data-model/data-model.service";
import MetricService from "./metric.service";
import MetricDirectoryService from "./metric-directory.service";
import { type IMetricDirectory } from "@/shared/model/metric-directory.model";
import {
  Metric,
  type IMetric,
  METRIC_TYPE_ATOMIC,
  METRIC_TYPE_DERIVED,
  METRIC_STATUS_ENABLED,
  FILTER_TYPE_FACT_FIELD,
  FILTER_TYPE_DIMENSION,
  FILTER_TYPE_TIME,
  FILTER_TYPE_OPTIONS,
  FILTER_OPERATORS,
  AGG_FUNCTIONS,
  DATA_TYPES,
  isRangeOperator,
  isUnaryOperator,
  parseFilterConfig,
  stringifyFilterConfig,
  parseFormulaRefs,
} from "@/shared/model/metric.model";
import { useAlertService } from "@/shared/alert/alert.service";

const DATE_TYPES = ["DATE", "DATETIME", "TIMESTAMP"];

const createCondition = (logic = "AND") => ({
  type: FILTER_TYPE_FACT_FIELD,
  factFieldName: null,
  dimensionModelId: null,
  dimensionFieldName: null,
  operator: "EQ",
  value: null,
  valueEnd: null,
  logic,
  _dimensionFields: [] as any[],
});

export default defineComponent({
  name: "MetricUpdate",
  setup() {
    const metricService = inject("metricService", () => new MetricService());
    const metricDirectoryService = inject(
      "metricDirectoryService",
      () => new MetricDirectoryService(),
    );
    const dataModelService = inject(
      "dataModelService",
      () => new DataModelService(),
    );
    const alertService = inject("alertService", () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();
    const previousState = () => router.go(-1);

    const isEdit = ref(false);
    const isSaving = ref(false);
    const metric: Ref<IMetric> = ref(new Metric());
    const formula = ref("");
    const filterConditions: Ref<any[]> = ref([]);
    const sqlDialogVisible = ref(false);
    const sqlContent = ref("");
    const selectedRefCode = ref("");
    const selectedFieldCode = ref("");

    const directories: Ref<IMetricDirectory[]> = ref([]);
    const factModels: Ref<any[]> = ref([]);
    const factFields: Ref<any[]> = ref([]);
    const allMetrics: Ref<IMetric[]> = ref([]);
    const loadingFields = ref(false);

    const metricTypeOptions = [
      { value: METRIC_TYPE_ATOMIC, label: "原子指标" },
      { value: METRIC_TYPE_DERIVED, label: "衍生原子指标" },
    ];

    const statusOptions = [
      { value: METRIC_STATUS_ENABLED, label: "启用" },
      { value: "DISABLED", label: "停用" },
    ];

    const filterTypeOptions = FILTER_TYPE_OPTIONS;
    const filterOperators = FILTER_OPERATORS;
    const dataTypes = DATA_TYPES;
    const aggFunctions = AGG_FUNCTIONS;

    const flatDirectoryOptions = computed(() => {
      const result: Array<{ id: number; name: string; level: number }> = [];
      const build = (parentId: number | null, level: number) => {
        directories.value
          .filter((d) => (d.parentId || null) === parentId)
          .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
          .forEach((dir) => {
            result.push({ id: dir.id!, name: dir.name || "", level });
            build(dir.id!, level + 1);
          });
      };
      build(null, 0);
      return result;
    });

    const dimensionFactFields = computed(() =>
      factFields.value.filter((f) => f.dimensionModelId),
    );
    const dateFactFields = computed(() =>
      factFields.value.filter(
        (f) =>
          DATE_TYPES.includes((f.fieldType || "").toUpperCase()) ||
          /date|time/i.test(f.fieldName || ""),
      ),
    );

    const formulaRefs = computed(() => parseFormulaRefs(formula.value));
    const insertableMetrics = computed(() => allMetrics.value);

    const loadDirectories = async () => {
      try {
        const res = await metricDirectoryService().retrieve();
        directories.value = res.data || [];
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    const loadFactModels = async () => {
      try {
        const res = await dataModelService().retrieveByType("DWD");
        factModels.value = res.data || [];
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    const loadAllMetrics = async () => {
      try {
        const res = await metricService().retrieveAll();
        allMetrics.value = res.data || [];
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    const loadFactFields = async () => {
      if (!metric.value.factModelId) {
        factFields.value = [];
        return;
      }
      loadingFields.value = true;
      try {
        const res = await dataModelService().getFields(
          metric.value.factModelId,
        );
        factFields.value = res.data || [];
      } catch (err: any) {
        factFields.value = [];
        alertService.showHttpError(err.response);
      } finally {
        loadingFields.value = false;
      }
    };

    const onFactModelChange = async () => {
      formula.value = "";
      selectedFieldCode.value = "";
      filterConditions.value = [];
      await loadFactFields();
    };

    const loadDimensionFields = async (condition: any) => {
      const factField = factFields.value.find(
        (f) => f.fieldName === condition.factFieldName,
      );
      if (!factField || !factField.dimensionModelId) {
        condition.dimensionModelId = null;
        condition._dimensionFields = [];
        return;
      }
      condition.dimensionModelId = factField.dimensionModelId;
      try {
        const res = await dataModelService().getFields(
          factField.dimensionModelId,
        );
        condition._dimensionFields = res.data || [];
      } catch {
        condition._dimensionFields = [];
      }
    };

    const onFilterFactFieldChange = async (condition: any) => {
      condition.dimensionFieldName = null;
      if (condition.type === FILTER_TYPE_DIMENSION) {
        await loadDimensionFields(condition);
      }
    };

    const onFilterTypeChange = (condition: any) => {
      condition.factFieldName = null;
      condition.dimensionModelId = null;
      condition.dimensionFieldName = null;
      condition.value = null;
      condition.valueEnd = null;
      condition.operator =
        condition.type === FILTER_TYPE_TIME ? "BETWEEN" : "EQ";
      condition._dimensionFields = [];
    };

    const addFilterCondition = () => {
      filterConditions.value.push(createCondition("AND"));
    };

    const removeFilterCondition = (index: number) => {
      filterConditions.value.splice(index, 1);
    };

    const appendFormula = (text: string) => {
      if (!text) {
        return;
      }
      formula.value = formula.value ? `${formula.value} ${text}` : text;
    };

    const insertMetricRef = (code: string) => {
      appendFormula("${" + code + "}");
    };

    const insertFieldRef = (fieldName: string) => {
      appendFormula(fieldName);
    };

    const insertAggFunction = (fn: { value: string }) => {
      const field = selectedFieldCode.value || "";
      const expression =
        fn.value === "COUNT_DISTINCT"
          ? `COUNT(DISTINCT ${field})`
          : `${fn.value}(${field})`;
      appendFormula(expression);
    };

    const buildPayload = (): IMetric | null => {
      if (!metric.value.name || !metric.value.name.trim()) {
        alertService.showError("请输入指标名称");
        return null;
      }
      if (!metric.value.code || !metric.value.code.trim()) {
        alertService.showError("请输入指标编码");
        return null;
      }
      if (!/^[A-Za-z0-9_-]+$/.test(metric.value.code.trim())) {
        alertService.showError("指标编码只能包含字母、数字、下划线和中划线");
        return null;
      }
      const payload: IMetric = {
        ...metric.value,
        name: metric.value.name.trim(),
        code: metric.value.code.trim(),
      };
      if (payload.metricType === METRIC_TYPE_ATOMIC) {
        if (!payload.factModelId) {
          alertService.showError("请选择事实表");
          return null;
        }
        if (!formula.value || !formula.value.trim()) {
          alertService.showError("请输入原子指标计算公式");
          return null;
        }
        payload.formula = formula.value.trim();
        payload.filterConfig = stringifyFilterConfig({
          conditions: filterConditions.value,
        });
      } else if (payload.metricType === METRIC_TYPE_DERIVED) {
        if (!formula.value || !formula.value.trim()) {
          alertService.showError("请输入衍生指标公式");
          return null;
        }
        payload.formula = formula.value.trim();
        payload.filterConfig = null;
        payload.factModelId = null;
      } else {
        alertService.showError("请选择指标类型");
        return null;
      }
      return payload;
    };

    const previewSql = async () => {
      const payload = buildPayload();
      if (!payload) {
        return;
      }
      try {
        const res = await metricService().previewSql(payload);
        sqlContent.value = res?.sql || "";
        sqlDialogVisible.value = true;
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    const save = () => {
      const payload = buildPayload();
      if (!payload) {
        return;
      }
      isSaving.value = true;
      const done = (saved: IMetric | null, message: string) => {
        isSaving.value = false;
        alertService.showSuccess(message);
        const id = saved?.id ?? metric.value.id;
        router.push({
          name: "Metric",
          query: id ? { metricId: String(id) } : {},
        });
      };
      const fail = (error: any) => {
        isSaving.value = false;
        alertService.showHttpError(error.response);
      };
      if (metric.value.id) {
        metricService()
          .update(payload)
          .then((saved) => done(saved, "指标已更新"))
          .catch(fail);
      } else {
        metricService()
          .create(payload)
          .then((saved) => done(saved, "指标已创建"))
          .catch(fail);
      }
    };

    const retrieveMetric = async (metricId: number) => {
      try {
        const res = await metricService().find(metricId);
        res.createTime = res.createTime ? new Date(res.createTime) : null;
        res.updateTime = res.updateTime ? new Date(res.updateTime) : null;
        metric.value = res;
        formula.value = res.formula || "";
        filterConditions.value = parseFilterConfig(
          res.filterConfig,
        ).conditions.map((c) => {
          const condition: any = {
            ...createCondition(c.logic || "AND"),
            ...c,
            _dimensionFields: [],
          };
          if (
            condition.type === FILTER_TYPE_DIMENSION &&
            condition.dimensionModelId
          ) {
            dataModelService()
              .getFields(condition.dimensionModelId)
              .then((r) => {
                condition._dimensionFields = r.data || [];
              })
              .catch(() => {});
          }
          return condition;
        });
        await loadFactFields();
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    const init = async () => {
      await loadDirectories();
      await loadFactModels();
      await loadAllMetrics();
      if (route.params?.metricId) {
        isEdit.value = true;
        await retrieveMetric(Number(route.params.metricId));
      } else {
        metric.value = new Metric();
        metric.value.metricType = METRIC_TYPE_ATOMIC;
        metric.value.status = METRIC_STATUS_ENABLED;
        if (route.query.directoryId) {
          metric.value.directoryId = Number(route.query.directoryId);
        }
      }
    };

    init();

    return {
      isEdit,
      isSaving,
      metric,
      formula,
      filterConditions,
      directories,
      factModels,
      factFields,
      allMetrics,
      loadingFields,
      metricTypeOptions,
      statusOptions,
      filterTypeOptions,
      filterOperators,
      dataTypes,
      aggFunctions,
      flatDirectoryOptions,
      dimensionFactFields,
      dateFactFields,
      formulaRefs,
      insertableMetrics,
      sqlDialogVisible,
      sqlContent,
      selectedRefCode,
      selectedFieldCode,
      previousState,
      save,
      onFactModelChange,
      onFilterFactFieldChange,
      onFilterTypeChange,
      addFilterCondition,
      removeFilterCondition,
      insertMetricRef,
      insertFieldRef,
      insertAggFunction,
      previewSql,
      isRangeOperator,
      isUnaryOperator,
      METRIC_TYPE_ATOMIC,
      METRIC_TYPE_DERIVED,
      FILTER_TYPE_FACT_FIELD,
      FILTER_TYPE_DIMENSION,
      FILTER_TYPE_TIME,
    };
  },
});
