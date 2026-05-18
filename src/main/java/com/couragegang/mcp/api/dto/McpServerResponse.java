package com.couragegang.mcp.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

@Serdeable
public record McpServerResponse(
        UUID id,
        String name,
        String transport,
        String baseUrl,
        String status
) {}
