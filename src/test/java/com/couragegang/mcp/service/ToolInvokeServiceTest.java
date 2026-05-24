package com.couragegang.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.couragegang.mcp.integration.NotionApiClient;
import com.couragegang.mcp.integration.NotionApiClient.DatabaseParent;
import com.couragegang.mcp.integration.SecretsClient;
import com.couragegang.mcp.repo.InstallationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolInvokeServiceTest {

    @Mock
    InstallationRepository installations;

    @Mock
    SecretsClient secrets;

    @Mock
    NotionApiClient notion;

    ToolInvokeService svc;
    UUID wsId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new ToolInvokeService(installations, secrets, notion);
    }

    @Test
    void failsWhenConnectorNotInstalled() throws Exception {
        when(installations.listByWorkspace(wsId)).thenReturn(List.of());
        var res = svc.invoke(wsId, "notion", "notion_write_page", Map.of());
        assertThat(res.ok()).isFalse();
        assertThat(res.error()).contains("не установлен");
    }

    @Test
    void skipsRevokedInstallation() throws Exception {
        var id = UUID.randomUUID();
        when(installations.listByWorkspace(wsId))
                .thenReturn(List.of(new InstallationRepository.InstallationRow(
                        id, wsId, "notion", "N", "revoked", Instant.now())));
        var res = svc.invoke(wsId, "notion", "write", Map.of());
        assertThat(res.ok()).isFalse();
    }

    @Test
    void failsWhenInstallationInactive() throws Exception {
        var id = UUID.randomUUID();
        when(installations.listByWorkspace(wsId))
                .thenReturn(List.of(new InstallationRepository.InstallationRow(
                        id, wsId, "notion", "N", "pending", Instant.now())));
        when(installations.findDetail(wsId, id))
                .thenReturn(Optional.of(detail(id, "pending", Map.of())));
        var res = svc.invoke(wsId, "notion", "write", Map.of());
        assertThat(res.error()).contains("неактивна");
    }

    @Test
    void failsWhenNotionTokenMissing() throws Exception {
        stubActiveNotion(Map.of());
        var res = svc.invoke(wsId, "notion", "notion_write_page", Map.of("content", "hi"));
        assertThat(res.error()).contains("integration_token");
    }

    @Test
    void writesToNotion() throws Exception {
        stubActiveNotion(Map.of("integration_token", "tok"));
        when(notion.resolveWriteParent(eq("tok"), eq(null), eq("Title")))
                .thenReturn(new DatabaseParent("db-id"));
        when(notion.describeWriteTarget(eq("tok"), any())).thenReturn("Моя база");
        when(notion.createPage(eq("tok"), any(), eq("Title"), eq("body")))
                .thenReturn("Страница создана");
        var res = svc.invoke(
                wsId,
                "notion",
                "notion_write_page",
                Map.of("content", "body", "title", "Title"));
        assertThat(res.ok()).isTrue();
        assertThat(res.summary()).contains("Страница");
    }

    @Test
    void searchesNotion() throws Exception {
        stubActiveNotion(Map.of("integration_token", "tok"));
        when(notion.search("tok", "query")).thenReturn("found");
        var res = svc.invoke(wsId, "notion", "notion_search", Map.of("query", "query"));
        assertThat(res.ok()).isTrue();
        assertThat(res.summary()).isEqualTo("found");
    }

    @Test
    void unknownNotionTool() throws Exception {
        stubActiveNotion(Map.of("integration_token", "tok"));
        var res = svc.invoke(wsId, "notion", "notion_delete", Map.of());
        assertThat(res.error()).contains("Неизвестный инструмент");
    }

    @Test
    void unsupportedConnector() throws Exception {
        stubActiveInstallation("slack", "active", Map.of());
        var res = svc.invoke(wsId, "slack", "post", Map.of());
        assertThat(res.error()).contains("не реализован");
    }

    @Test
    void mergesSecretsIntoConfig() throws Exception {
        var id = UUID.randomUUID();
        when(installations.listByWorkspace(wsId))
                .thenReturn(List.of(new InstallationRepository.InstallationRow(
                        id, wsId, "notion", "N", "active", Instant.now())));
        when(installations.findDetail(wsId, id))
                .thenReturn(Optional.of(detail(id, "active", Map.of("default_database_id", "db"))));
        when(secrets.resolvePayload("ref")).thenReturn(Map.of("integration_token", "from-secret"));
        when(notion.resolveWriteParent(eq("from-secret"), eq("db"), any()))
                .thenReturn(new DatabaseParent("db"));
        when(notion.describeWriteTarget(any(), any())).thenReturn("DB");
        when(notion.createPage(any(), any(), any(), any())).thenReturn("ok");
        svc.invoke(wsId, "notion", "create_page", Map.of("text", "x"));
    }

    @Test
    void allowsErrorStatusInstallation() throws Exception {
        stubActiveInstallation("notion", "error", Map.of("integration_token", "tok"));
        when(notion.search("tok", "q")).thenReturn("ok");
        var res = svc.invoke(wsId, "notion", "notion_query", Map.of("query", "q"));
        assertThat(res.ok()).isTrue();
    }

    @Test
    void notionReadUsesSearch() throws Exception {
        stubActiveNotion(Map.of("integration_token", "tok"));
        when(notion.search("tok", "hello")).thenReturn("found");
        var res = svc.invoke(wsId, "notion", "notion_read", Map.of("message", "hello"));
        assertThat(res.summary()).isEqualTo("found");
    }

    @Test
    void notionWriteHandlesClientError() throws Exception {
        stubActiveNotion(Map.of("integration_token", "tok"));
        when(notion.resolveWriteParent(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("bad link"));
        var res = svc.invoke(wsId, "notion", "write_page", Map.of("content", "c"));
        assertThat(res.ok()).isFalse();
        assertThat(res.error()).contains("bad link");
    }

    @Test
    void invokeWrapsSqlException() throws Exception {
        when(installations.listByWorkspace(wsId)).thenThrow(new java.sql.SQLException("db"));

        assertThatThrownBy(() -> svc.invoke(wsId, "notion", "notion_search", Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void notionWriteAppendsTargetLabel() throws Exception {
        stubActiveNotion(Map.of("integration_token", "tok", "default_database_id", "db-id"));
        when(notion.resolveWriteParent(eq("tok"), eq("db-id"), eq("T")))
                .thenReturn(new DatabaseParent("db-id"));
        when(notion.describeWriteTarget(eq("tok"), any())).thenReturn("My DB");
        when(notion.createPage(eq("tok"), any(), eq("T"), eq(""))).thenReturn("Created");

        var res = svc.invoke(wsId, "notion", "notion_create_page", Map.of("title", "T"));

        assertThat(res.ok()).isTrue();
        assertThat(res.summary()).contains("Куда записано").contains("My DB");
    }

    @Test
    void notionSearchHandlesGenericException() throws Exception {
        stubActiveNotion(Map.of("integration_token", "tok"));
        when(notion.search(eq("tok"), any())).thenThrow(new RuntimeException("rate limit"));

        var res = svc.invoke(wsId, "notion", "notion_search", Map.of("query", "x"));

        assertThat(res.ok()).isFalse();
        assertThat(res.error()).contains("Notion").contains("rate limit");
    }

    @Test
    void notionWriteUsesQueryFallbackWhenContentMissing() throws Exception {
        stubActiveNotion(Map.of("integration_token", "tok"));
        when(notion.resolveWriteParent(eq("tok"), eq(null), eq(null)))
                .thenReturn(new DatabaseParent("db"));
        when(notion.describeWriteTarget(any(), any())).thenReturn(null);
        when(notion.createPage(any(), any(), eq(null), eq(""))).thenReturn("ok");

        var res = svc.invoke(wsId, "notion", "notion_write", Map.of("query", "fallback text"));

        assertThat(res.ok()).isTrue();
    }

    @Test
    void notionWriteSkipsTargetLabelWhenBlank() throws Exception {
        stubActiveNotion(Map.of("integration_token", "tok"));
        when(notion.resolveWriteParent(any(), any(), any())).thenReturn(new DatabaseParent("db"));
        when(notion.describeWriteTarget(any(), any())).thenReturn("  ");
        when(notion.createPage(any(), any(), any(), any())).thenReturn("Created");

        var res = svc.invoke(wsId, "notion", "notion_write_page", Map.of("content", "body"));

        assertThat(res.ok()).isTrue();
        assertThat(res.summary()).isEqualTo("Created");
    }

    @Test
    void skipsWhenFindDetailReturnsEmpty() throws Exception {
        var id = UUID.randomUUID();
        when(installations.listByWorkspace(wsId))
                .thenReturn(List.of(new InstallationRepository.InstallationRow(
                        id, wsId, "notion", "N", "active", Instant.now())));
        when(installations.findDetail(wsId, id)).thenReturn(Optional.empty());

        var res = svc.invoke(wsId, "notion", "notion_search", Map.of());

        assertThat(res.ok()).isFalse();
        assertThat(res.error()).contains("не установлен");
    }

    private void stubActiveNotion(Map<String, Object> config) throws Exception {
        stubActiveInstallation("notion", "active", config);
    }

    private void stubActiveInstallation(String connector, String status, Map<String, Object> config)
            throws Exception {
        var id = UUID.randomUUID();
        when(installations.listByWorkspace(wsId))
                .thenReturn(List.of(new InstallationRepository.InstallationRow(
                        id, wsId, connector, "L", status, Instant.now())));
        when(installations.findDetail(wsId, id)).thenReturn(Optional.of(detail(id, status, config)));
    }

    private InstallationRepository.InstallationDetailRow detail(
            UUID id, String status, Map<String, Object> config) {
        return new InstallationRepository.InstallationDetailRow(
                id, UUID.randomUUID(), wsId, "notion", "L", status, config, "ref");
    }
}
