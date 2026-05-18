package com.couragegang.mcp.api;

import com.couragegang.mcp.api.dto.McpServerListResponse;
import com.couragegang.mcp.api.dto.McpServerResponse;
import com.couragegang.mcp.api.dto.RegisterMcpServerRequest;
import com.couragegang.mcp.service.McpServerRegistry;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import java.util.UUID;

@Controller("/servers")
@Validated
public class McpServersController {

    private final McpServerRegistry registry;

    public McpServersController(McpServerRegistry registry) {
        this.registry = registry;
    }

    @Get
    public McpServerListResponse list() {
        return new McpServerListResponse(registry.list());
    }

    @Get("/{id}")
    public McpServerResponse get(@PathVariable UUID id) {
        return registry.find(id)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "server not found"));
    }

    @Post
    @Status(HttpStatus.CREATED)
    public McpServerResponse register(@Body @Valid RegisterMcpServerRequest request) {
        return registry.register(request);
    }

    @Post("/{id}/health")
    public McpServerResponse probe(@PathVariable UUID id) {
        var server = registry.find(id)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "server not found"));
        // MVP: real MCP health probe (Notion spike) comes later.
        return new McpServerResponse(
                server.id(),
                server.name(),
                server.transport(),
                server.baseUrl(),
                "probe_pending"
        );
    }
}
