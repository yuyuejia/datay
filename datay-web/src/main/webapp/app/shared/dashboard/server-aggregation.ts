import type { Aggregation, DashboardWidget } from './types';

export interface ServerAggregation {
  groupFields: string[];
  aggregations: Array<{ field: string; agg: Aggregation }>;
}

const EMPTY: ServerAggregation = { groupFields: [], aggregations: [] };

/**
 * 生成服务端聚合下推参数。
 *
 * <p>图表与透视表在 groupBy（或透视表行列维度）明确时，直接把分组与聚合下推到数据库。
 * 筛选器统一由服务端执行，因此聚合前已经完成过滤。
 */
export function buildServerAggregation(widget: DashboardWidget): ServerAggregation {
  if (!widget.datasetId) {
    return EMPTY;
  }
  const encode = widget.encode;
  if (!encode) {
    return EMPTY;
  }
  const measures = (encode.measures ?? []).filter(measure => measure.field);
  if (measures.length === 0) {
    return EMPTY;
  }
  const dimensions = encode.dimensions ?? [];
  let selectedDimensions = dimensions;
  if (widget.type === 'pivot') {
    selectedDimensions = dimensions.filter(dimension => dimension.role === 'row' || dimension.role === 'column');
  } else if (widget.type === 'echarts' && encode.groupBy) {
    const chartType = widget.chart?.type;
    if (chartType === 'boxplot') {
      return EMPTY;
    }
    const singleDimension =
      chartType === 'pie' ||
      chartType === 'donut' ||
      chartType === 'funnel' ||
      chartType === 'treemap' ||
      chartType === 'radar' ||
      chartType === 'waterfall';
    selectedDimensions = dimensions.filter(dimension =>
      singleDimension ? dimension.role === 'x' : dimension.role === 'x' || dimension.role === 'series',
    );
  } else {
    return EMPTY;
  }
  const groupFields = Array.from(new Set(selectedDimensions.map(dimension => dimension.field).filter(Boolean)));
  if (groupFields.length === 0) {
    return EMPTY;
  }
  return {
    groupFields,
    aggregations: measures.map(measure => ({ field: measure.field, agg: measure.agg ?? 'sum' })),
  };
}

/**
 * 计算组件查询的服务端行数上限。
 *
 * <p>占总比/百分比堆积必须先拿到完整聚合结果再截断，因此 percent 模式不下推行数限制；
 * 表格分页仍使用 pageSize。
 */
export function serverQueryLimit(widget: DashboardWidget, serverPaged: boolean): number | undefined {
  if (serverPaged) {
    return widget.encode?.pageSize;
  }
  const percentMode =
    (widget.encode?.transform ?? []).some(transform => transform.type === 'percentOfTotal') || widget.chart?.subtype === 'stackedPercent';
  return percentMode ? undefined : widget.encode?.limit;
}
