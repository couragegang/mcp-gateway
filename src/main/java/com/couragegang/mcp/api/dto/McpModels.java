package com.couragegang.mcp.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class McpModels {

    private McpModels() {}

    @Serdeable
    public record CatalogTool(
            String connectorKey,
            String displayName,
            String description,
            Map<String, Object> connectionFormSchema,
            String policyPackVersion) {}

    @Serdeable
    public record CatalogListResponse(List<CatalogTool> items) {}

    @Serdeable
    public record Installation(
            UUID id,
            UUID workspaceId,
            String connectorKey,
            String displayLabel,
            String status) {}

    @Serdeable
    public record InstallationCreateRequest(
            @NotBlank String connectorKey,
            String displayLabel,
            @NotNull Map<String, Object> form) {}

    @Serdeable
    public record InstallationListResponse(List<Installation> items) {}

    @Serdeable
    public record HealthCheckResult(boolean ok, String message) {}

    @Serdeable
    public record ErrorBody(String code, String message) {
        public static ErrorBody of(String code, String message) {
            return new ErrorBody(code, message);
        }
    }
}
