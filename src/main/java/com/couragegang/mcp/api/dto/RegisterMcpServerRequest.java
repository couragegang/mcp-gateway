package com.couragegang.mcp.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record RegisterMcpServerRequest(
        @NotBlank String name,
        @NotBlank String transport,
        String baseUrl
) {}
