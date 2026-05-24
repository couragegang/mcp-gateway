package com.couragegang.mcp.api;

import com.couragegang.mcp.api.dto.NotionDiscoverModels.NotionDiscoverRequest;
import com.couragegang.mcp.api.dto.NotionDiscoverModels.NotionDiscoverResponse;
import com.couragegang.mcp.error.McpApiException;
import com.couragegang.mcp.integration.ConnectorRuntimeClient;
import com.couragegang.mcp.repo.CatalogRepository;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import java.sql.SQLException;
import java.util.UUID;

@Controller
@Validated
public class NotionSetupController {

    private final CatalogRepository catalog;
    private final ConnectorRuntimeClient connectorRuntime;

    public NotionSetupController(CatalogRepository catalog, ConnectorRuntimeClient connectorRuntime) {
        this.catalog = catalog;
        this.connectorRuntime = connectorRuntime;
    }

    @Post("/workspaces/{workspaceId}/notion/discover")
    public NotionDiscoverResponse discover(
            @PathVariable UUID workspaceId, @Body @Valid NotionDiscoverRequest body) {
        try {
            var row = catalog.findPublished("notion").orElseThrow(() -> notFound("connector not found"));
            var runtimeUrl =
                    connectorRuntime.resolveRuntimeBaseUrl("notion", row.runtimeBaseUrl());
            if (runtimeUrl == null || runtimeUrl.isBlank()) {
                throw new McpApiException(HttpStatus.BAD_GATEWAY, "connector_unavailable", "notion runtime not configured");
            }
            return connectorRuntime.discover(runtimeUrl, body);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static McpApiException notFound(String msg) {
        return new McpApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", msg);
    }
}
