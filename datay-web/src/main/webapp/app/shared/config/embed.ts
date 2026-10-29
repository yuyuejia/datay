/** URL 参数名：`embed=1` 表示当前页面被嵌入到其它应用中。 */
export const EMBED_QUERY_PARAM = 'embed';

const EMBED_STORAGE_KEY = 'jhi-embed-mode';

const readUrlFlag = (): string | null => {
  if (typeof window === 'undefined' || !window.location) {
    return null;
  }
  const query = new URLSearchParams(window.location.search).get(EMBED_QUERY_PARAM);
  if (query !== null) {
    return query;
  }
  const hash = window.location.hash || '';
  const hashParams = new URLSearchParams(hash.startsWith('#') ? hash.slice(1) : hash);
  return hashParams.get(EMBED_QUERY_PARAM);
};

/**
 * 是否处于嵌入模式。
 *
 * <p>命中一次 `embed=1` 后会缓存到 sessionStorage，保证在 iframe 内路由跳转
 * 丢失 URL 参数后仍然保持隐藏外围框架。`embed=0` 可显式退出嵌入模式。
 */
export const isEmbedMode = (): boolean => {
  const flag = readUrlFlag();
  if (flag === '1') {
    try {
      sessionStorage.setItem(EMBED_STORAGE_KEY, '1');
    } catch {
      // ignore
    }
    return true;
  }
  if (flag === '0') {
    try {
      sessionStorage.removeItem(EMBED_STORAGE_KEY);
    } catch {
      // ignore
    }
    return false;
  }
  try {
    return sessionStorage.getItem(EMBED_STORAGE_KEY) === '1';
  } catch {
    return false;
  }
};
