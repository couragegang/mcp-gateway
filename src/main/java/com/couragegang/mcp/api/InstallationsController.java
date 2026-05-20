package com.couragegang.mcp.api;

import com.couragegang.mcp.api.dto.McpModels.HealthCheckResult;
import com.couragegang.mcp.api.dto.McpModels.Installation;
import com.couragegang.mcp.api.dto.McpModels.InstallationCreateRequest;
import com.couragegang.mcp.api.dto.McpModels.InstallationListResponse;
import com.couragegang.mcp.error.McpApiException;
import com.couragegang.mcp.service.InstallationService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import java.util.UUID;

@Controller
public final class InstallationsController {

    private final InstallationService installations;

    public InstallationsController(InstallationService installations) {
        this.installations = installations;
    }

    @Get("/workspaces/{workspaceId}/installations")
    public HttpResponse<InstallationListResponse> list(@PathVariable UUID workspaceId) {
        return HttpResponse.ok(installations.list(workspaceId));
    }

    @Post("/workspaces/{workspaceId}/installations")
    public HttpResponse<Installation> create(
            @PathVariable UUID workspaceId,
            @Header("X-Org-Id") @Nullable String orgIdHeader,
            @Header("X-User-Id") @Nullable String userIdHeader,
            @Body @Valid InstallationCreateRequest body) {
        var orgId = parseUuid(orgIdHeader, "X-Org-Id");
        var userId = userIdHeader != null && !userIdHeader.isBlank() ? UUID.fromString(userIdHeader) : null;
        return HttpResponse.created(installations.create(orgId, workspaceId, body, userId));
    }

    @Delete("/workspaces/{workspaceId}/installations/{installationId}")
    public HttpResponse<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID installationId) {
        installations.delete(workspaceId, installationId);
        return HttpResponse.noContent();
    }

    @Post("/workspaces/{workspaceId}/installations/{installationId}/health")
    public HttpResponse<HealthCheckResult> health(
            @PathVariable UUID workspaceId, @PathVariable UUID installationId) {
        return HttpResponse.ok(installations.health(workspaceId, installationId));
    }

    private static UUID parseUuid(@Nullable String raw, String headerName) {
        if (raw == null || raw.isBlank()) {
            throw new McpApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", headerName + " required");
        }
        return UUID.fromString(raw.trim());
    }
}
