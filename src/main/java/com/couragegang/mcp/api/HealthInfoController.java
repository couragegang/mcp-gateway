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
                "metrics", "/v1/mcp/metrics",
                "servers", "/v1/mcp/servers"
        );
    }
}
