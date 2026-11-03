# 集成模板下载目录

本目录提供「集成模板市场」页面（`docs/templates.html`）的可下载模板文件。

## 目录结构

```text
download/
├── etl/   # 单任务 ETL 模板，格式为 datay-etl-task
└── app/   # 完整数据应用资产包，格式为 datay-app-package
```

## 导入方式

- `etl/*.json`：登录 DataY 后进入 **数据集成 → 导入任务**，选择下载的 JSON。
- `app/*.json`：登录 DataY 后进入 **数据应用 → 应用市场 → 导入资产包文件**，选择下载的 JSON。

## 维护说明

- `etl/` 下的模板由 `docs/templates.html` 中的模板卡片引用，新增或改名时请同步更新页面。
- `app/` 下的两个资产包来自 `datay-web/src/main/resources/app-packages/`，升级系统资产包后请重新复制：

```bash
cp datay-web/src/main/resources/app-packages/*.json docs/templates/download/app/
```

- 模板中的 `references.dataSources` 只是占位数据源，导入时按「名称 + 类型 + 地址」匹配当前租户的数据源；匹配不到时会在导入结果中给出警告，导入后在画布中重新选择数据源即可。
