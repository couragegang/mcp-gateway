package com.couragegang.mcp.service;

import com.couragegang.mcp.api.dto.McpModels.HealthCheckResult;
import com.couragegang.mcp.api.dto.McpModels.Installation;
import com.couragegang.mcp.api.dto.McpModels.InstallationCreateRequest;
import com.couragegang.mcp.api.dto.McpModels.InstallationDetail;
import com.couragegang.mcp.api.dto.McpModels.InstallationListResponse;
import com.couragegang.mcp.api.dto.McpModels.InstallationUpdateRequest;
import com.couragegang.mcp.error.McpApiException;
import com.couragegang.mcp.integration.AuditClient;
import com.couragegang.mcp.integration.NotionHealthProbe;
import com.couragegang.mcp.integration.NotionIdParser;
import com.couragegang.mcp.integration.PolicyPackApplier;
import com.couragegang.mcp.integration.SecretsClient;
import com.couragegang.mcp.repo.CatalogRepository;
import com.couragegang.mcp.repo.InstallationRepository;
import io.micronaut.http.HttpStatus;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Singleton
public final class InstallationService {

    private final CatalogRepository catalog;
    private final InstallationRepository installations;
    private final ConnectionFormSplitter formSplitter;
    private final SecretsClient secrets;
    private final PolicyPackApplier policyPack;
    private final AuditClient audit;
    private final NotionHealthProbe notionProbe;

    public InstallationService(
            CatalogRepository catalog,
            InstallationRepository installations,
            ConnectionFormSplitter formSplitter,
            SecretsClient secrets,
            PolicyPackApplier policyPack,
            AuditClient audit,
            NotionHealthProbe notionProbe) {
        this.catalog = catalog;
        this.installations = installations;
        this.formSplitter = formSplitter;
        this.secrets = secrets;
        this.policyPack = policyPack;
        this.audit = audit;
        this.notionProbe = notionProbe;
    }

    public InstallationListResponse list(UUID workspaceId) {
        try {
            var items = installations.listByWorkspace(workspaceId).stream()
                    .map(this::toDto)
                    .toList();
            return new InstallationListResponse(items);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Installation create(
            UUID orgId, UUID workspaceId, InstallationCreateRequest req, @Nullable UUID userId) {
        UUID installationId = null;
        String secretRef = null;
        try {
            var catalogRow = catalog.findPublished(req.connectorKey()).orElseThrow(() -> notFound("connector not found"));
            var existing = installations.findByWorkspaceAndConnector(workspaceId, req.connectorKey());
            if (existing.isPresent() && !"revoked".equals(existing.get().status())) {
                throw new McpApiException(HttpStatus.CONFLICT, "CONFLICT", "connector already installed");
            }
            var split = formSplitter.split(catalogRow.connectionFormSchema(), req.form());
            var config = normalizeNotionConfig(req.connectorKey(), new HashMap<>(split.config()));
            if (!split.secrets().isEmpty()) {
                secretRef =
                        secrets.storeCredentials(orgId, workspaceId, req.connectorKey(), split.secrets())
                                .orElseThrow(() -> new McpApiException(
                                        HttpStatus.BAD_GATEWAY, "secrets_store_failed", "failed to store credentials"));
            } else {
                secretRef = "local:none";
            }
            var label = req.displayLabel() != null && !req.displayLabel().isBlank()
                    ? req.displayLabel().trim()
                    : req.connectorKey();
            var pack =
                    req.policyPack() != null && !req.policyPack().isEmpty()
                            ? req.policyPack()
                            : catalogRow.policyTemplatePack();
            if (existing.isPresent() && "revoked".equals(existing.get().status())) {
                var revoked = existing.get();
                installationId = revoked.id();
                secrets.revoke(revoked.credentialSecretRef());
                policyPack.revokeInstallPack(installationId);
                if (!installations.reactivate(workspaceId, installationId, label, secretRef, config, userId)) {
                    secrets.revoke(secretRef);
                    throw notFound("installation not found");
                }
            } else {
                installationId =
                        installations.insert(
                                orgId,
                                workspaceId,
                                req.connectorKey(),
                                label,
                                secretRef,
                                config,
                                userId);
                installations.updateStatus(installationId, "active");
            }
            if (!policyPack.applyInstallPack(
                    orgId,
                    workspaceId,
                    installationId,
                    req.connectorKey(),
                    catalogRow.policyPackVersion(),
                    pack,
                    userId)) {
                rollback(installationId, secretRef);
                throw new McpApiException(HttpStatus.BAD_GATEWAY, "policy_apply_failed", "policy apply failed");
            }
            audit.emitInstallationEvent(orgId, workspaceId, installationId, req.connectorKey(), "created", userId);
            return toDto(installations.findById(workspaceId, installationId).orElseThrow());
        } catch (SQLException e) {
            if (isUniqueViolation(e)) {
                if (installationId != null) {
                    rollback(installationId, secretRef);
                }
                throw new McpApiException(HttpStatus.CONFLICT, "CONFLICT", "connector already installed");
            }
            throw new IllegalStateException(e);
        } catch (McpApiException e) {
            if (installationId != null) {
                rollback(installationId, secretRef);
            }
            throw e;
        }
    }

    public InstallationDetail getDetail(UUID workspaceId, UUID installationId) {
        try {
            var detail =
                    installations.findDetail(workspaceId, installationId).orElseThrow(() -> notFound("installation not found"));
            if ("revoked".equals(detail.status())) {
                throw notFound("installation not found");
            }
            var catalogRow =
                    catalog.findPublished(detail.connectorKey()).orElseThrow(() -> notFound("connector not found"));
            var config = new HashMap<String, Object>();
            if (detail.connectionConfig() != null) {
                config.putAll(detail.connectionConfig());
            }
            var hasSecrets =
                    detail.credentialSecretRef() != null
                            && !detail.credentialSecretRef().isBlank()
                            && !detail.credentialSecretRef().startsWith("local:");
            return new InstallationDetail(
                    toDto(installations.findById(workspaceId, installationId).orElseThrow()),
                    catalogRow.connectionFormSchema(),
                    config,
                    hasSecrets);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Installation update(UUID workspaceId, UUID installationId, InstallationUpdateRequest req) {
        try {
            var detail =
                    installations.findDetail(workspaceId, installationId).orElseThrow(() -> notFound("installation not found"));
            if ("revoked".equals(detail.status())) {
                throw notFound("installation not found");
            }
            var catalogRow =
                    catalog.findPublished(detail.connectorKey()).orElseThrow(() -> notFound("connector not found"));

            if (req.displayLabel() != null && !req.displayLabel().isBlank()) {
                if (!installations.updateDisplayLabel(workspaceId, installationId, req.displayLabel().trim())) {
                    throw notFound("installation not found");
                }
            }

            if (req.form() != null && !req.form().isEmpty()) {
                var split = formSplitter.split(catalogRow.connectionFormSchema(), req.form());
                if (!split.config().isEmpty()) {
                    var merged = new HashMap<>(detail.connectionConfig() != null ? detail.connectionConfig() : Map.of());
                    merged.putAll(split.config());
                    normalizeNotionConfig(detail.connectorKey(), merged);
                    installations.updateConnectionConfig(workspaceId, installationId, merged);
                }
                if (!split.secrets().isEmpty()) {
                    secrets.revoke(detail.credentialSecretRef());
                    var secretRef =
                            secrets.storeCredentials(detail.orgId(), workspaceId, detail.connectorKey(), split.secrets())
                                    .orElseThrow(() -> new McpApiException(
                                            HttpStatus.BAD_GATEWAY,
                                            "secrets_store_failed",
                                            "failed to store credentials"));
                    installations.updateCredentialSecretRef(workspaceId, installationId, secretRef);
                }
            }

            audit.emitInstallationEvent(detail.orgId(), workspaceId, installationId, detail.connectorKey(), "updated", null);
            return toDto(installations.findById(workspaceId, installationId).orElseThrow());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void delete(UUID workspaceId, UUID installationId) {
        try {
            var detail = installations
                    .findDetail(workspaceId, installationId)
                    .orElseThrow(() -> notFound("installation not found"));
            policyPack.revokeInstallPack(installationId);
            secrets.revoke(detail.credentialSecretRef());
            installations.updateStatus(installationId, "revoked");
            audit.emitInstallationEvent(
                    detail.orgId(), workspaceId, installationId, detail.connectorKey(), "deleted", null);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public HealthCheckResult health(UUID workspaceId, UUID installationId) {
        try {
            var detail = installations
                    .findDetail(workspaceId, installationId)
                    .orElseThrow(() -> notFound("installation not found"));
            var merged = mergeConfig(detail.connectionConfig(), detail.credentialSecretRef());
            NotionHealthProbe.ProbeResult probe;
            if ("notion".equals(detail.connectorKey())) {
                probe = notionProbe.probe(merged);
            } else {
                probe = new NotionHealthProbe.ProbeResult(true, "no probe for connector");
            }
            var result = probe.ok() ? "ok" : "error";
            installations.insertHealthCheck(
                    installationId, result, Map.of("message", probe.message()));
            if (!probe.ok()) {
                installations.updateStatus(installationId, "error");
            } else if ("error".equals(detail.status())) {
                installations.updateStatus(installationId, "active");
            }
            return new HealthCheckResult(probe.ok(), probe.message());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> mergeConfig(Map<String, Object> connectionConfig, String secretRef) {
        var merged = new HashMap<>(connectionConfig);
        secrets.resolvePayload(secretRef).forEach(merged::putIfAbsent);
        return merged;
    }

    private void rollback(UUID installationId, String secretRef) {
        try {
            installations.updateStatus(installationId, "revoked");
            policyPack.revokeInstallPack(installationId);
            secrets.revoke(secretRef);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Map<String, Object> normalizeNotionConfig(String connectorKey, Map<String, Object> config) {
        if (!"notion".equals(connectorKey)) {
            return config;
        }
        var raw = config.get("default_database_id");
        if (raw == null) {
            return config;
        }
        var s = String.valueOf(raw).strip();
        if (s.isBlank()) {
            config.remove("default_database_id");
            return config;
        }
        NotionIdParser.parseId(s).ifPresent(id -> config.put("default_database_id", id));
        return config;
    }

    private static boolean isUniqueViolation(SQLException e) {
        return "23505".equals(e.getSQLState());
    }

    private Installation toDto(InstallationRepository.InstallationRow r) {
        return new Installation(r.id(), r.workspaceId(), r.connectorKey(), r.displayLabel(), r.status());
    }

    private McpApiException notFound(String msg) {
        return new McpApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", msg);
    }

}
