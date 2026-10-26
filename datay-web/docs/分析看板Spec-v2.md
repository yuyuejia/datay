# 分析看板 Spec v2

当前看板协议版本为 `version: 2`。后端 `DashboardSpecValidator` 只接受 v2，
旧 v1 的 `categoryField / values / seriesField / valueField` 会被拒绝。

## 1. 顶层结构

```json
{
  "version": 2,
  "title": "销售分析",
  "description": "按区域和渠道分析销售",
  "layout": { "mode": "standard" },
  "datasets": [],
  "widgets": [],
  "filters": []
}
```

- `layout.mode`：`standard | screen`
- `datasets[]`：只保存数据集引用，推荐 `{ id, refId, name, type }`
- `widgets[]`：图表、KPI、表格、透视表、HTML
- `filters[]`：统一使用 `targets[]`，所有筛选器均由服务端执行，不做前端本地过滤

## 2. 组件类型

| type | 说明 |
| --- | --- |
| `echarts` | ECharts 图表 |
| `kpi` | 单值指标卡 |
| `table` | 明细表 |
| `pivot` | 交叉透视表 |
| `html` | 安全 HTML 说明文本 |

## 3. 数据映射 encode

```json
{
  "dimensions": [
    { "field": "region", "role": "x" },
    { "field": "channel", "role": "series" }
  ],
  "measures": [
    { "field": "amount", "agg": "sum", "role": "y" }
  ],
  "groupBy": true,
  "sortField": "amount",
  "sortOrder": "desc",
  "limit": 100
}
```

### 维度 role

| role | 用途 |
| --- | --- |
| `x` | 分类轴 / 指标项 / 源节点 |
| `series` | 系列拆分 / 热力图行 / 目标节点 |
| `row` | 透视表行维度 |
| `column` | 透视表列维度 |

### 度量 role

| role | 用途 |
| --- | --- |
| `y` | 坐标轴数值 |
| `value` | 饼图、环形图、漏斗图、矩形树图、仪表盘、热力图、透视表 |
| `size` | 散点大小 |

### 聚合方式

`sum | avg | min | max | count | none`

## 4. 图表类型

| type | 关键配置 |
| --- | --- |
| `bar` | `subtype: grouped/stacked/stackedPercent`，`orientation: vertical/horizontal` |
| `line` | `subtype: smooth/step` |
| `area` | `subtype: smooth/step` |
| `pie` | `role=value` 度量 |
| `donut` | `role=value` 度量 |
| `gauge` | 不需要维度 |
| `scatter` | `role=y` 纵轴，可选 `role=size` |
| `radar` | `role=x` 指标项，多个 `role=y` 系列 |
| `funnel` | 分类 + 单值 |
| `heatmap` | 必须有 `role=x` 与 `role=series` |
| `treemap` | 分类 + 单值 |
| `waterfall` | 分类 + 增减值 |
| `sankey` | `role=x` 源节点 + `role=series` 目标节点 + 权重 |
| `graph` | 同桑基图 |
| `boxplot` | 分类 + 原始明细数值 |

组合图 / 双轴通过 `chart.series[]` 声明：

```json
{
  "type": "bar",
  "subtype": "grouped",
  "series": [
    { "measure": "sales_amount", "type": "bar", "axis": "left" },
    { "measure": "order_count", "type": "line", "axis": "right" }
  ]
}
```

## 5. 透视表

```json
{
  "id": "pivot_store_month",
  "type": "pivot",
  "datasetId": "ds_sales",
  "encode": {
    "dimensions": [
      { "field": "store_name", "role": "row" },
      { "field": "month_name", "role": "column" }
    ],
    "measures": [
      { "field": "sales_amount", "agg": "sum", "role": "value" }
    ],
    "limit": 100
  }
}
```

## 6. 条件格式

适用于 `table / pivot / kpi`：

```json
{
  "formatRules": [
    {
      "field": "sales_amount",
      "operator": "gt",
      "value": 100000,
      "color": "#67c23a",
      "background": "#f0f9eb",
      "bold": true
    }
  ]
}
```

- `operator`：`gt | gte | lt | lte | eq | neq | between`
- `between` 必须提供 `value2`
- `field` 省略时对全部数值单元格生效

## 7. 筛选器

筛选器统一使用服务端过滤：

```json
{
  "id": "region",
  "type": "select",
  "targets": [
    { "datasetId": "ds_sales", "field": "region", "mode": "server" }
  ]
}
```

- SQL 数据集：直接字段会包装为子查询过滤条件。
- 指标数据集：直接字段会解析为维度过滤条件；日期筛选仍需使用 `metricTime` pushdown。
- `mode` 字段保留兼容，但保存时统一规范化为 `server`。
- 前端只把 `filterValues` 发给服务端，不再对返回行做本地过滤。

## 8. 服务端聚合

当图表 `encode.groupBy=true` 或组件为透视表时，前端会把分组字段与度量聚合下推到后端。
后端返回 `aggregated: true`，前端不再重复聚合。

- 筛选器先由服务端执行，再进行分组聚合。
- 单值图表（饼图/环形图/漏斗图/矩形树图/雷达图/瀑布图）只按 `x` 分组。
- 热力图/桑基图/关系图/柱折面积图按 `x + series` 分组。
- 透视表按 `row + column` 分组。

## 9. 校验与限制

- 单个 spec 最大 1,000,000 字符
- 数据集最多 50 个
- 组件最多 200 个
- 筛选器最多 100 个
- HTML 最多 100,000 字符
- 条件格式规则最多 50 条
- 图表必须有 `chart.type`
- `echarts` / `kpi` / `pivot` 必须有有效 `encode`
- 字段如有数据集缓存，会校验字段存在性
