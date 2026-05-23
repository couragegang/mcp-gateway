package com.couragegang.mcp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.couragegang.mcp.metrics.OutboundHttpMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotionHealthProbeTest {

    @Test
    void missingTokenReturnsFailure() {
        var probe = new NotionHealthProbe(new OutboundHttpMetrics(new SimpleMeterRegistry()));
        var result = probe.probe(Map.of());
        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("integration_token");
    }

    @Test
    void blankTokenReturnsFailure() {
        var probe = new NotionHealthProbe(new OutboundHttpMetrics(new SimpleMeterRegistry()));
        var result = probe.probe(Map.of("integration_token", "  "));
        assertThat(result.ok()).isFalse();
    }
}
