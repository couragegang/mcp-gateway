package com.couragegang.mcp.integration;

import com.couragegang.mcp.metrics.OutboundHttpMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URLEncoder;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class SecretsClient {

    private static final Logger LOG = LoggerFactory.getLogger(SecretsClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String internalKey;
    private final HttpClient http;
    private final OutboundHttpMetrics metrics;
    private final ObjectMapper json;

    public SecretsClient(
            @Value("${mcp.secrets-service.enabled:true}") boolean enabled,
            @Value("${mcp.secrets-service.base-url:http://localhost:8087/v1/secrets}") String baseUrl,
            @Value("${mcp.secrets-service.internal-api-key:dev-internal-key}") String internalKey,
            ObjectMapper json,
            OutboundHttpMetrics metrics) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalKey = internalKey;
        this.json = json;
        this.metrics = metrics;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public Optional<String> storeCredentials(
            UUID orgId, UUID workspaceId, String connectorKey, Map<String, String> payload) {
        if (!enabled || payload == null || payload.isEmpty()) {
            return Optional.empty();
        }
        try {
            var body = json.createObjectNode();
            body.put("orgId", orgId.toString());
            body.put("workspaceId", workspaceId.toString());
            body.put("connectorKey", connectorKey);
            body.set("payload", json.valueToTree(payload));
            var uri = URI.create(baseUrl + "/internal/credentials");
            var request =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .header("X-Secrets-Internal-Key", internalKey)
                            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                            .build();
            var response = metrics.send(http, request, "secrets", "store_credentials");
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                var node = json.readTree(response.body());
                return Optional.ofNullable(node.path("secretRef").asText(null));
            }
            LOG.warn("secrets store failed: status={} body={}", response.statusCode(), response.body());
        } catch (Exception e) {
            LOG.warn("secrets store error: {}", e.toString());
        }
        return Optional.empty();
    }

    public Map<String, Object> resolvePayload(String secretRef) {
        if (!enabled || secretRef == null || secretRef.isBlank()) {
            return Map.of();
        }
        try {
            var request =
                    HttpRequest.newBuilder(credentialUri(secretRef))
                            .timeout(Duration.ofSeconds(10))
                            .header("X-Secrets-Internal-Key", internalKey)
                            .GET()
                            .build();
            var response = metrics.send(http, request, "secrets", "resolve_credentials");
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode payload = json.readTree(response.body()).path("payload");
                var out = new LinkedHashMap<String, Object>();
                payload.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
                return out;
            }
        } catch (Exception e) {
            LOG.warn("secrets resolve error: {}", e.toString());
        }
        return Map.of();
    }

    public void revoke(String secretRef) {
        if (!enabled || secretRef == null || secretRef.isBlank() || secretRef.startsWith("local:")) {
            return;
        }
        try {
            var request =
                    HttpRequest.newBuilder(credentialUri(secretRef))
                            .timeout(Duration.ofSeconds(10))
                            .header("X-Secrets-Internal-Key", internalKey)
                            .DELETE()
                            .build();
            metrics.send(http, request, "secrets", "revoke_credentials");
        } catch (Exception e) {
            LOG.warn("secrets revoke error: {}", e.toString());
        }
    }

    private URI credentialUri(String secretRef) {
        var encoded = URLEncoder.encode(secretRef, StandardCharsets.UTF_8);
        return URI.create(baseUrl + "/internal/credentials/" + encoded);
    }
}
