package com.couragegang.mcp.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public final class ToolInvokeModels {

    private ToolInvokeModels() {}

    @Serdeable
    public record ToolInvokeRequest(
            @NotBlank String connectorKey,
            @NotBlank String toolName,
            Map<String, Object> arguments) {}

    @Serdeable
    public record ToolInvokeResponse(boolean ok, String summary, String error) {
        public static ToolInvokeResponse success(String summary) {
            return new ToolInvokeResponse(true, summary, null);
        }

        public static ToolInvokeResponse failure(String error) {
            return new ToolInvokeResponse(false, null, error);
        }
    }
}
