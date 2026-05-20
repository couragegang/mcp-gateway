package com.couragegang.mcp.integration;

import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Singleton
public final class NotionHealthProbe {

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public ProbeResult probe(Map<String, Object> connectionConfig) {
        var token = findToken(connectionConfig);
        if (token.isEmpty()) {
            return new ProbeResult(false, "integration_token missing");
        }
        try {
            var request =
                    HttpRequest.newBuilder(URI.create("https://api.notion.com/v1/users/me"))
                            .timeout(Duration.ofSeconds(10))
                            .header("Authorization", "Bearer " + token.get())
                            .header("Notion-Version", "2022-06-28")
                            .GET()
                            .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new ProbeResult(true, "Notion API OK");
            }
            return new ProbeResult(false, "Notion API status " + response.statusCode());
        } catch (Exception e) {
            return new ProbeResult(false, e.getMessage());
        }
    }

    private static Optional<String> findToken(Map<String, Object> config) {
        var v = config.get("integration_token");
        if (v == null) {
            return Optional.empty();
        }
        var s = String.valueOf(v);
        return s.isBlank() ? Optional.empty() : Optional.of(s);
    }

    public record ProbeResult(boolean ok, String message) {}
}
