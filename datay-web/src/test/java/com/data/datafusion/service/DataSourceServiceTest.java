package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class DataSourceServiceTest {

    @Test
    void shouldReturnRootCauseAndHintForRefusedConnection() {
        Exception error = new RuntimeException("Failed to initialize pool", new ConnectException("Connection refused"));

        assertThat(DataSourceService.describeConnectionFailure(error))
            .contains("Connection refused")
            .contains("端口是否开放");
    }

    @Test
    void shouldAddCredentialHintForAccessDenied() {
        Exception error = new SQLException("Access denied for user 'demo'@'localhost' (using password: YES)");

        assertThat(DataSourceService.describeConnectionFailure(error)).contains("Access denied").contains("用户名、密码");
    }

    @Test
    void shouldRedactPasswordInMessage() {
        Exception error = new SQLException("Connection failed: password=secret; host=db");

        String message = DataSourceService.describeConnectionFailure(error);

        assertThat(message).contains("password=***").doesNotContain("secret");
    }

    @Test
    void shouldFallBackToExceptionTypeWhenMessageIsMissing() {
        Exception error = new IllegalStateException();

        assertThat(DataSourceService.describeConnectionFailure(error)).contains("IllegalStateException");
    }
}
