package com.couragegang.mcp.integration;

import com.couragegang.mcp.metrics.OutboundHttpMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class AuditClient {

    private static final Logger LOG = LoggerFactory.getLogger(AuditClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String internalKey;
    private final HttpClient http;
    private final OutboundHttpMetrics metrics;
    private final ObjectMapper json;

    public AuditClient(
            @Value("${mcp.audit-service.enabled:true}") boolean enabled,
            @Value("${mcp.audit-service.base-url:http://localhost:8086/v1/audit}") String baseUrl,
            @Value("${mcp.audit-service.internal-api-key:dev-internal-key}") String internalKey,
            ObjectMapper json,
            OutboundHttpMetrics metrics) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalKey = internalKey;
        this.json = json;
        this.metrics = metrics;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public void emitInstallationEvent(
            UUID orgId,
            UUID workspaceId,
            UUID installationId,
            String connectorKey,
            String outcome,
            UUID actorUserId) {
        if (!enabled) {
            return;
        }
        try {
            var body = json.createObjectNode();
            body.put("orgId", orgId.toString());
            body.put("workspaceId", workspaceId.toString());
            body.put("installationId", installationId.toString());
            body.put("eventType", "mcp.installation");
            body.put("toolName", connectorKey);
            body.put("outcome", outcome);
            if (actorUserId != null) {
                body.put("actorUserId", actorUserId.toString());
            }
            body.set("metadata", json.createObjectNode());
            var uri = URI.create(baseUrl + "/internal/tool-events");
            var request =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .header("X-Audit-Internal-Key", internalKey)
                            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                            .build();
            metrics.send(http, request, "audit", "emit_tool_event");
        } catch (Exception e) {
            LOG.debug("audit emit skipped: {}", e.toString());
        }
    }
}
