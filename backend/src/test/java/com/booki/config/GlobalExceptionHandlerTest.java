package com.booki.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    @Test
    void anUnreachableDatabaseIsLoggedWithItsSqlStateButHiddenFromTheClient(CapturedOutput output) {
        RuntimeException failure = new CannotCreateTransactionException("Could not open JPA EntityManager",
                new SQLException("Connection to neon.internal refused", "08001"));

        ResponseEntity<Map<String, String>> response = new GlobalExceptionHandler().handleDatabase(failure);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("error")).doesNotContain("neon");
        assertThat(output).contains("db.error type=CannotCreateTransactionException sqlState=08001")
                .contains("SQLException: Connection to neon.internal refused");
    }
}
