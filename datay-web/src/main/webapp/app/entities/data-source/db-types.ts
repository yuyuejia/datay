export interface DbType {
  name: string;
  displayName: string;
  jdbcUrlTemplate: string;
  supportedVersions: string[];
  defaultPort: string;
  image?: string;
  icon?: string;
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
    icon: 'feather',
  },
  {
    name: 'DUCKLAKE',
    displayName: 'DuckLake',
    jdbcUrlTemplate: 'ducklake:metadata.ducklake',
    supportedVersions: ['0.4'],
    defaultPort: '',
    icon: 'feather',
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
  },
  {
    name: 'AVRO',
    displayName: 'Apache Avro',
    jdbcUrlTemplate: 'jdbc:avro://{host}:{port}/{database}',
    supportedVersions: ['1.8', '1.9', '1.10', '1.11'],
    defaultPort: '9090',
    icon: 'file-code',
  },
];