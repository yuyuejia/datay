import type { DataRef, DashboardWidget, DatasetResult } from "./types";

/**
 * 把 AI 生成的 ECharts option 与查询返回的数据结合。
 *
 * AI 负责图表类型与样式（option），`dataRef` 负责声明如何把数据集列映射到图上；
 * 本模块据此把真实的分类轴数据、系列数据注入 option，保证看板数据来自实时查询。
 */

function toNumber(value: any): number | null {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function sortRows(
  rows: Record<string, any>[],
  field: string,
  order?: "asc" | "desc",
): Record<string, any>[] {
  const direction = order === "desc" ? -1 : 1;
  return [...rows].sort((a, b) => {
    const left = a[field];
    const right = b[field];
    const leftNumber = Number(left);
    const rightNumber = Number(right);
    if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) {
      return (leftNumber - rightNumber) * direction;
    }
    return String(left ?? "").localeCompare(String(right ?? "")) * direction;
  });
}

function firstSeriesType(option: Record<string, any>): string | undefined {
  const series = option?.series;
  if (Array.isArray(series) && series.length > 0) {
    return series[0]?.type;
  }
  if (series && typeof series === "object") {
    return series.type;
  }
  return undefined;
}

function baseSeries(option: Record<string, any>): Record<string, any>[] {
  if (Array.isArray(option.series)) {
    return option.series;
  }
  if (option.series && typeof option.series === "object") {
    return [option.series];
  }
  return [];
}

function setAxisData(
  option: Record<string, any>,
  axisKey: "xAxis" | "yAxis",
  categories: any[],
): void {
  const axis = option[axisKey];
  if (Array.isArray(axis)) {
    option[axisKey] = axis.map((item, index) =>
      index === 0 ? { ...item, data: categories } : item,
    );
  } else if (axis && typeof axis === "object") {
    option[axisKey] = { ...axis, data: categories };
  } else {
    option[axisKey] = { type: "category", data: categories };
  }
}

/**
 * 分类轴既可能在 x 轴（纵向柱/折线），也可能在 y 轴（横向柱）。
 * 优先识别 type=category 的轴，否则默认 x 轴。
 */
function setCategoryAxis(option: Record<string, any>, categories: any[]): void {
  const yAxis = Array.isArray(option.yAxis) ? option.yAxis[0] : option.yAxis;
  const axisKey = yAxis && yAxis.type === "category" ? "yAxis" : "xAxis";
  setAxisData(option, axisKey, categories);
}

function resolveSeriesDefs(
  ref: DataRef,
  columns: string[],
  categoryField?: string,
) {
  if (ref.series && ref.series.length > 0) {
    return ref.series;
  }
  return columns
    .filter((column) => column !== categoryField)
    .map((column) => ({ name: column, field: column }));
}

/**
 * 展示字段优先取业务名称：当字段为 id/sk 代理键且存在对应的 name 字段时返回 name，否则原样返回。
 */
export function resolveNameField(
  field: string | undefined,
  columns: string[],
): string | undefined {
  if (!field) {
    return field;
  }
  const lower = field.toLowerCase();
  if (!(lower.endsWith("_id") || lower.endsWith("_sk"))) {
    return field;
  }
  const prefix = field.replace(/_(id|sk)$/i, "");
  const candidates = [`${prefix}_name`, "name"];
  for (const candidate of candidates) {
    const hit = columns.find(
      (column) => column.toLowerCase() === candidate.toLowerCase(),
    );
    if (hit) {
      return hit;
    }
  }
  return field;
}

/**
 * 构建最终 ECharts option；无数据或未声明 dataRef 时原样返回 AI option。
 */
export function buildEchartsOption(
  widget: DashboardWidget,
  result?: DatasetResult,
): Record<string, any> {
  const option: Record<string, any> = JSON.parse(
    JSON.stringify(widget.echartsOption || {}),
  );
  // 组件卡片已展示标题，移除图表内 title 避免重复（默认保留 dashboard-widget-title）
  delete option.title;
  const ref = widget.dataRef;
  if (
    !ref ||
    !result ||
    !Array.isArray(result.rows) ||
    result.rows.length === 0
  ) {
    return option;
  }

  let rows = [...result.rows];
  if (ref.sortField) {
    rows = sortRows(rows, ref.sortField, ref.sortOrder);
  }
  if (ref.limit && rows.length > ref.limit) {
    rows = rows.slice(0, ref.limit);
  }

  const columns = result.columns ?? [];
  const categoryField = resolveNameField(
    ref.categoryField ?? columns[0],
    columns,
  );
  const chartType = firstSeriesType(option);
  const valueField = resolveNameField(
    ref.series?.[0]?.field ??
      ref.valueField ??
      columns.find((column) => column !== categoryField),
    columns,
  );

  if (chartType === "pie") {
    option.series = [
      {
        ...(baseSeries(option)[0] || {}),
        type: "pie",
        data: rows.map((row) => ({
          name: row[categoryField],
          value: toNumber(row[valueField as string]),
        })),
      },
    ];
    return option;
  }

  if (chartType === "gauge") {
    const first = rows[0];
    option.series = [
      {
        ...(baseSeries(option)[0] || {}),
        type: "gauge",
        data: [
          {
            value: toNumber(first[valueField as string]),
            name: first[categoryField] ?? widget.title,
          },
        ],
      },
    ];
    return option;
  }

  if (categoryField) {
    setCategoryAxis(
      option,
      rows.map((row) => row[categoryField]),
    );
  }

  const base = baseSeries(option);

  if (ref.seriesField) {
    const seriesField = resolveNameField(ref.seriesField, columns) as string;
    const valueFieldName = valueField as string;
    const categories = Array.from(
      new Set(rows.map((row) => row[categoryField])),
    );
    if (categoryField) {
      setCategoryAxis(option, categories);
    }
    const groups = Array.from(new Set(rows.map((row) => row[seriesField])));
    option.series = groups.map((group, index) => {
      const template = base[index] || base[0] || {};
      const data = categories.map((category) => {
        const row = rows.find(
          (item) =>
            item[categoryField] === category && item[seriesField] === group,
        );
        return row ? toNumber(row[valueFieldName]) : null;
      });
      return { ...template, name: String(group), data };
    });
    return option;
  }

  const seriesDefs = resolveSeriesDefs(ref, columns, categoryField).map(
    (series) => ({
      ...series,
      field: resolveNameField(series.field, columns) ?? series.field,
    }),
  );
  option.series = seriesDefs.map((series, index) => {
    const template = base[index] || base[0] || {};
    return {
      ...template,
      name: series.name || series.field,
      data: rows.map((row) => toNumber(row[series.field])),
    };
  });
  return option;
}

/**
 * 计算 KPI 单值：优先取显式 valueField，否则取首个数值列并按行求和。
 */
export function resolveKpiValue(
  widget: DashboardWidget,
  result?: DatasetResult,
): { value: number | null; label?: string } {
  if (!result || !Array.isArray(result.rows) || result.rows.length === 0) {
    return { value: null };
  }
  const ref = widget.dataRef;
  const columns = result.columns ?? [];
  const field = ref?.valueField ?? ref?.series?.[0]?.field;
  if (field) {
    const values = result.rows
      .map((row) => toNumber(row[field]))
      .filter((value): value is number => value !== null);
    return { value: values.reduce((sum, value) => sum + value, 0) };
  }
  const numericColumn = columns.find((column) =>
    result.rows.some((row) => toNumber(row[column]) !== null),
  );
  if (!numericColumn) {
    return { value: null };
  }
  const values = result.rows
    .map((row) => toNumber(row[numericColumn]))
    .filter((value): value is number => value !== null);
  return {
    value: values.reduce((sum, value) => sum + value, 0),
    label: numericColumn,
  };
}
