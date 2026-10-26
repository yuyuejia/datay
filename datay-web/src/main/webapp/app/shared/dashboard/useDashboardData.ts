import { computed, ref } from 'vue';
import type { Ref } from 'vue';

import DashboardService from './dashboard.service';
import type { DashboardQuery } from './dashboard.service';
import { buildServerAggregation, serverQueryLimit } from './server-aggregation';
import type { Aggregation, DashboardSpec, DatasetResult, FilterValues } from './types';

/**
 * 看板取数组合式函数。
 *
 * - 所有筛选器统一由服务端执行，前端不再做本地行过滤；
 * - 数据集查询与组件级查询都携带当前 filterValues；
 * - 组件级查询额外下推聚合、sort、limit。
 */
export function useDashboardData(config: { dashboardId?: Ref<string | undefined>; spec: Ref<DashboardSpec | undefined> }) {
  const service = new DashboardService();
  const datasetData = ref<Record<string, DatasetResult>>({});
  const rawDatasetData = ref<Record<string, DatasetResult>>({});
  const widgetData = ref<Record<string, DatasetResult>>({});
  const rawWidgetData = ref<Record<string, DatasetResult>>({});
  const loadingDatasets = ref<Record<string, boolean>>({});
  const datasetErrors = ref<Record<string, string>>({});
  const widgetErrors = ref<Record<string, string>>({});
  const loading = ref(false);
  const dataVersion = ref(0);
  let lastServerSignature = '';
  let loadSequence = 0;
  let pageSequence = 0;
  let currentFilterValues: FilterValues = {};

  /**
   * 同一数据集、同一聚合与 sort/limit 的组件共用一次查询。
   */
  const buildWidgetQueries = (spec: DashboardSpec): { queries: DashboardQuery[]; byWidget: Record<string, string> } => {
    const queries: DashboardQuery[] = [];
    const byWidget: Record<string, string> = {};
    const keyToId = new Map<string, string>();
    for (const widget of spec.widgets ?? []) {
      if (!widget.datasetId || widget.type === 'html') {
        continue;
      }
      const encode = widget.encode;
      const sortField = encode?.sortField || undefined;
      const serverPaged = widget.type === 'table' && !!encode?.pageSize;
      const aggregation = buildServerAggregation(widget);
      const hasAggregation = aggregation.groupFields.length > 0 && aggregation.aggregations.length > 0;
      const limit = serverQueryLimit(widget, serverPaged);
      const offset = serverPaged ? 0 : undefined;
      if (!sortField && !limit && !hasAggregation) {
        continue;
      }
      const key = JSON.stringify([
        widget.datasetId,
        sortField ?? null,
        encode?.sortOrder ?? null,
        limit ?? null,
        offset ?? null,
        aggregation.groupFields,
        aggregation.aggregations,
      ]);
      let queryId = keyToId.get(key);
      if (!queryId) {
        queryId = `q${keyToId.size + 1}`;
        keyToId.set(key, queryId);
        queries.push({
          id: queryId,
          datasetId: widget.datasetId,
          sortField,
          sortOrder: encode?.sortOrder,
          limit,
          offset,
          ...(hasAggregation ? { groupFields: aggregation.groupFields, aggregations: aggregation.aggregations } : {}),
        });
      }
      byWidget[widget.id] = queryId;
    }
    return { queries, byWidget };
  };

  const serverPaginationWidgets = computed<Record<string, boolean>>(() => {
    const result: Record<string, boolean> = {};
    const spec = config.spec.value;
    if (!spec) {
      return result;
    }
    for (const widget of spec.widgets ?? []) {
      if (widget.type === 'table' && widget.encode?.pageSize && widget.datasetId) {
        result[widget.id] = true;
      }
    }
    return result;
  });

  /** 服务端结果已经包含筛选，直接同步到视图，不再做本地行过滤。 */
  const syncBatchResults = (spec: DashboardSpec) => {
    widgetData.value = {};
    for (const dataset of spec.datasets ?? []) {
      const raw = rawDatasetData.value[dataset.id];
      if (raw) {
        datasetData.value[dataset.id] = raw;
      }
    }
    const { byWidget } = buildWidgetQueries(spec);
    for (const widget of spec.widgets ?? []) {
      const queryId = byWidget[widget.id];
      if (!queryId) {
        continue;
      }
      const raw = rawWidgetData.value[queryId];
      if (raw) {
        widgetData.value[widget.id] = raw;
      }
    }
  };

  /**
   * 服务端筛选签名：所有带字段/pushdown 目标的筛选器都参与签名。
   */
  const serverFilterSignature = (spec: DashboardSpec, filterValues: FilterValues): string => {
    const serverFilters = (spec.filters ?? [])
      .map(filter => {
        const targets = (filter.targets ?? []).filter(target => !!target.pushdown || !!target.field);
        return targets.length ? { id: filter.id, targets, value: filterValues[filter.id] } : undefined;
      })
      .filter((item): item is { id: string; targets: any[]; value: any } => !!item);
    return JSON.stringify({
      dashboardId: config.dashboardId?.value,
      datasets: (spec.datasets ?? []).map(dataset => [dataset.id, dataset.refId]),
      filters: serverFilters,
      queries: buildWidgetQueries(spec).queries,
    });
  };

  const queryBatch = (datasetIds: string[], queries: DashboardQuery[], filterValues: FilterValues) => {
    const id = config.dashboardId?.value;
    if (id) {
      return service.queryWidgets(id, datasetIds, queries, filterValues);
    }
    if (!config.spec.value) {
      return Promise.reject(new Error('看板尚未就绪'));
    }
    return service.previewWidgets(config.spec.value, datasetIds, queries, filterValues);
  };

  const loadAll = async (filterValues: FilterValues, options: { force?: boolean } = {}) => {
    const spec = config.spec.value;
    if (!spec) {
      return;
    }
    const signature = serverFilterSignature(spec, filterValues);
    if (!options.force && signature === lastServerSignature && Object.keys(rawDatasetData.value).length > 0) {
      syncBatchResults(spec);
      return;
    }
    const datasetIds = Array.from(new Set((spec.datasets ?? []).map(dataset => dataset.id)));
    if (datasetIds.length === 0) {
      return;
    }
    const { queries, byWidget } = buildWidgetQueries(spec);
    const sequence = ++loadSequence;
    pageSequence += 1;
    dataVersion.value += 1;
    loading.value = true;
    for (const datasetId of datasetIds) {
      loadingDatasets.value[datasetId] = true;
      datasetErrors.value[datasetId] = '';
    }
    widgetErrors.value = {};
    for (const widgetId of Object.keys(byWidget)) {
      widgetErrors.value[widgetId] = '';
    }
    try {
      const batch = await queryBatch(datasetIds, queries, filterValues);
      if (sequence !== loadSequence) {
        return;
      }
      rawDatasetData.value = { ...(batch.datasets ?? {}) };
      rawWidgetData.value = { ...(batch.widgets ?? {}) };
      for (const datasetId of datasetIds) {
        const error = batch.errors?.[datasetId];
        if (error) {
          datasetErrors.value[datasetId] = error;
        }
      }
      for (const widget of spec.widgets ?? []) {
        const queryId = byWidget[widget.id];
        if (!queryId) {
          continue;
        }
        const error = batch.widgetErrors?.[queryId];
        if (error) {
          widgetErrors.value[widget.id] = error;
        }
      }
      syncBatchResults(spec);
    } catch (error: any) {
      if (sequence !== loadSequence) {
        return;
      }
      const message =
        error?.response?.data?.title || error?.response?.data?.detail || error?.response?.data?.message || error?.message || '查询失败';
      for (const datasetId of datasetIds) {
        datasetErrors.value[datasetId] = message;
      }
      for (const widgetId of Object.keys(byWidget)) {
        widgetErrors.value[widgetId] = message;
      }
    } finally {
      if (sequence === loadSequence) {
        for (const datasetId of datasetIds) {
          loadingDatasets.value[datasetId] = false;
        }
        lastServerSignature = signature;
        loading.value = false;
      }
    }
  };

  const loadWidgetPage = async (widgetId: string, page: number) => {
    const spec = config.spec.value;
    const widget = spec?.widgets.find(item => item.id === widgetId);
    const pageSize = widget?.encode?.pageSize;
    if (!spec || !widget || !widget.datasetId || !pageSize) {
      return;
    }
    const sequence = ++pageSequence;
    const query: DashboardQuery = {
      id: `page:${widget.id}`,
      datasetId: widget.datasetId,
      sortField: widget.encode?.sortField || undefined,
      sortOrder: widget.encode?.sortOrder,
      limit: pageSize,
      offset: Math.max(0, (page - 1) * pageSize),
    };
    try {
      const batch = await queryBatch([], [query], currentFilterValues);
      if (sequence !== pageSequence) {
        return;
      }
      const result = batch.widgets?.[query.id];
      if (!result) {
        widgetErrors.value[widget.id] = batch.widgetErrors?.[query.id] || '分页查询失败';
        return;
      }
      widgetErrors.value[widget.id] = '';
      rawWidgetData.value[query.id] = result;
      widgetData.value[widget.id] = result;
    } catch (error: any) {
      if (sequence !== pageSequence) {
        return;
      }
      widgetErrors.value[widget.id] = error?.response?.data?.message || error?.response?.data?.title || error?.message || '分页查询失败';
    }
  };

  const loadAllWrapped = async (filterValues: FilterValues, options: { force?: boolean } = {}) => {
    currentFilterValues = filterValues;
    await loadAll(filterValues, options);
  };

  return {
    datasetData,
    rawDatasetData,
    widgetData,
    loadingDatasets,
    datasetErrors,
    widgetErrors,
    serverPaginationWidgets,
    dataVersion,
    loading,
    loadAll: loadAllWrapped,
    loadWidgetPage,
  };
}
