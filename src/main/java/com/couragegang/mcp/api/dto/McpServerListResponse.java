package com.couragegang.mcp.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record McpServerListResponse(List<McpServerResponse> items) {}
