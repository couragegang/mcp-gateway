package com.couragegang.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectionFormSplitterTest {

    private final ConnectionFormSplitter splitter = new ConnectionFormSplitter(new ObjectMapper());

    @Test
    void splitsSecretAndConfigFields() {
        Map<String, Object> schema =
                Map.of(
                        "fields",
                        List.of(
                                Map.of("key", "integration_token", "storage", "secret"),
                                Map.of("key", "default_database_id", "storage", "config")));
        Map<String, Object> form =
                Map.of(
                        "integration_token", (Object) "tok",
                        "default_database_id", (Object) "db-1");
        var split = splitter.split(schema, form);
        assertThat(split.secrets()).containsEntry("integration_token", "tok");
        assertThat(split.config()).containsEntry("default_database_id", "db-1");
        assertThat(split.config()).doesNotContainKey("integration_token");
    }

    @Test
    void skipsNullFormValues() {
        var form = new HashMap<String, Object>();
        form.put("integration_token", null);
        form.put("name", "x");
        Map<String, Object> schema = Map.of("fields", List.of(Map.of("key", "name", "storage", "config")));

        var split = splitter.split(schema, form);

        assertThat(split.secrets()).isEmpty();
        assertThat(split.config()).containsEntry("name", "x");
    }

    @Test
    void emptySchemaGuessesSecretByKeyName() {
        var split = splitter.split(Map.of(), Map.of("api_token", "abc"));

        assertThat(split.secrets()).containsEntry("api_token", "abc");
    }

    @Test
    void sensitiveFlagMarksSecret() {
        Map<String, Object> schema =
                Map.of("fields", List.of(Map.of("key", "api_key", "sensitive", true)));
        var split = splitter.split(schema, Map.of("api_key", "k"));

        assertThat(split.secrets()).containsEntry("api_key", "k");
    }

    @Test
    void explicitConfigStorage() {
        Map<String, Object> schema =
                Map.of("fields", List.of(Map.of("key", "region", "storage", "config")));
        var split = splitter.split(schema, Map.of("region", "eu"));

        assertThat(split.config()).containsEntry("region", "eu");
        assertThat(split.secrets()).isEmpty();
    }

    @Test
    void skipsBlankFieldKeysInSchema() {
        Map<String, Object> schema =
                Map.of(
                        "fields",
                        List.of(
                                Map.of("key", "", "storage", "secret"),
                                Map.of("key", "name", "storage", "config")));
        var split = splitter.split(schema, Map.of("name", "x", "ignored", "y"));

        assertThat(split.config()).containsEntry("name", "x");
        assertThat(split.secrets()).isEmpty();
    }

    @Test
    void schemaWithoutFieldsArrayUsesGuess() {
        var split = splitter.split(Map.of("title", "x"), Map.of("user_password", "p"));

        assertThat(split.secrets()).containsEntry("user_password", "p");
    }
}
