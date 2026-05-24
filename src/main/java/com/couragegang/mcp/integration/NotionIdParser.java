package com.couragegang.mcp.integration;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

/** Извлекает UUID страницы/базы из ссылки Notion или сырого идентификатора. */
public final class NotionIdParser {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

    private static final Pattern HEX32 = Pattern.compile("[0-9a-f]{32}", Pattern.CASE_INSENSITIVE);

    private NotionIdParser() {}

    public static Optional<String> parseId(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        var trimmed = input.strip();
        if (UUID_PATTERN.matcher(trimmed).matches()) {
            return Optional.of(trimmed.toLowerCase());
        }
        var noDashes = trimmed.replace("-", "");
        if (noDashes.length() == 32 && HEX32.matcher(noDashes).matches()) {
            return Optional.of(toUuid(noDashes));
        }
        if (trimmed.contains("notion.so") || trimmed.contains("notion.site")) {
            return parseFromNotionUrl(trimmed);
        }
        var matcher = HEX32.matcher(trimmed.replace("-", ""));
        if (matcher.find()) {
            return Optional.of(toUuid(matcher.group()));
        }
        return Optional.empty();
    }

    private static Optional<String> parseFromNotionUrl(String url) {
        try {
            var uri = url.startsWith("http") ? URI.create(url) : URI.create("https://" + url);
            var path = uri.getPath();
            if (path == null || path.isBlank()) {
                return Optional.empty();
            }
            var segment = path.substring(path.lastIndexOf('/') + 1);
            var dash = segment.lastIndexOf('-');
            if (dash >= 0 && dash < segment.length() - 1) {
                var candidate = segment.substring(dash + 1).replace("-", "");
                if (candidate.length() == 32 && HEX32.matcher(candidate).matches()) {
                    return Optional.of(toUuid(candidate));
                }
            }
            var compact = segment.replace("-", "");
            if (compact.length() == 32 && HEX32.matcher(compact).matches()) {
                return Optional.of(toUuid(compact));
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Optional.empty();
    }

    private static String toUuid(String hex32) {
        var h = hex32.toLowerCase();
        return h.substring(0, 8)
                + "-"
                + h.substring(8, 12)
                + "-"
                + h.substring(12, 16)
                + "-"
                + h.substring(16, 20)
                + "-"
                + h.substring(20, 32);
    }
}
