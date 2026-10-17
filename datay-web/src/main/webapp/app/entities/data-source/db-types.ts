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
  /** 该模式通过「主机/端口」连接 */
  network?: boolean;
  /** 主机字段标签（默认「IP/主机」） */
  hostLabel?: string;
  /** 该模式使用「本地文件路径」输入 */
  file?: boolean;
  /** 该模式 URL 生成模板，占位符：{host} {port} {database} {file} */
  urlTemplate?: string;
  /** 用于从已有 URL 反推当前模式的前缀 */
  urlPrefix?: string;
}

export interface IdentifierTypeDef {
  value: string;
  label: string;
}

export interface DbType {
  name: string;
  displayName: string;
  jdbcUrlTemplate: string;
  supportedVersions: string[];
  defaultPort: string;
  image?: string;
  icon?: string;

  // ---------------- 声明式表单/连接能力配置 ----------------
  // 通过下面的开关控制表单展示哪些字段与保存行为，新增数据源类型只需配置，
  // 无需在组件里书写 `type === 'XXX'` 之类的判断。
  /** 网络型数据库：显示主机/端口与「简易/自定义」URL 模式切换。默认 true */
  network?: boolean;
  /** 需要用户名/密码。默认 true */
  credentials?: boolean;
  /** 用户名/密码为可选（如 MongoDB 允许无认证），填写凭据时不缺省校验。默认 false */
  credentialsOptional?: boolean;
  /** 显示并保存「默认 Schema」。默认 false */
  schemaEnabled?: boolean;
  /** 简易模式下显示「数据库名」。默认 true */
  databaseEnabled?: boolean;
  /** 数据库名输入框旁的连接标识类型（如 Oracle 服务名/SID） */
  identifierTypes?: IdentifierTypeDef[];
  /** 保存时清空用户名/密码 */
  clearCredentialsOnSave?: boolean;
  /** 保存时清空数据库名与 Schema */
  clearDatabaseOnSave?: boolean;
  /** 保存时清空默认 Schema */
  clearSchemaOnSave?: boolean;
  /** 未启用 Schema 时，用用户名（大写）作为默认 Schema（Oracle 习惯） */
  schemaFromUsername?: boolean;

  extraParamsTemplate?: ExtraParamDef[];
  connectionModes?: ConnectionModeDef[];
  defaultConnectionMode?: string;
  /** 该类型驱动未内置，支持从 Maven 仓库按需下载 */
  downloadable?: boolean;
}

/**
 * DbType 配置在组件中使用的规范化结果（补齐默认值）。
 */
export interface DbFormConfig {
  network: boolean;
  credentials: boolean;
  credentialsOptional: boolean;
  schemaEnabled: boolean;
  databaseEnabled: boolean;
  identifierTypes: IdentifierTypeDef[];
  clearCredentialsOnSave: boolean;
  clearDatabaseOnSave: boolean;
  clearSchemaOnSave: boolean;
  schemaFromUsername: boolean;
  connectionModes: ConnectionModeDef[];
  defaultConnectionMode?: string;
}

const DEFAULT_FORM_CONFIG: DbFormConfig = {
  network: true,
  credentials: true,
  credentialsOptional: false,
  schemaEnabled: false,
  databaseEnabled: true,
  identifierTypes: [],
  clearCredentialsOnSave: false,
  clearDatabaseOnSave: false,
  clearSchemaOnSave: false,
  schemaFromUsername: false,
  connectionModes: [],
};

/**
 * 按类型标识查找数据源类型配置（大小写不敏感）。
 * 数据源存储的 type 可能大小写不一致（如 MCP 注册传入 "mysql"、界面选择 "MYSQL"），
 * 统一以大小写不敏感方式匹配，避免图标/表单配置丢失。
 */
export function findDbType(type?: string | null): DbType | undefined {
  if (!type) return undefined;
  const normalized = type.toUpperCase();
  return dbTypes.find(db => db.name.toUpperCase() === normalized);
}

export function resolveDbFormConfig(dbType?: DbType | null): DbFormConfig {
  if (!dbType) return DEFAULT_FORM_CONFIG;
  return {
    network: dbType.network ?? DEFAULT_FORM_CONFIG.network,
    credentials: dbType.credentials ?? DEFAULT_FORM_CONFIG.credentials,
    credentialsOptional: dbType.credentialsOptional ?? DEFAULT_FORM_CONFIG.credentialsOptional,
    schemaEnabled: dbType.schemaEnabled ?? DEFAULT_FORM_CONFIG.schemaEnabled,
    databaseEnabled: dbType.databaseEnabled ?? DEFAULT_FORM_CONFIG.databaseEnabled,
    identifierTypes: dbType.identifierTypes ?? DEFAULT_FORM_CONFIG.identifierTypes,
    clearCredentialsOnSave: dbType.clearCredentialsOnSave ?? DEFAULT_FORM_CONFIG.clearCredentialsOnSave,
    clearDatabaseOnSave: dbType.clearDatabaseOnSave ?? DEFAULT_FORM_CONFIG.clearDatabaseOnSave,
    clearSchemaOnSave: dbType.clearSchemaOnSave ?? DEFAULT_FORM_CONFIG.clearSchemaOnSave,
    schemaFromUsername: dbType.schemaFromUsername ?? DEFAULT_FORM_CONFIG.schemaFromUsername,
    connectionModes: dbType.connectionModes ?? DEFAULT_FORM_CONFIG.connectionModes,
    defaultConnectionMode: dbType.defaultConnectionMode,
  };
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
    name: 'MARIADB',
    displayName: 'MariaDB',
    jdbcUrlTemplate: 'jdbc:mysql://{host}:{port}/{database}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai',
    supportedVersions: ['10.6', '10.11', '11.4'],
    defaultPort: '3306',
    image: '/content/images/mariadb.svg',
  },
  {
    name: 'STARROCKS',
    displayName: 'StarRocks',
    jdbcUrlTemplate: 'jdbc:mysql://{host}:{port}/{database}',
    supportedVersions: ['2.5', '3.0', '3.1', '3.2'],
    defaultPort: '9030',
    image: '/content/images/starrocks.svg',
  },
  {
    name: 'OCEANBASE',
    displayName: 'OceanBase',
    jdbcUrlTemplate: 'jdbc:mysql://{host}:{port}/{database}',
    supportedVersions: ['3.2', '4.0', '4.2', '4.3'],
    defaultPort: '2881',
    image: '/content/images/oceanbase.png',
  },
  {
    name: 'POLARDB',
    displayName: 'PolarDB',
    jdbcUrlTemplate: 'jdbc:mysql://{host}:{port}/{database}',
    supportedVersions: ['5.6', '5.7', '8.0'],
    defaultPort: '3306',
    image: '/content/images/polardb.png',
  },
  {
    name: 'TIDB',
    displayName: 'TiDB',
    jdbcUrlTemplate: 'jdbc:mysql://{host}:{port}/{database}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai',
    supportedVersions: ['5.4', '6.1', '6.5', '7.1', '7.5', '8.0'],
    defaultPort: '4000',
    image: '/content/images/tidb.svg',
  },
  {
    name: 'ORACLE',
    displayName: 'Oracle Database',
    jdbcUrlTemplate: 'jdbc:oracle:thin:@//{host}:{port}/{database}',
    supportedVersions: ['11g', '12c', '18c', '19c', '21c', '23c'],
    defaultPort: '1521',
    image: '/content/images/oracle.svg',
    identifierTypes: [
      { value: 'service', label: '服务名 (Service Name)' },
      { value: 'sid', label: 'SID' },
    ],
    schemaFromUsername: true,
  },
  {
    name: 'HANA',
    displayName: 'SAP HANA',
    jdbcUrlTemplate: 'jdbc:sap://{host}:{port}/?databaseName={database}',
    supportedVersions: ['2.22.12', '2.23.10', '2.24.8', '2.25.9'],
    defaultPort: '30015',
    image: '/content/images/hana.svg',
    clearSchemaOnSave: true,
    downloadable: true,
  },
  {
    name: 'POSTGRESQL',
    displayName: 'PostgreSQL',
    jdbcUrlTemplate: 'jdbc:postgresql://{host}:{port}/{database}',
    supportedVersions: ['9.6', '10', '11', '12', '13', '14', '15', '16'],
    defaultPort: '5432',
    image: '/content/images/postgres.svg',
    schemaEnabled: true,
  },
  {
    name: 'GAUSSDB',
    displayName: 'GaussDB',
    jdbcUrlTemplate: 'jdbc:postgresql://{host}:{port}/{database}',
    supportedVersions: ['2.0', '3.0', '5.0', '6.0'],
    defaultPort: '8000',
    image: '/content/images/gaussdb.svg',
    schemaEnabled: true,
  },
  {
    name: 'DM',
    displayName: '达梦数据库',
    jdbcUrlTemplate: 'jdbc:dm://{host}:{port}',
    supportedVersions: ['8.1.1.193', '8.1.2.79', '8.1.2.141', '8.1.2.192', '8.1.3.62', '8.1.3.140'],
    defaultPort: '5236',
    image: '/content/images/dm.png',
    databaseEnabled: false,
    clearDatabaseOnSave: true,
    downloadable: true,
  },
  {
    name: 'SQLSERVER',
    displayName: 'Microsoft SQL Server',
    jdbcUrlTemplate: 'jdbc:sqlserver://{host}:{port};databaseName={database}',
    supportedVersions: ['2008', '2012', '2014', '2016', '2017', '2019', '2022'],
    defaultPort: '1433',
    image: '/content/images/sqlserver.svg',
    schemaEnabled: true,
  },
  {
    name: 'DUCKDB',
    displayName: 'DuckDB',
    jdbcUrlTemplate: 'jdbc:duckdb:{database}',
    supportedVersions: ['0.8', '0.9', '1.0'],
    defaultPort: '',
    image: '/content/images/DuckDB.svg',
    network: false,
    credentials: false,
    databaseEnabled: false,
    clearCredentialsOnSave: true,
    defaultConnectionMode: 'file',
    connectionModes: [
      {
        value: 'file',
        label: '本地文件',
        description: '连接本地数据库文件，留空表示内存数据库，无需填写主机和端口',
        file: true,
        urlPrefix: 'jdbc:duckdb:',
        urlTemplate: 'jdbc:duckdb:{file}',
      },
      {
        value: 'quack',
        label: 'Quack 协议',
        defaultPort: '9494',
        description: '通过 Quack 远程协议连接 DuckDB 服务',
        network: true,
        hostLabel: 'Quack 服务地址',
        urlPrefix: 'quack:',
        urlTemplate: 'quack:{host}:{port}',
        extraParamsTemplate: [
          { key: 'quack.token', label: 'Token', required: false, description: 'Quack 服务端认证 Token' },
          {
            key: 'quack.disable_ssl',
            label: 'DISABLE_SSL',
            required: false,
            defaultValue: 'false',
            description: '远程非 localhost 时是否使用明文 HTTP 连接',
          },
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
    network: false,
    credentials: false,
    databaseEnabled: false,
    clearCredentialsOnSave: true,
    clearDatabaseOnSave: true,
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
    name: 'SQLITE',
    displayName: 'SQLite',
    jdbcUrlTemplate: 'jdbc:sqlite:{database}',
    supportedVersions: ['3.45.1.0', '3.46.1.0', '3.47.1.0'],
    defaultPort: '',
    image: '/content/images/sqlite.svg',
    // 嵌入式文件数据库：无账号密码，连接本地数据库文件
    network: false,
    credentials: false,
    databaseEnabled: false,
    clearCredentialsOnSave: true,
    defaultConnectionMode: 'file',
    connectionModes: [
      {
        value: 'file',
        label: '本地文件',
        description: '连接本地 SQLite 数据库文件，例如 /data/app.db；留空表示内存数据库',
        file: true,
        urlPrefix: 'jdbc:sqlite:',
        urlTemplate: 'jdbc:sqlite:{file}',
      },
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
    schemaEnabled: true,
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
  {
    name: 'JDBC',
    displayName: '通用 JDBC',
    jdbcUrlTemplate: '',
    supportedVersions: [],
    defaultPort: '',
    image: '/content/images/jdbc.svg',
    // 通用 JDBC：手动填写任意 JDBC URL 与驱动类，适配平台未内置的数据库（Hive/Phoenix/Trino/StarRocks 等）。
    network: false,
    credentials: true,
    credentialsOptional: true,
    databaseEnabled: false,
    schemaEnabled: true,
    extraParamsTemplate: [
      { key: 'jdbc.driverClass', label: '驱动类名', required: true, description: 'JDBC 驱动类，如 org.apache.hive.jdbc.HiveDriver' },
      { key: 'jdbc.driverJar', label: '驱动 Jar 路径', required: false, description: '驱动 Jar 文件或目录路径；留空则使用平台类路径' },
      {
        key: 'jdbc.maven.groupId',
        label: 'Maven GroupId',
        required: false,
        description: '从 Maven 仓库下载驱动时的 groupId，如 org.apache.hive',
      },
      {
        key: 'jdbc.maven.artifactId',
        label: 'Maven ArtifactId',
        required: false,
        description: '从 Maven 仓库下载驱动时的 artifactId，如 hive-jdbc',
      },
      { key: 'jdbc.maven.version', label: 'Maven Version', required: false, description: '从 Maven 仓库下载驱动时的版本，如 3.1.3' },
      { key: 'jdbc.testQuery', label: '测试查询', required: false, defaultValue: 'SELECT 1', description: '连通性测试语句，默认 SELECT 1' },
    ],
  },
  {
    name: 'MONGODB',
    displayName: 'MongoDB',
    jdbcUrlTemplate: 'mongodb://{host}:{port}/{database}',
    supportedVersions: [],
    defaultPort: '27017',
    image: '/content/images/mongodb.svg',
    // 非 JDBC 数据源：文档型数据库，集合即「表」，查询使用 JSON 信封（find/aggregate）。
    credentials: true,
    credentialsOptional: true,
    databaseEnabled: true,
    extraParamsTemplate: [
      { key: 'authSource', label: '认证库 (authSource)', required: false, description: '认证数据库，默认取连接数据库或 admin' },
      { key: 'replicaSet', label: '副本集 (replicaSet)', required: false, description: '副本集名称' },
      { key: 'tls', label: 'TLS', required: false, defaultValue: 'false', description: '是否启用 TLS 加密连接' },
    ],
  },
  {
    name: 'ELASTICSEARCH',
    displayName: 'Elasticsearch',
    jdbcUrlTemplate: 'http://{host}:{port}',
    supportedVersions: [],
    defaultPort: '9200',
    image: '/content/images/elasticsearch.svg',
    // 非 JDBC 数据源：通过 REST API 访问，无数据库/schema 概念，索引即「表」。
    // 仅使用自定义 URL 模式（不提供 http/https 连接模式选择），https 证书默认忽略校验。
    network: false,
    databaseEnabled: false,
    clearDatabaseOnSave: true,
    extraParamsTemplate: [
      { key: 'es.path_prefix', label: 'Path Prefix', required: false, description: '反向代理下的路径前缀，如 es' },
      { key: 'es.api_key', label: 'API Key', required: false, description: '使用 ApiKey 认证时填写（与用户名/密码二选一）' },
      { key: 'es.default_index', label: '默认索引', required: false, description: '使用 Query DSL 查询时的默认索引' },
    ],
  },
  {
    name: 'INFLUXDB',
    displayName: 'InfluxDB',
    jdbcUrlTemplate: 'http://{host}:{port}',
    supportedVersions: [],
    defaultPort: '8086',
    image: '/content/images/influxdb.svg',
    // 非 JDBC 时序数据库：通过 HTTP API 访问（1.x InfluxQL / 2.x Flux）。
    // 仅使用自定义 URL 模式：URL 直接携带协议与主机端口，Bucket（2.x）/ Database（1.x）存入「默认Schema」，
    // 1.x 使用用户名/密码认证；2.x 只需额外的 Org 与 Token 两个参数（填写即自动按 2.x 连接）。
    network: false,
    credentials: true,
    credentialsOptional: true,
    schemaEnabled: true,
    databaseEnabled: false,
    clearDatabaseOnSave: true,
    extraParamsTemplate: [
      { key: 'influx.org', label: 'Org (2.x)', required: false, description: 'InfluxDB 2.x 组织名，填写后自动按 2.x（Flux）连接' },
      {
        key: 'influx.token',
        label: 'Token (2.x)',
        required: false,
        description: 'InfluxDB 2.x API Token；缺省且无 Org 时按 1.x（InfluxQL + 用户名/密码）连接',
      },
    ],
  },
];
