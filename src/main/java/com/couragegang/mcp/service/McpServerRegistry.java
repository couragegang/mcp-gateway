package com.couragegang.mcp.service;

import com.couragegang.mcp.api.dto.McpServerResponse;
import com.couragegang.mcp.api.dto.RegisterMcpServerRequest;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry until PostgreSQL persistence is added.
 */
@Singleton
public final class McpServerRegistry {

    private final Map<UUID, McpServerResponse> servers = new ConcurrentHashMap<>();

    public List<McpServerResponse> list() {
        return new ArrayList<>(servers.values());
    }

    public McpServerResponse register(RegisterMcpServerRequest request) {
        var id = UUID.randomUUID();
        var entry = new McpServerResponse(
                id,
                request.name(),
                request.transport(),
                request.baseUrl(),
                "registered"
        );
        servers.put(id, entry);
        return entry;
    }

    public Optional<McpServerResponse> find(UUID id) {
        return Optional.ofNullable(servers.get(id));
    }

    public void seedNotionPlaceholder() {
        if (servers.values().stream().anyMatch(s -> "notion".equalsIgnoreCase(s.name()))) {
            return;
        }
        register(new RegisterMcpServerRequest(
                "notion",
                "stdio",
                null
        ));
    }
}
