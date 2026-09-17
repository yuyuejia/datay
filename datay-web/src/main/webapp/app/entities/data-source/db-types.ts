export interface ExtraParamDef {
  key: string;
  label: string;
  required: boolean;
  defaultValue?: string;
  description?: string;
}

export interface ConnectionModeDef {
  value: string;
  label: string;
  description?: string;
  defaultPort?: string;
  extraParamsTemplate?: ExtraParamDef[];
}

export interface DbType {
  name: string;
  displayName: string;
  jdbcUrlTemplate: string;
  supportedVersions: string[];
  defaultPort: string;
  image?: string;
  icon?: string;
  extraParamsTemplate?: ExtraParamDef[];
  connectionModes?: ConnectionModeDef[];
  defaultConnectionMode?: string;
}

export const dbTypes: DbType[] = [
  {
    name: 'MYSQL',
    displayName: 'MySQL',
    jdbcUrlTemplate: 'jdbc:mysql://{host}:{port}/{database}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai',
    supportedVersions: ['5.7', '8.0', '8.1', '8.2', '8.3', '8.4'],
    defaultPort: '3306',
    image: '/content/images/mysql.svg',
  },
  {
    name: 'ORACLE',
    displayName: 'Oracle Database',
    jdbcUrlTemplate: 'jdbc:oracle:thin:@{host}:{port}:{database}',
    supportedVersions: ['11g', '12c', '18c', '19c', '21c', '23c'],
    defaultPort: '1521',
    image: '/content/images/oracle.svg',
  },
  {
    name: 'POSTGRESQL',
    displayName: 'PostgreSQL',
    jdbcUrlTemplate: 'jdbc:postgresql://{host}:{port}/{database}',
    supportedVersions: ['9.6', '10', '11', '12', '13', '14', '15', '16'],
    defaultPort: '5432',
    image: '/content/images/postgres.svg',
  },
  {
    name: 'SQLSERVER',
    displayName: 'Microsoft SQL Server',
    jdbcUrlTemplate: 'jdbc:sqlserver://{host}:{port};databaseName={database}',
    supportedVersions: ['2008', '2012', '2014', '2016', '2017', '2019', '2022'],
    defaultPort: '1433',
    image: '/content/images/sqlserver.svg',
  },
  {
    name: 'DUCKDB',
    displayName: 'DuckDB',
    jdbcUrlTemplate: 'jdbc:duckdb:{database}',
    supportedVersions: ['0.8', '0.9', '1.0'],
    defaultPort: '',
    image: '/content/images/DuckDB.svg',
    defaultConnectionMode: 'file',
    connectionModes: [
      {
        value: 'file',
        label: '本地文件',
        description: '连接本地数据库文件，留空表示内存数据库，无需填写主机和端口',
      },
      {
        value: 'quack',
        label: 'Quack 协议',
        defaultPort: '9494',
        description: '通过 Quack 远程协议连接 DuckDB 服务',
        extraParamsTemplate: [
          { key: 'quack.token', label: 'Token', required: false, description: 'Quack 服务端认证 Token' },
          { key: 'quack.disable_ssl', label: 'DISABLE_SSL', required: false, defaultValue: 'false', description: '远程非 localhost 时是否使用明文 HTTP 连接' },
        ],
      },
    ],
  },
  {
    name: 'DUCKLAKE',
    displayName: 'DuckLake',
    jdbcUrlTemplate: 'ducklake:metadata.ducklake',
    supportedVersions: ['0.4'],
    defaultPort: '',
    image: '/content/images/DuckDB.svg',
    extraParamsTemplate: [
      { key: 's3.data_path', label: 'Data Path', required: true, description: '数据存储路径，支持本地路径或 s3:// 路径' },
      { key: 's3.key_id', label: 'S3 Key ID', required: false, description: 'S3 访问密钥 ID（s3:// 路径时需要）' },
      { key: 's3.secret', label: 'S3 Secret', required: false, description: 'S3 访问密钥（s3:// 路径时需要）' },
      { key: 's3.endpoint', label: 'S3 Endpoint', required: false, description: 'S3 服务端点，例如 http://minio:9000' },
      { key: 's3.url_style', label: 'S3 URL Style', required: false, defaultValue: 'path', description: 'URL 风格：path 或 vhost' },
      { key: 's3.use_ssl', label: 'S3 Use SSL', required: false, defaultValue: 'false', description: '是否启用 SSL 连接' },
    ],
  },
  {
    name: 'CLICKHOUSE',
    displayName: 'ClickHouse',
    jdbcUrlTemplate: 'jdbc:clickhouse://{host}:{port}/{database}',
    supportedVersions: ['21.8', '22.3', '23.3', '24.1'],
    defaultPort: '8123',
    image: '/content/images/clickhouse.svg',
  },
  {
    name: 'GREENPLUM',
    displayName: 'Greenplum',
    jdbcUrlTemplate: 'jdbc:postgresql://{host}:{port}/{database}',
    supportedVersions: ['5', '6', '7'],
    defaultPort: '5432',
    image: '/content/images/Greenplum.svg',
  },
  {
    name: 'DORIS',
    displayName: 'Apache Doris',
    jdbcUrlTemplate: 'jdbc:mysql://{host}:{port}/{database}',
    supportedVersions: ['1.2', '2.0', '2.1'],
    defaultPort: '9030',
    image: '/content/images/doris.svg',
    extraParamsTemplate: [
      { key: 'fe_endpoint', label: 'FE Endpoint', required: false, description: 'Doris FE 节点 HTTP 端点，格式：http://host:port' },
    ],
  },
];