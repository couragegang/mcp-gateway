package com.couragegang.mcp.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.couragegang.mcp.api.dto.McpModels.ErrorBody;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

class McpExceptionHandlerTest {

    @Test
    void mapsStatusAndBody() {
        var ex = new McpApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "missing");
        var response = new McpExceptionHandler().handle(HttpRequest.GET("/"), ex);

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(response.body()).isEqualTo(new ErrorBody("NOT_FOUND", "missing"));
    }
}
