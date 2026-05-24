package com.couragegang.mcp.service;

import com.couragegang.mcp.api.dto.ToolInvokeModels.ToolInvokeResponse;
import com.couragegang.mcp.integration.ConnectorRuntimeClient;
import com.couragegang.mcp.integration.SecretsClient;
import com.couragegang.mcp.repo.CatalogRepository;
import com.couragegang.mcp.repo.InstallationRepository;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Singleton
public final class ToolInvokeService {

    private final InstallationRepository installations;
    private final CatalogRepository catalog;
    private final SecretsClient secrets;
    private final ConnectorRuntimeClient connectorRuntime;

    public ToolInvokeService(
            InstallationRepository installations,
            CatalogRepository catalog,
            SecretsClient secrets,
            ConnectorRuntimeClient connectorRuntime) {
        this.installations = installations;
        this.catalog = catalog;
        this.secrets = secrets;
        this.connectorRuntime = connectorRuntime;
    }

    public ToolInvokeResponse invoke(
            UUID workspaceId, String connectorKey, String toolName, Map<String, Object> arguments) {
        try {
            InstallationRepository.InstallationDetailRow detail = null;
            for (var row : installations.listByWorkspace(workspaceId)) {
                if (!connectorKey.equals(row.connectorKey()) || "revoked".equals(row.status())) {
                    continue;
                }
                detail = installations.findDetail(workspaceId, row.id()).orElse(null);
                break;
            }
            if (detail == null) {
                return ToolInvokeResponse.failure("Коннектор «" + connectorKey + "» не установлен в workspace");
            }
            if (!"active".equals(detail.status()) && !"error".equals(detail.status())) {
                return ToolInvokeResponse.failure("Установка «" + connectorKey + "» неактивна (status=" + detail.status() + ")");
            }
            var catalogRow = catalog.findPublished(connectorKey).orElse(null);
            var runtimeUrl =
                    connectorRuntime.resolveRuntimeBaseUrl(
                            connectorKey, catalogRow != null ? catalogRow.runtimeBaseUrl() : null);
            if (runtimeUrl == null || runtimeUrl.isBlank()) {
                return ToolInvokeResponse.failure("Вызов инструментов для «" + connectorKey + "» пока не реализован");
            }
            var merged = mergeConfig(detail.connectionConfig(), detail.credentialSecretRef());
            return connectorRuntime.invokeTool(
                    runtimeUrl,
                    detail.id(),
                    workspaceId,
                    detail.orgId(),
                    merged,
                    detail.credentialSecretRef(),
                    toolName,
                    arguments);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> mergeConfig(Map<String, Object> connectionConfig, String secretRef) {
        var merged = new HashMap<>(connectionConfig != null ? connectionConfig : Map.of());
        secrets.resolvePayload(secretRef).forEach(merged::putIfAbsent);
        return merged;
    }
}
