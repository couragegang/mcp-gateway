package com.couragegang.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.mcp.api.dto.McpModels.InstallationCreateRequest;
import com.couragegang.mcp.error.McpApiException;
import com.couragegang.mcp.integration.AuditClient;
import com.couragegang.mcp.integration.NotionHealthProbe;
import com.couragegang.mcp.integration.PolicyPackApplier;
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
class InstallationServiceTest {

    @Mock
    CatalogRepository catalog;

    @Mock
    InstallationRepository installations;

    @Mock
    ConnectionFormSplitter formSplitter;

    @Mock
    SecretsClient secrets;

    @Mock
    PolicyPackApplier policyPack;

    @Mock
    AuditClient audit;

    @Mock
    NotionHealthProbe notionProbe;

    InstallationService svc;

    UUID orgId = UUID.randomUUID();
    UUID wsId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new InstallationService(
                catalog, installations, formSplitter, secrets, policyPack, audit, notionProbe);
    }

    @Test
    void createInstallation() throws Exception {
        var schema = Map.<String, Object>of("fields", List.of());
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "Notion", "d", schema, 1, Map.of("rules", List.of()))));
        when(installations.listByWorkspace(wsId)).thenReturn(List.of());
        when(formSplitter.split(eq(schema), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(
                        Map.of("integration_token", "secret_x"), Map.of()));
        when(secrets.storeCredentials(any(), any(), eq("notion"), any()))
                .thenReturn(Optional.of("secrets:" + UUID.randomUUID()));
        var instId = UUID.randomUUID();
        when(installations.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(instId);
        when(policyPack.applyInstallPack(any(), any(), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(true);
        when(installations.findById(wsId, instId))
                .thenReturn(Optional.of(new InstallationRepository.InstallationRow(
                        instId, wsId, "notion", "Notion", "active", Instant.now())));

        var result =
                svc.create(
                        orgId,
                        wsId,
                        new InstallationCreateRequest("notion", "Notion", Map.of("integration_token", "secret_x")),
                        null);

        assertThat(result.connectorKey()).isEqualTo("notion");
    }

    @Test
    void createUsesLocalSecretWhenNoSecretsInForm() throws Exception {
        when(catalog.findPublished("slack"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "slack", "Slack", "d", Map.of(), 1, Map.of())));
        when(installations.listByWorkspace(wsId)).thenReturn(List.of());
        when(formSplitter.split(any(), any()))
                .thenReturn(new ConnectionFormSplitter.SplitResult(Map.of(), Map.of("x", 1)));
        var instId = UUID.randomUUID();
        when(installations.insert(any(), any(), any(), any(), eq("local:none"), any(), any()))
                .thenReturn(instId);
        when(policyPack.applyInstallPack(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(installations.findById(wsId, instId))
                .thenReturn(Optional.of(new InstallationRepository.InstallationRow(
                        instId, wsId, "slack", "slack", "active", Instant.now())));

        svc.create(orgId, wsId, new InstallationCreateRequest("slack", null, Map.of()), null);

        verify(secrets, org.mockito.Mockito.never()).storeCredentials(any(), any(), any(), any());
    }

    @Test
    void createConflictWhenAlreadyInstalled() throws Exception {
        when(catalog.findPublished("notion"))
                .thenReturn(Optional.of(new CatalogRepository.CatalogRow(
                        "notion", "N", "d", Map.of(), 1, Map.of())));
        when(installations.listByWorkspace(wsId))
                .thenReturn(List.of(new InstallationRepository.InstallationRow(
                        UUID.randomUUID(), wsId, "notion", "N", "active", Instant.now())));

        assertThatThrownBy(
                        () -> svc.create(orgId, wsId, new InstallationCreateRequest("notion", "N", Map.of()), null))
                .isInstanceOf(McpApiException.class);
    }

    @Test
    void listInstallations() throws Exception {
        var id = UUID.randomUUID();
        when(installations.listByWorkspace(wsId))
                .thenReturn(
                        List.of(
                                new InstallationRepository.InstallationRow(
                                        id, wsId, "notion", "Notion", "active", Instant.now())));

        var page = svc.list(wsId);

        assertThat(page.items()).hasSize(1);
    }

    @Test
    void deleteInstallation() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id))
                .thenReturn(
                        Optional.of(
                                new InstallationRepository.InstallationDetailRow(
                                        id, orgId, wsId, "notion", "Notion", "active", Map.of(), "secrets:ref")));

        svc.delete(wsId, id);

        verify(policyPack).revokeInstallPack(id);
        verify(secrets).revoke("secrets:ref");
    }

    @Test
    void healthNotionOk() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id))
                .thenReturn(
                        Optional.of(
                                new InstallationRepository.InstallationDetailRow(
                                        id, orgId, wsId, "notion", "N", "active", Map.of(), "secrets:r")));
        when(secrets.resolvePayload("secrets:r")).thenReturn(Map.of("integration_token", "t"));
        when(notionProbe.probe(any())).thenReturn(new NotionHealthProbe.ProbeResult(true, "ok"));

        var res = svc.health(wsId, id);

        assertThat(res.ok()).isTrue();
    }

    @Test
    void healthGenericConnectorSkipsProbe() throws Exception {
        var id = UUID.randomUUID();
        when(installations.findDetail(wsId, id))
                .thenReturn(
                        Optional.of(
                                new InstallationRepository.InstallationDetailRow(
                                        id, orgId, wsId, "custom", "C", "error", Map.of(), "local:none")));

        var res = svc.health(wsId, id);

        assertThat(res.ok()).isTrue();
        verify(notionProbe, org.mockito.Mockito.never()).probe(any());
    }
}
