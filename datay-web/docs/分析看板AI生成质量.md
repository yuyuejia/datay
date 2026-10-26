# 分析看板 AI 生成质量

本文说明「看板设计」助手生成 spec 的约束、自动修复和验收方式。

## 1. 生成链路

1. 识别指标数据集：`list_analysis_datasets` / `prepare_metric_dataset`
2. 生成 v2 看板 JSON
3. `DashboardDesignAssistant.postProcessSpec` 处理数据集引用
4. `repairCommonWidgetMistakes` 自动修复常见结构偏差
5. `DashboardSpecValidator.validateAndNormalize` 严格校验
6. 保存或返回给设计器

## 2. 自动修复

AI 输出在严格校验前会修复以下常见问题：

- 漏写 `chart.type`：从 `echartsOption.series[0].type` 推断
  - `line + areaStyle` → `area`
  - `pie + radius/avoidLabelOverlap` → `donut`
- 仍使用 v1 `encode`：
  - `categoryField` → `dimensions[].role=x`
  - `seriesField` → `dimensions[].role=series`
  - `values[]` / `valueField` / `series[]` → `measures[]`
  - 自动补 `agg=sum`、`role=y`
- 透视表维度 role 错误：
  - `x` → `row`
  - `series` → `column`

自动修复只用于 AI 生成结果，不改变已保存看板。

## 3. 严格校验

以下问题会直接拒绝：

- `version != 2`
- 未知组件类型 / 图表类型
- `echarts` / `kpi` / `pivot` 缺少 `encode`
- `chart.type` 缺失
- `chart.series[].measure` 引用了未声明的度量
- 热力图/桑基图/关系图缺少 `role=series`
- 透视表缺少 `role=row` 或度量不是 `role=value`
- 条件格式 operator 非法、between 缺 value2、颜色非法
- 字段不在数据集字段缓存中

## 4. 自检清单

提示词要求模型在输出前逐项检查：

1. 顶层 `version=2`
2. 每个图表都有 `chart.type`
3. `dimensions` role 合法，`measures` role 与 agg 合法
4. 热力图/桑基图/关系图有 `x + series`
5. 透视表有 `row + value`
6. `chart.series` 只引用已声明度量
7. `formatRules` 只用于 table/pivot/kpi，between 有 value2
8. 24 栅格布局不重叠、不越界
9. 不输出 dataRef / bindings / 内嵌数据集 / v1 encode
10. 只输出 JSON，不输出解释文字

## 5. 推荐示例

### 基础柱状图

```json
{
  "encode": {
    "dimensions": [{ "field": "region", "role": "x" }],
    "measures": [{ "field": "amount", "agg": "sum", "role": "y" }],
    "groupBy": true,
    "limit": 100
  },
  "chart": { "type": "bar", "subtype": "grouped" }
}
```

### 热力图

```json
{
  "encode": {
    "dimensions": [
      { "field": "region", "role": "x" },
      { "field": "channel", "role": "series" }
    ],
    "measures": [{ "field": "amount", "agg": "sum", "role": "value" }],
    "groupBy": true
  },
  "chart": { "type": "heatmap" }
}
```

### 透视表

```json
{
  "type": "pivot",
  "encode": {
    "dimensions": [
      { "field": "region", "role": "row" },
      { "field": "channel", "role": "column" }
    ],
    "measures": [{ "field": "amount", "agg": "sum", "role": "value" }]
  }
}
```

## 6. 测试覆盖

- `DashboardDesignAssistantTest`：数据集引用、草稿落库、常见结构修复
- `DashboardSpecValidatorTest`：v2 协议、图表类型、聚合、条件格式
- `AppPackagePresetTest`：预置资产包 spec 全部通过校验
