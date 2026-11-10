# datay-plugin-example

DataY ETL 外部 JAR 插件示例模块。它演示如何：

- 通过 `@ComponentRegister` 注册一个 ETL 组件；
- 通过 `@ComponentField` 声明配置字段，让前端自动渲染动态表单；
- 打包成一个不包含 `datay-core` 的普通 JAR，放入插件目录后由宿主应用加载。

示例模块包含两个组件：

- `ExampleHelloSource`：显示名称为“示例数据生成器”，演示各类动态表单字段；
- `ExampleJdbcSingleTableInput`：显示名称为“示例 JDBC 单表输入”，从指定数据源读取单表数据，可直接对接 `StreamJdbcOutput`。

## 构建

在项目根目录执行：

```bash
mvn -pl datay-plugin-example -am -DskipTests package
```

构建产物固定为：

```text
datay-plugin-example/target/datay-plugin-example.jar
```

## 使用

### 方式一：启动时指定插件目录

```bash
# JAR 名称以实际构建版本为准，例如 datay-web-1.2.0.jar
java -Ddatay.component.plugin-dir=/绝对路径/datay-plugin-example/target -jar datay-web/target/datay-web-<version>.jar
```

在 IDE 中启动时，把 `-Ddatay.component.plugin-dir=/绝对路径/datay-plugin-example/target`
加入 VM options 即可。也可以使用环境变量：

```bash
export DATAY_COMPONENT_PLUGIN_DIR=/绝对路径/datay-plugin-example/target
```

### 方式二：放入默认插件目录

```bash
mkdir -p plugins
cp datay-plugin-example/target/datay-plugin-example.jar plugins/
```

默认插件目录为 `${user.dir}/plugins`，即应用启动时工作目录下的 `plugins/`。

插件在应用启动时加载，替换 JAR 后需要重启应用，不支持运行时热插拔。

## 验证

1. 启动应用后打开 ETL 任务设计器；
2. 在组件面板的“示例插件”分组中找到“示例数据生成器”或“示例 JDBC 单表输入”；
3. `示例数据生成器` 可配置“消息内容”“生成行数”“输出格式”等字段；
4. `示例 JDBC 单表输入` 可配置源数据源、Schema、源表，下游连接 `StreamJdbcOutput` 同步到目标库；
5. 运行任务，查看节点的数据输出。

同时可以访问组件目录接口确认字段元数据已返回：

```bash
curl http://localhost:8080/api/etl-components/catalog
```

响应中 `ExampleHelloSource` 的 `fields` 应包含基础控件（`message`、`count`、`outputFormat`、`tags`、`includeTimestamp`）、数据源/表控件（`sourceId`、`schema`、`table`、`columns`）以及文件/文本控件（`templateFile`、`customParams`）。

### MySQL 到 DuckDB 集成验证

`ExampleJdbcSingleTableInputTest` 会实际创建 MySQL 源表，通过自定义 JDBC 单表输入读取 5 行数据，
再由 `StreamJdbcOutput` 写入 DuckDB，并校验行数、金额合计和首行字段。

默认连接 `127.0.0.1:3307`、用户 `root`、空密码；MySQL 不可用时测试自动跳过。也可以通过
系统属性指定其他 MySQL：

```bash
mvn -pl datay-plugin-example -am \
  -Dtest=ExampleJdbcSingleTableInputTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Ddatay.test.mysql.host=127.0.0.1 \
  -Ddatay.test.mysql.port=3306 \
  -Ddatay.test.mysql.user=root \
  -Ddatay.test.mysql.password=your_password \
  test
```

## 数据源配置说明

示例组件的“示例数据源”字段使用平台约定的 `sourceId` key。保存或调试任务时，平台会解析所有
`DATASOURCE` 类型字段，把数据源 ID 转换为完整连接信息，并注入到组件的 `DatasourceInfo` 字段；
自定义 key（例如 `targetSourceId`）同样支持。生成的任务定义中会包含 `type`、`url`、`driver`、
`username`、`password`、`hostname`、`port`、`extraParams`、`dbschema`。

## 打包说明

- `datay-core` 和 `fastjson2` 使用 `provided` 依赖，不会打进插件 JAR，运行期由宿主应用提供。
- 插件只包含自己的类；如果插件还依赖其他第三方库，可以把这些 JAR 一起放入同一插件目录，
  加载器会将目录内所有 JAR 加入同一个 `URLClassLoader`。
- 组件实现必须继承 `com.data.job.Component`，并使用 `@ComponentRegister` 标注。
