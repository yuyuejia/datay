package com.data.datafusion.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.job.TaskConstants;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class JobPriorityMigrationTest {

    @Test
    void addsPriorityColumnsWithNormalPriorityForExistingRows() throws Exception {
        String databaseName = "priority_migration_" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table dp_job (id varchar(50) primary key)");
                statement.execute("create table dp_etl_task (id varchar(50) primary key)");
                statement.execute("create table dp_job_instance (id varchar(50) primary key)");
                statement.execute("insert into dp_job (id) values ('existing-job')");
                statement.execute("insert into dp_etl_task (id) values ('existing-etl')");
                statement.execute("insert into dp_job_instance (id) values ('existing-instance')");
            }

            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                "config/liquibase/changelog/20261024000000_add_job_priority.xml",
                new ClassLoaderResourceAccessor(),
                database
            );
            liquibase.update(new Contexts(), new LabelExpression());

            assertPriorityColumn(connection, "dp_job", "existing-job");
            assertPriorityColumn(connection, "dp_etl_task", "existing-etl");
            assertPriorityColumn(connection, "dp_job_instance", "existing-instance");

            try (Statement statement = connection.createStatement()) {
                statement.execute("insert into dp_job (id) values ('new-job')");
                statement.execute("insert into dp_etl_task (id) values ('new-etl')");
                statement.execute("insert into dp_job_instance (id) values ('new-instance')");
            }

            assertPriorityColumn(connection, "dp_job", "new-job");
            assertPriorityColumn(connection, "dp_etl_task", "new-etl");
            assertPriorityColumn(connection, "dp_job_instance", "new-instance");
        }
    }

    private void assertPriorityColumn(Connection connection, String tableName, String id) throws Exception {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName.toUpperCase(), "PRIORITY")) {
                assertThat(columns.next()).as("priority column on %s", tableName).isTrue();
                assertThat(columns.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNoNulls);
            }
            try (ResultSet result = statement.executeQuery("select priority from " + tableName + " where id = '" + id + "'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(TaskConstants.DEFAULT_PRIORITY);
            }
        }
    }
}
