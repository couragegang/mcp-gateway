package com.couragegang.mcp.service;

import com.couragegang.mcp.api.dto.ToolInvokeModels.ToolInvokeResponse;
import com.couragegang.mcp.integration.NotionApiClient;
import com.couragegang.mcp.integration.SecretsClient;
import com.couragegang.mcp.repo.InstallationRepository;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Singleton
public final class ToolInvokeService {

    private final InstallationRepository installations;
    private final SecretsClient secrets;
    private final NotionApiClient notion;

    public ToolInvokeService(
            InstallationRepository installations, SecretsClient secrets, NotionApiClient notion) {
        this.installations = installations;
        this.secrets = secrets;
        this.notion = notion;
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
            var merged = mergeConfig(detail.connectionConfig(), detail.credentialSecretRef());
            if ("notion".equals(connectorKey)) {
                return invokeNotion(toolName, merged, arguments);
            }
            return ToolInvokeResponse.failure("Вызов инструментов для «" + connectorKey + "» пока не реализован");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private ToolInvokeResponse invokeNotion(String toolName, Map<String, Object> config, Map<String, Object> arguments) {
        var token =
                NotionApiClient.tokenFrom(config)
                        .orElse(null);
        if (token == null) {
            return ToolInvokeResponse.failure("Не настроен integration_token для Notion");
        }
        var args = arguments != null ? arguments : Map.<String, Object>of();
        var content = stringArg(args, "content", "message", "text");
        var title = stringArg(args, "title");
        var query = stringArg(args, "query", "q");
        if (query == null || query.isBlank()) {
            query = content;
        }
        var normalized = toolName != null ? toolName.toLowerCase(Locale.ROOT) : "";
        try {
            if (normalized.contains("write") || normalized.contains("create")) {
                var configured = config.get("default_database_id");
                var configuredStr = configured != null ? String.valueOf(configured) : null;
                var parent = notion.resolveWriteParent(token, configuredStr, title);
                var targetLabel = notion.describeWriteTarget(token, parent);
                var summary = notion.createPage(token, parent, title, content != null ? content : "");
                if (targetLabel != null && !targetLabel.isBlank()) {
                    summary = summary + "\nКуда записано: «" + targetLabel + "»";
                }
                return ToolInvokeResponse.success(summary);
            }
            if (normalized.contains("search")
                    || normalized.contains("read")
                    || normalized.contains("fetch")
                    || normalized.contains("query")) {
                var summary = notion.search(token, query != null ? query : "");
                return ToolInvokeResponse.success(summary);
            }
            return ToolInvokeResponse.failure("Неизвестный инструмент Notion: " + toolName);
        } catch (IllegalArgumentException e) {
            return ToolInvokeResponse.failure(e.getMessage());
        } catch (Exception e) {
            return ToolInvokeResponse.failure("Notion: " + e.getMessage());
        }
    }

    private Map<String, Object> mergeConfig(Map<String, Object> connectionConfig, String secretRef) {
        var merged = new HashMap<>(connectionConfig != null ? connectionConfig : Map.of());
        secrets.resolvePayload(secretRef).forEach(merged::putIfAbsent);
        return merged;
    }

    private static String stringArg(Map<String, Object> args, String... keys) {
        for (var key : keys) {
            var v = args.get(key);
            if (v != null) {
                var s = String.valueOf(v);
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }
}
