package com.couragegang.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectionFormSplitterTest {

    private final ConnectionFormSplitter splitter = new ConnectionFormSplitter(new ObjectMapper());

    @Test
    void splitsSecretAndConfigFields() {
        var schema =
                Map.of(
                        "fields",
                        java.util.List.of(
                                Map.of("key", "integration_token", "storage", "secret"),
                                Map.of("key", "default_database_id", "storage", "config")));
        var form =
                Map.of(
                        "integration_token", "tok",
                        "default_database_id", "db-1");
        var split = splitter.split(schema, form);
        assertThat(split.secrets()).containsEntry("integration_token", "tok");
        assertThat(split.config()).containsEntry("default_database_id", "db-1");
        assertThat(split.config()).doesNotContainKey("integration_token");
    }
}
