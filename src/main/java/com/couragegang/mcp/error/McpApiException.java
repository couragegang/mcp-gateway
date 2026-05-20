package com.couragegang.mcp.error;

import com.couragegang.mcp.api.dto.McpModels.ErrorBody;
import io.micronaut.http.HttpStatus;

public final class McpApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorBody body;

    public McpApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.body = ErrorBody.of(code, message);
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorBody body() {
        return body;
    }
}
