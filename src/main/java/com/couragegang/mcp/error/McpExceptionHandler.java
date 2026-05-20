package com.couragegang.mcp.error;

import com.couragegang.mcp.api.dto.McpModels.ErrorBody;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Singleton
@Produces
public final class McpExceptionHandler implements ExceptionHandler<McpApiException, HttpResponse<ErrorBody>> {

    @Override
    public HttpResponse<ErrorBody> handle(HttpRequest request, McpApiException exception) {
        return HttpResponse.status(exception.status()).body(exception.body());
    }
}
