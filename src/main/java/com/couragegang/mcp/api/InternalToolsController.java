package com.couragegang.mcp.api;

import com.couragegang.mcp.api.dto.ToolInvokeModels.ToolInvokeRequest;
import com.couragegang.mcp.api.dto.ToolInvokeModels.ToolInvokeResponse;
import com.couragegang.mcp.service.ToolInvokeService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;

@Controller("/internal")
@Validated
public class InternalToolsController {

    private final ToolInvokeService tools;

    public InternalToolsController(ToolInvokeService tools) {
        this.tools = tools;
    }

    @Post("/workspaces/{workspaceId}/tools/invoke")
    public ToolInvokeResponse invoke(
            @PathVariable UUID workspaceId, @Body @Valid ToolInvokeRequest request) {
        return tools.invoke(
                workspaceId,
                request.connectorKey(),
                request.toolName(),
                request.arguments() != null ? request.arguments() : Map.of());
    }
}
