import { computed, ref } from 'vue';
import type { Ref } from 'vue';

import DashboardService from './dashboard.service';
import type { DashboardQuery } from './dashboard.service';
import { applyDashboardFilters } from './filter-utils';
import type { DashboardSpec, DatasetResult, FilterValues } from './types';

/**
 * 看板取数组合式函数。
 *
 * - 数据集原始查询用于筛选选项与设计器字段候选；
 * - 组件级查询用于把 encode.sort/limit 下推到服务端；
 * - 纯客户端筛选变化只重算结果，不发网络请求。
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

  const hasClientTarget = (spec: DashboardSpec, datasetId: string): boolean =>
    (spec.filters ?? []).some(filter =>
      (filter.targets ?? []).some(target => target.datasetId === datasetId && !!target.field && target.mode !== 'server'),
    );

  /**
   * 同一数据集、同一 sort/limit 的组件共用一次查询。
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
      const serverPaged = widget.type === 'table' && !!encode?.pageSize && !hasClientTarget(spec, widget.datasetId);
      const limit = serverPaged ? encode.pageSize : hasClientTarget(spec, widget.datasetId) ? undefined : encode?.limit;
      const offset = serverPaged ? 0 : undefined;
      if (!sortField && !limit) {
        continue;
      }
      const key = JSON.stringify([widget.datasetId, sortField ?? null, encode?.sortOrder ?? null, limit ?? null, offset ?? null]);
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
        result[widget.id] = !hasClientTarget(spec, widget.datasetId);
      }
    }
    return result;
  });

  const applyClientFilters = (spec: DashboardSpec, filterValues: FilterValues) => {
    widgetData.value = {};
    for (const dataset of spec.datasets ?? []) {
      const raw = rawDatasetData.value[dataset.id];
      if (raw) {
        datasetData.value[dataset.id] = applyDashboardFilters(raw, spec.filters, filterValues, dataset.id);
      }
    }
    const { byWidget } = buildWidgetQueries(spec);
    for (const widget of spec.widgets ?? []) {
      const queryId = byWidget[widget.id];
      if (!queryId || !widget.datasetId) {
        continue;
      }
      const raw = rawWidgetData.value[queryId];
      if (raw) {
        widgetData.value[widget.id] = applyDashboardFilters(raw, spec.filters, filterValues, widget.datasetId);
      }
    }
  };

  /**
   * 服务端筛选签名：只有 pushdown / server 目标、组件 sort/limit 变化时才需要重新请求。
   */
  const serverFilterSignature = (spec: DashboardSpec, filterValues: FilterValues): string => {
    const serverFilters = (spec.filters ?? [])
      .map(filter => {
        const targets = (filter.targets ?? []).filter(target => !!target.pushdown || (!!target.field && target.mode === 'server'));
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
      applyClientFilters(spec, filterValues);
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
      applyClientFilters(spec, filterValues);
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
      widgetData.value[widget.id] = applyDashboardFilters(result, spec.filters, currentFilterValues, widget.datasetId);
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
