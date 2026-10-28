# Token 两小时过期、日期每天变：HTTP 接口同步怎么配才不翻车

> 先调认证接口换 token，再带 token 取数；token 自动传递、时间窗口自动滚动、分页自动翻页。
> 这不是一段临时脚本，而是一条可调度、可复用的 ETL 链路。

> 本文示例中的域名、`client_id`、`client_secret` 均为占位符，实际使用时替换成自己的配置。

---

## 01 接口的“第一次握手”，难倒了很多同步任务

很多数据同步任务，卡住的不是 SQL，而是接口的第一次握手。

业务方给过来的文档通常长这样：

1. 先调 `/oauth/token`，用 `client_id` / `client_secret` 换一个 `access_token`；
2. 再调 `/orders/query`，在请求头里带上 `Authorization: Bearer <access_token>`；
3. 数据分页返回，一页 100 条，可能几十上百页；
4. 查询条件还要带时间范围，每天跑的时候自动取“昨天”。

看起来只是两个请求，真正写成脚本之后，问题才刚开始：

- token 写死在 Header 里，两小时后就 401；
- 每翻一页都重新认证一次，接口没挂，认证接口先被限流；
- 日期写死成某一天，第二天任务还在查前天的数据；
- token 藏在 `data.access_token` 里，路径取错一层，Header 变成 `Bearer {"code":0,...}`；
- 分页跑到一半 token 过期，前面几页已经写进库，后面全失败；
- 任务失败后重跑，数据又重复了一遍。

这类问题的根源，往往不是“脚本写得不够熟”，而是**把认证和取数混在了一段代码里**。

更合理的做法是：把认证和取数拆成两个阶段，把 token 当成一个在管道里流动的变量，而不是配置里写死的常量。

---

## 02 把 token 当成“流经管道的数据”

DataY 的任务模型里，组件之间传递的是 `FlowFile`，它有两部分：

- **数据体**：JSON 数组、JSON 对象、文本、CSV 等；
- **属性**：一组键值对，用来携带页码、表名、时间参数、token 等上下文。

基于这个模型，认证接口和取数接口可以拆成两个 `HttpInvoke` 节点：

```text
[认证接口] ──token──▶ [取数接口] ──rows──▶ [MySQL]
```

- 认证节点负责“换 token”：请求 token 接口，从响应里提取 token；
- 取数节点负责“用 token”：在 Header 里引用 token，按时间参数和分页参数拉数据；
- 目标节点负责“落库”：自动建表、批量写入。

关键点是：**认证节点放在链路最前面，一次任务执行只认证一次；取数节点内部再分页，所有页复用同一个 token。**

不要反过来，把认证节点放在分页节点后面。那样每翻一页都会重新认证，既慢又容易触发限流。

另外，认证节点前面如果还有会输出多条 FlowFile 的节点，它也会被多次执行。必要时用一个只输出一次的触发节点，保证“一次任务、一次认证”。

---

## 03 第一步：认证接口，把 token 抠出来

先看一个典型的 OAuth2 `client_credentials` 认证请求：

```bash
curl -X POST 'https://api.example.com/oauth/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials' \
  -d 'client_id=your-client-id' \
  -d 'client_secret=your-client-secret'
```

返回：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIs...",
    "token_type": "Bearer",
    "expires_in": 7200
  }
}
```

在 DataY 里新建一个 `HttpInvoke` 节点。配置时可以先把业务方给的 cURL 粘到“从 cURL 导入”里，URL、方法、Header、Body 会自动填好，再把写死的 token 和日期换成变量。

认证节点这样配：

| 配置项 | 填写内容 | 说明 |
| --- | --- | --- |
| 请求 URL | `https://api.example.com/oauth/token` | 认证接口地址 |
| 请求方法 | `POST` | 按接口文档 |
| 请求头 | `Content-Type: application/x-www-form-urlencoded` | 表单提交必须设对 |
| 请求体 | `grant_type=client_credentials&client_id=...&client_secret=...` | 和 cURL 的 `-d` 一致 |
| 数据路径 | `$.data.access_token` | 只取 token 字符串 |
| 分页 | 关闭 | 认证只调一次 |

这里最容易配错的是**数据路径**。

DataY 的“数据路径”不只是用来取数组，也可以取单个字符串。填 `$.data.access_token`，它会从响应里把 token 抠出来，作为下游 `FlowFile` 的文本数据。

如果填成 `$.data`，拿到的是一个对象，下游 Header 就会变成 `Bearer {"access_token":"..."}`，接口当然不认。

几种常见情况：

- token 在顶层：填 `$.access_token`；
- token 在 `data` 里：填 `$.data.access_token`；
- token 字段叫 `token`：填 `$.data.token`；
- 认证接口直接返回纯文本 token：数据路径留空，直接使用响应体。

另外，不是所有平台都用表单提交。有的要求 JSON Body，有的要求把 `client_id` / `client_secret` 放到 Basic Auth 请求头里。配置方式不变，按接口文档调整请求头和请求体即可。

**先单独调试认证节点。** 确认输出的确实是一串 token，而不是一段 JSON，再往下接取数节点。

---

## 04 中间加一个 10 行的桥接节点

如果取数接口只调一次、不分页，token 可以直接用 `${FLOW_FILE_DATA}` 引用，下游 Header 写：

```text
Authorization: Bearer ${FLOW_FILE_DATA}
```

`${FLOW_FILE_DATA}` 表示上游节点传递下来的数据体。认证节点输出的是 token，这里替换出来就是 token。

但只要开启分页，就会遇到一个细节：

> DataY 的分页循环会为每一页复制上游 FlowFile 的**属性**，不会逐页复制**数据体**。

也就是说，token 如果只放在数据体里，不开分页时没问题；一旦开启分页，分页节点为每一页创建的 FlowFile 只有属性、没有数据体，`${FLOW_FILE_DATA}` 取不到值，Header 会原样保留成 `Bearer ${FLOW_FILE_DATA}`，然后 401。

解决办法很简单：加一个“JAVA脚本”节点（组件名 `JavaScriptComponent`），把 token 从数据体搬到属性里。

```text
[认证接口] ──token──▶ [JAVA脚本：token 入属性] ──authToken──▶ [取数接口]
```

脚本不长，核心就三件事：取 token、校验、写入属性。

```java
import com.data.job.FlowFile;
import java.util.Map;

public class UserScript {
    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {
        String token = flowFile.getTextData();

        if (token == null || token.isBlank()) {
            throw new RuntimeException("认证接口没有返回 token，请检查数据路径和业务错误码");
        }

        flowFile.setAttribute("authToken", token.trim());
        return flowFile;
    }
}
```

这样 token 就变成了 `authToken` 属性。分页时每一页都会复制属性，所以每一页都能拿到同一个 token。

顺便还多了一层保护：认证接口如果返回 200 但业务失败、没给 token，脚本会直接抛错，任务不会带着一个空 token 继续往下跑。

如果认证节点还要接多个取数节点，也可以在这个脚本节点之后分叉。token 只换一次，多个下游共用。

---

## 05 第二步：取数接口，Header 里只放变量

取数接口的原始 cURL 大概是这样：

```bash
curl -X POST 'https://api.example.com/v1/orders/query' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiIs...' \
  -H 'Content-Type: application/json' \
  -d '{
    "pageNo": 1,
    "pageSize": 100,
    "startTime": "2026-10-27 00:00:00",
    "endTime": "2026-10-27 23:59:59"
  }'
```

在 DataY 里同样可以先“从 cURL 导入”，再把 Authorization 和日期换成变量。取数节点这样配：

| 配置项 | 填写内容 |
| --- | --- |
| 请求 URL | `https://api.example.com/v1/orders/query` |
| 请求方法 | `POST` |
| 请求头 | `Content-Type: application/json`；`Authorization: Bearer ${authToken}` |
| 请求体 | 见下方 |
| 数据路径 | `$.data.list` |
| 分页 | 开启，起始页 `1`，每页 `100`，最大页数 `1000`，间隔 `100ms` |

请求体：

```json
{
  "pageNo": ${pageNo},
  "pageSize": ${pageSize},
  "startTime": "#{NOW - 1d, yyyy-MM-dd} 00:00:00",
  "endTime": "#{NOW - 1d, yyyy-MM-dd} 23:59:59"
}
```

注意几个细节：

- `Authorization` 里的 `${authToken}` 是上一节脚本写入的属性；
- 如果 token 返回值本身已经带了 `Bearer ` 前缀，Header 就直接写 `${authToken}`，不要再加一次；
- 有些接口不用 `Authorization`，而是用 `X-Access-Token`、`X-Auth-Token` 之类的自定义头，变量替换方式一样；
- `pageNo` / `pageSize` 是数字占位，不要加引号；时间表达式是字符串，要放在引号里。

取数接口返回：

```json
{
  "code": 0,
  "data": {
    "list": [
      { "orderNo": "SO-0001", "amount": 128.5, "createdAt": "2026-10-27 09:12:33" }
    ],
    "total": 2350
  }
}
```

数据路径填 `$.data.list`，DataY 会把数组提取出来，交给下游 `StreamJdbcOutput` 自动建表、批量写入。

不同接口的数组位置不一样，可能是 `$.data.list`、`$.data.rows`、`$.result.records`，按实际返回结构填，不要照抄。

---

## 06 动态参数：四组占位符，各管各的

HTTP 接口同步里，最容易被写死的三样东西是：**token、时间、页码**。

DataY 用四组写法分别处理：

| 写法 | 示例 | 作用 | 求值时机 |
| --- | --- | --- | --- |
| `#{...}` | `#{NOW - 1d, yyyy-MM-dd}` | 时间计算、内置参数 | 节点执行时计算 |
| `${pageNo}` / `${pageSize}` | `"pageNo": ${pageNo}` | 当前页码、每页条数（分页开启时） | 每页请求 |
| `${authToken}` | `Bearer ${authToken}` | 上游属性 | 每次请求 |
| `${FLOW_FILE_DATA}` | `Bearer ${FLOW_FILE_DATA}` | 上游数据体 | 非分页场景 |

### 1. 时间参数：让日期自己往前走

最常用的是“昨天”：

```json
{
  "startTime": "#{NOW - 1d, yyyy-MM-dd} 00:00:00",
  "endTime": "#{NOW - 1d, yyyy-MM-dd} 23:59:59"
}
```

今天跑，取昨天；明天跑，自动变成新的昨天。不需要改配置，也不需要人工补数据。

其他常用写法：

| 需求 | 写法 |
| --- | --- |
| 今天 | `#{YYYY-MM-DD}` |
| 昨天 | `#{NOW - 1d, yyyy-MM-dd}` |
| 最近 7 天 | `#{NOW - 7d, yyyy-MM-dd}` |
| 本月第一天 | `#{THIS_MONTH_FIRST_DAY}` |
| 当前毫秒时间戳 | `#{TIMESTAMP}` |
| 昨天同一时刻的毫秒时间戳 | `#{NOW - 1d, timestamp}` |
| 请求唯一标识 | `#{UUID}` |

时间单位要区分大小写：

| 单位 | 含义 |
| --- | --- |
| `d` | 天 |
| `M` | 月 |
| `y` | 年 |
| `H` | 小时 |
| `m` | 分钟 |
| `s` | 秒 |

比如 `1d` 是一天，`1M` 是一个月，`1m` 是一分钟，别写混。

### 2. 分页参数：每一页都不一样

请求体里写：

```json
{
  "pageNo": ${pageNo},
  "pageSize": ${pageSize}
}
```

DataY 每发起一页请求，都会把当前页码和每页条数替换进去。

分页什么时候停？判断很直接：**当前页返回的条数小于 `pageSize`，就认为是最后一页**；返回空数组也直接停止。

例如 `pageSize = 100`：

- 第 1 页返回 100 条 → 继续；
- 第 2 页返回 100 条 → 继续；
- 第 3 页返回 37 条 → 小于 100，停止；
- 如果总数刚好是 200 条，第 3 页会返回空数组，同样停止。

有的接口用 `hasNext` 标记是否还有下一页，DataY 仍然按“返回条数是否小于 pageSize”判断，所以可能会多请求一次空页。确认接口对越界页返回空数组或正常 200，别让这一次多请求报错。

这里有一个很隐蔽的坑：**如果接口实际返回上限比你配置的 `pageSize` 小，分页会提前结束。**

比如你配了 `pageSize = 100`，但接口最多只返回 50 条。第 1 页返回 50 条，DataY 判断“50 < 100”，直接认为没有下一页，最终只同步了 50 条。

所以配置前要先确认接口的每页上限，让 `pageSize` 与接口实际能力一致。

### 3. token：一次获取，多页复用

`${authToken}` 来自上游脚本节点写入的属性。分页循环复制属性，所以第 1 页、第 2 页、第 N 页用的都是同一个 token。

这也是为什么认证节点要放在最前面：**认证一次，取数多页，而不是每页都去换一次 token。**

### 4. 时间参数放在请求体，不要放在 URL

时间表达式在请求体里，会在分页开始前统一求值一次，后续所有页复用同一个时间窗口。

这一点很重要。假设任务在 23:59:58 开始跑：

- 第 1 页还在查“昨天”；
- 翻到第 2 页时刚好跨过零点；
- 如果每页都重新计算时间，后面的页可能就跑到“今天”去了。

同一个任务，前后查了两个不同的时间窗口，数据范围就不一致了。

但要注意：**这个“一次求值”的保证，主要针对请求体。** 如果 `#{...}` 时间表达式写在 URL 或请求头里，它会按页求值，跨零点仍然可能漂移。

所以，时间范围尽量放在 POST 的 JSON Body 里。

如果接口只支持 GET，时间参数必须放在 URL 上，有两个办法：

1. 时间粒度只到“天”，并且确保分页不会跨零点；
2. 在前面加一个 JAVA 脚本节点，把 `startTime` / `endTime` 算好写入属性，后面统一用 `${startTime}` / `${endTime}`。

第二种方式更稳，直接把下面几行加在上一节的 token 桥接脚本里（放在 `return flowFile` 之前）即可：

```java
String day = java.time.LocalDate.now().minusDays(1).toString();
flowFile.setAttribute("startTime", day + " 00:00:00");
flowFile.setAttribute("endTime", day + " 23:59:59");
```

之后请求体或 URL 里写 `"${startTime}"`、`"${endTime}"`，所有页都用同一个值。

### 5. 时区：别忘了服务器在哪里

`#{...}` 的时间计算使用的是 JVM 默认时区。

如果服务器是 UTC，而业务要求北京时间，`#{NOW - 1d, yyyy-MM-dd}` 算出来的“昨天”可能和业务理解的“昨天”差 8 小时。

部署时把时区固定下来，例如：

```bash
java -Duser.timezone=Asia/Shanghai -jar datay-web.jar
```

或者在容器里设置 `TZ=Asia/Shanghai`。

如果接口要求 UTC 时间，就在配置里按 UTC 换算，别让“昨天”变成“前天”。

---

## 07 Token 会过期，任务要按有效期设计

认证接口返回的 `expires_in` 是 7200 秒，也就是两小时。

DataY 不会自动拿这个字段去刷新 token。它做的事情很直接：任务开始时认证一次，拿到 token，后面所有页复用。

这在大多数批任务里够用：

- 100 页、每页 100ms，十几秒跑完；
- 即使几千页，只要总时长小于 token 有效期，也没问题。

但如果分页很慢、页数很多，就要把 token 有效期当成任务设计约束：

1. **减少请求次数**：在接口允许的范围内调大 `pageSize`，减少总页数；
2. **拆分时间窗口**：不要一次跑半年，按天或按周拆成多个任务，每次任务重新认证；
3. **用 DAG 串起来**：多个时间窗口的任务按顺序执行，每个任务都有自己的 token；
4. **控制请求间隔**：`requestInterval` 不要设得太小，避免触发接口限流。

还有一个细节：`HttpInvoke` 的重试是**重发同一个请求**，不会重新走认证节点。所以 token 过期导致的 401，靠 `retryCount` 是救不回来的，只能重新执行任务，或者把时间窗口拆小。

如果任务跑到一半失败，前面几页可能已经写进库了。重跑之前要想清楚目标表的写入策略：

- `append`：简单，但重跑可能重复；
- `update`：按主键更新，适合有业务主键的表；
- `overwrite`：适合每次全量重刷。

没有主键的接口数据，建议在入库前补一个稳定唯一键，或者按时间分区覆盖，别让重跑变成重复数据。

---

## 08 上线前的检查清单

这类任务配好之后，不要急着挂调度。按下面顺序验一遍，能省掉很多排查时间。

1. **认证节点单独跑**：输出是不是一串 token，而不是 JSON 对象？
2. **数据路径对不对**：token 在 `$.data.access_token` 还是 `$.access_token`？
3. **脚本节点有没有生效**：脚本输出里是否看到 `authToken`？如果下游仍然 401，先确认属性名和引用名是否一致。
4. **单页请求先跑通**：关闭分页，确认带 token 能拿到 200 和正确的数据数组。
5. **再开分页**：观察第 1 页、最后一页的日志，确认没有少页、没有重复页。
6. **检查 pageSize 上限**：接口实际返回条数是否小于配置值？如果小于，分页会提前结束。
7. **检查时区**：`#{...}` 算出来的日期，和业务口径是不是同一天？
8. **检查 token 有效期**：任务总时长会不会超过 `expires_in`？
9. **检查业务错误码**：接口 HTTP 200 但 `code != 0` 时，DataY 不会自动当成失败。认证节点可以靠脚本校验 token 是否为空；取数节点建议看日志和数据行数，必要时加校验节点。
10. **检查敏感信息**：`client_secret` 还在任务配置里，导出、分享、提交仓库前要脱敏；给接口账号最小权限，定期轮换。

---

## 09 写在最后

HTTP 接口同步的难点，从来不是“发一个请求”。

真正难的是：让认证、参数、分页、写入在每一次调度里都稳定复现。

- token 不是配置里的常量，而是流经管道的数据；
- 时间不是写死的字符串，而是每次执行自动计算的表达式；
- 页码不是脚本里的循环变量，而是分页节点管理的上下文；
- 失败不是“重跑一下”，而是要考虑 token 有效期、写入策略和幂等。

把这些东西拆开、放到该在的位置，接口同步才会从“一次性脚本”变成“可调度的数据链路”。

今天对接的是订单接口，明天换成库存、财务、CRM，只需要换 URL、Header 和 Body，认证、分页、时间参数、写库这些骨架都可以复用。

**先把 token 拿对，再把每一页拿稳。**

---

## 10 DataY 是什么

上面这套链路，跑在 **DataY** 上。

DataY 是一个开源、轻量的一体化数据平台，目标很直接：用尽量小的资源，把数据集成、任务调度、数仓和指标分析这几件事跑通。

这里的“轻量”指的是**运行资源门槛低**，不是能力轻量。它不依赖 Hadoop、Spark 这类重型组件，一个 JAR 就能启动，默认内嵌 DuckDB 作为高性能分析引擎。开发机 2 核 4G 就能跑通从数据接入到报表查询的完整链路。

它由两块组成：

| 模块 | 定位 | 说明 |
| --- | --- | --- |
| **DataY Core** | 数据集成引擎 | 轻量、可嵌入、流批一体；JSON 定义任务、自动建表、CDC 实时同步 |
| **DataY Web** | 可视化数据平台 | 拖拽式任务设计、任务调度、维度建模、指标管理、智能问数 |

数据集成方面，支持 MySQL、PostgreSQL、Oracle、SQL Server、Doris、ClickHouse、DuckDB、DuckLake 等十余种数据源，也支持 MySQL CDC、PostgreSQL CDC、Oracle CDC 等实时同步场景。

HTTP 接口同步就是其中一个很典型的场景：把认证、分页、参数替换、JSON 解析、字段映射和写库这些动作，收敛成一个可配置、可调度、可复用的 ETL 任务。

**快速开始：**

```bash
git clone https://gitee.com/yuyuejia/datay.git
cd datay
mvn clean package -DskipTests
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx
# 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

产品首页：<http://datay.yuyuejia.com.cn/>

项目地址：<https://github.com/yuyuejia/datay>
