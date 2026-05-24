package com.couragegang.mcp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotionIdParserTest {

    @Test
    void parsesUuid() {
        var id = "2eaf1b3c-1234-5678-9abc-def012345678";
        assertThat(NotionIdParser.parseId(id)).contains(id.toLowerCase());
    }

    @Test
    void parsesNotionUrl() {
        var url = "https://www.notion.so/My-Workspace/My-Database-2eaf1b3c1234567890abcdef12345678";
        var parsed = NotionIdParser.parseId(url);
        assertThat(parsed).isPresent();
        assertThat(parsed.get()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void emptyForGarbage() {
        assertThat(NotionIdParser.parseId("my database name")).isEmpty();
    }
}
