package com.data.status;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioStatusStorageStrategyTest {

    @Test
    void missingObjectShouldBeTreatedAsNoStatus() {
        MinioStatusStorageStrategy strategy = new MinioStatusStorageStrategy(
            new FailingMinioClient(error("NoSuchKey")),
            "test-bucket"
        );

        assertNull(strategy.loadStatus("1001"));
    }

    @Test
    void accessDeniedShouldFailTaskInsteadOfReturningNull() {
        MinioStatusStorageStrategy strategy = new MinioStatusStorageStrategy(
            new FailingMinioClient(error("AccessDenied")),
            "test-bucket"
        );

        StatusStorageException exception = assertThrows(StatusStorageException.class, () -> strategy.loadStatus("1001"));
        assertEquals(StatusStorageException.Reason.STORAGE_UNAVAILABLE, exception.getReason());
        assertTrue(exception.getMessage().contains("从MinIO恢复状态失败"), exception.getMessage());
    }

    @Test
    void ioFailureShouldFailTaskInsteadOfReturningNull() {
        MinioStatusStorageStrategy strategy = new MinioStatusStorageStrategy(
            new FailingMinioClient(new IOException("connection reset")),
            "test-bucket"
        );

        StatusStorageException exception = assertThrows(StatusStorageException.class, () -> strategy.loadStatus("1001"));
        assertEquals(StatusStorageException.Reason.READ_FAILED, exception.getReason());
        assertTrue(exception.getMessage().contains("从MinIO读取状态失败"), exception.getMessage());
    }

    private ErrorResponseException error(String code) {
        return new ErrorResponseException(
            new ErrorResponse(code, "test error", "test-bucket", "etl-status/1001.json", "/test-bucket/etl-status/1001.json", "req", "host"),
            null,
            "test"
        );
    }

    private static class FailingMinioClient extends MinioClient {

        private final Exception failure;

        private FailingMinioClient(Exception failure) {
            super(MinioClient.builder().endpoint("http://127.0.0.1:9000").credentials("test", "testtest").build());
            this.failure = failure;
        }

        @Override
        public GetObjectResponse getObject(GetObjectArgs args) throws ErrorResponseException, IOException {
            if (failure instanceof ErrorResponseException errorResponseException) {
                throw errorResponseException;
            }
            if (failure instanceof IOException ioException) {
                throw ioException;
            }
            throw new RuntimeException(failure);
        }
    }
}
