package com.couragegang.mcp.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthInfoControllerTest {

    @Test
    void rootContainsMcpPaths() {
        var map = new HealthInfoController().root();
        assertThat(map.get("service")).contains("mcp");
    }
}
