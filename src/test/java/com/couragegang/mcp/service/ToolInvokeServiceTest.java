package com.couragegang.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.mcp.api.dto.ToolInvokeModels.ToolInvokeResponse;
import com.couragegang.mcp.integration.ConnectorRuntimeClient;
import com.couragegang.mcp.integration.SecretsClient;
import com.couragegang.mcp.repo.CatalogRepository;
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
    CatalogRepository catalog;

    @Mock
    SecretsClient secrets;

    @Mock
    ConnectorRuntimeClient connectorRuntime;

    ToolInvokeService svc;
    UUID wsId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new ToolInvokeService(installations, catalog, secrets, connectorRuntime);
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
    void delegatesToConnectorRuntime() throws Exception {
        stubActiveNotion(Map.of("integration_token", "tok"));
        when(catalog.findPublished("notion"))
                .thenReturn(
                        Optional.of(
                                new CatalogRepository.CatalogRow(
                                        "notion",
                                        "Notion",
                                        "d",
                                        Map.of(),
                                        1,
                                        Map.of(),
                                        "http://mcp-notion:8091/v1/notion",
                                        "http")));
        when(connectorRuntime.resolveRuntimeBaseUrl("notion", "http://mcp-notion:8091/v1/notion"))
                .thenReturn("http://mcp-notion:8091/v1/notion");
        when(connectorRuntime.invokeTool(
                        eq("http://mcp-notion:8091/v1/notion"),
                        any(),
                        eq(wsId),
                        any(),
                        any(),
                        any(),
                        eq("notion_write_page"),
                        any()))
                .thenReturn(ToolInvokeResponse.success("done"));
        var res = svc.invoke(wsId, "notion", "notion_write_page", Map.of("content", "hi"));
        assertThat(res.ok()).isTrue();
        assertThat(res.summary()).isEqualTo("done");
    }

    @Test
    void unsupportedConnectorWithoutRuntime() throws Exception {
        stubActiveInstallation("slack", "active", Map.of());
        when(catalog.findPublished("slack"))
                .thenReturn(
                        Optional.of(
                                new CatalogRepository.CatalogRow(
                                        "slack", "Slack", "d", Map.of(), 1, Map.of(), null, "http")));
        when(connectorRuntime.resolveRuntimeBaseUrl("slack", null)).thenReturn(null);
        var res = svc.invoke(wsId, "slack", "post", Map.of());
        assertThat(res.error()).contains("не реализован");
    }

    @Test
    void allowsErrorStatusInstallation() throws Exception {
        stubActiveInstallation("notion", "error", Map.of("integration_token", "tok"));
        when(catalog.findPublished("notion"))
                .thenReturn(
                        Optional.of(
                                new CatalogRepository.CatalogRow(
                                        "notion",
                                        "Notion",
                                        "d",
                                        Map.of(),
                                        1,
                                        Map.of(),
                                        "http://mcp-notion:8091/v1/notion",
                                        "http")));
        when(connectorRuntime.resolveRuntimeBaseUrl("notion", "http://mcp-notion:8091/v1/notion"))
                .thenReturn("http://mcp-notion:8091/v1/notion");
        when(connectorRuntime.invokeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ToolInvokeResponse.success("ok"));
        assertThat(svc.invoke(wsId, "notion", "notion_query", Map.of()).ok()).isTrue();
    }

    @Test
    void skipsWhenFindDetailReturnsEmpty() throws Exception {
        var id = UUID.randomUUID();
        when(installations.listByWorkspace(wsId))
                .thenReturn(List.of(new InstallationRepository.InstallationRow(
                        id, wsId, "notion", "N", "active", Instant.now())));
        when(installations.findDetail(wsId, id)).thenReturn(Optional.empty());
        var res = svc.invoke(wsId, "notion", "notion_search", Map.of());
        assertThat(res.error()).contains("не установлен");
    }

    @Test
    void mergesSecretsBeforeDelegate() throws Exception {
        var id = UUID.randomUUID();
        when(installations.listByWorkspace(wsId))
                .thenReturn(List.of(new InstallationRepository.InstallationRow(
                        id, wsId, "notion", "N", "active", Instant.now())));
        when(installations.findDetail(wsId, id))
                .thenReturn(Optional.of(detail(id, "active", Map.of("default_database_id", "db"))));
        when(secrets.resolvePayload("ref")).thenReturn(Map.of("integration_token", "from-secret"));
        when(catalog.findPublished("notion"))
                .thenReturn(
                        Optional.of(
                                new CatalogRepository.CatalogRow(
                                        "notion",
                                        "Notion",
                                        "d",
                                        Map.of(),
                                        1,
                                        Map.of(),
                                        "http://mcp-notion:8091/v1/notion",
                                        "http")));
        when(connectorRuntime.resolveRuntimeBaseUrl("notion", "http://mcp-notion:8091/v1/notion"))
                .thenReturn("http://mcp-notion:8091/v1/notion");
        when(connectorRuntime.invokeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ToolInvokeResponse.success("ok"));

        svc.invoke(wsId, "notion", "notion_write_page", Map.of("content", "x"));

        verify(connectorRuntime)
                .invokeTool(
                        eq("http://mcp-notion:8091/v1/notion"),
                        eq(id),
                        eq(wsId),
                        any(),
                        org.mockito.ArgumentMatchers.argThat(
                                cfg -> "from-secret".equals(((Map<?, ?>) cfg).get("integration_token"))),
                        eq("ref"),
                        eq("notion_write_page"),
                        any());
    }

    @Test
    void invokeWrapsSqlException() throws Exception {
        when(installations.listByWorkspace(wsId)).thenThrow(new java.sql.SQLException("db"));

        assertThatThrownBy(() -> svc.invoke(wsId, "notion", "notion_search", Map.of()))
                .isInstanceOf(IllegalStateException.class);
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
