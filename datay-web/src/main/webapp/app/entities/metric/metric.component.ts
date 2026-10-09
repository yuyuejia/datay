import {
  type Ref,
  computed,
  defineComponent,
  inject,
  nextTick,
  onMounted,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter } from "vue-router";

import MetricService from "./metric.service";
import MetricDirectoryService from "./metric-directory.service";
import {
  type IMetric,
  type IMetricFilterCondition,
  METRIC_TYPE_ATOMIC,
  METRIC_TYPE_DERIVED,
  FILTER_TYPE_DIMENSION,
  FILTER_TYPE_TIME,
  FILTER_OPERATORS,
  parseFilterConfig,
  stringifyFilterConfig,
  dataTypeLabel,
  operatorLabel,
  isUnaryOperator,
  isRangeOperator,
} from "@/shared/model/metric.model";
import {
  type IMetricDirectory,
  MetricDirectory,
} from "@/shared/model/metric-directory.model";
import { useDateFormat } from "@/shared/composables";
import { useAlertService } from "@/shared/alert/alert.service";

const toDateTimeText = (value: any): string | null => {
  if (!value) {
    return null;
  }
  const text = String(value).trim().replace("T", " ");
  if (!text) {
    return null;
  }
  // datetime-local 值为 YYYY-MM-DDTHH:mm（或含秒），统一为 YYYY-MM-DD HH:mm:ss
  return text.length === 16 ? `${text}:00` : text;
};

interface TreeNode {
  id: string;
  label: string;
  type: "directory" | "metric";
  children?: TreeNode[];
  data?: any;
}

export default defineComponent({
  name: "Metric",
  setup() {
    const dateFormat = useDateFormat();
    const metricService = inject("metricService", () => new MetricService());
    const metricDirectoryService = inject(
      "metricDirectoryService",
      () => new MetricDirectoryService(),
    );
    const alertService = inject("alertService", () => useAlertService(), true);

    const router = useRouter();
    const route = useRoute();

    const treeData: Ref<TreeNode[]> = ref([]);
    const treeProps = { children: "children", label: "label" };
    const treeRef = ref(null);
    const treeSearch = ref("");
    const loading = ref(false);
    const queryMode = ref(false);

    const filterNode = (value: string, data: any) => {
      if (!value) {
        return true;
      }
      if (data.type !== "metric") {
        return false;
      }
      const keyword = value.toLowerCase();
      const metric = data.data || {};
      return (
        (data.label || "").toLowerCase().includes(keyword) ||
        (metric.code || "").toLowerCase().includes(keyword)
      );
    };

    watch(treeSearch, (value) => {
      (treeRef.value as any)?.filter(value);
    });

    const selectedMetric: Ref<IMetric | null> = ref(null);
    const selectedDirectoryId = ref<string | null>(null);

    const directoryDialogVisible = ref(false);
    const directoryDialogTitle = ref("新增目录");
    const directoryForm: Ref<IMetricDirectory> = ref(new MetricDirectory());
    const editingDirectoryId: Ref<string | null> = ref(null);

    const deleteDialogVisible = ref(false);
    const deleteMessage = ref("");
    const deleteTarget: Ref<any> = ref(null);
    const deleteType = ref("");

    const sqlDialogVisible = ref(false);
    const sqlLoading = ref(false);
    const sqlContent = ref("");

    const selectedConditions = computed<IMetricFilterCondition[]>(
      () => parseFilterConfig(selectedMetric.value?.filterConfig).conditions,
    );

    const loadTree = async () => {
      loading.value = true;
      try {
        const dirRes = await metricDirectoryService().retrieve();
        const directories: IMetricDirectory[] = dirRes.data || [];
        const metricRes = await metricService().retrieveAll();
        const metrics: IMetric[] = metricRes.data || [];

        const metricMap = new Map<string, IMetric[]>();
        for (const metric of metrics) {
          const dirId = metric.directoryId || "";
          if (!metricMap.has(dirId)) {
            metricMap.set(dirId, []);
          }
          metricMap.get(dirId)!.push(metric);
        }

        const buildTree = (parentId: string | null): TreeNode[] => {
          const children: TreeNode[] = [];
          directories
            .filter((d) => (d.parentId || null) === parentId)
            .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
            .forEach((dir) => {
              const subChildren = buildTree(dir.id!);
              (metricMap.get(dir.id!) || []).forEach((metric) => {
                subChildren.push({
                  id: metric.id!,
                  label: metric.name || "",
                  type: "metric",
                  data: metric,
                });
              });
              const node: TreeNode = {
                id: dir.id!,
                label: dir.name || "",
                type: "directory",
                data: dir,
              };
              if (subChildren.length > 0) {
                node.children = subChildren;
              }
              children.push(node);
            });
          return children;
        };

        const rootNodes = buildTree(null);
        (metricMap.get("") || []).forEach((metric) => {
          rootNodes.push({
            id: metric.id!,
            label: metric.name || "",
            type: "metric",
            data: metric,
          });
        });
        treeData.value = rootNodes;
        await nextTick();
        (treeRef.value as any)?.filter(treeSearch.value);
      } catch (err: any) {
        alertService.showHttpError(err.response);
      } finally {
        loading.value = false;
      }
    };

    const handleNodeClick = (data: TreeNode) => {
      queryMode.value = false;
      if (data.type === "metric") {
        selectedMetric.value = data.data;
        selectedDirectoryId.value = data.data.directoryId ?? null;
      } else {
        selectedMetric.value = null;
        selectedDirectoryId.value = data.id;
      }
    };

    const selectMetricId = async (metricId: string) => {
      const findNode = (nodes: TreeNode[]): TreeNode | null => {
        for (const node of nodes) {
          if (node.type === "metric" && node.id === metricId) {
            return node;
          }
          if (node.children) {
            const found = findNode(node.children);
            if (found) {
              return found;
            }
          }
        }
        return null;
      };
      const target = findNode(treeData.value);
      if (target) {
        await nextTick();
        handleNodeClick(target);
        (treeRef.value as any)?.setCurrentKey(target.id);
      }
    };

    const showAddDirectoryDialog = (parentId: string | null) => {
      editingDirectoryId.value = null;
      directoryDialogTitle.value = "新增目录";
      directoryForm.value = new MetricDirectory();
      directoryForm.value.parentId = parentId;
      directoryForm.value.sortOrder = 0;
      directoryDialogVisible.value = true;
    };

    const saveDirectory = async () => {
      if (!directoryForm.value.name || !directoryForm.value.name.trim()) {
        alertService.showError("请输入目录名称");
        return;
      }
      try {
        if (editingDirectoryId.value) {
          directoryForm.value.id = editingDirectoryId.value;
          await metricDirectoryService().update(directoryForm.value);
        } else {
          await metricDirectoryService().create(directoryForm.value);
        }
        directoryDialogVisible.value = false;
        alertService.showSuccess("目录保存成功");
        await loadTree();
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    const showAddMetricDialog = (directoryId: string | null) => {
      router.push({
        name: "MetricCreate",
        query: directoryId != null ? { directoryId: String(directoryId) } : {},
      });
    };

    const handleDeleteNode = (data: TreeNode) => {
      deleteTarget.value = data;
      deleteType.value = data.type;
      if (data.type === "directory") {
        deleteMessage.value = `确定要删除目录"${data.label}"吗？删除后该目录下的子目录与指标将移动到根目录。`;
      } else {
        deleteMessage.value = `确定要删除指标"${data.label}"吗？`;
      }
      deleteDialogVisible.value = true;
    };

    const prepareRemove = (metric: IMetric) => {
      deleteTarget.value = {
        id: metric.id,
        label: metric.name,
        type: "metric",
      };
      deleteType.value = "metric";
      deleteMessage.value = `确定要删除指标"${metric.name}"吗？`;
      deleteDialogVisible.value = true;
    };

    const confirmDelete = async () => {
      try {
        if (deleteType.value === "directory") {
          await metricDirectoryService().delete(deleteTarget.value.id);
          if (selectedDirectoryId.value === deleteTarget.value.id) {
            selectedDirectoryId.value = null;
          }
          alertService.showSuccess("目录删除成功");
        } else {
          await metricService().delete(deleteTarget.value.id);
          if (selectedMetric.value?.id === deleteTarget.value.id) {
            selectedMetric.value = null;
          }
          alertService.showSuccess("指标删除成功");
        }
        deleteDialogVisible.value = false;
        await loadTree();
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    // ===== 公共指标查询 =====
    const queryMetaLoading = ref(false);
    const queryMeta = ref<any>(null);
    const queryMetricRows: Ref<any[]> = ref([]);
    const queryDimensions: Ref<any[]> = ref([]);
    const queryConditions: Ref<any[]> = ref([]);
    const queryTimeRange: Ref<any> = ref({
      start: "",
      end: "",
    });
    const queryLoading = ref(false);
    const queryColumns: Ref<string[]> = ref([]);
    const queryRows: Ref<any[]> = ref([]);
    const querySql = ref("");

    const filterOperators = FILTER_OPERATORS;

    const allMetricOptions = computed<IMetric[]>(() => {
      const out: IMetric[] = [];
      const walk = (nodes: TreeNode[]) => {
        nodes.forEach((node) => {
          if (node.type === "metric" && node.data) {
            out.push(node.data);
          }
          if (node.children) {
            walk(node.children);
          }
        });
      };
      walk(treeData.value);
      return out;
    });

    const queryDimensionOptions = computed<any[]>(
      () => queryMeta.value?.dimensions || [],
    );
    const queryTimeFields = computed<any[]>(
      () => queryMeta.value?.timeFields || [],
    );
    const selectedQueryMetricCodes = computed<string[]>(() =>
      queryMetricRows.value
        .map((row: any) => row.metricCode)
        .filter((code: any): code is string => !!code),
    );

    const loadDimensionFieldsFor = (target: any) => {
      const option = queryDimensionOptions.value.find(
        (o: any) => o.dimensionModelCode === target.dimensionModelCode,
      );
      target._dimensionFields = option?.fields || [];
    };

    const resetQueryForm = () => {
      queryDimensions.value = [];
      queryConditions.value = [];
      queryTimeRange.value = { start: "", end: "" };
      queryColumns.value = [];
      queryRows.value = [];
      querySql.value = "";
    };

    const loadQueryMeta = async () => {
      if (selectedQueryMetricCodes.value.length === 0) {
        queryMeta.value = null;
        resetQueryForm();
        return;
      }
      queryMetaLoading.value = true;
      try {
        const res = await metricService().getQueryMeta({
          metricCodes: selectedQueryMetricCodes.value,
        });
        queryMeta.value = res;
        resetQueryForm();
      } catch (err: any) {
        alertService.showHttpError(err.response);
      } finally {
        queryMetaLoading.value = false;
      }
    };

    const openQuery = async () => {
      queryMode.value = true;
      queryMetricRows.value = [
        { metricCode: selectedMetric.value?.code ?? null },
      ];
      await loadQueryMeta();
    };

    const closeQuery = () => {
      queryMode.value = false;
    };

    const addQueryMetric = () => {
      queryMetricRows.value.push({ metricCode: null });
    };

    const removeQueryMetric = async (index: number) => {
      queryMetricRows.value.splice(index, 1);
      await loadQueryMeta();
    };

    const onQueryMetricChange = async () => {
      await loadQueryMeta();
    };

    const addDimension = () => {
      queryDimensions.value.push({
        dimensionModelCode: null,
        dimensionFieldNames: [],
        levelIndex: null,
        _dimensionFields: [],
      });
    };

    const removeDimension = (index: number) => {
      queryDimensions.value.splice(index, 1);
    };

    const dimensionOption = (dimension: any) => {
      return queryDimensionOptions.value.find(
        (o: any) => o.dimensionModelCode === dimension.dimensionModelCode,
      );
    };

    const isHierarchyDimension = (dimension: any) => {
      return !!dimensionOption(dimension)?.isHierarchy;
    };

    const dimensionLevels = (dimension: any) => {
      const option = dimensionOption(dimension);
      if (!option?.isHierarchy) {
        return [];
      }
      if (Array.isArray(option.levels) && option.levels.length > 0) {
        return option.levels;
      }
      const count = Number(option.levelCount) || 0;
      return Array.from({ length: count }, (_v, i) => ({ levelIndex: i + 1 }));
    };

    const levelLabel = (dimension: any, levelIndex: number) => {
      const levels = dimensionLevels(dimension);
      const current = levels.find((l: any) => l.levelIndex === levelIndex);
      if (current && current.label) {
        return current.label;
      }
      const last =
        levels.length > 0 &&
        levels[levels.length - 1].levelIndex === levelIndex;
      const names = [
        "一",
        "二",
        "三",
        "四",
        "五",
        "六",
        "七",
        "八",
        "九",
        "十",
      ];
      const label = names[levelIndex - 1]
        ? names[levelIndex - 1] + "级"
        : `第${levelIndex}级`;
      return last ? `${label}(末级)` : label;
    };

    const onDimensionModelChange = async (dimension: any) => {
      dimension.dimensionFieldNames = [];
      dimension.levelIndex = null;
      await loadDimensionFieldsFor(dimension);
      if (isHierarchyDimension(dimension)) {
        const levels = dimensionLevels(dimension);
        dimension.levelIndex =
          levels.length > 0 ? levels[levels.length - 1].levelIndex : 1;
      }
    };

    const toggleDimensionField = (dimension: any, fieldName: string) => {
      if (!dimension.dimensionFieldNames) {
        dimension.dimensionFieldNames = [];
      }
      const index = dimension.dimensionFieldNames.indexOf(fieldName);
      if (index >= 0) {
        dimension.dimensionFieldNames.splice(index, 1);
      } else {
        dimension.dimensionFieldNames.push(fieldName);
      }
    };

    const dimensionSelectionText = (dimension: any) => {
      const selected = dimension.dimensionFieldNames || [];
      return selected.length > 0 ? selected.join(", ") : "默认按主键汇总";
    };

    const createQueryCondition = () => ({
      dimensionModelCode: null,
      dimensionFieldName: null,
      operator: "EQ",
      value: null,
      valueEnd: null,
      logic: "AND",
      _dimensionFields: [],
    });

    const addQueryCondition = () => {
      queryConditions.value.push(createQueryCondition());
    };

    const removeQueryCondition = (index: number) => {
      queryConditions.value.splice(index, 1);
    };

    const onQueryConditionModelChange = async (condition: any) => {
      condition.dimensionFieldName = null;
      await loadDimensionFieldsFor(condition);
    };

    const clearQueryTimeRange = () => {
      queryTimeRange.value = { start: "", end: "" };
    };

    const buildQueryPayload = () => {
      const dimensions = queryDimensions.value
        .map((d: any) => {
          if (!d.dimensionModelCode) {
            return null;
          }
          if (isHierarchyDimension(d)) {
            if (d.levelIndex == null) {
              return null;
            }
            return {
              dimensionModelCode: d.dimensionModelCode,
              levelIndex: d.levelIndex,
            };
          }
          // 未选择显示字段时不传字段，后端默认按维度主键汇总并显示主键
          return {
            dimensionModelCode: d.dimensionModelCode,
            dimensionFieldNames: d.dimensionFieldNames || [],
          };
        })
        .filter((d: any) => d !== null);
      const conditions = queryConditions.value
        .filter((c: any) => c.dimensionModelCode && c.dimensionFieldName)
        .map((c: any) => ({
          type: FILTER_TYPE_DIMENSION,
          dimensionModelCode: c.dimensionModelCode,
          dimensionFieldName: c.dimensionFieldName,
          operator: c.operator,
          value: c.value,
          valueEnd: c.valueEnd,
          logic: c.logic,
        }));
      const range = queryTimeRange.value;
      const start = toDateTimeText(range.start);
      const end = toDateTimeText(range.end);
      const timeRange =
        start || end
          ? {
              start,
              end,
            }
          : null;
      return {
        metricCodes: selectedQueryMetricCodes.value,
        dimensions,
        filterConfig:
          conditions.length > 0
            ? stringifyFilterConfig({ conditions } as any)
            : null,
        timeRange,
      };
    };

    const runQuery = async () => {
      if (selectedQueryMetricCodes.value.length === 0) {
        alertService.showError("请至少选择一个指标");
        return;
      }
      queryLoading.value = true;
      try {
        const res = await metricService().queryMetricData(buildQueryPayload());
        queryColumns.value = res.columns || [];
        queryRows.value = res.rows || [];
        querySql.value = res.sql || "";
      } catch (err: any) {
        alertService.showHttpError(err.response);
      } finally {
        queryLoading.value = false;
      }
    };

    const previewQuerySql = async () => {
      if (selectedQueryMetricCodes.value.length === 0) {
        alertService.showError("请至少选择一个指标");
        return;
      }
      try {
        const res = await metricService().buildQuerySql(buildQueryPayload());
        querySql.value = res.sql || "";
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    const previewSql = async (metric: IMetric) => {
      if (!metric.id) {
        return;
      }
      sqlContent.value = "";
      sqlDialogVisible.value = true;
      sqlLoading.value = true;
      try {
        const res = await metricService().previewSqlById(metric.id);
        sqlContent.value = res?.sql || "";
      } catch (err: any) {
        sqlDialogVisible.value = false;
        alertService.showHttpError(err.response);
      } finally {
        sqlLoading.value = false;
      }
    };

    const typeLabel = (type?: string | null) => {
      if (type === METRIC_TYPE_ATOMIC) {
        return "原子指标";
      }
      if (type === METRIC_TYPE_DERIVED) {
        return "衍生原子指标";
      }
      return type || "-";
    };

    const typeTag = (type?: string | null) => {
      if (type === METRIC_TYPE_ATOMIC) {
        return "success";
      }
      if (type === METRIC_TYPE_DERIVED) {
        return "warning";
      }
      return "info";
    };

    const statusLabel = (status?: string | null) => {
      return status === "DISABLED" ? "停用" : "启用";
    };

    const factDisplay = (metric: IMetric) => {
      if (metric.metricType !== METRIC_TYPE_ATOMIC) {
        return "-";
      }
      return metric.factModelName || metric.factTableName || "-";
    };

    const filterTypeLabel = (type?: string | null) => {
      if (type === FILTER_TYPE_DIMENSION) {
        return "维度";
      }
      if (type === FILTER_TYPE_TIME) {
        return "时间";
      }
      return "事实字段";
    };

    const conditionTarget = (condition: IMetricFilterCondition) => {
      if (condition.type === FILTER_TYPE_DIMENSION) {
        return `${condition.factFieldName || "-"} → ${condition.dimensionFieldName || "-"}`;
      }
      return condition.factFieldName || "-";
    };

    const conditionValue = (condition: IMetricFilterCondition) => {
      if (isUnaryOperator(condition.operator)) {
        return "-";
      }
      if (isRangeOperator(condition.operator)) {
        return `${condition.value ?? ""} ~ ${condition.valueEnd ?? ""}`;
      }
      return condition.value ?? "-";
    };

    onMounted(async () => {
      await loadTree();
      const metricId = route.query.metricId;
      if (metricId) {
        await selectMetricId(String(metricId));
      }
    });

    return {
      treeData,
      treeProps,
      treeRef,
      treeSearch,
      filterNode,
      loading,
      selectedMetric,
      selectedDirectoryId,
      selectedConditions,
      directoryDialogVisible,
      directoryDialogTitle,
      directoryForm,
      deleteDialogVisible,
      deleteMessage,
      sqlDialogVisible,
      sqlLoading,
      sqlContent,
      handleNodeClick,
      showAddDirectoryDialog,
      saveDirectory,
      showAddMetricDialog,
      handleDeleteNode,
      prepareRemove,
      confirmDelete,
      previewSql,
      typeLabel,
      typeTag,
      statusLabel,
      factDisplay,
      filterTypeLabel,
      conditionTarget,
      conditionValue,
      dataTypeLabel,
      operatorLabel,
      queryMode,
      queryMetaLoading,
      queryMeta,
      queryMetricRows,
      queryDimensions,
      queryConditions,
      queryTimeRange,
      queryLoading,
      queryColumns,
      queryRows,
      querySql,
      filterOperators,
      allMetricOptions,
      queryDimensionOptions,
      queryTimeFields,
      selectedQueryMetricCodes,
      openQuery,
      closeQuery,
      addQueryMetric,
      removeQueryMetric,
      onQueryMetricChange,
      addDimension,
      removeDimension,
      onDimensionModelChange,
      toggleDimensionField,
      dimensionSelectionText,
      isHierarchyDimension,
      dimensionLevels,
      levelLabel,
      addQueryCondition,
      removeQueryCondition,
      onQueryConditionModelChange,
      clearQueryTimeRange,
      runQuery,
      previewQuerySql,
      isUnaryOperator,
      isRangeOperator,
      ...dateFormat,
    };
  },
});
