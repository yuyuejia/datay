import { computed, ref, watch } from 'vue';
import type { Ref } from 'vue';

import DashboardService from './dashboard.service';
import type { DashboardQuery, DrillQuery } from './dashboard.service';
import { buildServerAggregation, serverQueryLimit } from './server-aggregation';
import type {
  ActiveWidgetLinkage,
  Aggregation,
  DashboardSpec,
  DashboardWidget,
  DatasetResult,
  DrillDimension,
  FilterValues,
  LinkageCondition,
  LinkageSelection,
  WidgetDrillLoader,
  WidgetLinkageMapping,
} from './types';

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
  /** 联动等局部刷新时只标记受影响组件的加载状态，避免整块看板闪烁。 */
  const loadingWidgets = ref<Record<string, boolean>>({});
  /** 局部刷新时递增的组件分页重置计数，与 dataVersion 叠加使用。 */
  const widgetResetKeys = ref<Record<string, number>>({});
  let lastServerSignature = '';
  let loadSequence = 0;
  let pageSequence = 0;
  let linkageSequence = 0;
  let currentFilterValues: FilterValues = {};

  // ---------------------------------------------------------------- 图表联动

  const linkage = ref<ActiveWidgetLinkage | null>(null);

  /** 同数据集直接复用点击字段；跨数据集按配置的字段关联转换。 */
  const resolveLinkageConditions = (
    source: DashboardWidget | undefined,
    target: DashboardWidget,
    conditions: LinkageCondition[],
    fieldMap?: WidgetLinkageMapping[],
  ): LinkageCondition[] => {
    if (!source) {
      return [];
    }
    if (source.datasetId && source.datasetId === target.datasetId) {
      return conditions;
    }
    const mapping = new Map((fieldMap ?? []).map(item => [item.sourceField, item.targetField]));
    const result: LinkageCondition[] = [];
    for (const condition of conditions) {
      const targetField = mapping.get(condition.field);
      if (targetField) {
        result.push({ field: targetField, values: condition.values });
      }
    }
    return result;
  };

  /** 计算某个联动状态下每个目标组件生效的条件；未配置关联关系的跨数据集目标不会出现在这里。 */
  const resolveLinkageConditionsFor = (
    spec: DashboardSpec | undefined,
    active: ActiveWidgetLinkage | null,
  ): Record<string, LinkageCondition[]> => {
    if (!spec || !active) {
      return {};
    }
    const source = (spec.widgets ?? []).find(widget => widget.id === active.sourceWidgetId);
    if (!source) {
      return {};
    }
    const result: Record<string, LinkageCondition[]> = {};
    for (const targetConfig of source.interaction?.targets ?? []) {
      const target = (spec.widgets ?? []).find(widget => widget.id === targetConfig.widgetId);
      if (!target) {
        continue;
      }
      const conditions = resolveLinkageConditions(source, target, active.conditions, targetConfig.fieldMap);
      if (conditions.length > 0) {
        result[target.id] = conditions;
      }
    }
    return result;
  };

  const linkageConditionsByWidget = computed<Record<string, LinkageCondition[]>>(() =>
    resolveLinkageConditionsFor(config.spec.value, linkage.value),
  );

  const linkageWidgetIds = computed<Record<string, boolean>>(() => {
    const result: Record<string, boolean> = {};
    for (const id of Object.keys(linkageConditionsByWidget.value)) {
      result[id] = true;
    }
    return result;
  });

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
      // 分组字段可以为空（KPI / 仪表盘等全局聚合），只要存在度量聚合就下推。
      const hasAggregation = aggregation.aggregations.length > 0;
      const limit = serverQueryLimit(widget, serverPaged);
      const offset = serverPaged ? 0 : undefined;
      const conditions = linkageConditionsByWidget.value[widget.id] ?? [];
      const hasConditions = conditions.length > 0;
      if (!sortField && !limit && !hasAggregation && !hasConditions) {
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
        conditions,
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
          ...(hasConditions ? { conditions } : {}),
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
    // 全量刷新会覆盖联动结果，取消尚未返回的局部联动刷新。
    linkageSequence += 1;
    loadingWidgets.value = {};
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
    const conditions = linkageConditionsByWidget.value[widgetId] ?? [];
    const query: DashboardQuery = {
      id: `page:${widget.id}`,
      datasetId: widget.datasetId,
      sortField: widget.encode?.sortField || undefined,
      sortOrder: widget.encode?.sortOrder,
      limit: pageSize,
      offset: Math.max(0, (page - 1) * pageSize),
      ...(conditions.length > 0 ? { conditions } : {}),
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

  /**
   * 只刷新受联动影响的组件。
   *
   * <p>与全量 loadAll 不同：不重新查询数据集、不清空其它组件数据、不触发全局 loading；
   * 旧目标恢复为未过滤查询，新目标按联动条件下推，全部用一个轻量批量请求完成。</p>
   */
  const refreshLinkage = async (previous: ActiveWidgetLinkage | null, next: ActiveWidgetLinkage | null) => {
    const spec = config.spec.value;
    if (!spec) {
      return;
    }
    // 仅切换选中数据项时联动条件不变，只需更新源图表高亮，不需要重新查询目标组件。
    const signatureOf = (state: ActiveWidgetLinkage | null) =>
      JSON.stringify(state ? [state.sourceWidgetId, state.conditions, state.targetWidgetIds] : null);
    if (signatureOf(previous) === signatureOf(next)) {
      return;
    }
    const affected = new Set<string>([
      ...(previous?.targetWidgetIds ?? Object.keys(resolveLinkageConditionsFor(spec, previous))),
      ...(next?.targetWidgetIds ?? Object.keys(resolveLinkageConditionsFor(spec, next))),
    ]);
    if (affected.size === 0) {
      return;
    }
    const sequence = ++linkageSequence;
    const { queries, byWidget } = buildWidgetQueries(spec);
    const queryById = new Map(queries.map(query => [query.id, query]));
    const requestById = new Map<string, DashboardQuery>();
    const widgetQueries = new Map<string, DashboardQuery>();
    const fallbackWidgets: string[] = [];
    for (const widgetId of affected) {
      const queryId = byWidget[widgetId];
      const query = queryId ? queryById.get(queryId) : undefined;
      if (!query) {
        // 取消联动后组件可能不再需要组件级查询，回落到未过滤的数据集结果。
        fallbackWidgets.push(widgetId);
        continue;
      }
      requestById.set(query.id, query);
      widgetQueries.set(widgetId, query);
    }
    for (const widgetId of affected) {
      loadingWidgets.value[widgetId] = true;
    }
    const bumpResetKey = (widgetId: string) => {
      widgetResetKeys.value[widgetId] = (widgetResetKeys.value[widgetId] ?? 0) + 1;
    };
    for (const widgetId of fallbackWidgets) {
      delete widgetData.value[widgetId];
      delete widgetErrors.value[widgetId];
      bumpResetKey(widgetId);
    }
    if (requestById.size === 0) {
      for (const widgetId of affected) {
        delete loadingWidgets.value[widgetId];
      }
      return;
    }
    try {
      const batch = await queryBatch([], [...requestById.values()], currentFilterValues);
      if (sequence !== linkageSequence) {
        return;
      }
      for (const [widgetId, query] of widgetQueries) {
        const result = batch.widgets?.[query.id];
        if (result) {
          widgetData.value[widgetId] = result;
          widgetErrors.value[widgetId] = batch.widgetErrors?.[query.id] || '';
        } else if (batch.widgetErrors?.[query.id]) {
          widgetErrors.value[widgetId] = batch.widgetErrors[query.id];
        }
        bumpResetKey(widgetId);
      }
    } catch (error: any) {
      if (sequence !== linkageSequence) {
        return;
      }
      const message =
        error?.response?.data?.title || error?.response?.data?.detail || error?.response?.data?.message || error?.message || '查询失败';
      for (const widgetId of widgetQueries.keys()) {
        widgetErrors.value[widgetId] = message;
      }
    } finally {
      if (sequence === linkageSequence) {
        for (const widgetId of affected) {
          delete loadingWidgets.value[widgetId];
        }
      }
    }
  };

  const loadAllWrapped = async (filterValues: FilterValues, options: { force?: boolean } = {}) => {
    currentFilterValues = filterValues;
    await loadAll(filterValues, options);
  };

  /**
   * 图表明细下钻：使用当前看板筛选值，查询指定数据集未做组件级聚合的明细行。
   */
  const loadDrill: WidgetDrillLoader = async request => {
    // 当前组件处于联动状态时，明细与图表保持一致：叠加联动条件，去重并遵守后端维度上限。
    const existing = new Set(request.dimensions.map(dimension => dimension.field));
    const extra = (request.widgetId ? (linkageConditionsByWidget.value[request.widgetId] ?? []) : [])
      .filter(condition => !existing.has(condition.field))
      .slice(0, Math.max(0, 10 - request.dimensions.length));
    const dimensions = [...request.dimensions, ...extra];
    const body: DrillQuery = {
      datasetId: request.datasetId,
      dimensions,
      filterValues: currentFilterValues,
      limit: request.pageSize,
      offset: Math.max(0, (request.page - 1) * request.pageSize),
    };
    if (request.sortField) {
      body.sortField = request.sortField;
      body.sortOrder = request.sortOrder;
    }
    const id = config.dashboardId?.value;
    if (id) {
      return service.drill(id, body);
    }
    if (!config.spec.value) {
      throw new Error('看板尚未就绪');
    }
    return service.previewDrill(config.spec.value, body);
  };

  /**
   * 点击图表后应用联动：同一来源同一取值再次点击则取消。
   *
   * @returns 是否产生了有效联动（没有可联动目标时返回 false，由调用方决定是否提示）
   */
  const applyLinkage = (sourceWidgetId: string, dimensions: DrillDimension[], selection?: LinkageSelection): boolean => {
    const spec = config.spec.value;
    const source = spec?.widgets.find(widget => widget.id === sourceWidgetId);
    if (!spec || !source || !source.interaction?.targets?.length || dimensions.length === 0) {
      return false;
    }
    const validTargets = source.interaction.targets.filter(targetConfig => {
      const target = spec.widgets.find(widget => widget.id === targetConfig.widgetId);
      if (!target) {
        return false;
      }
      if (source.datasetId && source.datasetId === target.datasetId) {
        return true;
      }
      return (targetConfig.fieldMap ?? []).some(item => !!item.sourceField && !!item.targetField);
    });
    if (validTargets.length === 0) {
      return false;
    }
    const conditions: LinkageCondition[] = dimensions.map(dimension => ({
      field: dimension.field,
      values: [...dimension.values],
    }));
    const selectedItem =
      selection && typeof selection.dataIndex === 'number'
        ? { seriesIndex: selection.seriesIndex, dataIndex: selection.dataIndex, dataType: selection.dataType }
        : undefined;
    const signature = JSON.stringify([sourceWidgetId, conditions, selectedItem ?? null]);
    if (
      linkage.value &&
      JSON.stringify([linkage.value.sourceWidgetId, linkage.value.conditions, linkage.value.selection ?? null]) === signature
    ) {
      linkage.value = null;
      return true;
    }
    const label = conditions
      .map(condition => {
        const declared = source.encode?.dimensions?.find(dimension => dimension.field === condition.field)?.name;
        return `${declared || condition.field} = ${condition.values.join(' / ')}`;
      })
      .join('，');
    linkage.value = {
      sourceWidgetId,
      sourceTitle: source.title || source.id,
      label,
      conditions,
      targetWidgetIds: validTargets.map(target => target.widgetId),
      ...(selectedItem ? { selection: selectedItem } : {}),
    };
    return true;
  };

  const clearLinkage = () => {
    linkage.value = null;
  };

  // 联动变化只刷新目标组件，不重载整个看板。
  watch(linkage, (next, previous) => {
    void refreshLinkage(previous ?? null, next ?? null);
  });

  return {
    datasetData,
    rawDatasetData,
    widgetData,
    loadingDatasets,
    datasetErrors,
    widgetErrors,
    serverPaginationWidgets,
    loadingWidgets,
    widgetResetKeys,
    dataVersion,
    loading,
    loadAll: loadAllWrapped,
    loadWidgetPage,
    loadDrill,
    linkage,
    linkageWidgetIds,
    applyLinkage,
    clearLinkage,
  };
}
