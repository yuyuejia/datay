package com.data.datasource;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 架构约束：JDBC 数据源的运行时路径（连接与元数据读取）不得依赖 DBType 中心枚举或连接池，
 * 从而保证新增数据源时无需触碰中心化分支。
 */
class ProviderDecouplingTest {

    private static final JavaClasses JDBC_PROVIDER_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.data.datasource.provider.jdbc");

    @Test
    void jdbcConnectionHasNoDbTypeOrPoolingCoupling() {
        noClasses()
            .that()
            .haveSimpleName("JdbcDataSourceConnection")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.data.metadata.DBType")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.data.metadata.util.ConnectionPoolManager")
            .check(JDBC_PROVIDER_CLASSES);
    }

    @Test
    void jdbcMetadataHasNoDbTypeCoupling() {
        noClasses()
            .that()
            .haveSimpleName("JdbcMetadata")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.data.metadata.DBType")
            .check(JDBC_PROVIDER_CLASSES);
    }
}
