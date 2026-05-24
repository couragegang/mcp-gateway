package com.couragegang.mcp.integration;

import com.couragegang.mcp.api.dto.McpModels.HealthCheckResult;
import com.couragegang.mcp.api.dto.NotionDiscoverModels.NotionDiscoverRequest;
import com.couragegang.mcp.api.dto.NotionDiscoverModels.NotionDiscoverResponse;
import com.couragegang.mcp.api.dto.NotionDiscoverModels.NotionResourceItem;
import com.couragegang.mcp.api.dto.ToolInvokeModels.ToolInvokeResponse;
import com.couragegang.mcp.metrics.OutboundHttpMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class ConnectorRuntimeClient {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorRuntimeClient.class);
    public static final String HEADER = "X-Internal-Api-Key";

    private final String internalKey;
    private final String notionRuntimeFallback;
    private final HttpClient http;
    private final OutboundHttpMetrics metrics;
    private final ObjectMapper json;

    public ConnectorRuntimeClient(
            @Value("${mcp.internal-api-key}") String internalKey,
            @Value("${mcp.connectors.notion-runtime-url:http://mcp-notion:8091/v1/notion}") String notionRuntimeFallback,
            ObjectMapper json,
            OutboundHttpMetrics metrics) {
        this.internalKey = internalKey;
        this.notionRuntimeFallback = trimSlash(notionRuntimeFallback);
        this.json = json;
        this.metrics = metrics;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public String resolveRuntimeBaseUrl(String connectorKey, String catalogRuntimeBaseUrl) {
        if (catalogRuntimeBaseUrl != null && !catalogRuntimeBaseUrl.isBlank()) {
            return trimSlash(catalogRuntimeBaseUrl);
        }
        if ("notion".equals(connectorKey)) {
            return notionRuntimeFallback;
        }
        return null;
    }

    public ToolInvokeResponse invokeTool(
            String runtimeBaseUrl,
            UUID installationId,
            UUID workspaceId,
            UUID orgId,
            Map<String, Object> connectionConfig,
            String credentialSecretRef,
            String toolName,
            Map<String, Object> arguments) {
        try {
            var body = json.createObjectNode();
            body.put("installationId", installationId.toString());
            body.put("workspaceId", workspaceId.toString());
            body.put("orgId", orgId.toString());
            body.put("credentialSecretRef", credentialSecretRef);
            body.put("toolName", toolName);
            body.set("connectionConfig", json.valueToTree(connectionConfig != null ? connectionConfig : Map.of()));
            body.set("arguments", json.valueToTree(arguments != null ? arguments : Map.of()));
            var response = post(runtimeBaseUrl + "/internal/tools/invoke", body);
            return new ToolInvokeResponse(
                    response.path("ok").asBoolean(false),
                    textOrNull(response, "summary"),
                    textOrNull(response, "error"));
        } catch (ConnectorCallException e) {
            return ToolInvokeResponse.failure(e.getMessage());
        }
    }

    public HealthCheckResult probeHealth(
            String runtimeBaseUrl,
            UUID installationId,
            UUID workspaceId,
            UUID orgId,
            Map<String, Object> connectionConfig,
            String credentialSecretRef) {
        try {
            var body = installationContext(installationId, workspaceId, orgId, connectionConfig, credentialSecretRef);
            var response = post(runtimeBaseUrl + "/internal/health", body);
            return new HealthCheckResult(
                    response.path("ok").asBoolean(false), response.path("message").asText("probe failed"));
        } catch (ConnectorCallException e) {
            return new HealthCheckResult(false, e.getMessage());
        }
    }

    public Map<String, Object> normalizeConfig(
            String runtimeBaseUrl, String connectorKey, Map<String, Object> connectionConfig) {
        try {
            var body = json.createObjectNode();
            body.put("connectorKey", connectorKey);
            body.set("connectionConfig", json.valueToTree(connectionConfig != null ? connectionConfig : Map.of()));
            var response = post(runtimeBaseUrl + "/internal/normalize-config", body);
            return json.convertValue(response.path("connectionConfig"), Map.class);
        } catch (ConnectorCallException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public NotionDiscoverResponse discover(String runtimeBaseUrl, NotionDiscoverRequest request) {
        try {
            var body = json.createObjectNode();
            body.put("integrationToken", request.integrationToken());
            var response = post(runtimeBaseUrl + "/internal/discover", body);
            List<NotionResourceItem> items = new ArrayList<>();
            for (var node : response.path("items")) {
                items.add(
                        new NotionResourceItem(
                                node.path("id").asText(),
                                node.path("title").asText(),
                                node.path("url").asText(""),
                                node.path("kind").asText("database")));
            }
            return new NotionDiscoverResponse(items);
        } catch (ConnectorCallException e) {
            throw new IllegalStateException("Notion discover failed: " + e.getMessage(), e);
        }
    }

    private ObjectNode installationContext(
            UUID installationId,
            UUID workspaceId,
            UUID orgId,
            Map<String, Object> connectionConfig,
            String credentialSecretRef) {
        var body = json.createObjectNode();
        body.put("installationId", installationId.toString());
        body.put("workspaceId", workspaceId.toString());
        body.put("orgId", orgId.toString());
        body.put("credentialSecretRef", credentialSecretRef);
        body.set("connectionConfig", json.valueToTree(connectionConfig != null ? connectionConfig : Map.of()));
        return body;
    }

    private JsonNode post(String url, ObjectNode body) throws ConnectorCallException {
        try {
            var request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(60))
                            .header("Content-Type", "application/json")
                            .header(HEADER, internalKey)
                            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                            .build();
            var response = metrics.send(http, request, "connector-runtime", "post");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ConnectorCallException(
                        "connector HTTP " + response.statusCode() + ": " + truncate(response.body()));
            }
            return json.readTree(response.body());
        } catch (ConnectorCallException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("connector call failed url={}: {}", url, e.toString());
            throw new ConnectorCallException(e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        var t = node.path(field);
        return t.isNull() || t.isMissingNode() ? null : t.asText();
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() > 400 ? s.substring(0, 400) + "…" : s);
    }

    private static final class ConnectorCallException extends Exception {
        ConnectorCallException(String message) {
            super(message);
        }

        ConnectorCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
