package com.data.metadata;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public enum DBType {
    MYSQL(
        "MySQL",
        Arrays.asList("5.7", "8.0", "8.1", "8.2", "8.3", "8.4"),
        "com.mysql.cj.jdbc.Driver",
        "jdbc:mysql://{host}:{port}/{database}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
        Map.of(
            "5.7",
            "mysql-connector-java-5.1.49.jar",
            "8.0",
            "mysql-connector-java-8.0.33.jar",
            "8.1",
            "mysql-connector-java-8.1.0.jar",
            "8.2",
            "mysql-connector-java-8.2.0.jar",
            "8.3",
            "mysql-connector-java-8.3.0.jar",
            "8.4",
            "mysql-connector-java-8.4.0.jar"
        )
    ),

    ORACLE(
        "Oracle Database",
        Arrays.asList("11g", "12c", "18c", "19c", "21c", "23c"),
        "oracle.jdbc.driver.OracleDriver",
        "jdbc:oracle:thin:@//{host}:{port}/{database}",
        Map.of("11g", "ojdbc6.jar", "12c", "ojdbc7.jar", "18c", "ojdbc8.jar", "19c", "ojdbc8.jar", "21c", "ojdbc8.jar", "23c", "ojdbc8.jar")
    ),

    POSTGRESQL(
        "PostgreSQL",
        Arrays.asList("9.6", "10", "11", "12", "13", "14", "15", "16"),
        "org.postgresql.Driver",
        "jdbc:postgresql://{host}:{port}/{database}",
        Map.of(
            "9.6",
            "postgresql-9.4.1212.jar",
            "10",
            "postgresql-42.2.24.jar",
            "11",
            "postgresql-42.2.24.jar",
            "12",
            "postgresql-42.2.24.jar",
            "13",
            "postgresql-42.5.0.jar",
            "14",
            "postgresql-42.6.0.jar",
            "15",
            "postgresql-42.7.0.jar",
            "16",
            "postgresql-42.7.0.jar"
        )
    ),

    SQLSERVER(
        "Microsoft SQL Server",
        Arrays.asList("2008", "2012", "2014", "2016", "2017", "2019", "2022"),
        "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "jdbc:sqlserver://{host}:{port};databaseName={database}",
        Map.of(
            "2008",
            "sqljdbc4.jar",
            "2012",
            "sqljdbc42.jar",
            "2014",
            "sqljdbc42.jar",
            "2016",
            "mssql-jdbc-9.4.1.jre8.jar",
            "2017",
            "mssql-jdbc-9.4.1.jre8.jar",
            "2019",
            "mssql-jdbc-12.4.1.jre11.jar",
            "2022",
            "mssql-jdbc-12.4.1.jre11.jar"
        )
    ),

    DUCKDB(
        "DuckDB",
        Arrays.asList("0.8", "0.9", "1.0"),
        "org.duckdb.DuckDBDriver",
        "jdbc:duckdb:{database}",
        Map.of("0.8", "duckdb_jdbc-0.8.1.jar", "0.9", "duckdb_jdbc-0.9.2.jar", "1.0", "duckdb_jdbc-1.0.0.jar")
    ),

    DUCKLAKE(
        "DuckLake",
        Arrays.asList("0.3", "0.4"),
        "org.duckdb.DuckDBDriver",
        "jdbc:duckdb:{database}",
        Map.of("0.3", "duckdb_jdbc-0.3.0.jar", "0.4", "duckdb_jdbc-0.4.0.jar")
    ),

    CLICKHOUSE(
        "ClickHouse",
        Arrays.asList("21.8", "22.3", "23.3", "24.1"),
        "com.clickhouse.jdbc.ClickHouseDriver",
        "jdbc:clickhouse://{host}:{port}/{database}",
        Map.of(
            "21.8",
            "clickhouse-jdbc-0.3.2.jar",
            "22.3",
            "clickhouse-jdbc-0.4.6.jar",
            "23.3",
            "clickhouse-jdbc-0.4.6.jar",
            "24.1",
            "clickhouse-jdbc-0.4.6.jar"
        )
    ),

    GREENPLUM(
        "Greenplum",
        Arrays.asList("5", "6", "7"),
        "org.postgresql.Driver",
        "jdbc:postgresql://{host}:{port}/{database}",
        Map.of("5", "postgresql-42.2.24.jar", "6", "postgresql-42.5.0.jar", "7", "postgresql-42.7.0.jar")
    ),

    DORIS(
        "Doris",
        Arrays.asList("1.2", "2.0", "2.1"),
        "com.mysql.cj.jdbc.Driver",
        "jdbc:mysql://{host}:{port}/{database}",
        Map.of("1.2", "mysql-connector-java-8.0.33.jar", "2.0", "mysql-connector-java-8.0.33.jar", "2.1", "mysql-connector-java-8.0.33.jar")
    ),

    AVRO(
        "Apache Avro",
        Arrays.asList("1.8", "1.9", "1.10", "1.11"),
        "org.apache.avro.jdbc.AvroDriver",
        "jdbc:avro://{host}:{port}/{database}",
        Map.of("1.8", "avro-jdbc-1.8.2.jar", "1.9", "avro-jdbc-1.9.2.jar", "1.10", "avro-jdbc-1.10.2.jar", "1.11", "avro-jdbc-1.11.3.jar")
    );

    private final String displayName;
    private final List<String> supportedVersions;
    private final String driverClassName;
    private final String jdbcUrlTemplate;
    private final Map<String, String> versionToDriverJar;

    DBType(
        String displayName,
        List<String> supportedVersions,
        String driverClassName,
        String jdbcUrlTemplate,
        Map<String, String> versionToDriverJar
    ) {
        this.displayName = displayName;
        this.supportedVersions = supportedVersions;
        this.driverClassName = driverClassName;
        this.jdbcUrlTemplate = jdbcUrlTemplate;
        this.versionToDriverJar = versionToDriverJar;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getSupportedVersions() {
        return Collections.unmodifiableList(supportedVersions);
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public String getJdbcUrlTemplate() {
        return jdbcUrlTemplate;
    }

    public String getDriverClassNameForVersion(String version) {
        // 对于大多数数据库，驱动类名不随版本变化
        // 如果需要版本特定的驱动类名，可以在这里实现
        return driverClassName;
    }

    public String getDriverJarForVersion(String version) {
        return versionToDriverJar.getOrDefault(version, versionToDriverJar.get(getDefaultVersion()));
    }

    public boolean isVersionSupported(String version) {
        return supportedVersions.contains(version);
    }

    public String getDefaultVersion() {
        // 返回最新版本作为默认版本
        return supportedVersions.isEmpty() ? "default" : supportedVersions.get(supportedVersions.size() - 1);
    }

    public String buildJdbcUrl(String baseUrl, String version) {
        // 这里可以添加版本特定的URL参数
        // 例如：对于MySQL，可以添加版本特定的连接参数
        if (this == MYSQL) {
            if (version.startsWith("5.")) {
                return baseUrl + "&useSSL=false&serverTimezone=UTC";
            } else {
                return baseUrl + "&useSSL=true&serverTimezone=Asia/Shanghai";
            }
        }
        return baseUrl;
    }

    public static DBType fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<DBType> getTypesWithVersionSupport() {
        return Arrays.asList(MYSQL, ORACLE, POSTGRESQL, SQLSERVER, DUCKDB, CLICKHOUSE, GREENPLUM, DORIS);
    }

    public static DBType getByDisplayName(String displayName) {
        for (DBType type : values()) {
            if (type.getDisplayName().equalsIgnoreCase(displayName)) {
                return type;
            }
        }
        return null;
    }
}
