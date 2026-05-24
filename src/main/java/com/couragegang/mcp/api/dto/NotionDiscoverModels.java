package com.couragegang.mcp.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class NotionDiscoverModels {

    private NotionDiscoverModels() {}

    @Serdeable
    public record NotionDiscoverRequest(@NotBlank String integrationToken) {}

    @Serdeable
    public record NotionResourceItem(String id, String title, String url, String kind) {}

    @Serdeable
    public record NotionDiscoverResponse(List<NotionResourceItem> items) {}
}
