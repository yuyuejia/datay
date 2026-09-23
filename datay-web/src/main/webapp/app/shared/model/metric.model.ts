export const METRIC_TYPE_ATOMIC = "ATOMIC";
export const METRIC_TYPE_DERIVED = "DERIVED";

export const METRIC_STATUS_ENABLED = "ENABLED";
export const METRIC_STATUS_DISABLED = "DISABLED";

export const FILTER_TYPE_FACT_FIELD = "FACT_FIELD";
export const FILTER_TYPE_DIMENSION = "DIMENSION";
export const FILTER_TYPE_TIME = "TIME";

export interface IMetricFilterCondition {
  type?: string | null;
  factFieldName?: string | null;
  dimensionModelId?: number | null;
  dimensionFieldName?: string | null;
  operator?: string | null;
  value?: string | null;
  valueEnd?: string | null;
  logic?: string | null;
}

export interface IMetricFilterConfig {
  conditions: IMetricFilterCondition[];
}

export interface IMetricQueryField {
  factFieldName?: string | null;
  dimensionModelId?: number | null;
  dimensionFieldName?: string | null;
}

export interface IMetricQueryTimeRange {
  factFieldName?: string | null;
  dimensionModelId?: number | null;
  dimensionFieldName?: string | null;
  start?: string | null;
  end?: string | null;
}

export interface IMetricQueryRequest {
  metricIds?: number[];
  dimensions?: IMetricQueryField[];
  filterConfig?: string | null;
  timeRange?: IMetricQueryTimeRange | null;
}

export interface IMetricRef {
  id?: number;
  code?: string;
  name?: string;
  metricType?: string;
  status?: string;
}

export interface IMetric {
  id?: number;
  name?: string | null;
  code?: string | null;
  description?: string | null;
  directoryId?: number | null;
  metricType?: string | null;
  status?: string | null;
  factModelId?: number | null;
  filterConfig?: string | null;
  unit?: string | null;
  dataType?: string | null;
  isAdditive?: boolean | null;
  formula?: string | null;
  tenantId?: string | null;
  createTime?: Date | null;
  updateTime?: Date | null;
  refMetrics?: IMetricRef[];
  factModelName?: string | null;
  factTableName?: string | null;
}

export class Metric implements IMetric {
  constructor(
    public id?: number,
    public name?: string | null,
    public code?: string | null,
    public description?: string | null,
    public directoryId?: number | null,
    public metricType?: string | null,
    public status?: string | null,
    public factModelId?: number | null,
    public filterConfig?: string | null,
    public unit?: string | null,
    public dataType?: string | null,
    public isAdditive?: boolean | null,
    public formula?: string | null,
    public tenantId?: string | null,
    public createTime?: Date | null,
    public updateTime?: Date | null,
    public refMetrics?: IMetricRef[],
    public factModelName?: string | null,
    public factTableName?: string | null,
  ) {}
}

export const AGG_FUNCTIONS: Array<{ value: string; label: string }> = [
  { value: "SUM", label: "求和 SUM" },
  { value: "COUNT", label: "计数 COUNT" },
  { value: "COUNT_DISTINCT", label: "去重计数 COUNT DISTINCT" },
  { value: "AVG", label: "平均 AVG" },
  { value: "MIN", label: "最小 MIN" },
  { value: "MAX", label: "最大 MAX" },
];

export const DATA_TYPES: Array<{ value: string; label: string }> = [
  { value: "DECIMAL", label: "高精度数值 (DECIMAL)" },
  { value: "INTEGER", label: "整数 (INTEGER)" },
  { value: "LONG", label: "长整型 (LONG)" },
  { value: "DOUBLE", label: "双精度 (DOUBLE)" },
  { value: "VARCHAR", label: "字符串 (VARCHAR)" },
  { value: "TEXT", label: "大文本 (TEXT)" },
];

export function dataTypeLabel(value?: string | null): string {
  const found = DATA_TYPES.find((item) => item.value === value);
  return found ? found.label : value || "-";
}

export const FILTER_OPERATORS: Array<{
  value: string;
  label: string;
  unary?: boolean;
  range?: boolean;
}> = [
  { value: "EQ", label: "等于 (=)" },
  { value: "NE", label: "不等于 (<>)" },
  { value: "GT", label: "大于 (>)" },
  { value: "GE", label: "大于等于 (>=)" },
  { value: "LT", label: "小于 (<)" },
  { value: "LE", label: "小于等于 (<=)" },
  { value: "BETWEEN", label: "区间 (BETWEEN)", range: true },
  { value: "IN", label: "包含于 (IN)" },
  { value: "NOT_IN", label: "不包含于 (NOT IN)" },
  { value: "LIKE", label: "模糊匹配 (LIKE)" },
  { value: "IS_NULL", label: "为空 (IS NULL)", unary: true },
  { value: "IS_NOT_NULL", label: "不为空 (IS NOT NULL)", unary: true },
];

export const FILTER_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: FILTER_TYPE_FACT_FIELD, label: "事实表字段" },
  { value: FILTER_TYPE_DIMENSION, label: "维度" },
  { value: FILTER_TYPE_TIME, label: "时间" },
];

export function operatorLabel(value?: string | null): string {
  const found = FILTER_OPERATORS.find((item) => item.value === value);
  return found ? found.label : value || "";
}

export function isUnaryOperator(value?: string | null): boolean {
  const found = FILTER_OPERATORS.find((item) => item.value === value);
  return !!found?.unary;
}

export function isRangeOperator(value?: string | null): boolean {
  const found = FILTER_OPERATORS.find((item) => item.value === value);
  return !!found?.range;
}

export function parseFilterConfig(json?: string | null): IMetricFilterConfig {
  if (!json || !json.trim()) {
    return { conditions: [] };
  }
  try {
    const parsed = JSON.parse(json);
    if (parsed && Array.isArray(parsed.conditions)) {
      return { conditions: parsed.conditions };
    }
  } catch {
    // 忽略解析失败，返回空配置
  }
  return { conditions: [] };
}

export function stringifyFilterConfig(
  config: IMetricFilterConfig,
): string | null {
  const conditions = (config.conditions || []).filter(
    (c) => c.type && (c.factFieldName || c.dimensionFieldName || c.operator),
  );
  if (conditions.length === 0) {
    return null;
  }
  return JSON.stringify({ conditions });
}

const FORMULA_REF_PATTERN = /\$\{([A-Za-z0-9_-]+)\}/g;

export function parseFormulaRefs(formula?: string | null): string[] {
  const refs: string[] = [];
  if (!formula) {
    return refs;
  }
  let match: RegExpExecArray | null;
  const pattern = new RegExp(FORMULA_REF_PATTERN.source, "g");
  while ((match = pattern.exec(formula)) !== null) {
    if (!refs.includes(match[1])) {
      refs.push(match[1]);
    }
  }
  return refs;
}
