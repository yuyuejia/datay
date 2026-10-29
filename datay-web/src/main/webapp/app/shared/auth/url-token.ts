import { TOKEN_STORAGE_KEY } from '@/shared/config/axios-interceptor';

/** URL 参数名：`?token=` 或 `#token=`。 */
export const URL_TOKEN_PARAM = 'token';

export interface UrlTokenResult {
  /** 是否成功把 URL 中的 token 写入当前会话。 */
  applied: boolean;
  /** URL 中带了 token，但 token 已过期或格式非法。 */
  expired: boolean;
}

const readHash = (win: Window): string => {
  const hash = win.location.hash || '';
  return hash.startsWith('#') ? hash.slice(1) : hash;
};

const decodePayload = (token: string): Record<string, unknown> | null => {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }
  try {
    const normalized = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4);
    const binary = atob(padded);
    const bytes = Uint8Array.from(binary, char => char.charCodeAt(0));
    const payload = JSON.parse(new TextDecoder().decode(bytes));
    return payload && typeof payload === 'object' ? (payload as Record<string, unknown>) : null;
  } catch {
    return null;
  }
};

/**
 * 判断 token 是否已经过期或连 JWT 结构都不满足。
 *
 * <p>这里只做前端侧的快速判断，真正的签名校验始终由后端完成。
 */
export const isTokenExpired = (token: string, now: number = Date.now()): boolean => {
  const payload = decodePayload(token);
  if (!payload) {
    return true;
  }
  const exp = payload.exp;
  if (typeof exp !== 'number' || !Number.isFinite(exp)) {
    return false;
  }
  return exp * 1000 <= now;
};

/** 从地址栏移除 token，保留其它 query / hash 参数，且不新增浏览历史。 */
export const removeUrlToken = (win: Window = window): void => {
  if (!win.location) {
    return;
  }
  const url = new URL(win.location.href);
  let changed = false;

  if (url.searchParams.has(URL_TOKEN_PARAM)) {
    url.searchParams.delete(URL_TOKEN_PARAM);
    changed = true;
  }

  const hashParams = new URLSearchParams(url.hash.startsWith('#') ? url.hash.slice(1) : url.hash);
  if (hashParams.has(URL_TOKEN_PARAM)) {
    hashParams.delete(URL_TOKEN_PARAM);
    const rest = hashParams.toString();
    url.hash = rest ? `#${rest}` : '';
    changed = true;
  }

  if (!changed) {
    return;
  }
  win.history.replaceState(win.history.state, '', `${url.pathname}${url.search}${url.hash}`);
};

/**
 * 读取 URL 中的 JWT，写入 sessionStorage 并立即从地址栏清除。
 *
 * <p>hash（`#token=`）优先于 query（`?token=`）：hash 不会发送到服务端，
 * 也不会进入 Nginx access log，是嵌入时更推荐的方式。
 */
export const consumeUrlToken = (win: Window = window): UrlTokenResult => {
  if (!win.location) {
    return { applied: false, expired: false };
  }

  const hashToken = new URLSearchParams(readHash(win)).get(URL_TOKEN_PARAM);
  const queryToken = new URLSearchParams(win.location.search).get(URL_TOKEN_PARAM);
  const token = (hashToken || queryToken || '').trim();

  // 无论 token 是否有效，都先把它从地址栏移除，避免复制链接或 Referer 泄漏。
  removeUrlToken(win);

  if (!token) {
    return { applied: false, expired: false };
  }
  if (isTokenExpired(token)) {
    return { applied: false, expired: true };
  }

  try {
    // 嵌入方传入的新 token 必须覆盖本地可能残留的旧 token。
    win.sessionStorage.setItem(TOKEN_STORAGE_KEY, token);
    win.localStorage.removeItem(TOKEN_STORAGE_KEY);
    return { applied: true, expired: false };
  } catch {
    // 隐私模式 / 被浏览器策略禁用存储时静默降级，页面仍可继续加载。
    return { applied: false, expired: false };
  }
};
