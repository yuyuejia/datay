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
- `layout.rowHeight`：系统管理的看板行高，不提供前端配置入口；未设置时按历史默认 **40px** 计算，新看板默认 **20px**；历史 40px 看板在编辑保存时会自动迁移到 20px，并按行步长换算顶层组件的 `y/h` 以保持视觉高度
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
| `title` | 大屏标题栏（主标题 + 副标题 + 科技边框） |
| `clock` | 日期 / 时间 / 星期展示 |
| `container` | 面板容器：背景、标题与子组件分组 |

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
| `value` | 饼图、环形图、漏斗图、矩形树图、仪表盘、热力图、地图、透视表 |
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
| `map` | `chart.map: china`，`role=x` 中国省级名称 + `role=value` 指标值 |

中国地图示例：

```json
{
  "id": "map_province_sales",
  "type": "echarts",
  "datasetId": "ds_sales",
  "encode": {
    "dimensions": [{ "field": "province", "role": "x" }],
    "measures": [{ "field": "sales_amount", "agg": "sum", "role": "value" }],
    "groupBy": true,
    "limit": 100
  },
  "chart": { "type": "map", "map": "china" },
  "echartsOption": {
    "tooltip": { "trigger": "item" },
    "visualMap": { "calculable": true, "orient": "horizontal", "left": "center", "bottom": 0 },
    "series": [{ "type": "map", "map": "china", "roam": false }]
  }
}
```

- `chart.map` 当前仅支持 `china`（内置中国省级地图）。
- 区域字段支持 `北京`、`北京市`、`广东省`、`广西壮族自治区` 等写法，无法匹配省级区域的行会被忽略。
- 地图只使用第一个 `role=value` 度量；区域填色与图例由 `visualMap` 控制。

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
- 单值图表（饼图/环形图/漏斗图/矩形树图/雷达图/瀑布图/地图）只按 `x` 分组。
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

## 10. 图表明细下钻

`echarts` 组件默认支持点击数据项查看明细，无需在 spec 中额外配置。

- 点击柱/线/面积/饼/环形/漏斗/矩形树图/地图/热力图/散点/瀑布/箱线图/桑基图（边）时，
  前端把点击到的维度值提交给服务端。
- 明细查询复用图表绑定的数据集，**不再下推组件的 `groupBy` / 聚合**，
  只追加点击维度的等值或 `IN` 过滤，并保留当前看板筛选器。
- 分页由服务端执行，默认每页 50 行。
- `gauge` 与 `radar` 没有明确的单维度点击语义，不启用下钻。

已保存看板调用：

```http
POST /api/analysis-dashboards/{id}/drill
```

未保存的实时预览调用：

```http
POST /api/analysis-dashboards/preview-drill
```

请求体：

```json
{
  "datasetId": "ds_sales",
  "dimensions": [
    { "field": "region", "values": ["华东"] },
    { "field": "channel", "values": ["官网"] }
  ],
  "filterValues": { "date": ["2026-01-01", "2026-01-31"] },
  "limit": 50,
  "offset": 0,
  "sortField": "sales_amount",
  "sortOrder": "desc"
}
```

响应即数据集查询结果（`columns` / `rows` / `columnLabels` / `truncated` 等）。

> 如果 SQL 数据集自身的 SQL 已经 `GROUP BY`，明细展示的是该 SQL 输出粒度的行；
> 指标数据集的明细是数据集已声明维度组合下的行，指标值本身仍是聚合值。
> 如需事实层原始明细，请使用一个未聚合的 SQL 数据集。

## 11. 大屏画布与叠放

大屏模式支持在「24 栅格」与「像素画布」之间切换。两种模式共用同一套 `layout` 与组件结构，
仅坐标含义不同；字段全部可选，老看板不写即为栅格 + 16px 内边距。

### 11.1 `layout.screen.canvas`

```json
{
  "mode": "screen",
  "screen": {
    "width": 1920,
    "height": 1080,
    "canvas": { "unit": "px", "padding": 0, "gap": 8 }
  }
}
```

| 字段 | 取值 | 默认 | 说明 |
| --- | --- | --- | --- |
| `unit` | `grid` / `px` | `grid` | `grid` 按 24 列定位；`px` 按画布内容区像素定位 |
| `padding` | 0..64 | 16 | 画布内边距，`0` 为贴边 |
| `gap` | 0..48 | 12 | 栅格间距，仅 `grid` 模式生效 |

- `px` 模式下 `widget.layout = { x, y, w, h }` 的单位是像素，`x/y` 相对画布内容区，
  限制为 `w ∈ [40, W-2p]`、`h ∈ [20, H-2p]`、`x+w ≤ W-2p`、`y+h ≤ H-2p`；越界会被钳制。
- `grid` 模式的坐标与历史版本完全一致（`x` 列号、`w` 跨列数）。
- 标准模式（`mode=standard`）始终使用栅格，`canvas` 不参与定位；但 `screen` 块会照常保存
  （主题、背景、自动刷新等），切回大屏时不会丢配置。

### 11.2 `widget.zIndex`

```json
{ "id": "map", "type": "echarts", "zIndex": 10, "layout": { "x": 470, "y": 200, "w": 980, "h": 760 } }
```

- 取值 0..999，缺省 0；仅 `px` 模式生效，值越大越靠上。
- **`grid` 模式仍禁止组件重叠**（保存时校验并报错）；`px` 模式允许重叠，由 `zIndex` 与数组顺序决定层级。
- 设计器像素画布下提供「上移一层 / 下移一层」，调整后会按当前顺序把 `zIndex` 重新编号（步长 5，最大 995）；组件「基础设置 → 层级」可直接填写 `0..999` 的层级值，数值越大越靠上。

### 11.3 单位切换

设计器切换 `grid` / `px` 时会自动换算已有组件布局（按列宽比例与行高换算），不会出现组件瞬间缩成一条线；
在大屏与标准模式之间切换时，像素画布也会先换算回栅格再切换，避免把像素值当成列号渲染。
换算公式：

- grid → px：`w_px = round(w_grid / 24 × 内容宽)`，`h_px = round(h_grid × (rowHeight + gap))`
- px → grid：反向换算后按 24 列钳制

> 说明：本版本只实现画布双模式、贴边与叠放；组件样式（`widget.style`）、主题 token、
> 地图多图层与 `title/clock` 组件属于后续版本，尚未进入本协议。

## 12. 组件皮肤与主题 token

标准模式与大屏模式都支持「主题预设 + token」，并可在此基础上对单个组件做卡片与标题皮肤覆盖。
所有字段均可选，且只影响前端呈现，不参与取数、聚合、筛选与下钻。

### 12.1 `layout.screen.themePreset` 与 `layout.screen.tokens`

```json
{
  "mode": "screen",
  "screen": {
    "theme": "dark",
    "themePreset": "tech-blue",
    "tokens": {
      "primary": "#2fa8ff",
      "secondary": "#1de9b6",
      "accent": "#ffd54f",
      "text": "#d8ecff",
      "textSecondary": "#7c9cb9",
      "surface": "rgba(9, 26, 48, 0.55)",
      "border": "rgba(47, 168, 255, 0.35)",
      "palette": ["#2fa8ff", "#1de9b6", "#ffd54f"],
      "fontFamily": "system",
      "map": { "area": "#0b3a5b", "border": "#2fa8ff" }
    }
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `themePreset` | `tech-blue` / `tech-cyan` / `gov-red` / `ink-dark` / `custom` |
| `tokens` | 覆盖预设的 token；颜色非法会被丢弃，`palette` 最多 12 个 |
| `fontFamily` | `system` / `number-digital` / `number-condensed`（数字字体在后续版本启用） |
| `map` | 地图区域 / 边界 / 高亮 / 标签配色，地图多图层版本使用 |

渲染时 token 会写到看板根节点的 CSS 变量（`--dashboard-surface`、`--dashboard-border`、
`--dashboard-text`、`--dash-accent` 等），组件默认皮肤与图表主题都会跟随。

### 12.2 `widget.style`

```json
{
  "id": "panel_sales",
  "type": "echarts",
  "style": {
    "card": {
      "preset": "tech-corner",
      "fill": { "type": "gradient", "gradient": { "from": "#0a1b2e", "to": "#0d2643", "angle": 180 } },
      "opacity": 0.9,
      "border": { "width": 1, "color": "rgba(47,168,255,0.45)", "style": "dashed", "radius": 4 },
      "shadow": { "x": 0, "y": 0, "blur": 18, "spread": 0, "color": "rgba(0,180,255,0.25)" },
      "padding": 8,
      "backdropBlur": 4
    },
    "title": {
      "show": true,
      "icon": "chart-line",
      "fontSize": 16,
      "fontWeight": 600,
      "color": "#7fe3ff",
      "align": "left",
      "height": 36,
      "letterSpacing": 1,
      "background": "#0c2238",
      "decoration": "gradient-underline"
    },
    "body": { "padding": 4 }
  }
}
```

| 分组 | 字段 | 取值 |
| --- | --- | --- |
| `card.preset` | 卡片边框预设 | `none` / `tech-corner` / `tech-frame` / `bracket` |
| `card.fill` | 填充 | `type=none` / `color`+`color` / `gradient`+`gradient{from,to,angle}` |
| `card.border` | 描边 | `width 0..8`、`color`、`style solid/dashed/dotted`、`radius 0..32` |
| `card.shadow` | 阴影 | `x/y -50..50`、`blur 0..100`、`spread -20..50`、`color` |
| `card.opacity` | 透明度 | 0..1 |
| `card.padding` | 卡片内边距 | 0..48 |
| `card.backdropBlur` | 毛玻璃 | 0..20 |
| `title.show` | 标题栏显隐 | 布尔；`false` 时不渲染标题栏 |
| `title.icon` | 图标 | FontAwesome 名称，`^[a-z0-9-]{1,40}$` |
| `title.*` | 字号/字重/颜色/对齐/高度/字距/背景 | 见上表示例 |
| `title.decoration` | 标题装饰 | `none` / `underline` / `gradient-underline` / `left-bar` |
| `body.padding` | 内容区内边距 | 0..32 |

约束：

- 未知字段会被服务端丢弃；非法颜色、非法枚举直接跳过，不阻塞保存。
- 单个组件样式序列化后最大 4KB。
- 样式只做白名单映射，不接受任意 CSS 字符串，避免样式注入。
- 组件样式优先于主题 token；未设置的项回落到 token / 内置默认皮肤。

## 13. KPI 展示增强

`type=kpi` 的组件除 `format` 外，还可以通过 `style.kpi` 控制标签、图标、数值与单位的排版。

```json
{
  "id": "kpi_total",
  "type": "kpi",
  "style": {
    "kpi": {
      "label": "学生总数",
      "icon": "users",
      "layout": "label-top",
      "value": {
        "fontSize": 56,
        "color": "#eaf6ff",
        "fontFamily": "number-digital",
        "bold": true,
        "letterSpacing": 2
      },
      "unit": { "text": "人", "fontSize": 16, "color": "#8fb6d6" },
      "animate": true,
      "decimals": 0
    }
  }
}
```

| 字段 | 取值 | 说明 |
| --- | --- | --- |
| `label` | ≤ 40 字 | 覆盖组件标题作为 KPI 标签 |
| `icon` | FontAwesome 名称 | 标签左侧图标 |
| `layout` | `label-top` / `label-left` / `value-only` | 标签在上 / 在左 / 只显示数值 |
| `value.fontSize` | 12..120 | 数值字号 |
| `value.color` | 颜色 | 数值颜色 |
| `value.fontFamily` | `system` / `number-digital` / `number-condensed` | 数字字体 |
| `value.bold` | 布尔 | 是否加粗 |
| `value.letterSpacing` | 0..8 | 字距 |
| `unit.text` | ≤ 8 字 | 独立单位文案；为空时回落到 `format.unit` |
| `unit.fontSize` | 10..40 | 单位字号 |
| `unit.color` | 颜色 | 单位颜色 |
| `animate` | 布尔 | 数值变化时是否滚动动画（默认关闭） |
| `decimals` | 0..6 | 小数位，覆盖 `format.precision` |

说明：

- 数值格式仍兼容 `format.prefix / precision / suffix / unit` 与条件格式规则，
  条件格式的颜色/加粗优先于 `style.kpi.value`。
- `number-digital` 使用自托管的 Orbitron（SIL OFL 1.1），`number-condensed` 使用 Rajdhani；
  见 `content/fonts/` 与项目 `NOTICE`。
- 动画遵循 `prefers-reduced-motion`，减少动效偏好下自动关闭。

## 14. 地图多图层

`chart.type=map` 支持在区域填色之上叠加涟漪点与飞线。图层数组可选，不配置时保持原有单层地图渲染。

```json
{
  "chart": {
    "type": "map",
    "map": "china",
    "layers": [
      { "kind": "region", "valueField": "students", "agg": "sum" },
      {
        "kind": "effectScatter",
        "datasetId": "ds_city",
        "lngField": "lng",
        "latField": "lat",
        "nameField": "city",
        "valueField": "students",
        "symbolSize": [10, 30],
        "color": "#ffd54f"
      },
      {
        "kind": "lines",
        "datasetId": "ds_flow",
        "fromLngField": "from_lng",
        "fromLatField": "from_lat",
        "toLngField": "to_lng",
        "toLatField": "to_lat",
        "valueField": "flow",
        "color": "#ffd54f",
        "curveness": 0.3,
        "effect": { "show": true, "period": 4, "trailLength": 0.4 }
      }
    ]
  }
}
```

| 图层 | 必填字段 | 可选字段 |
| --- | --- | --- |
| `region` | `valueField` | `agg`（`sum/avg/min/max/count/none`）、`datasetId` |
| `effectScatter` | `lngField`、`latField` | `nameField`、`valueField`、`symbolSize`、`color`、`datasetId` |
| `lines` | `fromLngField`、`fromLatField`、`toLngField`、`toLatField` | `valueField`、`color`、`curveness 0..1`、`effect{show,period 1..20,trailLength 0..1}`、`datasetId` |

规则：

- 最多 4 个图层；`region` 最多一个，且存在时必须位于第一位。
- 每个图层可用 `datasetId` 引用 `datasets[]` 中已声明的数据集；缺省沿用组件自身的 `datasetId`。
  看板运行时会查询全部数据集，图层不需要额外请求。
- 字段存在性在数据集有字段缓存时校验，缺失会拒绝保存。
- 没有 `region` 图层时，区域填色回落到 `encode` 的 `role=x` 维度与 `role=value` 度量。
- 经纬度为 WGS84 十进制度；`nameField` 用作 tooltip 名称。
- 存在叠加层时前端会创建 `geo` 坐标系，涟漪点与飞线使用 `coordinateSystem=geo`，并使用 `visualMap.seriesIndex=0` 只给区域填色。
- 主题 token 的 `map.area/border/emphasis/label` 会作为区域底色、边界、高亮与标签颜色。

## 15. 图表样式预设

`type=echarts` 的组件可以通过 `style.chart` 一键套用图表皮肤（渐变柱、发光折线、坐标轴/图例/tooltip 配色），
无需手写 `echartsOption`。

设计器入口：**自定义配置 → 系列 → 图表风格**。下拉包含
「默认（跟随主题）/ 科技风 / 简约 / 深色」，默认选中「默认」，即不写 `style.chart`、保持原有渲染。

```json
{
  "style": {
    "chart": {
      "preset": "tech",
      "gradient": true,
      "glow": true,
      "showAxis": true,
      "showLegend": true,
      "showLabel": false
    }
  }
}
```

| 字段 | 取值 | 说明 |
| --- | --- | --- |
| `preset` | `tech` / `minimal` / `dark` | 科技风 / 简约 / 深色 |
| `gradient` | 布尔 | 柱状、面积是否使用渐变；缺省按预设决定（tech/dark 开，minimal 关） |
| `glow` | 布尔 | 折线是否发光；缺省按预设决定 |
| `showAxis` | 布尔 | `false` 时隐藏坐标轴（兼容字段，新配置优先使用 `xAxis.show` / `yAxis.show`） |
| `showLegend` | 布尔 | 控制图例显隐 |
| `showLabel` | 布尔 | `true` 时显示数据标签 |
| `legendPosition` | `top` / `bottom` / `left` / `right` | 图例位置 |
| `legendFontSize` | 8–24 | 图例文字字号 |
| `axisTitleFontSize` | 8–24 | 轴标题字号 |
| `axisTitleColor` | 颜色 | 轴标题颜色；缺省跟随预设 |
| `labelFontSize` | 8–24 | 数据标签字号 |
| `barRadius` | 0–16 | 柱条圆角 |
| `xAxis` / `yAxis` | 对象 | 单个坐标轴的样式覆盖：`show/title/labelFontSize/tick/lineColor/grid/gridStyle` |

规则：

- 预设只填充 `echartsOption` 中**没有显式设置**的键；用户在「自定义配置」「高级配置」里的配置优先，
  因此老看板不受影响，新看板也可以局部微调。
- `showAxis/showLegend/showLabel/gradient/glow` 为显式开关，优先级高于 `echartsOption`。
  设计器里的坐标轴/图例/标签/网格线按「自定义配置」的手风琴分组控制：
  图例、网格线、数据标签写入 `echartsOption`，坐标轴显隐与字体写入 `style.chart.xAxis/yAxis`；
  这几个字段主要用于自定义 JSON 或 AI 生成的 spec。
- 序列色优先取主题 token 的 `palette`；未配置时回落到 ECharts 内置色板。
- 渐变基于每个系列的序列色生成：柱状为「实色 → 透明」，面积为「半透明 → 全透明」。
- 发光通过 `lineStyle.shadowBlur/shadowColor` 实现，仅对折线/面积图生效。
- 未配置 `style.chart` 的组件完全保持原有渲染。

## 16. 标题栏与日期时间组件

`title` 与 `clock` 不绑定数据集，不参与取数、聚合、筛选与下钻，只做展示。

### 16.1 `title`

```json
{
  "id": "screen_title",
  "type": "title",
  "layout": { "x": 0, "y": 0, "w": 24, "h": 2 },
  "props": {
    "text": "领导驾驶舱",
    "subText": "学生管理主题",
    "align": "center",
    "frame": "tech-frame"
  },
  "style": {
    "title": {
      "icon": "chart-line",
      "fontSize": 32,
      "fontWeight": 700,
      "color": "#cfeaff",
      "letterSpacing": 4
    }
  }
}
```

| 字段 | 取值 | 说明 |
| --- | --- | --- |
| `props.text` | ≤ 120 字，必填 | 主标题 |
| `props.subText` | ≤ 60 字 | 副标题 |
| `props.align` | `left` / `center` / `right` | 默认 `center` |
| `props.frame` | `none` / `tech-frame` / `bracket` | 科技边框 / 左右括号；默认 `none` |
| `style.title.*` | 见组件皮肤 | 字号、字重、颜色、字距、图标、背景 |

### 16.2 `clock`

```json
{
  "id": "screen_clock",
  "type": "clock",
  "layout": { "x": 1, "y": 0, "w": 8, "h": 1 },
  "props": { "format": "date-week" },
  "style": { "title": { "fontSize": 14, "color": "#9fd3ff" } }
}
```

| `props.format` | 示例输出 | 刷新频率 |
| --- | --- | --- |
| `date-week` | `2026-11-26 星期四` | 每分钟 |
| `date` | `2026-11-26` | 每分钟 |
| `datetime` | `2026-11-26 09:30:05` | 每秒 |
| `time` | `09:30:05` | 每秒 |

说明：

- 两种组件都不渲染通用组件标题栏，内容区不留默认内边距，由组件自身控制。
- 在栅格画布中默认尺寸：标题栏 `24 × 2`，日期时间 `4 × 1`；像素画布按换算后的像素尺寸落位。
- `title` 组件在没有 `props.text` 时前端会显示占位文案，但保存时服务端要求 `props.text` 必填。

## 17. 容器组件

`type=container` 用于承载大屏面板的背景与标题，把多个组件归为一组。
容器不绑定数据集，不参与取数、聚合、筛选与下钻。

```json
{
  "version": 2,
  "widgets": [
    {
      "id": "panel_sales",
      "type": "container",
      "title": "招生数据",
      "layout": { "x": 16, "y": 96, "w": 420, "h": 720 },
      "style": {
        "card": { "preset": "tech-corner", "fill": { "type": "color", "color": "rgba(9,26,48,0.55)" } },
        "title": { "icon": "chart-column", "decoration": "gradient-underline" }
      }
    },
    {
      "id": "kpi_total",
      "type": "kpi",
      "parentId": "panel_sales",
      "layout": { "x": 0, "y": 0, "w": 24, "h": 6 },
      "datasetId": "ds_sales",
      "encode": { "measures": [{ "field": "amount", "agg": "sum", "role": "value" }] }
    }
  ]
}
```

新建容器的默认尺寸为 `12 × 8`（栅格单位；像素模式下按画布换算）。

规则：

- 子组件通过 `parentId` 指向容器；`parentId` 必须指向同看板内的 `container` 组件。
- 容器不能再嵌套容器（`container.parentId` 不允许设置）。
- 容器自身不需要 `datasetId`、`encode`、`chart`；即使传入也会被移除。
- 子组件的 `layout` 是**容器内部的 24 栅格坐标**，与看板本身是栅格还是像素无关。
  - `x` 取值 `0..23`，`w` 取值 `1..24`，且 `x + w ≤ 24`；
  - `y`/`h` 以 20px 行高、4px 间距为单位；
  - 旧版绝对坐标（`x > 23` 或 `w > 24`）会自动回落到默认 `12 × 6`；
  - 归入容器或读取旧看板时，`y`/`h` 会按容器可见行数收敛，避免子组件落在容器外不可见。
- 容器移动或调整大小时，子组件按容器内栅格自动重排，不需要再同步位移。
- 删除容器会级联删除子组件。
- 容器在渲染时只渲染顶层组件，子组件嵌套在容器卡片内部；容器卡片会裁剪超出内容区的子组件。
- 子组件的栅格重叠仍允许（像素画布语义），但建议按 24 栅格排布避免互相遮挡。
- 从组件库拖拽新组件到容器内部，会按容器 24 栅格计算落点并自动写入 `parentId`；点击添加或手动在属性面板「所属容器」中选择也可以。
