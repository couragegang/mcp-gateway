package com.couragegang.mcp.service;

import com.couragegang.mcp.api.dto.McpModels.HealthCheckResult;
import com.couragegang.mcp.api.dto.McpModels.Installation;
import com.couragegang.mcp.api.dto.McpModels.InstallationCreateRequest;
import com.couragegang.mcp.api.dto.McpModels.InstallationListResponse;
import com.couragegang.mcp.error.McpApiException;
import com.couragegang.mcp.integration.AuditClient;
import com.couragegang.mcp.integration.NotionHealthProbe;
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
            if (installations.listByWorkspace(workspaceId).stream()
                    .anyMatch(i -> i.connectorKey().equals(req.connectorKey()))) {
                throw new McpApiException(HttpStatus.CONFLICT, "CONFLICT", "connector already installed");
            }
            var split = formSplitter.split(catalogRow.connectionFormSchema(), req.form());
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
            installationId =
                    installations.insert(
                            orgId,
                            workspaceId,
                            req.connectorKey(),
                            label,
                            secretRef,
                            split.config(),
                            userId);
            installations.updateStatus(installationId, "active");
            if (!policyPack.applyInstallPack(
                    orgId,
                    workspaceId,
                    installationId,
                    req.connectorKey(),
                    catalogRow.policyPackVersion(),
                    catalogRow.policyTemplatePack(),
                    userId)) {
                rollback(installationId, secretRef);
                throw new McpApiException(HttpStatus.BAD_GATEWAY, "policy_apply_failed", "policy apply failed");
            }
            audit.emitInstallationEvent(orgId, workspaceId, installationId, req.connectorKey(), "created", userId);
            return toDto(installations.findById(workspaceId, installationId).orElseThrow());
        } catch (SQLException e) {
            if (isUniqueViolation(e) && installationId != null) {
                rollback(installationId, secretRef);
            }
            throw new IllegalStateException(e);
        } catch (McpApiException e) {
            if (installationId != null) {
                rollback(installationId, secretRef);
            }
            throw e;
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
