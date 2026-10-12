import { findDbType } from './db-types';

export type UrlMode = 'simple' | 'custom';

export type OracleIdentifierType = 'service' | 'sid';

export interface SimpleUrlFields {
  hostname: string;
  port: string;
  database: string;
  oracleIdentifierType?: OracleIdentifierType;
}

const NETWORK_URL_PATTERN = /^jdbc:[a-z0-9]+:\/\/([^:/]+)(?::(\d+))?(?:\/([^?]*))?/i;
const SQLSERVER_URL_PATTERN = /^jdbc:sqlserver:\/\/([^:;]+)(?::(\d+))?.*?databaseName=([^;]+)/i;
const ORACLE_SERVICE_PATTERN = /^jdbc:oracle:thin:@\/\/([^:/]+)(?::(\d+))?\/(.+)$/i;
const ORACLE_SID_PATTERN = /^jdbc:oracle:thin:@([^:/]+)(?::(\d+))?:(.+)$/i;
const HTTP_URL_PATTERN = /^(https?):\/\/([^:/]+)(?::(\d+))?/i;
const MONGO_URL_PATTERN = /^mongodb(?:\+srv)?:\/\/(?:[^@/]*@)?([^:/?]+)(?::(\d+))?(?:\/([^?]*))?/i;

const isNetworkType = (type: string): boolean => {
  const normalized = (type ?? '').toUpperCase();
  return normalized !== 'DUCKDB' && normalized !== 'DUCKLAKE';
};

/**
 * 根据数据库类型和简易模式字段生成 JDBC URL。
 * 字段为空时保留占位符，与原有 updateUrl 行为保持一致。
 */
export function buildSimpleUrl(type: string, fields: SimpleUrlFields, version?: string | null): string {
  const host = fields.hostname || '{host}';
  const port = fields.port || '{port}';
  const database = fields.database || '{database}';
  const normalizedType = (type ?? '').toUpperCase();

  if (normalizedType === 'ORACLE') {
    return fields.oracleIdentifierType === 'sid'
      ? `jdbc:oracle:thin:@${host}:${port}:${database}`
      : `jdbc:oracle:thin:@//${host}:${port}/${database}`;
  }

  if (normalizedType === 'ELASTICSEARCH' || normalizedType === 'INFLUXDB') {
    return `http://${fields.hostname || '{host}'}:${fields.port || '{port}'}`;
  }

  const dbType = findDbType(type);
  if (!dbType || !dbType.jdbcUrlTemplate) return '';

  let url = dbType.jdbcUrlTemplate
    .replace(/{host}/g, host)
    .replace(/{port}/g, port)
    .replace(/{database}/g, database);

  if (normalizedType === 'MYSQL' && version) {
    if (version.startsWith('5.')) {
      url = url.replace('serverTimezone=Asia/Shanghai', 'serverTimezone=UTC');
      if (!url.includes('useSSL=')) {
        url += '&useSSL=false';
      }
    } else if (!url.includes('useSSL=')) {
      url += '&useSSL=true';
    }
  }

  return url;
}

/**
 * 尝试从 JDBC URL 中解析出简易模式字段，无法识别时返回 null。
 */
export function parseSimpleUrl(type: string, url?: string | null): SimpleUrlFields | null {
  if (!url || !isNetworkType(type)) return null;

  const normalizedType = (type ?? '').toUpperCase();

  if (normalizedType === 'ORACLE') {
    const serviceMatch = ORACLE_SERVICE_PATTERN.exec(url);
    if (serviceMatch) {
      return {
        hostname: serviceMatch[1],
        port: serviceMatch[2] ?? '',
        database: serviceMatch[3],
        oracleIdentifierType: 'service',
      };
    }
    const sidMatch = ORACLE_SID_PATTERN.exec(url);
    if (sidMatch) {
      return {
        hostname: sidMatch[1],
        port: sidMatch[2] ?? '',
        database: sidMatch[3],
        oracleIdentifierType: 'sid',
      };
    }
    return null;
  }

  if (normalizedType === 'SQLSERVER') {
    const match = SQLSERVER_URL_PATTERN.exec(url);
    if (!match) return null;
    return { hostname: match[1], port: match[2] ?? '', database: match[3] };
  }

  if (normalizedType === 'ELASTICSEARCH' || normalizedType === 'INFLUXDB') {
    const match = HTTP_URL_PATTERN.exec(url);
    if (!match) return null;
    return { hostname: match[2], port: match[3] ?? '', database: '' };
  }

  if (normalizedType === 'MONGODB') {
    const match = MONGO_URL_PATTERN.exec(url);
    if (!match) return null;
    return { hostname: match[1], port: match[2] ?? '', database: match[3] ?? '' };
  }

  const match = NETWORK_URL_PATTERN.exec(url);
  if (!match) return null;
  return { hostname: match[1], port: match[2] ?? '', database: match[3] ?? '' };
}

export { isNetworkType };

export interface UrlTemplateValues {
  host?: string;
  port?: string;
  database?: string;
  file?: string;
}

/**
 * 按模板生成 JDBC URL，支持 {host} {port} {database} {file} 占位符。
 * {port} 与 {database} 为空时连同其前置分隔符一并移除（可选段），
 * 便于表达如 `quack:{host}:{port}` 这类端口可省略的地址。
 */
export function renderUrlTemplate(template: string, values: UrlTemplateValues): string {
  return template
    .replace(/[:/]?\{(port|database)\}/gi, (match, key: string) => {
      const value = values[key as keyof UrlTemplateValues];
      return value ? match.replace(/\{(\w+)\}/, String(value)) : '';
    })
    .replace(/\{(host|file)\}/gi, (_match, key: string) => values[key as keyof UrlTemplateValues] ?? '');
}
