package com.couragegang.mcp.security;

import com.couragegang.mcp.api.dto.McpModels.ErrorBody;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Filter("/internal/**")
public class InternalApiKeyFilter implements HttpServerFilter {

    public static final String HEADER = "X-Mcp-Internal-Key";

    private final String expectedKey;

    public InternalApiKeyFilter(@Value("${mcp.internal-api-key}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order();
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        var provided = request.getHeaders().get(HEADER);
        if (provided == null || !provided.equals(expectedKey)) {
            return Mono.just(
                    HttpResponse.status(HttpStatus.UNAUTHORIZED)
                            .body(ErrorBody.of("UNAUTHORIZED", "invalid internal api key")));
        }
        return chain.proceed(request);
    }
}
