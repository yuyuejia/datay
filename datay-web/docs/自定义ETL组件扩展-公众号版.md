# 不用改前端，也能扩展 ETL 组件：DataY 自定义组件开发实践

> 一个 ETL 平台好不好用，不只看它内置了多少组件，更看它能不能让团队把自己的业务能力接进来。

在实际项目里，ETL 组件往往很快就不够用：

- 要对接一套内部 CRM、ERP，没有现成 Connector；
- 想加一个行业专用的数据清洗逻辑；
- 某个业务系统需要特殊的分页、鉴权、字段映射；
- 外部团队想独立交付插件，而不是改主平台代码。

传统做法通常是：后端写组件、前端写配置页、注册组件、再发一个版本。每加一个组件，前后端都要动，交付周期自然短不了。

DataY 的组件扩展思路是：**把配置表单也变成组件元数据，让组件自己描述自己。** 开发者只需要写 Java 组件和字段注解，前端就能自动生成配置表单；组件还可以打包成外部 JAR，放入插件目录后由平台加载。

这篇文章，我们完整讲一遍自定义 ETL 组件的开发方式。

---

## 一、先看效果：一个组件，自动拥有配置表单

在 DataY 中，一个组件通过两个注解完成“自我描述”：

- `@ComponentRegister`：告诉平台“我是什么组件”；
- `@ComponentField`：告诉前端“我有哪些配置项、应该怎么渲染”。

后端启动时会扫描这些注解，生成组件元数据，并通过组件目录接口返回给前端。前端发现组件带有字段元数据后，自动使用动态表单渲染配置页面。

整体链路可以简化为：

```text
Java 组件
  ├─ @ComponentRegister
  └─ @ComponentField
          │
          ▼
  组件目录 catalog
          │ fields
          ▼
  DynamicComponentForm
          │
          ▼
  任务 config JSON
```

任务配置 JSON 的结构、数据库存储和引擎执行链路都保持不变。

也就是说，**组件开发只需要关注业务逻辑和配置字段，不需要再为每个组件手写一套 Vue 配置页。**

---

## 二、写一个最小可用的 ETL 组件

先看一个完整示例。这个组件叫 `ExampleHelloSource`，它会按配置生成 JSON 数组或纯文本数据，适合用来演示组件扩展。

### 1. 创建一个 Maven 模块

外部插件模块只需要依赖 `datay-core`，并且使用 `provided`，避免把平台核心类重复打进插件 JAR。

```xml
<dependency>
    <groupId>com.datay</groupId>
    <artifactId>datay-core</artifactId>
    <version>${datay.version}</version>
    <scope>provided</scope>
</dependency>
```

如果是外部团队独立开发，也可以直接创建一个普通 Maven 工程，把构建产物作为插件 JAR 使用。

### 2. 实现组件类

```java
@ComponentRegister(
    value = "ExampleHelloSource",
    name = "示例数据生成器",
    group = "示例插件",
    desc = "按配置生成示例 JSON 数组或文本数据",
    order = 10
)
public class ExampleHelloSource extends FlowComponent {

    @ComponentField(
        label = "消息内容",
        type = ComponentFieldType.INPUT,
        required = true,
        group = "基础配置",
        order = 10,
        defaultValue = "hello from plugin"
    )
    private String message = "hello from plugin";

    @ComponentField(
        label = "生成行数",
        type = ComponentFieldType.NUMBER,
        group = "基础配置",
        order = 20,
        defaultValue = "3",
        min = 1,
        max = 1000,
        step = 1
    )
    private Integer count = 3;

    @ComponentField(
        label = "输出格式",
        type = ComponentFieldType.SELECT,
        group = "基础配置",
        order = 30,
        defaultValue = "JSON_ARRAY",
        options = {
            @ComponentFieldOption(value = "JSON_ARRAY", label = "JSON 数组"),
            @ComponentFieldOption(value = "TEXT", label = "纯文本")
        }
    )
    private String outputFormat = "JSON_ARRAY";

    @ComponentField(
        key = "sourceId",
        label = "示例数据源",
        type = ComponentFieldType.DATASOURCE,
        required = true,
        group = "数据源与表（演示）",
        order = 10,
        schemaField = "schema",
        help = "演示数据源选择器；平台会把数据源 ID 解析为完整连接信息"
    )
    private DatasourceInfo datasource;

    @ComponentField(
        key = "schema",
        label = "Schema",
        type = ComponentFieldType.SCHEMA,
        group = "数据源与表（演示）",
        order = 20,
        dataSourceField = "sourceId",
        placeholder = "请选择 Schema"
    )
    private String schema;

    @ComponentField(
        key = "table",
        label = "示例数据表",
        type = ComponentFieldType.TABLE,
        group = "数据源与表（演示）",
        order = 30,
        dataSourceField = "sourceId",
        schemaField = "schema",
        placeholder = "请选择数据表"
    )
    private String table;

    @ComponentField(
        key = "columns",
        label = "示例字段",
        type = ComponentFieldType.COLUMN,
        group = "数据源与表（演示）",
        order = 40,
        multiple = true,
        valueFormat = ComponentFieldValueFormat.ARRAY,
        dataSourceField = "sourceId",
        schemaField = "schema",
        tableField = "table"
    )
    private List<String> columns;

    @ComponentField(
        label = "标签",
        type = ComponentFieldType.MULTI_SELECT,
        group = "高级配置",
        order = 10,
        multiple = true,
        valueFormat = ComponentFieldValueFormat.ARRAY,
        options = {
            @ComponentFieldOption(value = "alpha", label = "Alpha"),
            @ComponentFieldOption(value = "beta", label = "Beta")
        }
    )
    private List<String> tags;

    @ComponentField(
        label = "写入时间戳",
        type = ComponentFieldType.SWITCH,
        group = "高级配置",
        order = 20,
        defaultValue = "true"
    )
    private boolean includeTimestamp = true;

    @ComponentField(
        label = "模板文件",
        type = ComponentFieldType.FILE,
        group = "更多控件（演示）",
        order = 10,
        placeholder = "请选择模板文件"
    )
    private String templateFile;

    @ComponentField(
        label = "自定义参数",
        type = ComponentFieldType.TEXTAREA,
        group = "更多控件（演示）",
        order = 20,
        placeholder = "{\"key\":\"value\"}"
    )
    private String customParams;

    public ExampleHelloSource() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        writeRecords(buildOutput(flowFile));
    }

    private FlowFile buildOutput(FlowFile input) {
        JSONArray rows = new JSONArray();
        for (int i = 1; i <= count; i++) {
            JSONObject row = new JSONObject();
            row.put("id", i);
            row.put("message", message);

            if (tags != null && !tags.isEmpty()) {
                row.put("tags", tags);
            }
            if (includeTimestamp) {
                row.put("timestamp", System.currentTimeMillis());
            }
            rows.add(row);
        }

        FlowFile output = new FlowFile();
        output.setAttribute("_source", "ExampleHelloSource");
        if (datasource != null) {
            output.setAttribute("_datasourceType", datasource.getType());
            output.setAttribute("_datasourceSchema", schema != null ? schema : datasource.getDbschema());
        }
        if (table != null) {
            output.setAttribute("_demoTable", table);
        }
        if (columns != null) {
            output.setAttribute("_demoColumns", columns);
        }
        if (templateFile != null) {
            output.setAttribute("_templateFile", templateFile);
        }
        if (customParams != null) {
            output.setAttribute("_customParams", customParams);
        }
        output.setJsonArray(rows);
        return output;
    }

    // 省略 getter / setter
}
```

这里有几个关键点：

1. **继承 `FlowComponent`**：和平台内置组件使用同一套执行模型。
2. **在构造函数中设置组件类型**：例如 `SOURCE`、`OPERATOR`、`SINK`。
3. **通过注解声明配置字段**：字段名默认就是任务 config 中的 key，也可以用 `key` 显式指定。
4. **提供 getter / setter**：平台使用 Bean 映射填充配置，标准 getter / setter 最稳妥。
5. **关联字段选择器**：`DATASOURCE`、`SCHEMA`、`TABLE`、`COLUMN` 通过 `dataSourceField`、`schemaField`、`tableField` 串联，选择数据源后会自动带出 Schema、表和字段。
6. **业务逻辑只关心 FlowFile**：读取输入、生成输出、交给引擎继续流转。

> 所有 `type = ComponentFieldType.DATASOURCE` 的字段都会在任务保存或调试时解析数据源。平台会把数据源 ID 展开为包含 `type`、`url`、`driver`、`username`、`password`、`hostname`、`port`、`extraParams`、`dbschema` 的对象，并写回该字段的配置 key。字段 key 可以是 `sourceId`，也可以是 `targetSourceId` 这类自定义 key；Schema 通过 `schemaField` 关联。使用自定义 key 时，组件字段名建议和 key 保持一致，方便引擎做 Bean 映射。组件侧不需要自己再查数据源配置。

---

## 三、字段注解不只是“画一个输入框”

`@ComponentField` 的价值，不只是告诉前端“这里放一个输入框”，而是把配置语义完整表达出来。

常用字段类型包括：

- `INPUT`：单行输入框；
- `PASSWORD`：密码输入框；
- `TEXTAREA`：多行文本；
- `NUMBER`：数字输入，支持 `min`、`max`、`step`；
- `SELECT`：下拉框；
- `MULTI_SELECT`：多选下拉；
- `RADIO`：单选；
- `CHECKBOX_GROUP`：多选复选框；
- `SWITCH`：开关；
- `DATASOURCE`：数据源选择器；
- `SCHEMA`：Schema 选择器；
- `TABLE`：数据表选择器；
- `COLUMN`：字段选择器；
- `FILE`：文件选择器；
- `CUSTOM`：自定义 Vue 组件。

除此之外，还可以声明：

- `required`：是否必填；
- `defaultValue`：默认值；
- `placeholder`：输入提示；
- `help`：字段说明；
- `pattern`：正则校验；
- `dependsOn` / `visibleWhen`：字段显隐依赖；
- `dataSourceField` / `schemaField` / `tableField`：关联字段；
- `secret`：敏感字段；
- `valueFormat`：配置值格式。

其中 `valueFormat` 很实用。老的组件配置里，多选值可能是数组，也可能是 `a,b,c` 这样的逗号字符串。动态表单可以通过 `ARRAY` 或 `COMMA_STRING` 保持和原有配置结构兼容，不需要为了新表单改历史任务。

---

## 四、前端为什么能自动生成表单？

组件启动注册时，平台会扫描组件类上的 `@ComponentField`，生成字段元数据，并挂到组件描述信息里。

组件目录接口 `GET /api/etl-components/catalog` 返回的数据中，每个组件会多出一个 `fields` 列表：

```json
{
  "code": "ExampleHelloSource",
  "name": "示例数据生成器",
  "group": "示例插件",
  "fields": [
    {
      "key": "message",
      "label": "消息内容",
      "type": "INPUT",
      "required": true,
      "defaultValue": "hello from plugin"
    },
    {
      "key": "count",
      "label": "生成行数",
      "type": "NUMBER",
      "min": 1,
      "max": 1000
    },
    {
      "key": "sourceId",
      "label": "示例数据源",
      "type": "DATASOURCE",
      "required": true,
      "schemaField": "schema"
    },
    {
      "key": "schema",
      "label": "Schema",
      "type": "SCHEMA",
      "dataSourceField": "sourceId"
    },
    {
      "key": "table",
      "label": "示例数据表",
      "type": "TABLE",
      "dataSourceField": "sourceId",
      "schemaField": "schema"
    },
    {
      "key": "columns",
      "label": "示例字段",
      "type": "COLUMN",
      "multiple": true,
      "valueFormat": "ARRAY",
      "tableField": "table"
    }
  ]
}
```

当任务保存或调试时，平台还会把配置中的 `sourceId` 展开成完整的数据源连接信息：

```json
{
  "units": [
    {
      ".id": "node-1",
      ".name": "ExampleHelloSource",
      "parallelism": "2",
      "sourceId": {
        "type": "MYSQL",
        "url": "jdbc:mysql://127.0.0.1:3306/demo",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "root",
        "password": "******",
        "hostname": "127.0.0.1",
        "port": "3306",
        "extraParams": {
          "serverTimezone": "Asia/Shanghai"
        },
        "dbschema": "demo"
      },
      "schema": "demo",
      "table": "demo_table",
      "columns": ["id", "name"]
    }
  ],
  "connections": [],
  "version": "1.0.0"
}
```

组件收到的是已经解析好的 `DatasourceInfo`，不需要自己再根据 ID 查数据源配置。  
这样扩展组件和平台内置组件使用的是同一套数据源解析逻辑，连接信息不会缺失。

前端设计器拿到组件后，会这样选择配置页面：

- `fields` 非空：使用 `DynamicComponentForm` 动态渲染；
- `fields` 为空：继续使用原有的 `componentConfigMap` 中注册的 `*Config.vue`。

这意味着平台可以**渐进式迁移**，不需要一次性改造全部组件。

已经注解的组件自动使用动态表单；还没迁移的组件继续走原有配置页，老任务不受影响。

动态表单保存时，还会保留 config 中未在注解里声明的属性，并保留 `parallelism` 等平台字段。业务团队不需要担心“新表单把旧配置弄丢”。

---

## 五、打包成外部插件 JAR

组件写完后，可以直接编译成普通 JAR。

```bash
mvn -pl datay-plugin-example -am -DskipTests package
```

产物类似：

```text
datay-plugin-example/target/datay-plugin-example.jar
```

然后有两种加载方式。

### 方式一：启动时指定插件目录

```bash
java -Ddatay.component.plugin-dir=/path/to/plugins \
  -jar datay-web/target/datay-web-<version>.jar
```

### 方式二：放入默认插件目录

```bash
mkdir -p plugins
cp datay-plugin-example/target/datay-plugin-example.jar plugins/
```

插件目录的优先级是：

1. JVM 参数：`-Ddatay.component.plugin-dir`
2. 环境变量：`DATAY_COMPONENT_PLUGIN_DIR`
3. 默认目录：`${user.dir}/plugins`

平台启动时会扫描插件目录中的所有 JAR，找到实现了 `Component` 且带有 `@ComponentRegister` 注解的类，注册到组件工厂中。

插件目录里的多个 JAR 会被加入同一个 `URLClassLoader`，因此插件依赖的第三方库也可以一起放入插件目录。

需要注意：**目前插件是启动时加载，替换 JAR 后需要重启应用，不支持运行时热插拔。**
这是有意为之的取舍：实现简单、行为确定，也避免运行时类加载和状态管理带来的复杂度。

---

## 六、外部插件开发注意事项

### 1. 核心依赖使用 provided

`datay-core` 必须使用 `provided`，否则插件 JAR 会把平台核心类一起打进去，容易出现类冲突。

```xml
<scope>provided</scope>
```

`fastjson2` 等平台已经提供的公共依赖，也建议使用 `provided`。

### 2. 组件编码要稳定

`@ComponentRegister.value` 是任务配置中引用的组件编码，一旦发布就尽量不要改。
显示名称、分组、描述可以调整，但编码最好保持稳定。

### 3. 字段 key 要和配置结构一致

如果组件内部字段名和 config 中的 key 不一致，可以用 `key` 指定：

```java
@ComponentField(
    key = "sourceId",
    label = "目标数据源",
    type = ComponentFieldType.DATASOURCE
)
private DatasourceInfo datasource;
```

### 4. 敏感字段记得标记 secret

密码、Token、AccessKey 这类字段，设置 `secret = true`，前端会按敏感字段处理。

### 5. 多值字段明确 valueFormat

新组件建议直接使用数组：

```java
valueFormat = ComponentFieldValueFormat.ARRAY
```

如果要兼容老配置，再使用 `COMMA_STRING`。

### 6. 插件也要考虑版本管理

外部插件适合独立交付，但也意味着平台和插件之间需要约定好版本。
建议在插件 JAR 的 `pom.xml` 中明确版本，并在发布说明中标注兼容的 DataY 版本。

---

## 七、哪些场景适合做成插件？

下面这些场景，特别适合用自定义组件扩展：

- 对接内部业务系统，例如 CRM、ERP、OA；
- 行业专用数据源或数据目标；
- 企业自定义的数据清洗、脱敏、校验逻辑；
- 需要独立交付给客户或项目团队的 Connector；
- 希望和主平台解耦的二次开发能力。

对于平台自身来说，也可以把组件逐步迁移到注解模式：

- 新组件默认使用 `@ComponentField`；
- 老组件保持原有 `*Config.vue`；
- 迁移一个，切换一个，不影响其他组件。

这就是“渐进式组件化”的好处：**不搞一次性大重构，先把新能力跑起来。**

---

## 八、写在最后

自定义 ETL 组件扩展，真正麻烦的往往不是 `execute()` 里的业务逻辑，而是组件之外的配套工作：配置页面、字段校验、前后端联调、发布集成。

DataY 通过字段注解和动态表单，把这些重复工作收敛成一份组件元数据；再通过外部 JAR 插件机制，让组件可以独立开发、独立交付。

一句话总结：

> **组件只写一次，表单自动生成，插件独立交付。**

如果你也想给平台加一个自己的 ETL 组件，可以从仓库里的 `datay-plugin-example` 开始。
它包含两个完整示例：

- `ExampleHelloSource`：演示输入框、数字、下拉、多选、数据源、Schema、表、字段、文件等动态表单控件；
- `ExampleJdbcSingleTableInput`：自定义 JDBC 单表输入，从 MySQL 读取单表数据，再通过 `StreamJdbcOutput` 写入 DuckDB。

其中 JDBC 单表输入已经做过真实链路验证：5 行 MySQL 样例数据成功同步到 DuckDB，行数、金额合计和首行字段均校验通过。
两个组件都包含字段注解、构建配置和测试，可以直接作为你第一个插件的起点。

后续我们还会继续完善组件市场、插件依赖管理和版本兼容策略。
让“扩展一个组件”这件事，越来越像写一个普通 Java 类一样简单。

---

## 关于 DataY

DataY 是一套**开源、轻量、一体化的数据平台**，设计原则只有一句话：

> **数据平台可以很小，功能不打折。**

它不依赖 Hadoop、Spark 等外部大数据集群，一个 JAR 就能启动，在 2 核 4G 的机器上即可跑通从数据接入到分析应用的完整链路。可以把它理解为：

> **DataY = DataX + Nifi + DolphinScheduler**

但不是三个开源项目的拼盘，而是用同一套运行时重新实现数据同步、数据流编排和任务调度。

核心能力包括：

- **数据集成**：MySQL、PostgreSQL、Oracle、SQL Server、SAP HANA、Doris、ClickHouse、DuckDB 等二十余种数据源，支持全量、增量和 CDC 实时同步；
- **可视化 ETL**：拖拽画布编排输入、清洗、关联、转换、输出，也支持 JSON 任务定义和代码组件；
- **任务调度**：Quartz Cron 精准到秒，DAG 任务编排，失败重试，master / worker 可分离部署；
- **维度建模与指标管理**：星型 / 雪花模型、维度与事实表、原子指标和衍生指标；
- **高性能数仓**：内嵌 DuckDB，列存 + 向量化，也可以外挂 Doris、ClickHouse；
- **数据质量与数据比对**：质量规则、质量门禁、跨库跨表差异比对；
- **智能问数与 AI 能力**：SQL 分析数据集、分析看板、自然语言问数、ETL AI 设计助手、MCP Harness。

本文介绍的自定义 ETL 组件扩展，正是 DataY 组件体系的一部分：

- 组件通过注解声明配置字段，前端自动生成表单；
- 未迁移的组件继续使用原有 Vue 配置页；
- 组件可以打包成外部 JAR，独立开发、独立交付；
- 平台运行链路、任务配置结构和已有任务保持不变。

**适合谁？**

没有专职大数据工程师的中小团队；想用 2 核 4G 快速验证完整数据链路的团队；需要私有化部署的团队；以及希望基于 DataY 做二次开发、沉淀自己行业组件的团队。

**快速开始：**

```bash
git clone https://github.com/yuyuejia/datay
cd datay
mvn clean package -DskipTests
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx
# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

产品首页：<http://datay.yuyuejia.com.cn/>  
在线体验：<http://datay-demo.yuyuejia.com.cn/>  
GitHub：<https://github.com/yuyuejia/datay>  
Gitee：<https://gitee.com/yuyuejia/datay>

---

如果这篇文章对你有帮助，欢迎关注 DataY，后续我们会继续分享数据集成、任务调度、维度建模、智能问数和组件扩展的实践。
