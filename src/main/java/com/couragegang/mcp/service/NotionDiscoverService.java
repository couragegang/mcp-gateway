package com.couragegang.mcp.service;

import com.couragegang.mcp.api.dto.NotionDiscoverModels.NotionDiscoverResponse;
import com.couragegang.mcp.api.dto.NotionDiscoverModels.NotionResourceItem;
import com.couragegang.mcp.integration.NotionApiClient;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public final class NotionDiscoverService {

    private final NotionApiClient notion;

    public NotionDiscoverService(NotionApiClient notion) {
        this.notion = notion;
    }

    public NotionDiscoverResponse discover(String integrationToken) {
        try {
            var items = notion.listWritableTargets(integrationToken);
            return new NotionDiscoverResponse(items.stream().map(this::toItem).toList());
        } catch (Exception e) {
            throw new IllegalStateException("Notion discover failed: " + e.getMessage(), e);
        }
    }

    private NotionResourceItem toItem(NotionApiClient.NotionTarget t) {
        return new NotionResourceItem(t.id(), t.title(), t.url(), t.kind());
    }
}
