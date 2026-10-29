# 嵌入免登录与 URL Token

DataY 支持把 JWT 直接放在 URL 上，父应用（第三方系统）可以提前获取 token，然后通过 iframe 嵌入 DataY 页面，实现免登录。

## 1. 获取 token

父应用在嵌入前，先调用 DataY 的登录接口获取 JWT：

```bash
curl -X POST 'https://datay.example.com/api/authenticate' \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"******","rememberMe":true}'
```

token 在响应头 `Authorization: Bearer <jwt>` 中，响应体里的 `id_token` 也是同一个 JWT。

> 如果父应用在浏览器里跨域调用该接口，需要在 DataY 侧开启 CORS；服务端到服务端调用则不需要。

## 2. 嵌入地址

推荐把 token 放在 hash 中：hash 不会发送到服务端，也不会进入 Nginx access log。

```html
<iframe src="https://datay.example.com/dashboard.html?id=db1&embed=1#token=<jwt>" width="100%" height="720" frameborder="0"></iframe>
```

也兼容 query 方式（会进入服务端访问日志，建议仅在无法使用 hash 时使用）：

```html
<iframe src="https://datay.example.com/dashboard.html?id=db1&embed=1&token=<jwt>"></iframe>
```

主应用页面同样支持，并且可以直接指定菜单路由，嵌入任意菜单节点：

```html
<!-- 首页 -->
<iframe src="https://datay.example.com/?embed=1#token=<jwt>"></iframe>

<!-- 数据源列表 -->
<iframe src="https://datay.example.com/data-source?embed=1#token=<jwt>"></iframe>

<!-- ETL 任务 -->
<iframe src="https://datay.example.com/etl-task?embed=1#token=<jwt>"></iframe>

<!-- 带查询参数的路由（例如指定看板/数据集） -->
<iframe src="https://datay.example.com/analysis-dashboard?id=db1&embed=1#token=<jwt>"></iframe>
```

主应用使用 history 路由，路由路径直接放在 pathname 中即可。`embed=1` 建议放在 query，token 放在 hash。

规则：

- hash（`#token=`）优先于 query（`?token=`）。
- token 读到后会立即写入 `sessionStorage`，并从地址栏清除，不会新增浏览历史。
- 页面刷新后仍使用当前会话中的 token；关闭标签页/iframe 后失效。
- 如果 URL 中的 token 已过期或格式非法，会被忽略并按未登录处理。

## 3. embed=1

`embed=1` 表示当前页面被嵌入到其它应用中：

- 隐藏顶部导航栏、菜单页签（主应用）；
- 隐藏独立看板的顶部工具栏（独立看板）；
- 401 时不再弹登录框或跳转登录页，而是通过 `postMessage` 通知父应用。

`embed=0` 可以显式退出嵌入模式。

## 4. token 过期处理

嵌入模式下，DataY 检测到认证失败时会向父页面发送消息：

```js
window.addEventListener('message', event => {
  if (event.data?.type === 'datay:auth-required') {
    // 重新调用 DataY 登录接口获取新 token，然后刷新 iframe
    reloadIframeWithNewToken();
  }
});
```

父应用重新获取 token 后，刷新 iframe 的 `src` 即可，无需用户操作。

## 5. 内置演示页

DataY 自带一个嵌入演示页，部署后访问：

```text
https://datay.example.com/embed-demo.html
```

页面支持：

- 直接粘贴提前获取的 JWT；
- 输入账号密码调用 `/api/authenticate` 现场获取 token；
- 选择独立看板或主应用、选择 hash/query 传递方式；
- 主应用模式可填写路由路径，直接嵌入 `/data-source`、`/etl-task`、`/metric`、`/admin/user-management` 等菜单节点；
- 生成并复制 iframe 代码；
- 监听 `datay:auth-required`，在 token 失效时自动重新登录（已填写账号密码时）。

该页面与 DataY 同源，因此调用登录接口不需要额外配置 CORS。也可以通过参数预填：

```text
# 独立看板
/embed-demo.html?base=https://datay.example.com&id=db1&token=<jwt>

# 主应用指定路由
/embed-demo.html?base=https://datay.example.com&target=app&route=/etl-task&token=<jwt>
```

## 6. 允许 iframe 跨域嵌入

DataY 默认允许任意来源嵌入，并由 CSP `frame-ancestors` 控制。可通过配置收紧：

```yaml
datay:
  embed:
    # * 表示允许所有来源；多个来源用空格分隔
    allowed-origins: 'https://portal.example.com https://admin.example.com'
```

对应环境变量：

```bash
DATAY_EMBED_ALLOWED_ORIGINS="https://portal.example.com https://admin.example.com"
```

> 安全提示：`frame-ancestors *` 允许任意站点嵌入，存在点击劫持风险。生产环境建议配置明确的父页面来源。使用 query 传递 token 时，token 会出现在 Nginx access log 中，优先使用 hash。
>
> 如果前置 Nginx 等反向代理额外设置了 `X-Frame-Options: SAMEORIGIN`，需要同步移除该响应头，否则仍会拦截跨域 iframe。
