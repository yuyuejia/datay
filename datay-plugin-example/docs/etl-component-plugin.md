# ETL 组件插件化开发指南

本文说明如何通过字段注解让组件自动生成前端配置表单，以及如何把组件打包成外部 JAR 插件。

## 1. 字段注解

在组件配置字段上添加 `@ComponentField`，`ComponentMetadataExtractor` 会在组件注册时扫描注解，
生成前端动态表单所需的元数据。

```java
@ComponentRegister(
    value = "DemoSink",
    name = "示例写入",
    group = "数据输出",
    desc = "示例组件",
    order = 100
)
public class DemoSink extends FlowComponent {

    @ComponentField(
        key = "sourceId",
        label = "目标数据源",
        type = ComponentFieldType.DATASOURCE,
        required = true,
        group = "基础配置",
        order = 10
    )
    private DatasourceInfo datasource;

    @ComponentField(
        label = "目标表名",
        type = ComponentFieldType.TABLE,
        dependsOn = "sourceId",
        dataSourceField = "sourceId",
        schemaField = "schema",
        group = "基础配置",
        order = 20
    )
    private String table;

    @ComponentField(
        label = "写入模式",
        type = ComponentFieldType.SELECT,
        defaultValue = "INSERT",
        options = {
            @ComponentFieldOption(value = "INSERT", label = "INSERT"),
            @ComponentFieldOption(value = "UPDATE", label = "UPDATE")
        },
        group = "基础配置",
        order = 30
    )
    private String mode;

    @ComponentField(
        label = "事件类型",
        type = ComponentFieldType.MULTI_SELECT,
        valueFormat = ComponentFieldValueFormat.ARRAY,
        options = {
            @ComponentFieldOption(value = "INSERT", label = "INSERT"),
            @ComponentFieldOption(value = "DELETE", label = "DELETE")
        },
        group = "高级配置",
        order = 10
    )
    private List<String> eventTypes;
}
```

### 常用属性

| 属性                                             | 说明                                       |
| ------------------------------------------------ | ------------------------------------------ |
| `key`                                            | 任务 config 中的 key，默认使用 Java 字段名 |
| `label`                                          | 表单显示名称，默认使用 key                 |
| `type`                                           | 前端控件类型，见下表                       |
| `group`                                          | 表单分组，默认“基础配置”                   |
| `order`                                          | 同组内排序，值越小越靠前                   |
| `required`                                       | 是否必填，隐藏字段不参与校验               |
| `defaultValue`                                   | 默认值，使用字符串表示                     |
| `options`                                        | 下拉框、单选、多选等选项                   |
| `multiple`                                       | 是否多选                                   |
| `valueFormat`                                    | 保存到 config 的值格式                     |
| `dependsOn` / `visibleWhen`                      | 字段显隐依赖                               |
| `dataSourceType`                                 | 数据源类型过滤                             |
| `dataSourceField` / `schemaField` / `tableField` | 关联字段 key                               |
| `min` / `max` / `step`                           | 数字控件约束                               |
| `pattern` / `patternMessage`                     | 正则校验及失败提示                         |
| `customComponent`                                | `CUSTOM` 类型对应的前端组件名              |

### 字段类型

| 类型             | 前端控件         |
| ---------------- | ---------------- |
| `INPUT`          | 单行输入框       |
| `PASSWORD`       | 密码输入框       |
| `TEXTAREA`       | 多行文本域       |
| `NUMBER`         | 数字输入框       |
| `SELECT`         | 下拉框           |
| `MULTI_SELECT`   | 多选下拉框       |
| `RADIO`          | 单选按钮组       |
| `CHECKBOX_GROUP` | 多选复选框组     |
| `SWITCH`         | 开关             |
| `DATASOURCE`     | 数据源选择器     |
| `SCHEMA`         | Schema 选择器    |
| `TABLE`          | 数据表选择器     |
| `COLUMN`         | 数据表字段选择器 |
| `FILE`           | 文件管理选择器   |
| `CUSTOM`         | 自定义 Vue 组件  |

### 值格式

`valueFormat` 用于兼容组件已有的 config 结构：

- `AUTO`：前端按字段类型推断。
- `STRING` / `NUMBER` / `BOOLEAN`：基础类型。
- `ARRAY`：多选值保存为数组。
- `COMMA_STRING`：多选值保存为逗号分隔字符串，例如 `t1,t2`。

## 2. 动态表单与原有表单的兼容

- catalog 接口 `GET /api/etl-components/catalog` 会为每个组件返回 `fields`。
- 前端设计器在 `fields` 非空时使用 `DynamicComponentForm` 动态渲染；否则继续使用
  `componentConfigMap` 中注册的原有 `*Config.vue`。
- 动态表单保存时会保留 config 中未在字段注解里声明的属性，并保留 `parallelism`。
- 因此可以逐步迁移组件，不需要一次性改造全部组件。

所有 `type = ComponentFieldType.DATASOURCE` 的字段都会在保存或调试任务时解析数据源：
平台会把数据源 ID 展开成完整连接信息（`type`、`url`、`driver`、`username`、`password`、
`hostname`、`port`、`extraParams`、`dbschema`），并写回该字段的配置 key。字段 key 可以是
`sourceId`，也可以是自定义 key（例如 `targetSourceId`）；对应的 Schema 通过 `schemaField`
指定。使用自定义 key 时，组件字段名建议与 key 保持一致，便于引擎进行 Bean 映射；旧组件没有
字段元数据时，仍然兼容约定的 `sourceId` 字段。

### 自定义字段组件

当内置控件无法满足需求时，可以把字段声明为 `CUSTOM`：

```java
@ComponentField(
    label = "高级配置",
    type = ComponentFieldType.CUSTOM,
    customComponent = "MyAdvancedField"
)
private String advancedConfig;
```

然后在 `datay-web/src/main/webapp/app/entities/etl-task/dynamic/custom/` 下创建
`MyAdvancedField.vue`。组件会收到以下 props：

- `modelValue`：当前字段值；
- `field`：字段元数据；
- `formValues`：当前表单全部值。

修改值时向父组件发送 `change` 事件，payload 格式为：

```js
emit("change", { value: newValue });
// 或同时更新其他配置项
emit("change", { value: newValue, extra: { schema: "public" } });
```

## 3. 外部 JAR 插件

组件实现也可以放在外部 JAR 中，启动时自动加载：

1. 插件目录优先级：
   - JVM 参数：`-Ddatay.component.plugin-dir=/path/to/plugins`
   - 环境变量：`DATAY_COMPONENT_PLUGIN_DIR`
   - 默认目录：`${user.dir}/plugins`
2. 将组件 JAR 放入插件目录，重启应用生效。
3. 插件 JAR 中的类需要：
   - 实现 `com.data.job.Component`；
   - 使用 `@ComponentRegister` 标注；
   - 对 `datay-core` 使用 `provided` 依赖，避免把核心类打进插件 JAR。
4. 插件 JAR 依赖的其他库可以一起放入插件目录，加载器会把目录下所有 JAR 加入同一个
   `URLClassLoader`。
5. 外部插件同样支持 `@ComponentField`，catalog 会自动返回字段元数据并启用动态表单。

示例 `pom.xml` 依赖：

```xml
<dependency>
    <groupId>com.datay</groupId>
    <artifactId>datay-core</artifactId>
    <version>${datay.version}</version>
    <scope>provided</scope>
</dependency>
```

### 示例插件模块

仓库自带一个可直接构建和运行的外部 JAR 插件示例：`datay-plugin-example`。它包含两个组件：

- `ExampleHelloSource`：演示 `@ComponentRegister`、`@ComponentField`、动态表单字段以及 Source 组件的数据输出；
- `ExampleJdbcSingleTableInput`：自定义 JDBC 单表输入，演示 `DATASOURCE` / `SCHEMA` / `TABLE` 字段联动，并可直接对接 `StreamJdbcOutput` 完成 MySQL 到 DuckDB 的同步。

```bash
# 在项目根目录构建，产物为 datay-plugin-example/target/datay-plugin-example.jar
mvn -pl datay-plugin-example -am -DskipTests package

# 放入宿主应用工作目录下的默认插件目录
mkdir -p plugins
cp datay-plugin-example/target/datay-plugin-example.jar plugins/
```

也可以在启动时通过 `-Ddatay.component.plugin-dir=datay-plugin-example/target` 直接指定
构建产物目录。详细说明见 [datay-plugin-example/README.md](../README.md)。

> 注意：插件加载发生在启动阶段，不支持热插拔。替换 JAR 后需要重启应用。
