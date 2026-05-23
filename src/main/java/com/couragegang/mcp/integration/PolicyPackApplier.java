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
public final class PolicyPackApplier {

    private static final Logger LOG = LoggerFactory.getLogger(PolicyPackApplier.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String internalKey;
    private final HttpClient http;
    private final OutboundHttpMetrics metrics;
    private final ObjectMapper json;

    public PolicyPackApplier(
            @Value("${mcp.policy-service.enabled:false}") boolean enabled,
            @Value("${mcp.policy-service.base-url:http://localhost:8085/v1/policy}") String baseUrl,
            @Value("${mcp.policy-service.internal-api-key:dev-internal-key}") String internalKey,
            ObjectMapper json,
            OutboundHttpMetrics metrics) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalKey = internalKey;
        this.json = json;
        this.metrics = metrics;
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean applyInstallPack(
            UUID orgId,
            UUID workspaceId,
            UUID installationId,
            String connectorKey,
            int policyPackVersion,
            Map<String, Object> policyTemplatePack,
            UUID installedByUserId) {
        if (!enabled) {
            LOG.debug("policy-service disabled, skipping apply-pack");
            return true;
        }
        try {
            var body = json.createObjectNode();
            body.put("orgId", orgId.toString());
            body.put("workspaceId", workspaceId.toString());
            body.put("connectorKey", connectorKey);
            body.put("policyPackVersion", policyPackVersion);
            body.set("pack", json.valueToTree(policyTemplatePack));
            if (installedByUserId != null) {
                body.put("installedByUserId", installedByUserId.toString());
            }
            var uri =
                    URI.create(baseUrl + "/internal/installations/" + installationId + "/apply-pack");
            var request =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "application/json")
                            .header("X-Policy-Internal-Key", internalKey)
                            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                            .build();
            var response = metrics.send(http, request, "policy", "apply_install_pack");
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            LOG.warn("policy apply-pack failed: status={} body={}", response.statusCode(), response.body());
        } catch (Exception e) {
            LOG.warn("policy apply-pack error: {}", e.toString());
        }
        return false;
    }

    public void revokeInstallPack(UUID installationId) {
        if (!enabled) {
            return;
        }
        try {
            var uri =
                    URI.create(baseUrl + "/internal/installations/" + installationId + "/revoke-pack");
            var request =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(10))
                            .header("X-Policy-Internal-Key", internalKey)
                            .DELETE()
                            .build();
            metrics.send(http, request, "policy", "revoke_install_pack");
        } catch (Exception e) {
            LOG.warn("policy revoke-pack error: {}", e.toString());
        }
    }
}
