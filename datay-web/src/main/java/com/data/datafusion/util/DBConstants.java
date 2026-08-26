package com.data.datafusion.util;

/**
 * Application constants.
 */
public final class DBConstants {

    public static final String PHOENIX_DRIVER_NAME = "org.apache.phoenix.jdbc.PhoenixDriver";
    public static final String HIVE_DRIVER_NAME = "org.apache.hive.jdbc.HiveDriver";
    public static final String MYSQL_DRIVER_NAME = "com.mysql.jdbc.Driver";
    public static final String MSSQL_DRIVER_NAME = "net.sourceforge.jtds.jdbc.Driver";
    public static final String ORACLE_DRIVER_NAME = "oracle.jdbc.driver.OracleDriver";
    public static final String MONGODB_DRIVER_NAME = "mongodb.jdbc.MongoDriver";
    public static final String GREENPLUM_DRIVER_NAME = "com.pivotal.jdbc.GreenplumDriver";
    public static final String PRESTO_DRIVER_NAME = "com.facebook.presto.jdbc.PrestoDriver";
    public static final String HANA_DRIVER_NAME = "com.sap.db.jdbc.Driver";

    public static enum DB_TYPE {
        MYSQL,
        MSSQL,
        MONGODB,
        HBASE,
        HIVE,
        POSTGRESQL,
        ORACLE,
        GREENPLUM,
        HDFS,
        PHOENIX,
        PRESTO,
        HANA,
    }

    private DBConstants() {}
}
