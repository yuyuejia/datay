import DOMPurify from 'dompurify';

const HTML_FORMATTER_KEYS = new Set(['formatter']);

/**
 * 净化 ECharts option 中可承载 HTML 的字符串。
 *
 * <p>ECharts 会把 tooltip / axis label 的 formatter 字符串当作 HTML 渲染，
 * AI 生成的 option 直接入图会形成存储型 XSS 面。这里递归处理所有 `formatter`
 * 字符串，函数型 formatter 原样保留。
 */
export function sanitizeEchartsOption<T extends Record<string, any>>(option: T): T {
  return sanitizeValue(option, new WeakSet()) as T;
}

function sanitizeValue(value: any, seen: WeakSet<object>): any {
  if (value === null || value === undefined || typeof value !== 'object') {
    return value;
  }
  if (seen.has(value)) {
    return value;
  }
  seen.add(value);

  if (Array.isArray(value)) {
    return value.map(item => sanitizeValue(item, seen));
  }

  const result: Record<string, any> = {};
  for (const [key, item] of Object.entries(value)) {
    if (HTML_FORMATTER_KEYS.has(key) && typeof item === 'string') {
      result[key] = DOMPurify.sanitize(item, { USE_PROFILES: { html: true } });
    } else {
      result[key] = sanitizeValue(item, seen);
    }
  }
  return result;
}
