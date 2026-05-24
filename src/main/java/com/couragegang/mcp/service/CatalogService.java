package com.couragegang.mcp.service;

import com.couragegang.mcp.api.dto.McpModels.CatalogListResponse;
import com.couragegang.mcp.api.dto.McpModels.CatalogTool;
import com.couragegang.mcp.error.McpApiException;
import com.couragegang.mcp.repo.CatalogRepository;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import java.sql.SQLException;

@Singleton
public final class CatalogService {

    private final CatalogRepository catalog;

    public CatalogService(CatalogRepository catalog) {
        this.catalog = catalog;
    }

    public CatalogListResponse list() {
        try {
            var items = catalog.listPublished().stream()
                    .map(r -> new CatalogTool(
                            r.connectorKey(),
                            r.displayName(),
                            r.description(),
                            r.connectionFormSchema(),
                            String.valueOf(r.policyPackVersion()),
                            r.policyTemplatePack()))
                    .toList();
            return new CatalogListResponse(items);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public CatalogTool get(String connectorKey) {
        try {
            var row = catalog.findPublished(connectorKey).orElseThrow(this::notFound);
            return new CatalogTool(
                    row.connectorKey(),
                    row.displayName(),
                    row.description(),
                    row.connectionFormSchema(),
                    String.valueOf(row.policyPackVersion()),
                    row.policyTemplatePack());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private McpApiException notFound() {
        return new McpApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "connector not found");
    }
}
