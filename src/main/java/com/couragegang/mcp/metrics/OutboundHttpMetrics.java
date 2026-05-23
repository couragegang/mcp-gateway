package com.couragegang.mcp.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

@Singleton
public final class OutboundHttpMetrics {

    private static final String METRIC = "mcp.integration.http";

    private final MeterRegistry registry;

    public OutboundHttpMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public HttpResponse<String> send(
            HttpClient client, HttpRequest request, String integration, String operation)
            throws IOException, InterruptedException {
        var sample = Timer.start(registry);
        try {
            var resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            stop(sample, integration, operation, resp.statusCode(), null);
            return resp;
        } catch (IOException | InterruptedException e) {
            stop(sample, integration, operation, null, e);
            throw e;
        }
    }

    private void stop(
            Timer.Sample sample, String integration, String operation, Integer httpStatus, Throwable error) {
        var timer =
                Timer.builder(METRIC)
                        .description("Outbound HTTP for MCP integrations")
                        .tag("integration", Objects.requireNonNullElse(integration, "unknown"))
                        .tag("operation", Objects.requireNonNullElse(operation, "unknown"))
                        .tag("status", statusLabel(httpStatus, error))
                        .tag("outcome", outcomeLabel(httpStatus, error))
                        .publishPercentileHistogram()
                        .register(registry);
        sample.stop(timer);
    }

    private static String statusLabel(Integer httpStatus, Throwable error) {
        if (error instanceof InterruptedException) {
            return "interrupted";
        }
        if (error != null) {
            return "error";
        }
        return String.valueOf(httpStatus);
    }

    private static String outcomeLabel(Integer httpStatus, Throwable error) {
        if (error != null) {
            return "failure";
        }
        if (httpStatus == null) {
            return "unknown";
        }
        if (httpStatus >= 500) {
            return "server_error";
        }
        if (httpStatus >= 400) {
            return "client_error";
        }
        return "success";
    }
}
