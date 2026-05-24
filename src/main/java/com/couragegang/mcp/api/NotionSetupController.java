package com.couragegang.mcp.api;

import com.couragegang.mcp.api.dto.NotionDiscoverModels.NotionDiscoverRequest;
import com.couragegang.mcp.api.dto.NotionDiscoverModels.NotionDiscoverResponse;
import com.couragegang.mcp.service.NotionDiscoverService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import java.util.UUID;

@Controller
@Validated
public class NotionSetupController {

    private final NotionDiscoverService discover;

    public NotionSetupController(NotionDiscoverService discover) {
        this.discover = discover;
    }

    @Post("/workspaces/{workspaceId}/notion/discover")
    public NotionDiscoverResponse discover(
            @PathVariable UUID workspaceId, @Body @Valid NotionDiscoverRequest body) {
        return discover.discover(body.integrationToken());
    }
}
