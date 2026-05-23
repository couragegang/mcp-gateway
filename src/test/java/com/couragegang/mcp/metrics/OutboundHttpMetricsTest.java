package com.couragegang.mcp.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboundHttpMetricsTest {

    HttpServer server;
    String baseUrl;
    SimpleMeterRegistry registry;
    OutboundHttpMetrics metrics;
    HttpClient http;

    @BeforeEach
    void start() throws Exception {
        registry = new SimpleMeterRegistry();
        metrics = new OutboundHttpMetrics(registry);
        http = HttpClient.newHttpClient();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void recordsSuccessTimer() throws Exception {
        server.createContext(
                "/ok",
                exchange -> {
                    var body = "ok";
                    exchange.sendResponseHeaders(200, body.length());
                    exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
                    exchange.close();
                });

        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/ok")).GET().build();
        var response = metrics.send(http, request, "test", "get_ok");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(registry.find("mcp.integration.http").timers()).isNotEmpty();
    }

    @Test
    void propagatesIoFailure() {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:1/unreachable")).GET().build();
        assertThatThrownBy(() -> metrics.send(http, request, "test", "fail"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void recordsClientAndServerErrorOutcomes() throws Exception {
        server.createContext(
                "/client-error",
                exchange -> {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                });
        server.createContext(
                "/server-error",
                exchange -> {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                });

        metrics.send(
                http,
                HttpRequest.newBuilder(URI.create(baseUrl + "/client-error")).GET().build(),
                "test",
                "client_err");
        metrics.send(
                http,
                HttpRequest.newBuilder(URI.create(baseUrl + "/server-error")).GET().build(),
                "test",
                "server_err");

        assertThat(registry.find("mcp.integration.http").timers()).hasSizeGreaterThanOrEqualTo(2);
    }
}
