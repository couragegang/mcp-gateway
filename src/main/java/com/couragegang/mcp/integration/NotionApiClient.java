package com.couragegang.mcp.integration;

import com.couragegang.mcp.metrics.OutboundHttpMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Singleton
public final class NotionApiClient {

    private final HttpClient http;
    private final OutboundHttpMetrics metrics;
    private final ObjectMapper json;

    public NotionApiClient(OutboundHttpMetrics metrics, ObjectMapper json) {
        this.metrics = metrics;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public record NotionTarget(String id, String title, String url, String kind) {}

    public sealed interface WriteParent permits DatabaseParent, PageParent {
        String id();
    }

    public record DatabaseParent(String id) implements WriteParent {}

    public record PageParent(String id) implements WriteParent {}

    public List<NotionTarget> listWritableTargets(String token) throws Exception {
        var body = json.createObjectNode();
        body.put("page_size", 50);
        var filter = body.putObject("filter");
        filter.put("value", "database");
        filter.put("property", "object");
        var response = post(token, "https://api.notion.com/v1/search", json.writeValueAsString(body));
        var items = new ArrayList<NotionTarget>();
        for (var node : response.path("results")) {
            if (!node.path("object").asText().equals("database")) {
                continue;
            }
            items.add(
                    new NotionTarget(
                            node.path("id").asText(),
                            extractDatabaseTitle(node),
                            node.path("url").asText(""),
                            "database"));
        }
        items.sort(Comparator.comparing(NotionTarget::title, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    public WriteParent resolveWriteParent(String token, String configuredRaw, String nameHint) throws Exception {
        if (configuredRaw != null && !configuredRaw.isBlank()) {
            var parsed = NotionIdParser.parseId(configuredRaw);
            if (parsed.isPresent()) {
                var id = parsed.get();
                if (isDatabase(token, id)) {
                    return new DatabaseParent(id);
                }
                if (isPage(token, id)) {
                    return new PageParent(id);
                }
                throw new IllegalArgumentException(
                        "Не удалось открыть базу или страницу Notion по ссылке — проверьте доступ integration");
            }
            throw new IllegalArgumentException(
                    "Не удалось извлечь ID из ссылки Notion. Вставьте ссылку «Copy link» на базу или страницу");
        }
        var targets = listWritableTargets(token);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "Нет доступных баз. В Notion откройте базу → … → Connections → добавьте integration");
        }
        if (targets.size() == 1) {
            return new DatabaseParent(targets.getFirst().id());
        }
        var hint = nameHint != null ? nameHint.toLowerCase() : "";
        for (var t : targets) {
            if (!hint.isBlank() && t.title().toLowerCase().contains(hint)) {
                return new DatabaseParent(t.id());
            }
        }
        return new DatabaseParent(targets.getFirst().id());
    }

    public String describeWriteTarget(String token, WriteParent parent) throws Exception {
        if (parent instanceof DatabaseParent db) {
            return extractDatabaseTitle(get(token, "https://api.notion.com/v1/databases/" + db.id()));
        }
        var page = get(token, "https://api.notion.com/v1/pages/" + ((PageParent) parent).id());
        return extractTitle(page);
    }

    public String createPage(String token, WriteParent parent, String title, String content) throws Exception {
        if (parent instanceof DatabaseParent db) {
            return createPageInDatabase(token, db.id(), title, content);
        }
        return createPageUnderPage(token, ((PageParent) parent).id(), title, content);
    }

    public String createPageInDatabase(String token, String databaseId, String title, String content) throws Exception {
        var dbId =
                NotionIdParser.parseId(databaseId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Некорректный ID или ссылка на базу Notion"));
        var titleKey = resolveTitlePropertyName(token, dbId);
        var body = json.createObjectNode();
        body.set("parent", json.createObjectNode().put("database_id", dbId));
        var titleArr = body.putObject("properties").putObject(titleKey).putArray("title");
        titleArr.addObject().putObject("text").put("content", title != null && !title.isBlank() ? title : "Chat note");
        if (content != null && !content.isBlank()) {
            var children = body.putArray("children");
            for (var chunk : splitContent(content, 1800)) {
                var block = children.addObject();
                block.put("object", "block");
                block.put("type", "paragraph");
                block.putObject("paragraph").set("rich_text", richText(chunk));
            }
        }
        var response = post(token, "https://api.notion.com/v1/pages", json.writeValueAsString(body));
        var url = response.path("url").asText(null);
        return formatCreated(url);
    }

    public String createPageUnderPage(String token, String pageId, String title, String content) throws Exception {
        var pid =
                NotionIdParser.parseId(pageId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Некорректный ID или ссылка на страницу Notion"));
        var body = json.createObjectNode();
        body.set("parent", json.createObjectNode().put("page_id", pid));
        var titleArr = body.putObject("properties").putObject("title").putArray("title");
        titleArr.addObject().putObject("text").put("content", title != null && !title.isBlank() ? title : "Chat note");
        if (content != null && !content.isBlank()) {
            var children = body.putArray("children");
            for (var chunk : splitContent(content, 1800)) {
                var block = children.addObject();
                block.put("object", "block");
                block.put("type", "paragraph");
                block.putObject("paragraph").set("rich_text", richText(chunk));
            }
        }
        var response = post(token, "https://api.notion.com/v1/pages", json.writeValueAsString(body));
        return formatCreated(response.path("url").asText(null));
    }

    private static String formatCreated(String url) {
        return url != null && !url.isBlank()
                ? "Страница создана в Notion: " + url
                : "Страница создана в Notion.";
    }

    public String search(String token, String query) throws Exception {
        var body = json.createObjectNode();
        body.put("query", query != null ? query : "");
        body.put("page_size", 5);
        var response = post(token, "https://api.notion.com/v1/search", json.writeValueAsString(body));
        var results = response.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return "В Notion ничего не найдено по запросу «" + query + "».";
        }
        var lines = new ArrayList<String>();
        for (var node : results) {
            var title = extractTitle(node);
            var url = node.path("url").asText("");
            lines.add("- " + title + (url.isBlank() ? "" : " (" + url + ")"));
        }
        return "Найдено в Notion:\n" + String.join("\n", lines);
    }

    private JsonNode post(String token, String url, String body) throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + token)
                        .header("Notion-Version", "2022-06-28")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
        var response = metrics.send(http, request, "notion", "api");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Notion API HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }
        return json.readTree(response.body());
    }

    private boolean isDatabase(String token, String id) {
        try {
            get(token, "https://api.notion.com/v1/databases/" + id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPage(String token, String id) {
        try {
            get(token, "https://api.notion.com/v1/pages/" + id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String extractDatabaseTitle(JsonNode database) {
        var titleArr = database.path("title");
        if (titleArr.isArray() && !titleArr.isEmpty()) {
            return titleArr.get(0).path("plain_text").asText("База");
        }
        return "База Notion";
    }

    private static String extractTitle(JsonNode page) {
        var props = page.path("properties");
        if (props.isObject()) {
            var it = props.fields();
            while (it.hasNext()) {
                var e = it.next();
                var titleNode = e.getValue().path("title");
                if (titleNode.isArray() && !titleNode.isEmpty()) {
                    return titleNode.get(0).path("plain_text").asText(e.getKey());
                }
            }
        }
        return page.path("id").asText("page");
    }

    private ArrayNode richText(String text) {
        var arr = json.createArrayNode();
        arr.addObject().putObject("text").put("content", text);
        return arr;
    }

    private String resolveTitlePropertyName(String token, String databaseId) throws Exception {
        var response = get(token, "https://api.notion.com/v1/databases/" + databaseId);
        var props = response.path("properties");
        if (props.isObject()) {
            var it = props.fields();
            while (it.hasNext()) {
                var e = it.next();
                if ("title".equals(e.getValue().path("type").asText())) {
                    return e.getKey();
                }
            }
        }
        return "Name";
    }

    private JsonNode get(String token, String url) throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + token)
                        .header("Notion-Version", "2022-06-28")
                        .GET()
                        .build();
        var response = metrics.send(http, request, "notion", "api_get");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Notion API HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }
        return json.readTree(response.body());
    }

    private static List<String> splitContent(String content, int maxLen) {
        var lines = new ArrayList<String>();
        var rest = content;
        while (rest.length() > maxLen) {
            lines.add(rest.substring(0, maxLen));
            rest = rest.substring(maxLen);
        }
        if (!rest.isBlank()) {
            lines.add(rest);
        }
        return lines;
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() > 400 ? s.substring(0, 400) + "…" : s);
    }

    public static Optional<String> tokenFrom(Map<String, Object> config) {
        var v = config.get("integration_token");
        if (v == null) {
            return Optional.empty();
        }
        var s = String.valueOf(v);
        return s.isBlank() ? Optional.empty() : Optional.of(s);
    }
}
