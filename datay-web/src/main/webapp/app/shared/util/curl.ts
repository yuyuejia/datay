export interface CurlParseResult {
  url?: string;
  method?: string;
  headers: Record<string, string>;
  body?: string;
  timeout?: number;
  sslVerify?: boolean;
  warnings: string[];
}

const FLAGS_WITH_VALUE = new Set([
  '-X',
  '--request',
  '-H',
  '--header',
  '-d',
  '--data',
  '--data-raw',
  '--data-binary',
  '--data-ascii',
  '--data-urlencode',
  '-F',
  '--form',
  '--form-string',
  '--url',
  '-u',
  '--user',
  '--oauth2-bearer',
  '-b',
  '--cookie',
  '--cookie-jar',
  '-A',
  '--user-agent',
  '-e',
  '--referer',
  '--connect-timeout',
  '-m',
  '--max-time',
  '-x',
  '--proxy',
  '--cacert',
  '--cert',
  '--key',
  '-o',
  '--output',
  '-T',
  '--upload-file',
  '--json',
  '--retry',
  '--retry-delay',
  '--resolve',
]);

function encodeBase64(value: string): string {
  try {
    return btoa(unescape(encodeURIComponent(value)));
  } catch {
    return btoa(value);
  }
}

export function tokenizeCurl(input: string): string[] {
  const text = input.replace(/\\\r?\n/g, ' ').replace(/\^\r?\n/g, ' ');
  const tokens: string[] = [];
  let current = '';
  let quote: "'" | '"' | null = null;
  let hasToken = false;

  for (let i = 0; i < text.length; i++) {
    const ch = text[i];

    if (quote === "'") {
      if (ch === "'") {
        quote = null;
      } else {
        current += ch;
      }
      continue;
    }

    if (quote === '"') {
      if (ch === '"') {
        quote = null;
      } else if (ch === '\\') {
        const next = text[i + 1];
        if (next === 'n') {
          current += '\n';
          i++;
        } else if (next === 't') {
          current += '\t';
          i++;
        } else if (next === '"' || next === '\\' || next === '$' || next === '`') {
          current += next;
          i++;
        } else {
          current += ch;
        }
      } else {
        current += ch;
      }
      continue;
    }

    if (ch === "'" || ch === '"') {
      quote = ch;
      hasToken = true;
      continue;
    }

    if (ch === '\\') {
      const next = text[i + 1];
      if (next !== undefined) {
        current += next;
        hasToken = true;
        i++;
      }
      continue;
    }

    if (/\s/.test(ch)) {
      if (hasToken) {
        tokens.push(current);
        current = '';
        hasToken = false;
      }
      continue;
    }

    current += ch;
    hasToken = true;
  }

  if (hasToken) {
    tokens.push(current);
  }

  return tokens;
}

function appendQuery(url: string, query: string): string {
  if (!query) {
    return url;
  }
  return url + (url.includes('?') ? '&' : '?') + query;
}

export function parseCurl(command: string): CurlParseResult {
  const result: CurlParseResult = { headers: {}, warnings: [] };
  const normalized = command.trim().replace(/^\$\s+/, '');
  const tokens = tokenizeCurl(normalized);

  if (tokens.length === 0) {
    return result;
  }

  let start = 0;
  if (tokens[0].toLowerCase() === 'curl') {
    start = 1;
  }

  let forceGet = false;
  let connectTimeout: number | undefined;
  let maxTime: number | undefined;
  let bodyFromForm = false;

  const setMethod = (method: string) => {
    result.method = method.toUpperCase();
  };

  for (let i = start; i < tokens.length; i++) {
    let token = tokens[i];
    let inlineValue: string | undefined;

    if (token.startsWith('--') && token.includes('=')) {
      const idx = token.indexOf('=');
      inlineValue = token.slice(idx + 1);
      token = token.slice(0, idx);
    }

    const takeValue = (): string | undefined => {
      if (inlineValue !== undefined) {
        return inlineValue;
      }
      if (i + 1 < tokens.length) {
        i++;
        return tokens[i];
      }
      return undefined;
    };

    switch (token) {
      case '-X':
      case '--request': {
        const value = takeValue();
        if (value) {
          setMethod(value);
        }
        break;
      }
      case '-H':
      case '--header': {
        const value = takeValue();
        if (value) {
          const idx = value.indexOf(':');
          if (idx > 0) {
            const name = value.slice(0, idx).trim();
            const headerValue = value.slice(idx + 1).trim();
            if (name) {
              result.headers[name] = headerValue;
            }
          } else if (value.trim().endsWith(';')) {
            const name = value.trim().slice(0, -1).trim();
            if (name) {
              result.headers[name] = '';
            }
          }
        }
        break;
      }
      case '-d':
      case '--data':
      case '--data-raw':
      case '--data-binary':
      case '--data-ascii':
      case '--data-urlencode': {
        const value = takeValue();
        if (value !== undefined) {
          result.body = result.body ? `${result.body}&${value}` : value;
          if (!forceGet && !result.method) {
            setMethod('POST');
          }
        }
        break;
      }
      case '--json': {
        const value = takeValue();
        if (value !== undefined) {
          result.body = value;
          if (!result.headers['Content-Type']) {
            result.headers['Content-Type'] = 'application/json';
          }
          if (!result.headers['Accept']) {
            result.headers['Accept'] = 'application/json';
          }
          if (!forceGet && !result.method) {
            setMethod('POST');
          }
        }
        break;
      }
      case '-F':
      case '--form':
      case '--form-string': {
        takeValue();
        bodyFromForm = true;
        break;
      }
      case '--url': {
        const value = takeValue();
        if (value) {
          result.url = value;
        }
        break;
      }
      case '-u':
      case '--user': {
        const value = takeValue();
        if (value) {
          result.headers['Authorization'] = `Basic ${encodeBase64(value)}`;
        }
        break;
      }
      case '--oauth2-bearer': {
        const value = takeValue();
        if (value) {
          result.headers['Authorization'] = `Bearer ${value}`;
        }
        break;
      }
      case '-b':
      case '--cookie': {
        const value = takeValue();
        if (value) {
          result.headers['Cookie'] = value;
        }
        break;
      }
      case '--cookie-jar': {
        takeValue();
        break;
      }
      case '-A':
      case '--user-agent': {
        const value = takeValue();
        if (value) {
          result.headers['User-Agent'] = value;
        }
        break;
      }
      case '-e':
      case '--referer': {
        const value = takeValue();
        if (value) {
          result.headers['Referer'] = value;
        }
        break;
      }
      case '--compressed': {
        if (!result.headers['Accept-Encoding']) {
          result.headers['Accept-Encoding'] = 'gzip, deflate, br';
        }
        break;
      }
      case '--connect-timeout': {
        const value = takeValue();
        if (value) {
          connectTimeout = Number(value);
        }
        break;
      }
      case '-m':
      case '--max-time': {
        const value = takeValue();
        if (value) {
          maxTime = Number(value);
        }
        break;
      }
      case '-G':
      case '--get': {
        forceGet = true;
        break;
      }
      case '-I':
      case '--head': {
        setMethod('HEAD');
        break;
      }
      case '-k':
      case '--insecure': {
        result.sslVerify = false;
        break;
      }
      default: {
        if (FLAGS_WITH_VALUE.has(token)) {
          takeValue();
        } else if (!token.startsWith('-')) {
          if (!result.url) {
            result.url = token;
          }
        }
      }
    }
  }

  if (bodyFromForm) {
    result.warnings.push('暂不支持 -F/--form 表单参数，请手动配置请求体');
  }
  if (connectTimeout !== undefined && !Number.isNaN(connectTimeout)) {
    result.timeout = Math.round(connectTimeout * 1000);
  }
  if (maxTime !== undefined && !Number.isNaN(maxTime)) {
    const timeoutMs = Math.round(maxTime * 1000);
    result.timeout = result.timeout ? Math.max(result.timeout, timeoutMs) : timeoutMs;
  }

  if (forceGet) {
    if (result.body) {
      result.url = appendQuery(result.url || '', result.body);
      result.body = undefined;
    }
    setMethod('GET');
  } else if (!result.method) {
    setMethod(result.body ? 'POST' : 'GET');
  }

  return result;
}
