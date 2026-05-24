package com.couragegang.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.couragegang.mcp.integration.ConnectorRuntimeClient;
import com.couragegang.mcp.metrics.OutboundHttpMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ConnectorRuntimeClientTest {

    @Test
    void resolveRuntimeUsesCatalogUrlWhenPresent() {
        var client = new ConnectorRuntimeClient(
                "key", "http://fallback:8091/v1/notion", new ObjectMapper(), mock(OutboundHttpMetrics.class));
        assertThat(client.resolveRuntimeBaseUrl("notion", "http://custom:8091/v1/notion"))
                .isEqualTo("http://custom:8091/v1/notion");
    }

    @Test
    void resolveRuntimeUsesFallbackForNotion() {
        var client = new ConnectorRuntimeClient(
                "key", "http://fallback:8091/v1/notion", new ObjectMapper(), mock(OutboundHttpMetrics.class));
        assertThat(client.resolveRuntimeBaseUrl("notion", null)).isEqualTo("http://fallback:8091/v1/notion");
    }

    @Test
    void resolveRuntimeUnknownConnector() {
        var client = new ConnectorRuntimeClient(
                "key", "http://fallback:8091/v1/notion", new ObjectMapper(), mock(OutboundHttpMetrics.class));
        assertThat(client.resolveRuntimeBaseUrl("slack", null)).isNull();
    }
}
