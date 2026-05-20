package com.couragegang.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class ConnectionFormSplitter {

    private final ObjectMapper json;

    public ConnectionFormSplitter(ObjectMapper json) {
        this.json = json;
    }

    public SplitResult split(Map<String, Object> formSchema, Map<String, Object> form) {
        var secrets = new LinkedHashMap<String, String>();
        var config = new LinkedHashMap<String, Object>();
        var fieldRules = extractFieldRules(formSchema, json);
        for (var entry : form.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            if (value == null) {
                continue;
            }
            var storage = fieldRules.getOrDefault(key, guessStorage(key, value));
            if ("secret".equals(storage)) {
                secrets.put(key, String.valueOf(value));
            } else {
                config.put(key, value);
            }
        }
        return new SplitResult(secrets, config);
    }

    private static Map<String, String> extractFieldRules(Map<String, Object> formSchema, ObjectMapper json) {
        var rules = new HashMap<String, String>();
        if (formSchema == null || formSchema.isEmpty()) {
            return rules;
        }
        var node = json.valueToTree(formSchema);
        var fields = node.path("fields");
        if (!fields.isArray()) {
            return rules;
        }
        for (Iterator<JsonNode> it = fields.elements(); it.hasNext(); ) {
            var field = it.next();
            var key = field.path("key").asText(null);
            if (key == null || key.isBlank()) {
                continue;
            }
            var storage = field.path("storage").asText(null);
            if (storage == null || storage.isBlank()) {
                if (field.path("sensitive").asBoolean(false)) {
                    storage = "secret";
                } else {
                    storage = "config";
                }
            }
            rules.put(key, storage);
        }
        return rules;
    }

    private static String guessStorage(String key, Object value) {
        var lower = key.toLowerCase();
        if (lower.contains("token") || lower.contains("secret") || lower.contains("password")) {
            return "secret";
        }
        return "config";
    }

    public record SplitResult(Map<String, String> secrets, Map<String, Object> config) {}
}
