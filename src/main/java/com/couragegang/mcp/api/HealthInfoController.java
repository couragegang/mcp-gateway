package com.couragegang.mcp.api;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.Map;

@Controller
public final class HealthInfoController {

    @Get("/")
    public Map<String, String> root() {
        return Map.of(
                "service", "mcp-gateway",
                "health", "/v1/mcp/health",
                "metrics", "/v1/mcp/prometheus",
                "catalog", "/v1/mcp/catalog",
                "installations", "/v1/mcp/workspaces/{workspaceId}/installations");
    }
}
