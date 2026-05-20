package com.couragegang.mcp.api;

import com.couragegang.mcp.api.dto.McpModels.CatalogListResponse;
import com.couragegang.mcp.api.dto.McpModels.CatalogTool;
import com.couragegang.mcp.service.CatalogService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;

@Controller
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @Get("/catalog")
    public HttpResponse<CatalogListResponse> list() {
        return HttpResponse.ok(catalog.list());
    }

    @Get("/catalog/{connectorKey}")
    public HttpResponse<CatalogTool> get(@PathVariable String connectorKey) {
        return HttpResponse.ok(catalog.get(connectorKey));
    }
}
