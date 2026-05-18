package com.couragegang.mcp.config;

import com.couragegang.mcp.service.McpServerRegistry;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

@Singleton
public final class StartupListener {

    private final McpServerRegistry registry;

    public StartupListener(McpServerRegistry registry) {
        this.registry = registry;
    }

    @EventListener
    public void onStartup(StartupEvent event) {
        registry.seedNotionPlaceholder();
    }
}
