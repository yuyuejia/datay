import { dbTypes } from './db-types';

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

const isNetworkType = (type: string): boolean => type !== 'DUCKDB' && type !== 'DUCKLAKE';

/**
 * 根据数据库类型和简易模式字段生成 JDBC URL。
 * 字段为空时保留占位符，与原有 updateUrl 行为保持一致。
 */
export function buildSimpleUrl(type: string, fields: SimpleUrlFields, version?: string | null): string {
  const host = fields.hostname || '{host}';
  const port = fields.port || '{port}';
  const database = fields.database || '{database}';

  if (type === 'ORACLE') {
    return fields.oracleIdentifierType === 'sid'
      ? `jdbc:oracle:thin:@${host}:${port}:${database}`
      : `jdbc:oracle:thin:@//${host}:${port}/${database}`;
  }

  const dbType = dbTypes.find(db => db.name === type);
  if (!dbType) return '';

  let url = dbType.jdbcUrlTemplate.replace(/{host}/g, host).replace(/{port}/g, port).replace(/{database}/g, database);

  if (type === 'MYSQL' && version) {
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

  if (type === 'ORACLE') {
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

  if (type === 'SQLSERVER') {
    const match = SQLSERVER_URL_PATTERN.exec(url);
    if (!match) return null;
    return { hostname: match[1], port: match[2] ?? '', database: match[3] };
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
