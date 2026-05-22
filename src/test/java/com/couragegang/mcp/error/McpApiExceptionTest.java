package com.couragegang.mcp.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

class McpApiExceptionTest {

    @Test
    void exposesStatusAndBody() {
        var ex = new McpApiException(HttpStatus.CONFLICT, "CONFLICT", "already installed");

        assertThat(ex.getMessage()).isEqualTo("already installed");
        assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.body().code()).isEqualTo("CONFLICT");
    }
}
